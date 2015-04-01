package com.skplanet.ciscommon.comm;

/**
 * Exception thrown by the HTTP handlers when presented with a bad request.
 */
public final class BadRequestException extends RuntimeException
{

    public BadRequestException(final String message)
    {
        super(message);
    }

    public static BadRequestException missingParameter(final String paramname)
    {
        return new BadRequestException("Missing parameter <code>" + paramname
                + "</code>");
    }

    // for Object Serialization
    static final long serialVersionUID = 1276251669;
}
