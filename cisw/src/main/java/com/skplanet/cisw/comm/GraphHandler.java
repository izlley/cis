package com.skplanet.cisw.comm;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.AbstractMap.SimpleEntry;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.skplanet.cisw.qparser.QueryParser;
import com.skplanet.cisw.qparser.SymbolTable;
import com.skplanet.cisw.qparser.SymbolTable.KeyType;
import com.skplanet.cisw.qparser.SymbolTable.symPlot;
import com.skplanet.cisw.qparser.SymbolTable.ActionType;
import com.skplanet.cisw.qparser.SymbolTable.EventType;
import com.skplanet.cisw.core.Aggregator;
import com.skplanet.cisw.core.Aggregators;
import com.skplanet.cisw.core.Const;
import com.skplanet.cisw.core.Query;
import com.skplanet.cisw.core.CISW;
import com.skplanet.cisw.core.Tags;
import com.skplanet.cisw.graph.Plot;
import com.skplanet.ciscommon.stats.Histogram;
import com.skplanet.ciscommon.stats.StatsCollector;
import com.skplanet.ciscommon.uid.NoSuchUniqueName;
import com.skplanet.ciscommon.dataop.DataPoint;
import com.skplanet.ciscommon.dataop.DataPoints;
import com.skplanet.ciscommon.comm.BadRequestException;

/**
 * Stateless handler of HTTP graph requests (the {@code /q} endpoint).
 */
final class GraphHandler implements HttpRpc
{

    private static final Logger LOG = LoggerFactory
            .getLogger(GraphHandler.class);

    /** Number of times we had to do all the work up to running Gnuplot. */
    private static final AtomicInteger graphs_generated = new AtomicInteger();
    /** Number of times a graph request was served from disk, no work needed. */
    private static final AtomicInteger graphs_diskcache_hit = new AtomicInteger();

    /** Keep track of the latency of graphing requests. */
    private static final Histogram graphlatency = new Histogram(16000,
            (short) 2, 100);

    /** Keep track of the latency (in ms) introduced by running Gnuplot. */
    private static final Histogram gnuplotlatency = new Histogram(16000,
            (short) 2, 100);

    /** Executor to run Gnuplot in separate bounded thread pool. */
    private final ThreadPoolExecutor gnuplot;

    /** Directory where to cache query results. */
    private final String cachedir;

    /**
     * Constructor.
     */
    public GraphHandler()
    {
        // Gnuplot is mostly CPU bound and does only a little bit of IO at the
        // beginning to read the input data and at the end to write its output.
        // We want to avoid running too many Gnuplot instances concurrently as
        // it can steal a significant number of CPU cycles from us. Instead, we
        // allow only one per core, and we nice it (the nicing is done in the
        // shell script we use to start Gnuplot). Similarly, the queue we use
        // is sized so as to have a fixed backlog per core.
        final int ncores = Runtime.getRuntime().availableProcessors();
        gnuplot = new ThreadPoolExecutor(ncores, ncores, // Thread pool of a
                                                         // fixed size.
                /* 5m = */300000, MILLISECONDS, // How long to keep idle
                                                // threads.
                new ArrayBlockingQueue<Runnable>(20 * ncores), // XXX Don't
                                                               // hardcode?
                thread_factory);
        // ArrayBlockingQueue does not scale as much as LinkedBlockingQueue in
        // terms
        // of throughput but we don't need high throughput here. We use ABQ
        // instead
        // of LBQ because it creates far fewer references.
        cachedir = RpcHandler.getDirectoryFromSystemProp("cisw.http.cachedir");
    }

    public void execute(final CISW cisw, final HttpQuery query)
    {
        try
        {
            doGraph(cisw, query);
        }
        catch( BadRequestException e )
        {
            query.badRequest( e.getMessage() );
        }
        catch( Exception e )
        {
            query.internalError(e);
        }
    }

    private void doGraph(final CISW cisw, final HttpQuery query)
            throws Exception
    {
        /*
         * Initialize
         */
        QueryParser sParser = new QueryParser();
        SymbolTable sSymbolT = sParser.getSymbolTable();
        query.setSymbolTable(sSymbolT);
        /*
         * Parse the query
         */
        sParser.doParse(query.getQueryString());
        
        final String basepath = getGnuplotBasePath(sSymbolT);
        final long start_time = sSymbolT.getPeriodStartTime();
        final boolean nocache = sSymbolT.existOption("nocache");

        if (start_time == -1)
        {
            throw BadRequestException.missingParameter("start");
        }
        
        long end_time = sSymbolT.getPeriodEndTime();
        final long now = System.currentTimeMillis() / 1000;
        if (end_time == -1)
        {
            end_time = now;
        }
        final int max_age = computeMaxAge(query, start_time, end_time, now);
        if (!nocache && isDiskCacheHit(query, end_time, max_age, basepath))
        {
            // Cache Hit!
            return;
        }
        Query[] ciswqueries;

        /*
         * Alloc CiswQuery
         */
        ArrayList<String> sOptions = new ArrayList<String>();
        ciswqueries = allocCiswQuerys(cisw, sSymbolT, sOptions);

        for (final Query ciswquery : ciswqueries)
        {
            try
            {
                ciswquery.setStartTime(start_time);
            }
            catch (IllegalArgumentException e)
            {
                throw new BadRequestException("start time: " + e.getMessage());
            }
            try
            {
                ciswquery.setEndTime(end_time);
            }
            catch (IllegalArgumentException e)
            {
                throw new BadRequestException("end time: " + e.getMessage());
            }
        }
        final Plot plot = new Plot(start_time, end_time);
        setPlotDimensions(sSymbolT, plot);
        setPlotParams(sSymbolT, plot);

        final int nqueries = ciswqueries.length;
        @SuppressWarnings("unchecked")
        final HashSet<String>[] aggregated_tags = new HashSet[nqueries];
        final ArrayList<Integer> sInd4Legend = new ArrayList<Integer>();
        int npoints = 0;
        for (int i = 0; i < nqueries; i++)
        {
            try
            { // execute the CSIW query!
              // XXX Tttyhis is slow and will block Ne. TODO: Don't block.
              // TODO: Optimization: run each query in parallel.
                final DataPoints[] series = ciswqueries[i].run();
                for (final DataPoints datapoints : series)
                {
                    sInd4Legend.add(i);
                    plot.add( i, datapoints, sOptions.get(i) );
                    aggregated_tags[i] = new HashSet<String>();
                    aggregated_tags[i].addAll(datapoints.getAggregatedTags());
                    npoints += datapoints.aggregatedSize();
                }
            }
            catch (RuntimeException e)
            {
                logInfo(query, "Query failed (stack trace coming): "
                        + ciswqueries[i]);
                throw e;
            }
            ciswqueries[i] = null; // free()
        }
        ciswqueries = null; // free()

        if ( sSymbolT.getGetType().equals("ascii") )
        {
            respondAsciiQuery(query, max_age, basepath, plot);
            return;
        }
        else if( sSymbolT.getGetType().equals("json") )
        {
            respondJsonQuery(query, max_age, basepath, plot, aggregated_tags, npoints, sInd4Legend);
            return;
        }

        try
        {
            gnuplot.execute(new RunGnuplot(query, max_age, plot, basepath,
                    aggregated_tags, npoints));
        }
        catch (RejectedExecutionException e)
        {
            
            query.internalError(new Exception("Too many requests pending,"
                    + " please try again later", e));
        }
    }

