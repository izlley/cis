package com.skplanet.cisw.comm;

import java.io.IOException;

import com.skplanet.cisw.core.CISW;
import com.skplanet.ciscommon.comm.BadRequestException;

/** Implements the "/s" endpoint to serve static files. */
final class StaticFileRpc implements HttpRpc
{

    /**
     * The path to the directory where to find static files (for the {@code /s}
     * URLs).
     */
    private final String webroot;
    private final String mHomepagePath = "/cis/mypage/index.html";

    /**
     * Constructor.
     */
    public StaticFileRpc()
    {
        webroot = RpcHandler.getDirectoryFromSystemProp("cisw.http.webroot");
    }

    public void execute(final CISW cisw, final HttpQuery query)
            throws IOException
    {
        final String uri = query.request().getUri();
        if( "/".equals(uri) )
        {
            query.sendFile(webroot + mHomepagePath, 31536000 /* =1yr */, false);
            return;
        }
        else if ("/favicon.ico".equals(uri))
        {
            query.sendFile(webroot + "/favicon.ico", 31536000 /* =1yr */, false);
            return;
        }
        if (uri.length() < 3)
        { // Must be at least 3 because of the "/s/".
            throw new BadRequestException("URI too short <code>" + uri
                    + "</code>");
        }
        // Cheap security check to avoid directory traversal attacks.
        // TODO(tsuna): This is certainly not sufficient.
        if (uri.indexOf("..", 3) > 0)
        {
            throw new BadRequestException("Malformed URI <code>" + uri
                    + "</code>");
        }
        final int questionmark = uri.indexOf('?', 3);
        final int pathend = questionmark > 0 ? questionmark : uri.length();
        query.sendFile(webroot + uri.substring(3, pathend),
                uri.contains("nocache") ? 0 : 31536000 /* =1yr */, false);
    }
}
