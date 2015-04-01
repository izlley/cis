package com.skplanet.cissd.comm;

import java.nio.charset.Charset;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.oneone.OneToOneDecoder;

import com.skplanet.cissd.core.Tags;

/**
 * Splits a ChannelBuffer in multiple space separated words.
 */
final class WordSplitter extends OneToOneDecoder
{

    private static final Charset CHARSET = Charset.forName("ISO-8859-1");

    /** Constructor. */
    public WordSplitter()
    {
    }

    @Override
    protected Object decode(final ChannelHandlerContext ctx,
            final Channel channel, final Object msg) throws Exception
    {
        return Tags.splitString(((ChannelBuffer) msg).toString(CHARSET), ' ');
    }

}