    /**
     * Decides how long we're going to allow the client to cache our response.
     * <p>
     * Based on the query, we'll decide whether or not we want to allow the
     * client to cache our response and for how long.
     * 
     * @param query
     *            The query to serve.
     * @param start_time
     *            The start time on the query (32-bit unsigned int, secs).
     * @param end_time
     *            The end time on the query (32-bit unsigned int, seconds).
     * @param now
     *            The current time (32-bit unsigned int, seconds).
     * @return A positive integer, in seconds.
     */
    private static int computeMaxAge(final HttpQuery query,
            final long start_time, final long end_time, final long now)
    {
        // If the end time is in the future (1), make the graph uncacheable.
        // Otherwise, if the end time is far enough in the past (2) such that
        // no CISW can still be writing to rows for that time span and it's not
        // specified in a relative fashion (3) (e.g. "1d-ago"), make the graph
        // cacheable for a day since it's very unlikely that any data will
        // change
        // for this time span.
        // Otherwise (4), allow the client to cache the graph for ~0.1% of the
        // time span covered by the request e.g., for 1h of data, it's OK to
        // serve something 3s stale, for 1d of data, 84s stale.
        if ( (end_time > now) || (query.getSymbolTable().getPeriodStartTimeIsRelative()) )
        { // (1)
            return 0;
        }
        else if (end_time < now - Const.MAX_TIMESPAN // (2)
                && !query.getSymbolTable().getPeriodStartTimeIsRelative()
                && !query.getSymbolTable().getPeriodEndTimeIsRelative())
        {
            return 86400;
        }
        else
        { // (4)
            return (int) (end_time - start_time) >> 10;
        }
    }

    // Runs Gnuplot in a subprocess to generate the graph.
    private static final class RunGnuplot implements Runnable
    {

        private final HttpQuery query;
        private final int max_age;
        private final Plot plot;
        private final String basepath;
        private final HashSet<String>[] aggregated_tags;
        private final int npoints;
        private final String mCISWAddr = "http://192.168.16.75:2848";

        public RunGnuplot(final HttpQuery query, final int max_age,
                final Plot plot, final String basepath,
                final HashSet<String>[] aggregated_tags, final int npoints)
        {
            this.query = query;
            this.max_age = max_age;
            this.plot = plot;
            this.basepath = basepath;
            this.aggregated_tags = aggregated_tags;
            this.npoints = npoints;
        }

        public void run()
        {
            try
            {
                execute();
            }
            catch (BadRequestException e)
            {
                query.badRequest(e.getMessage());
            }
            catch (GnuplotException e)
            {
                query.badRequest("<pre>" + e.getMessage() + "</pre>");
            }
            catch (RuntimeException e)
            {
                query.internalError(e);
            }
            catch (IOException e)
            {
                query.internalError(e);
            }
        }

        private void execute() throws IOException
        {
            final int nplotted = runGnuplot(query, basepath, plot);
            if (query.getSymbolTable().getGetType().equals("png"))
            {
                query.sendFile(basepath + ".png", max_age, false);
            } else
            {
                if (nplotted > 0)
                {
                    query.sendReply(HttpQuery.makePage(
                            "CISW Query",
                            "Your graph is ready",
                            "<img src=\"" + query.request().getUri()
                                    + "&amp;png\"/><br/>" + "<small>("
                                    + nplotted + " points plotted in "
                                    + query.processingTimeMillis()
                                    + "ms)</small>"));
                } else
                {
                    query.sendReply(HttpQuery
                            .makePage(
                                    "CISW Query",
                                    "No results found",
                                    "<blockquote><h1>No results</h1>Your query didn't return"
                                            + " anything.  Try changing some parameters.</blockquote>"));
                }
            }

            // TODO: Expire old files from the on-disk cache.
            graphlatency.add(query.processingTimeMillis());
            graphs_generated.incrementAndGet();
            
            /*
             * check WHEN clause
             */
            if (query.getSymbolTable().getGetType().equals("png"))
            {
                EventType sWhenEvent = query.getSymbolTable().getWhenEvent();
                if( sWhenEvent != null )
                {
                    switch( sWhenEvent )
                    {
                        case DATA_EXISTS:
                            if( nplotted > 0 )
                            {
                                switch( query.getSymbolTable().getWhenAction() )
                                {
                                    case NOTIFY_EMAIL:
                                        makeAndSendEmail( sWhenEvent );
                                        break;
                                    case NOTIFY_SMS:
                                        break;
                                }
                            }
                            break;
                        case DATA_NONEXISTS:
                            if( nplotted <= 0 )
                            {
                                switch( query.getSymbolTable().getWhenAction() )
                                {
                                    case NOTIFY_EMAIL:
                                        makeAndSendEmail( sWhenEvent );
                                        break;
                                    case NOTIFY_SMS:
                                        break;
                                }
                            }
                            break;
                        default:
                            break;
                    }
                }
            }
        }
        
