package com.skplanet.ciscommon.uid;

import java.util.NoSuchElementException;

/**
 * Exception used when a name's Unique ID can't be found.
 * 
 * @see UniqueIdInterface
 */
public final class NoSuchUniqueName extends NoSuchElementException
{

    /** The 'kind' of the table. */
    private final String kind;
    /** The name that couldn't be found. */
    private final String name;

    /**
     * Constructor.
     * 
     * @param kind
     *            The kind of unique ID that triggered the exception.
     * @param name
     *            The name that couldn't be found.
     */
    public NoSuchUniqueName(final String kind, final String name)
    {
        super("No such name for '" + kind + "': '" + name + "'");
        this.kind = kind;
        this.name = name;
    }

    /** Returns the kind of unique ID that couldn't be found. */
    public String kind()
    {
        return kind;
    }

    /** Returns the name for which the unique ID couldn't be found. */
    public String name()
    {
        return name;
    }

    static final long serialVersionUID = 1266815261;

}
