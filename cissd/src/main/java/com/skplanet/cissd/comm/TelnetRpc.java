package com.skplanet.cissd.comm;

import com.stumbleupon.async.Deferred;

import org.jboss.netty.channel.Channel;

import com.skplanet.cissd.core.CISSD;

/** Base interface for all telnet-style RPC handlers. */
interface TelnetRpc
{

    /**
     * Executes this RPC.
     * 
     * @param cissd
     *            The CISSD to use.
     * @param chan
     *            The channel on which the RPC was received.
     * @param command
     *            The command received, split.
     * @return A deferred result.
     */
    Deferred<Object> execute(CISSD cissd, Channel chan, String[] command);

}