        private void makeAndSendEmail( EventType aWhenEvent )
        {
            // From addr
            final String sFrom = "CISAdmin@skplanet.com";
            // To addr
            final String sTo = query.getSymbolTable().getWhenTo();
            // Title
            String sTitle = "";
            switch( aWhenEvent )
            {
                case DATA_EXISTS:
                    sTitle = "[CIS ALERT] Abnormal datapoints exist !!";
                    break;
                case DATA_NONEXISTS:
                    sTitle = "[CIS ALERT] Datapoints do not exist !!";
                    break;
            }
            // Content
            String sContent = "Hi! This is the cis-alert robot.<br><br>";

            switch( aWhenEvent )
            {
                case DATA_EXISTS:
                    sContent += 
                            "<b>===== Alert info =====</b><br><br>" +
                            "<b>1. Query</b> : <br>&nbsp;&nbsp;&nbsp;&nbsp;" +
                            query.getQueryString() +"<br><br>" +
                            "<b>2. Graph</b> : <br>" +
                            "<p align=\"center\"><img src=\"" + mCISWAddr +
                            trimWhenClause(query.getRawQuery()) + "\" border=\"0\" width=\"900\" height=\"650\"></p>" +
                            "<b>==================</b><br><br>";
                    break;
                case DATA_NONEXISTS:
                    sContent +=
                            "<b>===== Alert info =====</b><br><br>" +
                            "<b>1. Query</b> : <br>&nbsp;&nbsp;&nbsp;&nbsp;" +
                            query.getQueryString() +"<br><br>" +
                            "<b>2. Graph</b> : <br>" +
                            "<p align=\"center\"><img src=\"" + mCISWAddr +
                            trimWhenClause(query.getRawQuery()) + "\" border=\"0\" width=\"900\" height=\"650\"></p>" +
                            "<b>==================</b><br><br>";
                    break;
            }

            MailSender.sendEmail(sFrom, sTo, sTitle, sContent);
            logInfo(query, "Send Email { \"From\" : \"" + sFrom + "\" , \"To\" : \"" + sTo + "\" , \"Title\" : \"" + sTitle + "\" }");
        }
        
        private String trimWhenClause( String aQuery )
        {
            String reform = aQuery.replaceAll("(?i)when.+email", "");
            return reform;
        }
    }

    /** Shuts down the thread pool used to run Gnuplot. */
    public void shutdown()
    {
        gnuplot.shutdown();
    }

    /**
     * Collects the stats and metrics tracked by this instance.
     * 
     * @param collector
     *            The collector to use.
     */
    public static void collectStats(final StatsCollector collector)
    {
        collector.record("http.latency", graphlatency, "type=graph");
        collector.record("http.latency", gnuplotlatency, "type=gnuplot");
        collector.record("http.graph.requests", graphs_diskcache_hit,
                "cache=disk");
        collector.record("http.graph.requests", graphs_generated, "cache=miss");
    }

    /** Returns the base path to use for the Gnuplot files. */
    private String getGnuplotBasePath(final SymbolTable aSymbolT)
    {
        // Super cheap caching mechanism: hash the query string.
        final HashMap<KeyType, Object> sClone = new HashMap<KeyType, Object>( aSymbolT.getSymbolTable() );
        // But first remove the parameters that don't influence the output.
        sClone.remove(KeyType.GET);
        return cachedir + Integer.toHexString(sClone.hashCode());
    }

    /**
     * Checks whether or not it's possible to re-serve this query from disk.
     * 
     * @param query
     *            The query to serve.
     * @param end_time
     *            The end time on the query (32-bit unsigned int, seconds).
     * @param max_age
     *            The maximum time (in seconds) we wanna allow clients to cache
     *            the result in case of a cache hit.
     * @param basepath
     *            The base path used for the Gnuplot files.
     * @return {@code true} if this request was served from disk (in which case
     *         processing can stop here), {@code false} otherwise (in which case
     *         the query needs to be processed).
     */
    private boolean isDiskCacheHit(final HttpQuery query, final long end_time,
            final int max_age, final String basepath) throws IOException
    {
        final SymbolTable sSymbolT = query.getSymbolTable();
        assert sSymbolT != null;
        String sGetType = sSymbolT.getGetType();

        String cachepath = basepath;
        if( sGetType.equals("ascii") )
        {
            cachepath += ".txt";
        }
        else if( sGetType.equals("json") )
        {
            cachepath += ".json";
        }
        else
        {
            cachepath += ".png";
        }

        final File cachedfile = new File(cachepath);
        if (cachedfile.exists())
        {
            final long bytes = cachedfile.length();
            if (bytes < 21)
            { // Minimum possible size for a PNG: 21 bytes.
              // For .txt files, <21 bytes is almost impossible.
                logWarn(query, "Cached " + cachepath + " is too small ("
                        + bytes + " bytes) to be valid.  Ignoring it.");
                return false;
            }
            if (staleCacheFile(query, end_time, max_age, cachedfile))
            {
                return false;
            }
            if (sGetType.equals("json"))
            {
                StringBuilder json = loadCachedJson(query, end_time, max_age,
                        basepath);
                if (json == null)
                {
                    json = new StringBuilder(50);
                    json.append("{\"cached\":{\"timing\":").append(query.processingTimeMillis()).append(
                            ",\"cachehit\":\"disk\"}}");
                }
                else
                {
                    json.append(",\"cached\":{\"timing\":").append(query.processingTimeMillis()).append(
                            ",\"cachehit\":\"disk\"}}");
                }
                query.sendReply(json);
            }
            else if (sGetType.equals("png")
                    || sGetType.equals("ascii"))
            {
                query.sendFile(cachepath, max_age, false);
            }
            else
            {
                query.sendReply(HttpQuery.makePage("CISW Query",
                        "Your graph is ready", "<img src=\""
                                + query.request().getUri()
                                + "&amp;png\"/><br/>"
                                + "<small>(served from disk cache)</small>"));
            }
            graphs_diskcache_hit.incrementAndGet();
            return true;
        }
        // We didn't find an image. Do a negative cache check. If we've seen
        // this query before but there was no result, we at least wrote the
        // JSON.
        final StringBuilder json = loadCachedJson(query, end_time, max_age,
                basepath);
        // If we don't have a JSON file it's a complete cache miss. If we have
        // one, and it says 0 data points were plotted, it's a negative cache
        // hit.
        if (json == null || json.lastIndexOf("\"points\":0") != -1) 
        {
            return false;
        }
        if (sGetType.equals("json"))
        {
            json.append(",\"cached\":{\"timing\":").append(query.processingTimeMillis()).append(
                    ",\"cachehit\":\"disk\"}}");
            query.sendReply(json);
        }
        else if (sGetType.equals("png"))
        {
            query.sendReply(" "); // Send back an empty response...
        }
        else
        {
            query.sendReply(HttpQuery.makePage("CISW Query", "No results",
                    "Sorry, your query didn't return anything.<br/>"
                            + "<small>(served from disk cache)</small>"));
        }
        graphs_diskcache_hit.incrementAndGet();
        return true;
    }

