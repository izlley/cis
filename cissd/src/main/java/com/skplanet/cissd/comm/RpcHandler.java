package com.skplanet.cissd.comm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import com.stumbleupon.async.Callback;
import com.stumbleupon.async.Deferred;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;

// LJY
// import BuildData;
// import core.Aggregators;
import com.skplanet.cissd.core.CISSD;
import com.skplanet.ciscommon.comm.BadRequestException;
import com.skplanet.ciscommon.comm.ConnectionManager;
import com.skplanet.ciscommon.stats.StatsCollector;

/**
 * Stateless handler for RPCs (telnet-style or HTTP).
 */
final class RpcHandler extends SimpleChannelUpstreamHandler
{

    private static final Logger LOG = LoggerFactory.getLogger(RpcHandler.class);

    private static final AtomicLong telnet_rpcs_received = new AtomicLong();
    private static final AtomicLong http_rpcs_received = new AtomicLong();
    private static final AtomicLong exceptions_caught = new AtomicLong();

    /** Commands we can serve on the simple, telnet-style RPC interface. */
    private final HashMap<String, TelnetRpc> telnet_commands;
    /** RPC executed when there's an unknown telnet-style command. */
    private final TelnetRpc unknown_cmd = new Unknown();
    /** Commands we serve on the HTTP interface. */
    private final HashMap<String, HttpRpc> http_commands;

    /** The CISSD to use. */
    private final CISSD cissd;

    /**
     * Constructor.
     * 
     * @param cissd
     *            The CISSD to use.
     */
    public RpcHandler(final CISSD cissd)
    {
        this.cissd = cissd;

        telnet_commands = new HashMap<String, TelnetRpc>(6);
        http_commands = new HashMap<String, HttpRpc>(10);
        {
            final Stop stop = new Stop();
            telnet_commands.put("stop", stop);
            http_commands.put("stop", stop);
        }
        {
            final Stats stats = new Stats();
            telnet_commands.put("stats", stats);
            http_commands.put("stats", stats);
        }
        {
            final Version version = new Version();
            telnet_commands.put("version", version);
            http_commands.put("version", version);
        }
        {
            final PutDataPointRpc putdata = new PutDataPointRpc();
            telnet_commands.put("put", putdata);
            http_commands.put("put", putdata);
        }

        telnet_commands.put("exit", new Exit());
        telnet_commands.put("help", new Help());

        http_commands.put("", new HomePage());
        http_commands.put("logs", new LogsRpc());
    }

    @Override
    public void messageReceived(final ChannelHandlerContext ctx,
            final MessageEvent msgevent)
    {
        try
        {
            final Object message = msgevent.getMessage();
            if (message instanceof String[])
            {
                handleTelnetRpc(msgevent.getChannel(), (String[]) message);
            }
            else if (message instanceof HttpRequest)
            {
                handleHttpQuery(msgevent.getChannel(), (HttpRequest) message);
            }
            else
            {
                logError(msgevent.getChannel(), "Unexpected message type "
                        + message.getClass() + ": " + message);
                exceptions_caught.incrementAndGet();
            }
        }
        catch (Exception e)
        {
            Object pretty_message = msgevent.getMessage();
            if (pretty_message instanceof String[])
            {
                pretty_message = Arrays.toString((String[]) pretty_message);
            }
            logError(msgevent.getChannel(), "Unexpected exception caught"
                    + " while serving " + pretty_message, e);
            exceptions_caught.incrementAndGet();
        }
    }

    /**
     * Finds the right handler for a telnet-style RPC and executes it.
     * 
     * @param chan
     *            The channel on which the RPC was received.
     * @param command
     *            The split telnet-style command.
     */
    private void handleTelnetRpc(final Channel chan, final String[] command)
    {
        TelnetRpc rpc = telnet_commands.get(command[0]);
        if (rpc == null)
        {
            rpc = unknown_cmd;
        }
        telnet_rpcs_received.incrementAndGet();
        rpc.execute(cissd, chan, command);
    }

