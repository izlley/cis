package com.skplanet.cisw.graph;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Map;
import java.util.TimeZone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.skplanet.ciscommon.dataop.DataPoint;
import com.skplanet.ciscommon.dataop.DataPoints;
import com.skplanet.cisw.comm.HttpQuery;
import com.skplanet.cisw.qparser.SymbolTable;
import com.skplanet.cisw.qparser.Token;

/**
 * Produces files to generate graphs with Gnuplot.
 * <p>
 * This class takes a bunch of {@link DataPoints} instances and generates a
 * Gnuplot script as well as the corresponding data files to feed to Gnuplot.
 */
public final class Plot
{

    private static final Logger LOG = LoggerFactory.getLogger(Plot.class);

    /** Start time (UNIX timestamp in seconds) on 32 bits ("unsigned" int). */
    private int start_time;

    /** End time (UNIX timestamp in seconds) on 32 bits ("unsigned" int). */
    private int end_time;

    /** All the DataPoints we want to plot. */
    private ArrayList<DataPoints> datapoints = new ArrayList<DataPoints>();

    /** Per-DataPoints Gnuplot options. */
    private ArrayList<String> options = new ArrayList<String>();

    private ArrayList<Integer> mPlotIndex = new ArrayList<Integer>();
    
    /** Global Gnuplot parameters. */
    private Map<String, String> params;

    /** Minimum width / height allowed. */
    private static final short MIN_PIXELS = 100;

    /** Width of the graph to generate, in pixels. */
    private short width = (short) 1024;

    /** Height of the graph to generate, in pixels. */
    private short height = (short) 768;

    private final class PlotCircleFile
    {
        double mStartDeg = 0.;
        double mDegVal = 0.;
        double mPercent = 0;
        boolean mIsLong = true;
        long mSumLong = 0;
        double mSumDouble = 0.;
        String mTagStr = null;
    }
    
    public int getStarttime()
    {
        return this.start_time;
    }
    
    /**
     * Number of seconds of difference to apply in order to get local time.
     * Gnuplot always renders timestamps in UTC, so we simply apply a delta to
     * get local time. If the local time changes (e.g. due to DST changes) we
     * won't pick up the change unless we restart. TODO(tsuna): Do we want to
     * recompute the offset every day to avoid this problem?
     */
    private static final int utc_offset = TimeZone.getDefault().getOffset(
            System.currentTimeMillis()) / 1000;

    /**
     * Constructor.
     * 
     * @param start_time
     *            Timestamp of the start time of the graph.
     * @param end_time
     *            Timestamp of the end time of the graph.
     * @throws IllegalArgumentException
     *             if either timestamp is 0 or negative.
     * @throws IllegalArgumentException
     *             if {@code start_time >= end_time}.
     */
    public Plot(final long start_time, final long end_time)
    {
        if ((start_time & 0xFFFFFFFF00000000L) != 0)
        {
            throw new IllegalArgumentException("Invalid start time: "
                    + start_time);
        }
        else if ((end_time & 0xFFFFFFFF00000000L) != 0)
        {
            throw new IllegalArgumentException("Invalid end time: " + end_time);
        }
        else if (start_time >= end_time)
        {
            throw new IllegalArgumentException("start time (" + start_time
                    + ") is greater than or equal to end time: " + end_time);
        }
        this.start_time = (int) start_time;
        this.end_time = (int) end_time;
    }

    /**
     * Sets the global parameters for this plot.
     * 
     * @param params
     *            Each entry is a Gnuplot setting that will be written as-is in
     *            the Gnuplot script file: {@code set KEY VALUE}. When the value
     *            is {@code null} the script will instead contain
     *            {@code unset KEY}.
     *            <p>
     *            Special parameters with a special meaning:
     *            <ul>
     *            <li>{@code bgcolor}: Either {@code transparent} or an RGB
     *            color in hexadecimal (with a leading 'x' as in {@code x01AB23}
     *            ).</li>
     *            <li>{@code fgcolor}: An RGB color in hexadecimal (
     *            {@code x42BEE7}).</li>
     *            </ul>
     */
    public void setParams(final Map<String, String> params)
    {
        this.params = params;
    }