    /**
     * Returns whether or not the given cache file can be used or is stale.
     * 
     * @param query
     *            The query to serve.
     * @param end_time
     *            The end time on the query (32-bit unsigned int, seconds).
     * @param max_age
     *            The maximum time (in seconds) we wanna allow clients to cache
     *            the result in case of a cache hit. If the file is exactly that
     *            old, it is not considered stale.
     * @param cachedfile
     *            The file to check for staleness.
     */
    private static boolean staleCacheFile(final HttpQuery query,
            final long end_time, final long max_age, final File cachedfile)
    {
        final long mtime = cachedfile.lastModified() / 1000;
        if (mtime <= 0)
        {
            return true; // File doesn't exist, or can't be read.
        }

        final long now = System.currentTimeMillis() / 1000;
        // How old is the cached file, in seconds?
        final long staleness = now - mtime;
        if (staleness < 0)
        {   // Can happen if the mtime is "in the future".
            logWarn(query, "Not using file @ " + cachedfile + " with weird"
                    + " mtime in the future: " + mtime);
            return true; // Play it safe, pretend we can't use this file.
        }

        // Case 1: The end time is an absolute point in the past.
        // We might be able to re-use the cached file.
        if (0 < end_time && end_time < now)
        {
            // If the file was created prior to the end time, maybe we first
            // executed this query while the result was uncacheable. We can
            // tell by looking at the mtime on the file. If the file was created
            // before the query end time, then it contains partial results that
            // shouldn't be served again.
            return mtime < end_time;
        }

        // Case 2: The end time of the query is now or in the future.
        // The cached file contains partial data and can only be re-used if it's
        // not too old.
        if (staleness > max_age)
        {
            logInfo(query, "Cached file @ " + cachedfile.getPath() + " is "
                    + staleness + "s stale, which is more than its limit of "
                    + max_age + "s, and needs to be regenerated.");
            return true;
        }
        return false;
    }

    /**
     * Writes the given byte array into a file. This function logs an error but
     * doesn't throw if it fails.
     * 
     * @param query
     *            The query being handled (for logging purposes).
     * @param path
     *            The path to write to.
     * @param contents
     *            The contents to write into the file.
     */
    private static void writeFile(final HttpQuery query, final String path,
            final byte[] contents)
    {
        try
        {
            final FileOutputStream out = new FileOutputStream(path);
            try
            {
                out.write(contents);
            }
            finally
            {
                out.close();
            }
        }
        catch (FileNotFoundException e)
        {
            logError(query, "Failed to create file " + path, e);
        }
        catch (IOException e)
        {
            logError(query, "Failed to write file " + path, e);
        }
    }

    /**
     * Reads a file into a byte array.
     * 
     * @param query
     *            The query being handled (for logging purposes).
     * @param file
     *            The file to read.
     * @param max_length
     *            The maximum number of bytes to read from the file.
     * @return {@code null} if the file doesn't exist or is empty or couldn't be
     *         read, otherwise a byte array of up to {@code max_length} bytes.
     */
    private static byte[] readFile(final HttpQuery query, final File file,
            final int max_length)
    {
        final int length = (int) file.length();
        if (length <= 0)
        {
            return null;
        }
        else
        {
            if(length > max_length)
            {
                logError(query, "When reading " + file + ": File size is bigger than 50M. (" + length
                        + " bytes)");
                return null;
            }
        }
        FileInputStream in;
        try
        {
            in = new FileInputStream(file.getPath());
        }
        catch (FileNotFoundException e)
        {
            return null;
        }
        try
        {
            final byte[] buf = new byte[Math.min(length, max_length)];
            final int read = in.read(buf);
            if (read != buf.length)
            {
                logError(query, "When reading " + file + ": read only " + read
                        + " bytes instead of " + buf.length);
                return null;
            }
            return buf;
        }
        catch (IOException e)
        {
            logError(query, "Error while reading " + file, e);
            return null;
        }
        finally
        {
            try
            {
                in.close();
            }
            catch (IOException e)
            {
                logError(query, "Error while closing " + file, e);
            }
        }
    }

    /**
     * Attempts to read the cached {@code .json} file for this query.
     * 
     * @param query
     *            The query to serve.
     * @param end_time
     *            The end time on the query (32-bit unsigned int, seconds).
     * @param max_age
     *            The maximum time (in seconds) we wanna allow clients to cache
     *            the result in case of a cache hit.
     * @param basepath
     *            The base path used for the Gnuplot files.
     * @return {@code null} in case no file was found, or the contents of the
     *         file if it was found. In case some contents was found, it is
     *         truncated after the position of the last `:' in order to allow
     *         the caller to add the time taken to serve by the request and
     *         other JSON elements if wanted.
     */
    private StringBuilder loadCachedJson(final HttpQuery query,
            final long end_time, final long max_age, final String basepath)
    {
        final String jsonPath = basepath + ".json";
        File jsonCache = new File(jsonPath);
        if (staleCacheFile(query, end_time, max_age, jsonCache))
        {
            return null;
        }
        final byte[] json = readFile(query, jsonCache, Const.MAX_CACHE_BYTE); //50M
        if (json == null)
        {
            return null;
        }
        jsonCache = null;
        final StringBuilder buf = new StringBuilder(50 + json.length);
        // Add new attribute to The json file : cached:{"timing":N}
        buf.append(json);
        
        int sInd = buf.lastIndexOf("}");
        if (sInd != -1)
        {
            buf.setLength(sInd);
            return buf;
        }
        else
        {
            logError(query, "No `}' found in " + jsonPath + " (" + json.length
                    + " bytes) = " + new String(json));
            return null;
        }
    }

    /** Parses the {@code wxh} query parameter to set the graph dimension. */
    static void setPlotDimensions(final SymbolTable aSymT, final Plot plot)
    {
        final String wxh = aSymT.getOption("wxh");
        if (wxh != null && !wxh.isEmpty())
        {
            final int wxhlength = wxh.length();
            if (wxhlength < 7)
            { // 100x100 minimum.
                throw new BadRequestException("Parameter wxh too short: " + wxh);
            }
            final int x = wxh.indexOf('x', 3); // Start at 2 as min size is
                                               // 100x100
            if (x < 0)
            {
                throw new BadRequestException("Invalid wxh parameter: " + wxh);
            }
            try
            {
                final short width = Short.parseShort(wxh.substring(0, x));
                final short height = Short.parseShort(wxh.substring(x + 1,
                        wxhlength));
                try
                {
                    plot.setDimensions(width, height);
                }
                catch (IllegalArgumentException e)
                {
                    throw new BadRequestException("Invalid wxh parameter: "
                            + wxh + ", " + e.getMessage());
                }
            }
            catch (NumberFormatException e)
            {
                throw new BadRequestException("Can't parse wxh '" + wxh + "': "
                        + e.getMessage());
            }
        }
    }

