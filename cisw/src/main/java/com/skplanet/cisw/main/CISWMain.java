package com.skplanet.cisw.main;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.logging.Slf4JLoggerFactory;
import org.hbase.async.HBaseClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Level;

import com.skplanet.ciscommon.arg.ArgParser;
import com.skplanet.cisw.core.CISW;
import com.skplanet.cisw.comm.PipelineMgr;

/**
 * Main class of the CISW, the Time Series Daemon.
 * 
 * @author leejy
 */
final class CISWMain
{
    private static final short DEFAULT_FLUSH_INTERVAL = 1000;
    private static final boolean DONT_CREATE = false;
    private static final boolean CREATE_IF_NEEDED = true;
    private static final boolean MUST_BE_WRITEABLE = true;

    static
    {
        InternalLoggerFactory.setDefaultFactory(new Slf4JLoggerFactory());
    }

    public static void main(String[] args)
    {
        Logger sLog = LoggerFactory.getLogger(CISWMain.class);
        sLog.info("Starting");
        // LJY
        // log.info(BuildData.revisionString());
        // log.info(BuildData.buildString());

        final ArgParser sArgParser = new ArgParser();
        addCommonArgs(sArgParser);
        sArgParser.addOption("--port", "NUM", "CISW TCP port to listen on.");
        sArgParser.addOption("--webroot", "PATH",
                "Web root path to serve web static files (/wr URL).");
        sArgParser.addOption("--cachedir", "PATH",
                "Directory under which to cache result of requests.");
        sArgParser.addOption("--flush-interval", "MSEC",
                "Maximum time for which a new data point can be buffered"
                        + " (default: " + DEFAULT_FLUSH_INTERVAL + ").");
        addAutoMetricArg(sArgParser);
        addVerbose(sArgParser);
        args = optionParse(sArgParser, args);
        if (args == null || !sArgParser.exists("--port")
                || !sArgParser.exists("--webroot")
                || !sArgParser.exists("--cachedir"))
        {
            printUsage(sArgParser, "Invalid usage.", 1);
        }
        else
        {
            if (args.length != 0)
            {
                printUsage(sArgParser, "Too many arguments.", 2);
            }
        }
        final short sFlushInterval = getFlushInterval(sArgParser);

        setDirectoryInSystemProps("cisw.http.webroot",
                sArgParser.get("--webroot"), DONT_CREATE, !MUST_BE_WRITEABLE);
        setDirectoryInSystemProps("cisw.http.cachedir",
                sArgParser.get("--cachedir"), CREATE_IF_NEEDED,
                MUST_BE_WRITEABLE);

        final NioServerSocketChannelFactory sFactory = new NioServerSocketChannelFactory(
                Executors.newCachedThreadPool(),
                Executors.newCachedThreadPool());
        final HBaseClient sClient = getHBaseClient(sArgParser);

        try
        {
            final String sTable = sArgParser.get("--table-data", "cis_data");
            final String sUidTable = sArgParser.get("--table-id", "cis_id");
            sClient.ensureTableExists(sTable).joinUninterruptibly();
            sClient.ensureTableExists(sUidTable).joinUninterruptibly();
            sClient.setFlushInterval(sFlushInterval);

            final CISW sCisw = new CISW(sClient, sTable, sUidTable);
            regShutdownHook(sCisw);

            final ServerBootstrap sServer = new ServerBootstrap(sFactory);
            sServer.setPipelineFactory(new PipelineMgr(sCisw));
            sServer.setOption("child.tcpNoDelay", true);
            sServer.setOption("child.keepAlive", true);
            // In the case of stream-oriented sockets, this socket option will
            // usually determine whether the socket can be bound to a socket
            // address
            // when a previous connection involving that socket address is in
            // the TIME_WAIT state.
            sServer.setOption("reuseAddress", true);

            final InetSocketAddress sAddr = new InetSocketAddress(
                    Integer.parseInt(sArgParser.get("--port")));
            sServer.bind(sAddr);
            sLog.info("Ready to serve on " + sAddr);
        }
        catch (Throwable e)
        {
            sFactory.releaseExternalResources();
            try
            {
                sClient.shutdown().joinUninterruptibly();
            }
            catch (Exception ee)
            {
                sLog.error("Failed to shutdown HBase client", ee);
            }
            throw new RuntimeException("Initialization failed", e);
        }
        // The server is now running in separate threads
    }

    static void printUsage(final ArgParser aArgp, final String aErrmsg,
            final int aRet)
    {
        System.err.println(aErrmsg);
        System.err.println("# Usage: cisw --port=PORT"
                + " --webroot=PATH --cachedir=PATH\n");
        if (aArgp != null)
        {
            System.err.print(aArgp.usage());
        }
        System.exit(aRet);
    }

