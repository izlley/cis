package com.skplanet.ciscommon.uid;

import java.util.Arrays;
import java.util.NoSuchElementException;

/**
 * Exception used when a Unique ID can't be found.
 * 
 * @see UniqueIdInterface
 */
public final class NoSuchUniqueId extends NoSuchElementException
{

    /** The 'kind' of the table. */
    private final String kind;
    /** The ID that couldn't be found. */
    private final byte[] id;

    /**
     * Constructor.
     * 
     * @param kind
     *            The kind of unique ID that triggered the exception.
     * @param id
     *            The ID that couldn't be found.
     */
    public NoSuchUniqueId(final String kind, final byte[] id)
    {
        super("No such unique ID for '" + kind + "': " + Arrays.toString(id));
        this.kind = kind;
        this.id = id;
    }

    /** Returns the kind of unique ID that couldn't be found. */
    public String kind()
    {
        return kind;
    }

    /** Returns the unique ID that couldn't be found. */
    public byte[] id()
    {
        return id;
    }

    static final long serialVersionUID = 1266815251;

}