    /**
     * Formats and quotes the given string so it's a suitable Gnuplot string.
     * 
     * @param s
     *            The string to stringify.
     * @return A string suitable for use as a literal string in Gnuplot.
     */
    private static String stringify(final String s)
    {
        final StringBuilder buf = new StringBuilder(1 + s.length() + 1);
        buf.append('"');
        HttpQuery.escapeJson(s, buf); // Abusing this function gets the job
                                      // done.
        buf.append('"');
        return buf.toString();
    }

    /**
     * Pops out of the query string the given parameter.
     * 
     * @param querystring
     *            The query string.
     * @param param
     *            The name of the parameter to pop out.
     * @return {@code null} if the parameter wasn't passed, otherwise the value
     *         of the last occurrence of the parameter.
     */
    private static String popParam(final Map<String, List<String>> querystring,
            final String param)
    {
        final List<String> params = querystring.remove(param);
        if (params == null)
        {
            return null;
        }
        return params.get(params.size() - 1);
    }

    /**
     * Applies the plot parameters from the query to the given plot.
     * 
     * @param aSymT
     *            The SymbolTable from which to get the query option values.
     * @param plot
     *            The plot on which to apply the parameters.
     */
    static void setPlotParams(final SymbolTable aSymT, final Plot plot)
    {
        final HashMap<String, String> params = new HashMap<String, String>();

        String value;
        if( (value = aSymT.getOption("yrange")) != null )
        {
            params.put("yrange", value);
        }
        if( (value = aSymT.getOption("y2range")) != null )
        {
            params.put("y2range", value);
        }
        if( (value = aSymT.getOption("ylabel")) != null )
        {
            params.put("ylabel", stringify(value));
        }
        if( (value = aSymT.getOption("y2label")) != null )
        {
            params.put("y2label", stringify(value));
        }
        if( (value = aSymT.getOption("yformat")) != null )
        {
            params.put("format y", stringify(value));
        }
        if( (value = aSymT.getOption("y2format")) != null )
        {
            params.put("format y2", stringify(value));
        }
        if( (value = aSymT.getOption("xformat")) != null )
        {
            params.put("format x", stringify(value));
        }
        if( (value = aSymT.getOption("key")) != null )
        {
            params.put("key", value);
        }
        if( (value = aSymT.getOption("title")) != null )
        {
            params.put("title", stringify(value));
        }
        if( (value = aSymT.getOption("bgcolor")) != null )
        {
            params.put("bgcolor", value);
        }
        if( (value = aSymT.getOption("fgcolor")) != null )
        {
            params.put("fgcolor", value);
        }
        if( aSymT.existOption("ylog") == true )
        {
            params.put("logscale", "y");
        }
        if( aSymT.existOption("y2log") == true )
        {
            params.put("logscale", "y2");
        }
        // This must remain after the previous `if' in order to properly
        // override
        // any previous `key' parameter if a `nokey' parameter is given.
        if( aSymT.existOption("nokey") == true )
        {
            params.put("key", null);
        }
        plot.setParams(params);
    }

    /**
     * Runs Gnuplot in a subprocess to generate the graph. <strong>This function
     * will block</strong> while Gnuplot is running.
     * 
     * @param query
     *            The query being handled (for logging purposes).
     * @param basepath
     *            The base path used for the Gnuplot files.
     * @param plot
     *            The plot object to generate Gnuplot's input files.
     * @return The number of points plotted by Gnuplot (0 or more).
     * @throws IOException
     *             if the Gnuplot files can't be written, or the Gnuplot
     *             subprocess fails to start, or we can't read the graph from
     *             the file it produces, or if we have been interrupted.
     * @throws GnuplotException
     *             if Gnuplot returns non-zero.
     */
    static int runGnuplot(final HttpQuery query, final String basepath,
            final Plot plot) throws IOException
    {
        final int nplotted = plot.dumpToFiles(basepath, query);
        final long start_time = System.nanoTime();
        final Process gnuplot = new ProcessBuilder(GNUPLOT, basepath + ".out",
                basepath + ".err", basepath + ".gnuplot").start();
        final int rv;
        try
        {
            rv = gnuplot.waitFor(); // Couldn't find how to do this
                                    // asynchronously.
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt(); // Restore the interrupted
                                                // status.
            throw new IOException("interrupted", e); // I hate checked
                                                     // exceptions.
        }
        finally
        {
            // We need to always destroy() the Process, otherwise we "leak" file
            // descriptors and pipes. Unless I'm blind, this isn't actually
            // documented in the Javadoc of the !@#$%^ JDK, and in Java 6
            // there's no
            // way to ask the stupid-ass ProcessBuilder to not create fucking
            // pipes.
            // I think when the GC kicks in the JVM may run some kind of a
            // finalizer
            // that closes the pipes, because I've never seen this issue on long
            // running CISW, except where ulimit -n was low (the default, 1024).
            gnuplot.destroy();
        }
        gnuplotlatency.add((int) ((System.nanoTime() - start_time) / 1000000));
        if (rv != 0)
        {
            final byte[] stderr = readFile(query, new File(basepath + ".err"),
                    4096);
            // Sometimes Gnuplot will error out but still create the file.
            new File(basepath + ".png").delete();
            if (stderr == null)
            {
                throw new GnuplotException(rv);
            }
            throw new GnuplotException(new String(stderr));
        }
        // Remove the files for stderr/stdout if they're empty.
        deleteFileIfEmpty(basepath + ".out");
        deleteFileIfEmpty(basepath + ".err");
        return nplotted;
    }

    private static void deleteFileIfEmpty(final String path)
    {
        final File file = new File(path);
        if (file.length() <= 0)
        {
            file.delete();
        }
    }

