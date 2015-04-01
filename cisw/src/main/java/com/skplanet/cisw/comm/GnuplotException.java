package com.skplanet.cisw.comm;

/**
 * Exception thrown when Gnuplot fails.
 */
final class GnuplotException extends RuntimeException
{

    public GnuplotException(final int gnuplot_return_value)
    {
        super("Gnuplot returned " + gnuplot_return_value);
    }

    public GnuplotException(final String gnuplot_stderr)
    {
        super("Gnuplot stderr:\n" + gnuplot_stderr);
    }

    static final long serialVersionUID = 1287770642;

}