    /**
     * Ensures the given directory path is usable and set it as a system prop.
     * In case of problem, this function calls {@code System.exit}.
     * 
     * @param prop
     *            The name of the system property to set.
     * @param dir
     *            The path to the directory that needs to be checked.
     * @param need_write
     *            Whether or not the directory must be writeable.
     * @param create
     *            If {@code true}, the directory {@code dir} will be created if
     *            it doesn't exist.
     */
    private static void setDirectoryInSystemProps(final String aProp,
            final String aDir, final boolean aNeedwrite, final boolean aCreate)
    {
        final File f = new File(aDir);
        final String sPath = f.getPath();
        if (!f.exists() && !(aCreate && f.mkdirs()))
        {
            printUsage(null, "No such directory: " + sPath, 3);
        }
        else if (!f.isDirectory())
        {
            printUsage(null, "Not a directory: " + sPath, 3);
        }
        else if (aNeedwrite && !f.canWrite())
        {
            printUsage(null, "Cannot write to directory: " + sPath, 3);
        }
        System.setProperty(aProp, sPath + '/');
    }

    private static short getFlushInterval(final ArgParser aArgp)
    {
        final String sFlush = aArgp.get("--flush-interval");
        if (sFlush == null)
        {
            return DEFAULT_FLUSH_INTERVAL;
        }
        final short sFlushinterval = Short.parseShort(sFlush);
        if (sFlushinterval < 0)
        {
            throw new IllegalArgumentException(
                    "--flush-interval should be positive: " + sFlushinterval);
        }
        return sFlushinterval;
    }

    private static void regShutdownHook(final CISW aCisw)
    {
        final class CISWShutdown extends Thread
        {
            public CISWShutdown()
            {
                super("CISWShutdown");
            }

            public void run()
            {
                try
                {
                    aCisw.shutdown().join();
                }
                catch (Exception e)
                {
                    LoggerFactory.getLogger(CISWShutdown.class).error(
                            "Uncaught exception during shutdown", e);
                }
            }
        }
        Runtime.getRuntime().addShutdownHook(new CISWShutdown());
    }

    /** Adds common CISW options to the given {@code argp}. */
    static void addCommonArgs(final ArgParser aArgp)
    {
        aArgp.addOption("--table-data", "TABLE",
                "Name of the HBase table where to store the time series"
                        + " (default: cis_data).");
        aArgp.addOption("--table-id", "TABLE",
                "Name of the HBase table to use for Unique IDs"
                        + " (default: cis-id).");
        aArgp.addOption("--zkquorum", "SPEC",
                "Specification of the ZooKeeper quorum to use"
                        + " (default: localhost).");
        aArgp.addOption("--zkbasedir", "PATH",
                "Path under which is the znode for the -ROOT- region"
                        + " (default: /hbase).");
    }

    /** Adds a --verbose flag. */
    static void addVerbose(final ArgParser aArgp)
    {
        aArgp.addOption("--verbose",
                "Print more logging messages and not just errors.");
        aArgp.addOption("-v", "Short for --verbose.");
    }

    /** Adds the --auto-metric flag. */
    static void addAutoMetricArg(final ArgParser aArgp)
    {
        aArgp.addOption("--auto-metric",
                "Automatically add metrics to cisw as they"
                        + " are inserted.  Warning: this may cause unexpected"
                        + " metrics to be tracked");
    }

    /**
     * Parse the command line arguments with the given options.
     * 
     * @param options
     *            Options to parse in the given args.
     * @param args
     *            Command line arguments to parse.
     * @return The remainder of the command line or {@code null} if {@code args}
     *         were invalid and couldn't be parsed.
     */
    static String[] optionParse(final ArgParser aArgp, String[] aArgs)
    {
        try
        {
            aArgs = aArgp.parse(aArgs);
        }
        catch (IllegalArgumentException e)
        {
            System.err.println("Invalid usage.  " + e.getMessage());
            return null;
        }
        honorVerboseFlag(aArgp);
        return aArgs;
    }

    /** Changes the log level to 'WARN' unless --verbose is passed. */
    private static void honorVerboseFlag(final ArgParser aArgp)
    {
        if (aArgp.optionExists("--verbose") && !aArgp.exists("--verbose")
                && !aArgp.exists("-v"))
        {
            // SLF4J doesn't provide any API to programmatically set the logging
            // level of the underlying logging library. So we have to violate
            // the encapsulation provided by SLF4J.
            for (final ch.qos.logback.classic.Logger logger : ((ch.qos.logback.classic.Logger) LoggerFactory
                    .getLogger(Logger.ROOT_LOGGER_NAME)).getLoggerContext()
                    .getLoggerList())
            {
                logger.setLevel(Level.WARN);
            }
        }
    }

    static HBaseClient getHBaseClient(final ArgParser aArgp)
    {
        if (aArgp.optionExists("--auto-metric")
                && aArgp.exists("--auto-metric"))
        {
            System.setProperty("cisw.core.auto_create_metrics", "true");
        }
        final String sZkq = aArgp.get("--zkquorum", "localhost");
        if (aArgp.exists("--zkbasedir"))
        {
            return new HBaseClient(sZkq, aArgp.get("--zkbasedir"));
        }
        else
        {
            return new HBaseClient(sZkq);
        }
    }
}