    /**
     * Respond to a query that wants the output in ASCII.
     * <p>
     * When a query specifies the "ascii" query string parameter, we send the
     * data points back to the client in plain text instead of sending a PNG.
     * 
     * @param query
     *            The query we're currently serving.
     * @param max_age
     *            The maximum time (in seconds) we wanna allow clients to cache
     *            the result in case of a cache hit.
     * @param basepath
     *            The base path used for the Gnuplot files.
     * @param plot
     *            The plot object to generate Gnuplot's input files.
     */
    private static void respondAsciiQuery(final HttpQuery query,
            final int max_age, final String basepath, final Plot plot)
    {
        final String path = basepath + ".txt";
        PrintWriter asciifile;
        try
        {
            asciifile = new PrintWriter(path);
        }
        catch (IOException e)
        {
            query.internalError(e);
            return;
        }
        try
        {
            final StringBuilder tagbuf = new StringBuilder();
            for (final DataPoints dp : plot.getDataPoints())
            {
                final String metric = dp.metricName();
                tagbuf.setLength(0);
                for (final Map.Entry<String, String> tag : dp.getTags()
                        .entrySet())
                {
                    tagbuf.append(' ').append(tag.getKey()).append('=')
                            .append(tag.getValue());
                }
                for (final DataPoint d : dp)
                {
                    asciifile.print(metric);
                    asciifile.print(' ');
                    asciifile.print(d.timestamp());
                    asciifile.print(' ');
                    if (d.isInteger())
                    {
                        asciifile.print(d.longValue());
                    }
                    else
                    {
                        final double value = d.doubleValue();
                        if (value != value || Double.isInfinite(value))
                        {
                            throw new IllegalStateException("NaN or Infinity:"
                                    + value + " d=" + d + ", query=" + query);
                        }
                        asciifile.print(value);
                    }
                    asciifile.print(tagbuf);
                    asciifile.print('\n');
                }
            }
        }
        finally
        {
            asciifile.close();
        }
        try
        {
            query.sendFile(path, max_age, false);
        }
        catch (IOException e)
        {
            query.internalError(e);
        }
    }

    /**
     * Respond to a query that wants the output in JSON.
     * <p>
     * When a query specifies the "json" query string parameter, we send the
     * data points back to the client in json format instead of sending a PNG.
     * 
     * @param query
     *            The query we're currently serving.
     * @param max_age
     *            The maximum time (in seconds) we wanna allow clients to cache
     *            the result in case of a cache hit.
     * @param basepath
     *            The base path used for the Gnuplot files.
     * @param plot
     *            The plot object to generate Gnuplot's input files.
     * @param aNumPoints
     *            Number of datapoints
     */
    private static void respondJsonQuery(final HttpQuery query,
            final int max_age, final String basepath, final Plot plot,
            HashSet<String>[] aAggreTags, int aNumPoints, ArrayList<Integer> aInd4Legend)
    {
        final String path = basepath + ".json";
        PrintWriter jsonFile;
        StringBuilder sReplyBuf = new StringBuilder(1024*1024); // init 1M
        try
        {
            jsonFile = new PrintWriter(path);
        }
        catch (IOException e)
        {
            query.internalError(e);
            return;
        }
        try
        {
            int i = 0;
            int j = 0;
            int k = 0;
            final StringBuilder sLabelBuf = new StringBuilder();
            
            /*{
             * "data" : [
             *      { "label" : "label1", "data" : [ [x1, y1], [x2,y2], [x3,y3], ... ] },
             *      { "label" : "label2", "data" : [ [x1, y1], [x2,y2], [x3,y3], ... ] },
             *      ...
             *     ]
             * }
             */
            
            jsonFile.print("{\"data\":[");
            sReplyBuf.append("{\"data\":[");
            for (final DataPoints dp : plot.getDataPoints())
            {
                sLabelBuf.setLength(0);
                
                /* BUG-39613 : Add Legend clause in CISQL */
                assert query.getSymbolTable() != null : "SymbolTable must be not-null.";
                if( query.getSymbolTable().getPlotLegendOption(aInd4Legend.get(i)) != null )
                {
                    makeLabelStr(sLabelBuf, dp.metricName(),
                                 dp.getAllTags(), query.getSymbolTable().getPlotLegendOption(aInd4Legend.get(i)));
                }
                else
                {
                    // default case
                    final String metric = dp.metricName();
                    sLabelBuf.setLength(0);
                    sLabelBuf.append(metric);
                    for (final Map.Entry<String, String> tag : dp.getTags()
                            .entrySet())
                    {
                        sLabelBuf.append('_'+tag.getValue());
                    }
                }
                
                i++;
                j = 0;
                for (final DataPoint d : dp)
                {
                    //
                    if( plot.getStarttime() <= d.timestamp() )
                    {
                        if( j == 0 )
                        {
                            if (k++ != 0)
                            {
                                jsonFile.print(',');
                                sReplyBuf.append(',');
                            }

                            jsonFile.print("{\"label\":\"" + sLabelBuf
                                    + "\",\"data\":[");
                            sReplyBuf.append("{\"label\":\"" + sLabelBuf
                                    + "\",\"data\":[");
                        }
                        
                        if( j++ != 0)
                        {
                            jsonFile.print(',');
                            sReplyBuf.append(',');
                        }
                        
                        jsonFile.print("[" + d.timestamp() + ",");
                        sReplyBuf.append("[" + d.timestamp() + ",");
                        if (d.isInteger())
                        {
                            jsonFile.print(d.longValue());
                            sReplyBuf.append(d.longValue());
                        }
                        else
                        {
                            final double value = d.doubleValue();
                            if (value != value || Double.isInfinite(value))
                            {
                                throw new IllegalStateException(
                                        "NaN or Infinity:" + value + " d=" + d
                                                + ", query=" + query);
                            }
                            jsonFile.print(value);
                            sReplyBuf.append(value);
                        }
                        jsonFile.print(']');
                        sReplyBuf.append(']');
                    }
                    //
                }
                if(j>0)
                {
                    jsonFile.print("]}");
                    sReplyBuf.append("]}");
                }
            }
            jsonFile.print(']');
            sReplyBuf.append(']');
            
            /*
             * res : { points:... , etags: [[...]], timing:... }
             */
            final StringBuilder sResBuf = new StringBuilder();
            
            sResBuf.append("{\"points\":").append(aNumPoints)
                .append(",\"etags\":[");
            
            for (final HashSet<String> tags : aAggreTags)
            {
                if (tags == null || tags.isEmpty())
                {
                    sResBuf.append("[]");
                }
                else
                {
                    HttpQuery.toJsonArray(tags, sResBuf);
                }
                sResBuf.append(',');
            }
            sResBuf.setCharAt(sResBuf.length() - 1, ']');
            // The "timing" field must remain last, loadCachedJson relies
            // this.
            sResBuf.append(",\"timing\":").append(query.processingTimeMillis())
                    .append('}');
            jsonFile.print("\n,\"res\":" + sResBuf + "}");
            sReplyBuf.append("\n,\"res\":" + sResBuf + "}");
        }
        finally
        {
            jsonFile.close();
        }
        try
        {
            query.sendReply(sReplyBuf);
            //query.sendFile(path, max_age, true);
        }
        catch (Exception e)
        {
            query.internalError(e);
        }
    }
    
