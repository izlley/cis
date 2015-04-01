package com.skplanet.cisw.core;

import java.util.Arrays;

/** Helper functions to deal with the row key. */
final class RowKey
{

    private RowKey()
    {
        // Can't create instances of this utility class.
    }

    /**
     * Extracts the name of the metric ID contained in a row key.
     * 
     * @param cisw
     *            The CISW to use.
     * @param row
     *            The actual row key.
     * @return The name of the metric.
     */
    static String metricName(final CISW cisw, final byte[] row)
    {
        final byte[] id = Arrays.copyOfRange(row, 0, cisw.metrics.width());
        return cisw.metrics.getName(id);
    }

}