    /**
     * Finds the right handler for an HTTP query and executes it.
     * 
     * @param chan
     *            The channel on which the query was received.
     * @param req
     *            The parsed HTTP request.
     */
    private void handleHttpQuery(final Channel chan, final HttpRequest req)
    {
        http_rpcs_received.incrementAndGet();
        final HttpQuery query = new HttpQuery(req, chan);
        if (req.isChunked())
        {
            logError(
                    query,
                    "Received an unsupported chunked request: "
                            + query.request());
            query.badRequest("Chunked request not supported.");
            return;
        }
        try
        {
            final HttpRpc rpc = http_commands.get(getEndPoint(query));

            if (rpc != null)
            {
                rpc.execute(cissd, query);
            }
            else
            {
                query.notFound();
            }
        }
        catch (BadRequestException ex)
        {
            query.badRequest(ex.getMessage());
        }
        catch (Exception ex)
        {
            query.internalError(ex);
            exceptions_caught.incrementAndGet();
        }
    }

    /**
     * Returns the "first path segment" in the URI.
     * 
     * Examples:
     * 
     * <pre>
     *   URI request | Value returned
     *   ------------+---------------
     *   /           | ""
     *   /foo        | "foo"
     *   /foo/bar    | "foo"
     *   /foo?quux   | "foo"
     * </pre>
     * 
     * @param query
     *            The HTTP query.
     */
    private String getEndPoint(final HttpQuery query)
    {
        final String uri = query.request().getUri();
        if (uri.length() < 1)
        {
            throw new BadRequestException("Empty query");
        }
        if (uri.charAt(0) != '/')
        {
            throw new BadRequestException(
                    "Query doesn't start with a slash: <code>"
                    // TODO: HTML escape to avoid XSS.
                            + uri + "</code>");
        }
        final int questionmark = uri.indexOf('?', 1);
        final int slash = uri.indexOf('/', 1);
        int pos; // Will be set to where the first path segment ends.
        if (questionmark > 0)
        {
            if (slash > 0)
            {
                pos = (questionmark < slash ? questionmark // Request:
                                                           // /foo?bar/quux
                        : slash); // Request: /foo/bar?quux
            }
            else
            {
                pos = questionmark; // Request: /foo?bar
            }
        }
        else
        {
            pos = (slash > 0 ? slash // Request: /foo/bar
                    : uri.length()); // Request: /foo
        }
        return uri.substring(1, pos);
    }

    /**
     * Collects the stats and metrics tracked by this instance.
     * 
     * @param collector
     *            The collector to use.
     */
    public static void collectStats(final StatsCollector collector)
    {
        collector.record("rpc.received", telnet_rpcs_received, "type=telnet");
        collector.record("rpc.received", http_rpcs_received, "type=http");
        collector.record("rpc.exceptions", exceptions_caught);
        HttpQuery.collectStats(collector);
        PutDataPointRpc.collectStats(collector);
    }

    // ---------------------------- //
    // Individual command handlers. //
    // ---------------------------- //

    /** The "stop" command and "/stop" endpoint. */
    private final class Stop implements TelnetRpc, HttpRpc
    {
        public Deferred<Object> execute(final CISSD cissd, final Channel chan,
                final String[] cmd)
        {
            logWarn(chan, "shutdown requested");
            chan.write("Cleaning up and exiting now.\n");
            return doShutdown(cissd, chan);
        }

        public void execute(final CISSD cissd, final HttpQuery query)
        {
            logWarn(query, "shutdown requested");
            query.sendReply(HttpQuery.makePage("CISSD Exiting",
                    "You killed me", "Cleaning up and exiting now."));
            doShutdown(cissd, query.channel());
        }

