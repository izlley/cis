package com.skplanet.ciscommon.dataop;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.AbstractMap.SimpleEntry;

/**
 * Represents a read-only sequence of continuous data points.
 * <p>
 * Implementations of this interface aren't expected to be synchronized.
 */
public interface DataPoints extends Iterable<DataPoint>
{

    /**
     * Returns the name of the series.
     */
    String metricName();

    /**
     * BUG-39613 : Add Legend clause in CISQL
     * Returns all tags in rowkey
     */
    ArrayList<SimpleEntry<String, String>> getAllTags();
    
    /**
     * Returns the tags associated with these data points.
     * 
     * @return A non-{@code null} map of tag names (keys), tag values (values).
     */
    Map<String, String> getTags();

    /**
     * Returns the tags associated with some but not all of the data points.
     * <p>
     * When this instance represents the aggregation of multiple time series
     * (same metric but different tags), {@link #getTags} returns the tags that
     * are common to all data points (intersection set) whereas this method
     * returns all the tags names that are not common to all data points (union
     * set minus the intersection set, also called the symmetric difference).
     * <p>
     * If this instance does not represent an aggregation of multiple time
     * series, the list returned is empty.
     * 
     * @return A non-{@code null} list of tag names.
     */
    List<String> getAggregatedTags();

    /**
     * Returns the number of data points.
     * <p>
     * This method must be implemented in {@code O(1)} or {@code O(n)} where
     * <code>n = {@link #aggregatedSize} &gt; 0</code>.
     * 
     * @return A positive integer.
     */
    int size();

    /**
     * Returns the number of data points aggregated in this instance.
     * <p>
     * When this instance represents the aggregation of multiple time series
     * (same metric but different tags), {@link #size} returns the number of
     * data points after aggregation, whereas this method returns the number of
     * data points before aggregation.
     * <p>
     * If this instance does not represent an aggregation of multiple time
     * series, then 0 is returned.
     * 
     * @return A positive integer.
     */
    int aggregatedSize();

    /**
     * Returns a <em>zero-copy view</em> to go through {@code size()} data
     * points.
     * <p>
     * The iterator returned must return each {@link DataPoint} in {@code O(1)}.
     * <b>The {@link DataPoint} returned must not be stored</b> and gets
     * invalidated as soon as {@code next} is called on the iterator. If you
     * want to store individual data points, you need to copy the timestamp and
     * value out of each {@link DataPoint} into your own data structures.
     */
    SeekableView iterator();

    /**
     * Returns the timestamp associated with the {@code i}th data point. The
     * first data point has index 0.
     * <p>
     * This method must be implemented in
     * <code>O({@link #aggregatedSize})</code> or better.
     * <p>
     * It is guaranteed that
     * 
     * <pre>
     * timestamp(i) &lt; timestamp(i + 1)
     * </pre>
     * 
     * @return A strictly positive integer.
     * @throws IndexOutOfBoundsException
     *             if {@code i} is not in the range <code>[0, {@link #size} - 1]
     *             </code>
     */
    long timestamp(int i);

    /**
     * Tells whether or not the {@code i}th value is of integer type. The first
     * data point has index 0.
     * <p>
     * This method must be implemented in
     * <code>O({@link #aggregatedSize})</code> or better.
     * 
     * @return {@code true} if the {@code i}th value is of integer type,
     *         {@code false} if it's of floating point type.
     * @throws IndexOutOfBoundsException
     *             if {@code i} is not in the range <code>[0, {@link #size} - 1]
     *             </code>
     */
    boolean isInteger(int i);

    /**
     * Returns the value of the {@code i}th data point as a long. The first data
     * point has index 0.
     * <p>
     * This method must be implemented in
     * <code>O({@link #aggregatedSize})</code> or better. Use {@link #iterator}
     * to get successive {@code O(1)} accesses.
     * 
     * @see #iterator
     * @throws IndexOutOfBoundsException
     *             if {@code i} is not in the range <code>[0, {@link #size} - 1]
     *             </code>
     * @throws ClassCastException
     *             if the <code>{@link #isInteger isInteger(i)} == false</code>.
     */
    long longValue(int i);

    /**
     * Returns the value of the {@code i}th data point as a float. The first
     * data point has index 0.
     * <p>
     * This method must be implemented in
     * <code>O({@link #aggregatedSize})</code> or better. Use {@link #iterator}
     * to get successive {@code O(1)} accesses.
     * 
     * @see #iterator
     * @throws IndexOutOfBoundsException
     *             if {@code i} is not in the range <code>[0, {@link #size} - 1]
     *             </code>
     * @throws ClassCastException
     *             if the <code>{@link #isInteger isInteger(i)} == true</code>.
     */
    double doubleValue(int i);

}