    /**
     * Sets the dimensions of the graph (in pixels).
     * 
     * @param width
     *            The width of the graph produced (in pixels).
     * @param height
     *            The height of the graph produced (in pixels).
     * @throws IllegalArgumentException
     *             if the width or height are negative, zero or "too small"
     *             (e.g. less than 100x100 pixels).
     */
    public void setDimensions(final short width, final short height)
    {
        if (width < MIN_PIXELS || height < MIN_PIXELS)
        {
            final String what = width < MIN_PIXELS ? "width" : "height";
            throw new IllegalArgumentException(what + " smaller than "
                    + MIN_PIXELS + " in " + width + 'x' + height);
        }
        this.width = width;
        this.height = height;
    }

    /**
     * Adds some data points to this plot.
     * 
     * @param datapoints
     *            The data points to plot.
     * @param options
     *            The options to apply to this specific series.
     */
    public void add(final int aInd, final DataPoints datapoints, final String options)
    {
        // Technically, we could check the number of data points in the
        // datapoints argument in order to do something when there are none, but
        // this is potentially expensive with a SpanGroup since it requires
        // iterating through the entire SpanGroup. We'll check this later
        // when we're trying to use the data, in order to avoid multiple passes
        // through the entire data.
        this.datapoints.add(datapoints);
        this.options.add(options);
        this.mPlotIndex.add(aInd);
    }

    /**
     * Returns a view on the datapoints in this plot. Do not attempt to modify
     * the return value.
     */
    public Iterable<DataPoints> getDataPoints()
    {
        return datapoints;
    }

