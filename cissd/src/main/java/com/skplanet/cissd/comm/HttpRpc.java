package com.skplanet.cissd.comm;

import java.io.IOException;

import com.skplanet.cissd.core.CISSD;

/** Base interface for all HTTP query handlers. */
interface HttpRpc
{

    /**
     * Executes this RPC.
     * 
     * @param cissd
     *            The CISSD to use.
     * @param query
     *            The HTTP query to execute.
     */
    void execute(CISSD cissd, HttpQuery query) throws IOException;

}