    /* BUG-39613 : Add Legend clause in CISQL */
    private enum LegendParseState { START, TRANS, KNUM, VNUM, NUM }
    private static void makeLabelStr( StringBuilder aLegend, 
                                      String aMetric,
                                      ArrayList<SimpleEntry<String, String>> aTags,
                                      String aLegendQuery )
    {
        String sNum = "";
        char sCh;
        LegendParseState sState = LegendParseState.START;
        for( int i = 0 ; aLegendQuery.length() > i ; i++)
        {
            if( sState == LegendParseState.START )
            {
                switch( sCh = aLegendQuery.charAt(i) )
                {
                    case '\\':
                        sState = LegendParseState.TRANS;
                        break;
                    default:
                        aLegend.append(sCh);
                        break;
                }
            }
            else if( sState == LegendParseState.TRANS )
            {
                switch( sCh = aLegendQuery.charAt(i) )
                {
                    case 'M':
                        aLegend.append(aMetric);
                        sState = LegendParseState.START;
                        break;
                    case 'K':
                        sState = LegendParseState.KNUM;
                        break;
                    case 'V':
                        sState = LegendParseState.VNUM;
                        break;
                    default:
                        sState = LegendParseState.START;
                        aLegend.append("\\" + sCh);
                        break;
                }
            }
            else if( (sState == LegendParseState.KNUM) ||
                       (sState == LegendParseState.VNUM) )
            {
                sCh = aLegendQuery.charAt(i);

                if( Character.isDigit(sCh) )
                {
                    sNum += sCh;
                }
                else if( sCh == '[' )
                {
                    // do nothing
                }
                else if( sCh == ']' )
                {
                    switch(sState)
                    {
                        case KNUM:
                            if( sNum != "" )
                            {
                                aLegend.append(aTags.get( Integer.parseInt(sNum) - 1 ).getKey());
                            }
                            break;
                        case VNUM:
                            if( sNum != "" )
                            {
                                aLegend.append(aTags.get( Integer.parseInt(sNum) - 1 ).getValue());
                            }
                            break;
                        default:
                            assert false;
                            break;
                    }
                    sNum = "";
                    sState = LegendParseState.START;
                }
                else
                {
                    i--;
                    sNum = "";
                    sState = LegendParseState.START;
                }
            }
            else
            {
                assert false : "Wrong Legend state!";
            }
        }
    }
    
    /**
     * Parses the {@code /q} query in a list of {@link Query} objects.
     * 
     * @param cisw
     *            The CISW to use.
     * @param aSymbolT
     *            The SymbolTable object of HTTP query for {@code /q}.
     * @return The corresponding {@link Query} objects.
     * @throws BadRequestException
     *             if the query was malformed.
     * @throws IllegalArgumentException
     *             if the metric or tags were malformed.
     */
    private static Query[] allocCiswQuerys(final CISW cisw,
                                           final SymbolTable aSymbolT,
                                           ArrayList<String> aOption )
    {
        //final List<String> ms = query.getQueryStringParams("m");
        final ArrayList<symPlot> sPlotlist = aSymbolT.getPlotlist();
        
        if (sPlotlist == null)
        {
            throw BadRequestException.missingParameter("PLOT");
        }
        final Query[] ciswqueries = new Query[sPlotlist.size()];
        int nqueries = 0;
        
        int sPlotSize = sPlotlist.size();
        for( int i = 0 ; i < sPlotSize ; i++ )
        {
            String sAggf = aSymbolT.getPlotAggfunc(i);
            if( sAggf == null )
            {
                // default aggregation func
                sAggf = "sum";
            }
            final Aggregator agg = getAggregator(sAggf);
            final String metric = aSymbolT.getPlotMetric(i);
            final HashMap<String, String> parsedtags = new HashMap<String, String>();
            String sTagstr = aSymbolT.getPlotTags(i);
            if( sTagstr != null )
            {
                if( sTagstr.length() > 2 )
                {
                    try
                    {
                        for( final String tag : 
                              com.skplanet.cisw.core.Tags.splitString( sTagstr.substring( 1, sTagstr.length() - 1 ), ',') )
                        {
                            Tags.parse( parsedtags, tag );
                        }
                    }
                    catch (IllegalArgumentException e)
                    {
                        throw new IllegalArgumentException("When parsing tag '" + sTagstr
                                + "': " + e.getMessage());
                    }
                }
            }

            final boolean rate = aSymbolT.getPlotRateOption(i);
            final Query ciswquery = cisw.newQuery();
            try
            {
                ciswquery.setTimeSeries(metric, parsedtags, agg, rate);
            }
            catch (NoSuchUniqueName e)
            {
                throw new BadRequestException(e.getMessage());
            }
            // downsampling function & interval.
            String sDs = aSymbolT.getPlotDownSampleOption(i);
            if( sDs != null )
            {
                Aggregator downsampler;
                int sInd = sDs.indexOf('-');
                try
                {
                    downsampler = Aggregators.get( sDs.substring( sInd + 1) );
                }
                catch(NoSuchElementException e)
                {
                    throw new BadRequestException("No such downsampling function: "
                            + sDs.substring( sInd + 1));
                }
                final int interval = parseDuration( sDs.substring(0, sInd) );
                ciswquery.downsample(interval, downsampler);
            }
            ciswqueries[nqueries++] = ciswquery;
            
            if( aOption != null )
            {
                String sOption = "";
                if( aSymbolT.getPlotIsAxisy2(i) == true )
                {
                    sOption += " axis x1y2";
                }
                switch( aSymbolT.getPlotGraphtype(i) )
                {
                    case LINE:
                        sOption += " with lines lw 3";
                        break;
                    case LINEPOINT:
                        sOption += " with linespoints";
                        break;
                    case FILLEDLINE:
                        sOption += " with filledcurves y1=0";
                        break;
                    case IMPULSE:
                        sOption += " with impulses";
                        break;
                    case POINT:
                        sOption += " with points";
                        break;
                    case BOX:
                        sOption += " with boxes";
                        break;
                    case CIRCLE:
                        sOption += "";
                        break;
                    default:
                        break;
                }

                aOption.add(sOption);
            }
        }
        return ciswqueries;
    }

    /**
     * Returns the aggregator with the given name.
     * 
     * @param name
     *            Name of the aggregator to get.
     * @throws BadRequestException
     *             if there's no aggregator with this name.
     */
    private static final Aggregator getAggregator(final String name)
    {
        try
        {
            return Aggregators.get(name);
        }
        catch (NoSuchElementException e)
        {
            throw new BadRequestException("No such aggregation function: "
                    + name);
        }
    }

