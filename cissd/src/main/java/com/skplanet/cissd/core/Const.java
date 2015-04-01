package com.skplanet.cissd.core;

/** Constants used in various places. */
public final class Const
{

    /** Number of bytes on which a timestamp is encoded. */
    public static final short TIMESTAMP_BYTES = 4;

    /** Maximum number of tags allowed per data point. */
    public static final short MAX_NUM_TAGS = 8;
    // 8 is an aggressive limit on purpose. Can always be increased later.

    /** Number of LSBs in time_deltas reserved for flags. */
    static final short FLAG_BITS = 4;

    /**
     * When this bit is set, the value is a floating point value. Otherwise it's
     * an integer value.
     */
    static final short FLAG_FLOAT = 0x8;

    /** Mask to select the size of a value from the qualifier. */
    static final short LENGTH_MASK = 0x7;

    /** Mask to select all the FLAG_BITS. */
    static final short FLAGS_MASK = FLAG_FLOAT | LENGTH_MASK;

    /** Max time delta (in seconds) we can store in a column qualifier. */
    public static final short MAX_TIMESPAN = 3600;

    /**
     * Array containing the hexadecimal characters (0 to 9, A to F). This array
     * is read-only, changing its contents leads to an undefined behavior.
     */
    public static final byte[] HEX = { '0', '1', '2', '3', '4', '5', '6', '7',
            '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

}
