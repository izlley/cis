package com.skplanet.ciscommon.comm;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.group.DefaultChannelGroup;

import com.skplanet.ciscommon.stats.StatsCollector;

/**
 * Keeps track of all existing connections.
 */
public final class ConnectionManager extends SimpleChannelHandler
{

    private static final Logger LOG = LoggerFactory.getLogger(ConnectionManager.class);

    private static final AtomicLong connections_established = new AtomicLong();
    private static final AtomicLong exceptions_caught       = new AtomicLong();

    private static final DefaultChannelGroup channels = new DefaultChannelGroup("all-channels");

    public static void closeAllConnections()
    {
        channels.close().awaitUninterruptibly();
    }

    /** Constructor. */
    public ConnectionManager()
    {
    }

    /**
     * Collects the stats and metrics tracked by this instance.
     * 
     * @param collector
     *            The collector to use.
     */
    public static void collectStats(final StatsCollector collector)
    {
        collector.record("connectionmgr.connections", connections_established);
        collector.record("connectionmgr.exceptions", exceptions_caught);
    }

    @Override
    public void channelOpen(final ChannelHandlerContext ctx,
            final ChannelStateEvent e)
    {
        channels.add(e.getChannel());
        connections_established.incrementAndGet();
    }

    @Override
    public void handleUpstream(final ChannelHandlerContext ctx,
            final ChannelEvent e) throws Exception
    {
        if (e instanceof ChannelStateEvent)
        {
            LOG.info(e.toString());
        }
        super.handleUpstream(ctx, e);
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx,
            final ExceptionEvent e)
    {
        final Throwable cause = e.getCause();
        final Channel chan = ctx.getChannel();
        if (cause instanceof ClosedChannelException)
        {
            LOG.warn("Attempt to write to closed channel " + chan);
        } else if (cause instanceof IOException
                && "Connection reset by peer".equals(cause.getMessage()))
        {
            // Do nothing. A client disconnecting isn't really our problem. Oh,
            // and I'm not kidding you, there's no better way to detect
            // ECONNRESET
            // in Java. Like, people have been bitching about errno for years,
            // and Java managed to do something *far* worse. That's quite a
            // feat.
        } else
        {
            LOG.error("Unexpected exception from downstream for " + chan, cause);
            e.getChannel().close();
        }
        exceptions_caught.incrementAndGet();
    }

}