    /**
     * Generates the Gnuplot script and data files.
     * 
     * @param basepath
     *            The base path to use. A number of new files will be created
     *            and their names will all start with this string.
     * @return The number of data points sent to Gnuplot. This can be less than
     *         the number of data points involved in the query due to things
     *         like aggregation or downsampling.
     * @throws IOException
     *             if there was an error while writing one of the files.
     */
    public int dumpToFiles(final String basepath, final HttpQuery query) throws IOException
    {
        int npoints = 0;
        long sLongVal = 0;
        double sDoubleVal = 0;
        boolean sIsLong = false;
        boolean sIsDouble = false;
        final int nseries = datapoints.size();
        String datafiles[] = null;
        String sGtype = query.getSymbolTable().getOption("graphtype");
        
        if( (sGtype != null)? sGtype.equals("circle") : false )
        {
            datafiles = nseries > 0 ? new String[1] : null;
            datafiles[0] = basepath + "_0" + ".dat";
            final PrintWriter datafile = new PrintWriter(datafiles[0]);
            try
            {
                double sSumSum = 0.;
                ArrayList<PlotCircleFile> sContents = new ArrayList<PlotCircleFile>();
                for (int i = 0; i < nseries; i++)
                {
                    long sSumLong = 0;
                    double sSumDouble = 0.;

                    for( final DataPoint d : datapoints.get(i) )
                    {
                        sIsLong = sIsDouble = false;
                        if( d.isInteger() )
                        {
                            sIsLong = true;
                            sLongVal = d.longValue();
                            if( !query.verifyWhere( (double) sLongVal, mPlotIndex.get(i) ) )
                            {
                                continue;
                            }
                            sSumLong += sLongVal;
                        }
                        else
                        {
                            sIsDouble = true;
                            sDoubleVal = d.doubleValue();
                            if( !query.verifyWhere( sDoubleVal, mPlotIndex.get(i) ) )
                            {
                                continue;
                            }
                            if (sDoubleVal != sDoubleVal || Double.isInfinite(sDoubleVal))
                            {
                                throw new IllegalStateException(
                                        "NaN or Infinity found in"
                                                + " datapoints #" + i + ": "
                                                + sDoubleVal + " d=" + d);
                            }
                            sSumDouble += sDoubleVal;
                        }
                        final long ts = d.timestamp();
                        if (ts >= start_time && ts <= end_time)
                        {
                            npoints++;
                        }
                    }
                    
                    /*
                     * Insert ascended order
                     */
                    if( sIsLong == true )
                    {
                        PlotCircleFile sContent = new PlotCircleFile();
                        sContent.mSumLong = sSumLong;
                        sContent.mTagStr = datapoints.get(i).metricName() + datapoints.get(i).getTags();
                        if( sContents.size() == 0 )
                        {
                            sContents.add( sContent );
                        }
                        else
                        {
                            for( int j = 0 ; sContents.size() > j ; j++ )
                            {
                                PlotCircleFile sRow = sContents.get(j);
                                if( sRow.mIsLong ? sRow.mSumLong >= sSumLong :
                                    sRow.mSumDouble >= sSumLong )
                                {
                                    sContents.add( j, sContent );
                                    break;
                                }
                                else
                                {
                                    if( (sContents.size() - 1) == j )
                                    {
                                        sContents.add( sContent );
                                        break;
                                    }
                                    else
                                    {
                                        continue;
                                    }
                                }
                            }
                        }
                        sSumSum += sSumLong;
                    }
                    else
                    {
                        PlotCircleFile sContent = new PlotCircleFile();
                        sContent.mIsLong = false;
                        sContent.mSumDouble = sSumDouble;
                        sContent.mTagStr = datapoints.get(i).metricName() + datapoints.get(i).getTags();
                        if( sContents.size() == 0 )
                        {
                            sContents.add( sContent );
                        }
                        else
                        {
                            for( int j = 0 ; sContents.size() > j ; j++ )
                            {
                                PlotCircleFile sRow = sContents.get(j);
                                if( sRow.mIsLong ? sRow.mSumLong >= sSumLong :
                                    sRow.mSumDouble >= sSumDouble )
                                {
                                    sContents.add( j, sContent );
                                    break;
                                }
                                else
                                {
                                    if( (sContents.size() - 1) == j )
                                    {
                                        sContents.add( sContent );
                                        break;
                                    }
                                    else
                                    {
                                        continue;
                                    }
                                }
                            }
                        }
                        sSumSum += sSumDouble;
                    }
                }
                
                /*
                 * Calculate degree and %
                 */
                for( int i = 0 ; sContents.size() > i ; i++ )
                {
                    PlotCircleFile sContent = sContents.get(i);
                    if( i == 0 )
                    {
                        sContent.mStartDeg = 0.;
                        sContent.mPercent = (sContent.mIsLong?sContent.mSumLong:sContent.mSumDouble)/sSumSum*100.;
                        sContent.mDegVal = sContent.mPercent/100.*360.;
                    }
                    else
                    {
                        double sSumStartDeg = 0.;
                        for( int j = i ; j >= 0 ; j-- )
                        {
                            sSumStartDeg += sContents.get(j).mDegVal;
                        }
                        sContent.mStartDeg = sSumStartDeg;
                        sContent.mPercent = (sContent.mIsLong?sContent.mSumLong:sContent.mSumDouble)/sSumSum*100.;
                        sContent.mDegVal = sContent.mPercent/100.*360.;
                    }
                    
                    /*
                     * Write to file
                     */
                    datafile.print( sContent.mStartDeg + " " + sContent.mDegVal + " " +
                                        i + " " + "\"" );
                    datafile.format("%.1f", sContent.mPercent).flush();
                    datafile.print("%\"" + " " + "\"" +
                                       sContent.mTagStr + "\"\n" );
                }
            }
            finally
            {
                datafile.close();
            }
        }
        else
        {
            datafiles = nseries > 0 ? new String[nseries] : null;
            for (int i = 0; i < nseries; i++)
            {
                datafiles[i] = basepath + "_" + i + ".dat";
                final PrintWriter datafile = new PrintWriter(datafiles[i]);
                try
                {
                    for (final DataPoint d : datapoints.get(i))
                    {
                        sIsLong = sIsDouble = false;
                        if( d.isInteger() )
                        {
                            sIsLong = true;
                            sLongVal = d.longValue();
                            if( !query.verifyWhere( (double) sLongVal, mPlotIndex.get(i) ) )
                            {
                                continue;
                            }
                        }
                        else
                        {
                            sIsDouble = true;
                            sDoubleVal = d.doubleValue();
                            if( !query.verifyWhere( sDoubleVal, mPlotIndex.get(i) ) )
                            {
                                continue;
                            }
                        }
                        final long ts = d.timestamp();
                        if (ts >= start_time && ts <= end_time)
                        {
                            npoints++;
                        }
                        datafile.print(ts + utc_offset);
                        datafile.print(' ');
                        if ( sIsLong == true )
                        {
                            datafile.print(sLongVal);
                        }
                        else
                        {
                            if (sDoubleVal != sDoubleVal || Double.isInfinite(sDoubleVal))
                            {
                                throw new IllegalStateException(
                                        "NaN or Infinity found in"
                                                + " datapoints #" + i + ": "
                                                + sDoubleVal + " d=" + d);
                            }
                            datafile.print(sDoubleVal);
                        }
                        datafile.print('\n');
                    }
                }
                finally
                {
                    datafile.close();
                }
            }
        }

        if (npoints == 0)
        {
            // Gnuplot doesn't like empty graphs when xrange and yrange aren't
            // entirely defined, because it can't decide on good ranges with no
            // data. We always set the xrange, but the yrange is supplied by the
            // user. Let's make sure it defines a min and a max.
            params.put("yrange", "[0:10]"); // Doesn't matter what values we
                                            // use.
        }
        writeGnuplotScript( basepath, datafiles, query.getSymbolTable() );
        return npoints;
    }