        private Deferred<Object> doShutdown(final CISSD cissd,
                final Channel chan)
        {
            ConnectionManager.closeAllConnections();
            // Netty gets stuck in an infinite loop if we shut it down from
            // within a
            // NIO thread. So do this from a newly created thread.
            final class ShutdownNetty extends Thread
            {
                ShutdownNetty()
                {
                    super("ShutdownNetty");
                }

                public void run()
                {
                    chan.getFactory().releaseExternalResources();
                }
            }
            // Attempt to commit any data point still only in RAM.
            // TODO: Need a way of ensuring we don't spend more than X
            // seconds doing this. If we're asked to die, we should do so
            // promptly. Right now I believe we can spend an indefinite and
            // unbounded amount of time in the HBase client library.
            final class ShutdownCISSD implements Callback<Object, Object>
            {
                public Object call(final Object arg)
                {
                    if (arg instanceof Exception)
                    {
                        LOG.error("Unexpected exception while shutting down",
                                (Exception) arg);
                    }
                    new ShutdownNetty().start();
                    return arg;
                }

                public String toString()
                {
                    return "shutdown callback";
                }
            }
            return cissd.shutdown().addBoth(new ShutdownCISSD());
        }
    }

    /** The "exit" command. */
    private static final class Exit implements TelnetRpc
    {
        public Deferred<Object> execute(final CISSD cissd, final Channel chan,
                final String[] cmd)
        {
            chan.disconnect();
            return Deferred.fromResult(null);
        }
    }

    /** The "help" command. */
    private final class Help implements TelnetRpc
    {
        public Deferred<Object> execute(final CISSD cissd, final Channel chan,
                final String[] cmd)
        {
            final StringBuilder buf = new StringBuilder();
            buf.append("# Available commands: ");
            // TODO: Maybe sort them?
            for (final String command : telnet_commands.keySet())
            {
                buf.append(command).append(' ');
            }
            buf.append("\n");
            chan.write(buf.toString());
            // callback nothing
            return Deferred.fromResult(null);
        }
    }

    /** The home page ("GET /"). */
    private static final class HomePage implements HttpRpc
    {
        public void execute(final CISSD cissd, final HttpQuery query)
        {
            final StringBuilder buf = new StringBuilder(2048);
            buf.append("<div id=queryuimain></div>"
                    + "<noscript>You must have JavaScript enabled.</noscript>"
                    + "<iframe src=javascript:'' id=__gwt_historyFrame tabIndex=-1"
                    + " style=position:absolute;width:0;height:0;border:0>"
                    + "</iframe>");
            query.sendReply(HttpQuery.makePage(
                    "",
                    "CISSD", "CIS Data trigger (prototype1)", buf.toString()));
        }
    }

    /** The "stats" command and the "/stats" endpoint. */
    private static final class Stats implements TelnetRpc, HttpRpc
    {
        public Deferred<Object> execute(final CISSD cissd, final Channel chan,
                final String[] cmd)
        {
            final StringBuilder buf = new StringBuilder(1024);
            final StatsCollector collector = new StatsCollector("cissd")
            {
                @Override
                public final void emit(final String line)
                {
                    buf.append(line);
                }
            };
            doCollectStats(cissd, collector);
            chan.write(buf.toString());
            return Deferred.fromResult(null);
        }

        public void execute(final CISSD cissd, final HttpQuery query)
        {
            final boolean json = query.hasQueryStringParam("json");
            final StringBuilder buf = json ? null : new StringBuilder(2048);
            final ArrayList<String> stats = json ? new ArrayList<String>(64)
                    : null;
            final StatsCollector collector = new StatsCollector("cissd")
            {
                @Override
                public final void emit(final String line)
                {
                    if (json)
                    {
                        stats.add(line.substring(0, line.length() - 1)); // strip
                                                                         // the
                                                                         // '\n'
                    }
                    else
                    {
                        buf.append(line);
                    }
                }
            };
            doCollectStats(cissd, collector);
            if (json)
            {
                query.sendJsonArray(stats);
            }
            else
            {
                query.sendReply(buf);
            }
        }

        private void doCollectStats(final CISSD cissd,
                final StatsCollector collector)
        {
            collector.addHostTag();
            ConnectionManager.collectStats(collector);
            RpcHandler.collectStats(collector);
            cissd.collectStats(collector);
        }
    }

    /** For unknown commands. */
    private static final class Unknown implements TelnetRpc
    {
        public Deferred<Object> execute(final CISSD cissd, final Channel chan,
                final String[] cmd)
        {
            logWarn(chan, "unknown command : " + Arrays.toString(cmd));
            chan.write("unknown command: " + cmd[0] + ".  Try `help'.\n");
            return Deferred.fromResult(null);
        }
    }

