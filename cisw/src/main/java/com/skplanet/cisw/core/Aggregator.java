package com.skplanet.cisw.core;

import java.util.NoSuchElementException;

/**
 * A function capable of aggregating multiple {@link DataPoints} together.
 * <p>
 * All aggregators must be stateless. All they can do is run through a sequence
 * of {@link Longs Longs} or {@link Doubles Doubles} and return an aggregated
 * value.
 */
public interface Aggregator
{

    /**
     * A sequence of {@code long}s.
     * <p>
     * This interface is semantically equivalent to {@code Iterator<long>}.
     */
    public interface Longs
    {

        /**
         * Returns {@code true} if this sequence has more values. {@code false}
         * otherwise.
         */
        boolean hasNextValue();

        /**
         * Returns the next {@code long} value in this sequence.
         * 
         * @throws NoSuchElementException
         *             if calling {@link #hasNextValue} returns {@code false}.
         */
        long nextLongValue();

    }

    /**
     * A sequence of {@code double}s.
     * <p>
     * This interface is semantically equivalent to {@code Iterator<double>}.
     */
    public interface Doubles
    {

        /**
         * Returns {@code true} if this sequence has more values. {@code false}
         * otherwise.
         */
        boolean hasNextValue();

        /**
         * Returns the next {@code double} value in this sequence.
         * 
         * @throws NoSuchElementException
         *             if calling {@link #hasNextValue} returns {@code false}.
         */
        double nextDoubleValue();

    }

    /**
     * Aggregates a sequence of {@code long}s.
     * 
     * @param values
     *            The sequence to aggregate.
     * @return The aggregated value.
     */
    long runLong(Longs values);

    /**
     * Aggregates a sequence of {@code double}s.
     * 
     * @param values
     *            The sequence to aggregate.
     * @return The aggregated value.
     */
    double runDouble(Doubles values);

}