    /**
     * Generates the Gnuplot script.
     * 
     * @param basepath
     *            The base path to use.
     * @param datafiles
     *            The names of the data files that need to be plotted, in the
     *            order in which they ought to be plotted. It is assumed that
     *            the ith file will correspond to the ith entry in
     *            {@code datapoints}. Can be {@code null} if there's no data to
     *            plot.
     */
    private void writeGnuplotScript(final String basepath,
            final String[] datafiles, final SymbolTable aSymT) throws IOException
    {
        final String script_path = basepath + ".gnuplot";
        final PrintWriter gp = new PrintWriter(script_path);
        String sGtype = aSymT.getOption("graphtype");
        
        try
        {
            /**
             * common script
             */
            // XXX don't hardcode all those settings. At least not like that.
            gp.append("set term png small size ")
                    // Why the fuck didn't they also add methods for numbers?
                    .append(Short.toString(width)).append(",")
                    .append(Short.toString(height));
            final String fgcolor = params.remove("fgcolor");
            String bgcolor = params.remove("bgcolor");
            if (fgcolor != null && bgcolor == null)
            {
                // We can't specify a fgcolor without specifying a bgcolor.
                bgcolor = "xFFFFFF"; // So use a default.
            }
            if (bgcolor != null)
            {
                if (fgcolor != null && "transparent".equals(bgcolor))
                {
                    // In case we need to specify a fgcolor but we wanted a
                    // transparent
                    // background, we also need to pass a bgcolor otherwise the
                    // first
                    // hex color will be mistakenly taken as a bgcolor by
                    // Gnuplot.
                    bgcolor = "transparent xFFFFFF";
                }
                gp.append(' ').append(bgcolor);
            }
            if (fgcolor != null)
            {
                gp.append(' ').append(fgcolor);
            }
            gp.append('\n');
            if (params != null)
            {
                for (final Map.Entry<String, String> entry : params.entrySet())
                {
                    final String key = entry.getKey();
                    final String value = entry.getValue();
                    if (value != null)
                    {
                        gp.append("set ").append(key).append(' ').append(value)
                                .write('\n');
                    }
                    else
                    {
                        gp.append("unset ").append(key).write('\n');
                    }
                }
            }
            
            /**
             * Graphtype dependent script
             */
            if( sGtype == null )
            {
                sGtype = "";
            }

            if( sGtype.equals("stack") )
            {
                stackGenGnuscript( gp, basepath, datafiles, aSymT );
            }
            else if( sGtype.equals("circle") )
            {
                circleGenGnuscript( gp, basepath, datafiles, aSymT );
            }
            else
            {
                generalGenGnuscript( sGtype, gp, basepath, datafiles, aSymT );
            }
        }
        finally
        {
            gp.close();
            LOG.info("Wrote Gnuplot script to " + script_path);
        }
    }