    /** The "version" command. */
    private static final class Version implements TelnetRpc, HttpRpc
    {
        public Deferred<Object> execute(final CISSD cissd, final Channel chan,
                final String[] cmd)
        {
            if (chan.isConnected())
            {
                /*
                 * chan.write(BuildData.revisionString() + '\n' +
                 * BuildData.buildString() + '\n');
                 */
                chan.write("prototype!!!\n");
            }
            // callback nothing
            return Deferred.fromResult(null);
        }

        public void execute(final CISSD cissd, final HttpQuery query)
        {
            final boolean json = query.request().getUri().endsWith("json");
            // LJY
            /*
             * StringBuilder buf; if (json) { buf = new StringBuilder(157 +
             * BuildData.repo_status.toString().length() +
             * BuildData.user.length() + BuildData.host.length() +
             * BuildData.repo.length());
             * buf.append("{\"short_revision\":\"").append
             * (BuildData.short_revision)
             * .append("\",\"full_revision\":\"").append
             * (BuildData.full_revision)
             * .append("\",\"timestamp\":").append(BuildData.timestamp)
             * .append(",\"repo_status\":\"").append(BuildData.repo_status)
             * .append("\",\"user\":\"").append(BuildData.user)
             * .append("\",\"host\":\"").append(BuildData.host)
             * .append("\",\"repo\":\"").append(BuildData.repo) .append("\"}");
             * } else { final String revision = BuildData.revisionString();
             * final String build = BuildData.buildString(); buf = new
             * StringBuilder(2 // For the \n's + revision.length() +
             * build.length());
             * buf.append(revision).append('\n').append(build).append('\n'); }
             * 
             * query.sendReply(buf);
             */
            query.sendReply(new String("prototype!!").getBytes());
        }
    }

    /**
     * Returns the directory path stored in the given system property.
     * 
     * @param prop
     *            The name of the system property.
     * @return The directory path.
     * @throws IllegalStateException
     *             if the system property is not set or has an invalid value.
     */
    static String getDirectoryFromSystemProp(final String prop)
    {
        final String dir = System.getProperty(prop);
        String err = null;
        if (dir == null)
        {
            err = "' is not set.";
        }
        else if (dir.isEmpty())
        {
            err = "' is empty.";
        }
        else if (dir.charAt(dir.length() - 1) != '/')
        { // Screw Windows.
            err = "' is not terminated with `/'.";
        }
        if (err != null)
        {
            throw new IllegalStateException("System property `" + prop + err);
        }
        return dir;
    }

    // ---------------- //
    // Logging helpers. //
    // ---------------- //

    // private static void logInfo(final HttpQuery query, final String msg) {
    // LOG.info(query.channel().toString() + ' ' + msg);
    // }

    private static void logWarn(final HttpQuery query, final String msg)
    {
        LOG.warn(query.channel().toString() + ' ' + msg);
    }

    // private void logWarn(final HttpQuery query, final String msg,
    // final Exception e) {
    // LOG.warn(query.channel().toString() + ' ' + msg, e);
    // }

    private void logError(final HttpQuery query, final String msg)
    {
        LOG.error(query.channel().toString() + ' ' + msg);
    }

    // private static void logError(final HttpQuery query, final String msg,
    // final Exception e) {
    // LOG.error(query.channel().toString() + ' ' + msg, e);
    // }

    // private void logInfo(final Channel chan, final String msg) {
    // LOG.info(chan.toString() + ' ' + msg);
    // }

    private static void logWarn(final Channel chan, final String msg)
    {
        LOG.warn(chan.toString() + ' ' + msg);
    }

    // private void logWarn(final Channel chan, final String msg, final
    // Exception e) {
    // LOG.warn(chan.toString() + ' ' + msg, e);
    // }

    private void logError(final Channel chan, final String msg)
    {
        LOG.error(chan.toString() + ' ' + msg);
    }

    private void logError(final Channel chan, final String msg,
            final Exception e)
    {
        LOG.error(chan.toString() + ' ' + msg, e);
    }

}