    /**
     * Parses a human-readable duration (e.g, "10m", "3h", "14d") into seconds.
     * <p>
     * Formats supported: {@code s}: seconds, {@code m}: minutes, {@code h}:
     * hours, {@code d}: days, {@code w}: weeks, {@code y}: years.
     * 
     * @param duration
     *            The human-readable duration to parse.
     * @return A strictly positive number of seconds.
     * @throws BadRequestException
     *             if the interval was malformed.
     */
    private static final int parseDuration(final String duration)
    {
        int interval;
        final int lastchar = duration.length() - 1;
        try
        {
            interval = Integer.parseInt(duration.substring(0, lastchar));
        }
        catch (NumberFormatException e)
        {
            throw new BadRequestException("Invalid duration (number): "
                    + duration);
        }
        if (interval <= 0)
        {
            throw new BadRequestException("Zero or negative duration: "
                    + duration);
        }
        switch (duration.charAt(lastchar))
        {
        case 's':
            return interval; // seconds
        case 'm':
            return interval * 60; // minutes
        case 'h':
            return interval * 3600; // hours
        case 'd':
            return interval * 3600 * 24; // days
        case 'w':
            return interval * 3600 * 24 * 7; // weeks
        case 'y':
            return interval * 3600 * 24 * 365; // years (screw leap years)
        }
        throw new BadRequestException("Invalid duration (suffix): " + duration);
    }

    /**
     * Returns whether or not a date is specified in a relative fashion.
     * <p>
     * A date is specified in a relative fashion if it ends in "-ago", e.g.
     * "1d-ago" is the same as "24h-ago".
     * 
     * @param query
     *            The HTTP query from which to get the query string parameter.
     * @param paramname
     *            The name of the query string parameter.
     * @return {@code true} if the parameter is passed and is a relative date.
     *         Note the method doesn't attempt to validate the relative date. So
     *         this function can return true on something that looks like a
     *         relative date, but is actually invalid once we really try to
     *         parse it.
     */
    /*
    private static boolean isRelativeDate(final HttpQuery query,
            final String paramname)
    {
        final String date = query.getQueryStringParam(paramname);
        return date == null || date.endsWith("-ago");
    }*/

    /**
     * Returns a timestamp from a date specified in a query string parameter.
     * Formats accepted are: - Relative: "5m-ago", "1h-ago", etc. See
     * {@link #parseDuration}. - Absolute human readable date:
     * "yyyy/MM/dd-HH:mm:ss". - UNIX timestamp (seconds since Epoch):
     * "1234567890".
     * 
     * @param query
     *            The HTTP query from which to get the query string parameter.
     * @param paramname
     *            The name of the query string parameter.
     * @return A UNIX timestamp in seconds (strictly positive 32-bit "unsigned")
     *         or -1 if there was no query string parameter named
     *         {@code paramname}.
     * @throws BadRequestException
     *             if the date is invalid.
     */
    /*
    private static long getQueryStringDate(final HttpQuery query,
            final String paramname)
    {
        final String date = query.getQueryStringParam(paramname);
        if (date == null)
        {
            return -1;
        }
        else if (date.endsWith("-ago"))
        {
            return (System.currentTimeMillis() / 1000 - parseDuration(date
                    .substring(0, date.length() - 4)));
        }
        long timestamp;
        if (date.length() < 5 || date.charAt(4) != '/')
        { // Already a timestamp?
            try
            {
                timestamp = Tags.parseLong(date); // => Looks like it.
            }
            catch (NumberFormatException e)
            {
                throw new BadRequestException("Invalid " + paramname
                        + " time: " + date + ". " + e.getMessage());
            }
        }
        else
        { // => Nope, there is a slash, so parse a date then.
            try
            {
                final SimpleDateFormat fmt = new SimpleDateFormat(
                        "yyyy/MM/dd-HH:mm:ss");
                timestamp = fmt.parse(date).getTime() / 1000;
            }
            catch (ParseException e)
            {
                throw new BadRequestException("Invalid " + paramname
                        + " date: " + date + ". " + e.getMessage());
            }
        }
        if (timestamp < 0)
        {
            throw new BadRequestException("Bad " + paramname + " date: " + date);
        }
        return timestamp;
    }*/

    private static final PlotThdFactory thread_factory = new PlotThdFactory();

    private static final class PlotThdFactory implements ThreadFactory
    {
        private final AtomicInteger id = new AtomicInteger(0);

        public Thread newThread(final Runnable r)
        {
            return new Thread(r, "Gnuplot #" + id.incrementAndGet());
        }
    }

    /** Name of the wrapper script we use to execute Gnuplot. */
    private static final String WRAPPER = "mygnuplot.sh";
    /** Path to the wrapper script. */
    private static final String GNUPLOT;
    static
    {
        GNUPLOT = findGnuplotHelperScript();
    }

    /**
     * Iterate through the class path and look for the Gnuplot helper script.
     * 
     * @return The path to the wrapper script.
     */
    private static String findGnuplotHelperScript()
    {
        final URL url = GraphHandler.class.getClassLoader()
                .getResource(WRAPPER);
        if (url == null)
        {
            throw new RuntimeException("Couldn't find " + WRAPPER + " on the"
                    + " CLASSPATH: " + System.getProperty("java.class.path"));
        }
        final String path = url.getFile();
        LOG.debug("Using Gnuplot wrapper at {}", path);
        final File file = new File(path);
        final String error;
        if (!file.exists())
        {
            error = "non-existent";
        }
        else if (!file.canExecute())
        {
            error = "non-executable";
        }
        else if (!file.canRead())
        {
            error = "unreadable";
        }
        else
        {
            return path;
        }
        throw new RuntimeException("The " + WRAPPER + " found on the"
                + " CLASSPATH (" + path + ") is a " + error + " file...  WTF?"
                + "  CLASSPATH=" + System.getProperty("java.class.path"));
    }

    // ---------------- //
    // Logging helpers. //
    // ---------------- //

    static void logInfo(final HttpQuery query, final String msg)
    {
        LOG.info(query.channel().toString() + ' ' + msg);
    }

    static void logWarn(final HttpQuery query, final String msg)
    {
        LOG.warn(query.channel().toString() + ' ' + msg);
    }

    static void logError(final HttpQuery query, final String msg)
    {
        LOG.error(query.channel().toString() + ' ' + msg);
    }

    static void logError(final HttpQuery query, final String msg,
            final Throwable e)
    {
        LOG.error(query.channel().toString() + ' ' + msg, e);
    }

}