    void circleGenGnuscript( final PrintWriter aGp,
                                       final String aBasePath,
                                       final String[] aDatafiles,
                                       final SymbolTable aSymT )
    {
        final int nseries = datapoints.size();
        if (nseries > 0)
        {
            aGp.write(
            "unset key\n" +
            "set border 0\n" +
            "unset tics\n" +
            "unset colorbox\n" +
            "set yrange [-20:20]\n" +
            "set xrange [-20:20]\n" +
            "set palette model HSV func gray*0.8, 0.7, 0.9\n" +
            // urange and vrange govern the parametric variables.
            "set macro\n" +
            "keyx = 0.\n" +
            "keyy = 0.\n" +
            "keyr = 10.\n" +
            "types = " + nseries + "\n" +
            "set angle degree\n" +
            "set output \"" + aBasePath + ".png\"\n"
            );
            
            aGp.write(
                    "plot '" + aDatafiles[0] + "' using (keyx):(keyy):(keyr):1:($1+$2):3 " +
                    "with circles lc pal fs solid 1.0 border rgb \"white\", " +
                    "for [i=0:types-1] '' using " +
                    "(keyx+(keyr+1.5)*cos($1+0.5*$2)):(keyy+(keyr+1.5)*sin($1+0.5*$2)):5 " +
                    "every ::i::i with labels font \"Times-Italic,10\", " +
                    "for [i=0:types-1] '' using " +
                    "(keyx+(keyr*0.6)*cos($1+0.5*$2)):(keyy+(keyr*0.6)*sin($1+0.5*$2)):4 " +
                    "every ::i::i with labels font \"Helvetica,10\" tc rgb \"white\"");
        }
        else
        {
            aGp.write("unset key\n");
            if (params == null || !params.containsKey("label"))
            {
                aGp.write("set label \"No data\" at graph 0.5,0.9 center\n");
            }
        }
    }
    
    void stackGenGnuscript( final PrintWriter aGp,
                                        final String aBasePath,
                                        final String[] aDatafiles,
                                        final SymbolTable aSymT )
    {
        final int nseries = datapoints.size();
        
        aGp.append(
                        "set xdata time\n" + "set timefmt \"%s\"\n"
                        + "set xtic rotate\n" + "set output \"")
                .append(aBasePath + ".png")
                .append("\"\n" + "set xrange [\"")
                .append(String.valueOf(start_time + utc_offset))
                .append("\":\"")
                .append(String.valueOf(end_time + utc_offset))
                .append("\"]\n");
        if (!params.containsKey("format x"))
        {
            aGp.append("set format x \"").append(xFormat()).append("\"\n");
        }
        
        if (nseries > 0)
        {
            aGp.write("set style fill solid border -1\n" +
                          "set style data boxes\n" );
            aGp.write("set grid\n");
            
            if( !params.containsKey("key") )
            {
                aGp.write("set key right box\n");
            }
        }
        else
        {
            aGp.write("unset key\n");
            if (params == null || !params.containsKey("label"))
            {
                aGp.write("set label \"No data\" at graph 0.5,0.9 center\n");
            }
        }
        
        aGp.write("plot ");

        for (int i = 0; i < nseries; i++)
        {
            String sSumstr = "";
            final DataPoints dp = datapoints.get(nseries - i - 1);
            final String title = dp.metricName() + dp.getTags();
            if( i == 0 )
            {
                aGp.write(" '< paste");
                for( int j=0 ; j < nseries ; j++ )
                {
                    aGp.append(" \"").append(aDatafiles[j]).write('"');
                }
                for( int k=nseries*2 ; k > 0 ; k -= 2 )
                {
                    sSumstr += "$"+k;
                    if( k > 2 )
                    {
                        sSumstr += "+";
                    }
                }
                aGp.append("' using 1:(").append(sSumstr).append(") title \"")
                .append(title).write('"');
            }
            else
            {
                for( int k=(nseries-i)*2 ; k > 0 ; k -= 2 )
                {
                    sSumstr += "$"+k;
                    if( k > 2 )
                    {
                        sSumstr += "+";
                    }
                }
                aGp.append(" \"\" using 1:(").append(sSumstr).append(") title \"")
                .append(title).write('"');
            }
            final String opts = options.get(i);
            if (!opts.isEmpty())
            {
                aGp.append(' ').write(opts);
            }
            if (i != nseries - 1)
            {
                aGp.print(", \\");
            }
            aGp.write('\n');
        }
        
        if (nseries == 0)
        {
            aGp.write('0');
        }
    }
    
