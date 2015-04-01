package com.skplanet.ciscommon.dataop;

/**
 * Some illegal / malformed / corrupted data has been found in HBase.
 */
public final class IllegalDataException extends IllegalStateException
{

    /**
     * Constructor.
     * 
     * @param msg
     *            Message describing the problem.
     */
    public IllegalDataException(final String msg)
    {
        super(msg);
    }

    /**
     * Constructor.
     * 
     * @param msg
     *            Message describing the problem.
     */
    public IllegalDataException(final String msg, final Throwable cause)
    {
        super(msg, cause);
    }

    static final long serialVersionUID = 1307719142;

}
