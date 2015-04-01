package com.skplanet.cisw.comm;

import java.io.IOException;

import com.skplanet.cisw.core.CISW;

/** Base interface for all HTTP query handlers. */
interface HttpRpc
{

    /**
     * Executes this RPC.
     * 
     * @param cisw
     *            The CISW to use.
     * @param query
     *            The HTTP query to execute.
     */
    void execute(CISW cisw, HttpQuery query) throws IOException;

}