    void generalGenGnuscript( final String aGtype,
                                          final PrintWriter aGp,
                                          final String aBasePath,
                                          final String[] aDatafiles,
                                          final SymbolTable aSymT )
    {
        boolean sIsLine = false;
        final int nseries = datapoints.size();

        aGp.append(
                        "set xdata time\n" + "set timefmt \"%s\"\n"
                        + "set xtic rotate\n" + "set output \"")
                .append(aBasePath + ".png")
                .append("\"\n" + "set xrange [\"")
                .append(String.valueOf(start_time + utc_offset))
                .append("\":\"")
                .append(String.valueOf(end_time + utc_offset))
                .append("\"]\n");
        if (!params.containsKey("format x"))
        {
            aGp.append("set format x \"").append(xFormat()).append("\"\n");
        }

        if (nseries > 0)
        {
            String sGtype = aSymT.getOption("graphtype");

            Token sKey = Token.LINEPOINT;
            if( sGtype != null )
            {
                sKey = Token.valueOf(sGtype.toUpperCase());
            }

            switch( sKey )
            {
                case LINEPOINT:
                    aGp.write("set style data linespoints\n");
                    break;
                case LINE:
                    aGp.write("set style data lines\n");
                    sIsLine = true;
                    break;
                case FILLEDLINE:
                    aGp.write("set style fill transparent solid 0.50 noborder\n" +
                            "set style data filledcurves y1=0\n" +
                            "set terminal png truecolor\n" );
                    break;
                case IMPULSE:
                    aGp.write("set style data impulses\n");
                    break;
                case POINT:
                    aGp.write("set style data points\n");
                    break;
                case BOX:
                    aGp.write("set style fill transparent solid 0.50 noborder\n" +
                            "set style data boxes\n" +
                            "set terminal png truecolor\n" );
                    break;
                default:
                    // default = LINEPOINT
                    aGp.write("set style data linespoints\n");
            }
            aGp.write("set grid\n");
            
            if( !params.containsKey("key") )
            {
                aGp.write("set key right box\n");
            }
        }
        else
        {
            aGp.write("unset key\n");
            if (params == null || !params.containsKey("label"))
            {
                aGp.write("set label \"No data\" at graph 0.5,0.9 center\n");
            }
        }
        
        for (final String opts : options)
        {
            if (opts.contains("x1y2"))
            {
                // Create a second scale for the y-axis on the right-hand
                // side.
                aGp.write("set y2tics border\n");
                break;
            }
        }
        
        if( aSymT.getPlotlist() != null )
        {
            // set transparency
            for( int i = 0, size = aSymT.getPlotlist().size() ; size > i ; i++ )
            {
                if( (aSymT.getPlotGraphtype(i) == Token.FILLEDLINE) ||
                    (aSymT.getPlotGraphtype(i) == Token.BOX) )
                {
                    aGp.write("set style fill transparent solid 0.50 noborder\n" +
                                "set terminal png truecolor\n" );
                    break;
                }
            }
        }
        
        aGp.write("plot ");
        for (int i = 0; i < nseries; i++)
        {
            final DataPoints dp = datapoints.get(i);
            final String title = dp.metricName() + dp.getTags();
            aGp.append(" \"").append(aDatafiles[i])
            .append("\" using 1:2 title \"")
            // TODO: Escape double quotes in title.
            .append(title).write('"');
            final String opts = options.get(i);
            if (!opts.isEmpty())
            {
                aGp.append(' ').write(opts);
            }
            if ( sIsLine == true )
            {
                aGp.append(' ').write("lw 3");
            }
            if (i != nseries - 1)
            {
                aGp.print(", \\");
            }
            aGp.write('\n');
        }

        if (nseries == 0)
        {
            aGp.write('0');
        }
    }

    /**
     * Finds some sensible default formatting for the X axis (time).
     * 
     * @return The Gnuplot time format string to use.
     */
    private String xFormat()
    {
        long timespan = end_time - start_time;
        if (timespan < 2100)
        { // 35m
            return "%H:%M:%S";
        }
        else if (timespan < 86400)
        { // 1d
            return "%H:%M";
        }
        else if (timespan < 604800)
        { // 1w
            return "%a %H:%M";
        }
        else if (timespan < 1209600)
        { // 2w
            return "%a %d %H:%M";
        }
        else if (timespan < 7776000)
        { // 90d
            return "%b %d";
        }
        else
        {
            return "%Y/%m/%d";
        }
    }

}
