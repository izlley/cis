package com.skplanet.cissd.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.stumbleupon.async.Callback;
import com.stumbleupon.async.Deferred;

import org.hbase.async.Bytes;
import org.hbase.async.DeleteRequest;
import org.hbase.async.GetRequest;
import org.hbase.async.HBaseClient;
import org.hbase.async.HBaseException;
import org.hbase.async.KeyValue;
import org.hbase.async.PutRequest;

import com.skplanet.ciscommon.uid.UniqueId;
import com.skplanet.ciscommon.stats.Histogram;
import com.skplanet.ciscommon.stats.StatsCollector;

/**
 * Thread-safe implementation of the CISSD.
 * <p>
 * This class is the central class of CISSD. You use it to add new data points
 * or query the database.
 */
public final class CISSD
{

    static final byte[] FAMILY = { 't' };

    private static final String METRICS_QUAL = "metrics";
    private static final short METRICS_WIDTH = 3;
    private static final String TAG_NAME_QUAL = "tagk";
    private static final short TAG_NAME_WIDTH = 3;
    private static final String TAG_VALUE_QUAL = "tagv";
    private static final short TAG_VALUE_WIDTH = 3;

    static final boolean enable_compactions;
    static
    {
        final String compactions = System
                .getProperty("cissd.feature.compactions");
        enable_compactions = compactions != null
                && !"false".equals(compactions);
    }

    /** Client for the HBase cluster to use. */
    final HBaseClient client;

    /** Name of the table in which timeseries are stored. */
    final byte[] table;

    /** Unique IDs for the metric names. */
    final UniqueId metrics;
    /** Unique IDs for the tag names. */
    final UniqueId tag_names;
    /** Unique IDs for the tag values. */
    final UniqueId tag_values;

    /**
     * Row keys that need to be compacted. Whenever we write a new data point to
     * a row, we add the row key to this set. Every once in a while, the
     * compaction thread will go through old row keys and will read re-compact
     * them.
     */
    // LJY
    // private final CompactionQueue compactionq;

    /**
     * Constructor.
     * 
     * @param client
     *            The HBase client to use.
     * @param timeseries_table
     *            The name of the HBase table where time series data is stored.
     * @param uniqueids_table
     *            The name of the HBase table where the unique IDs are stored.
     */
    public CISSD(final HBaseClient client, final String timeseries_table,
            final String uniqueids_table)
    {
        this.client = client;
        table = timeseries_table.getBytes();

        final byte[] uidtable = uniqueids_table.getBytes();
        metrics = new UniqueId(client, uidtable, table, METRICS_QUAL, METRICS_WIDTH);
        tag_names = new UniqueId(client, uidtable, table, TAG_NAME_QUAL,
                TAG_NAME_WIDTH);
        tag_values = new UniqueId(client, uidtable, table, TAG_VALUE_QUAL,
                TAG_VALUE_WIDTH);
        // LJY
        // compactionq = new CompactionQueue(this);
    }

    /** Number of cache hits during lookups involving UIDs. */
    public int uidCacheHits()
    {
        return (metrics.cacheHits() + tag_names.cacheHits() + tag_values
                .cacheHits());
    }

    /** Number of cache misses during lookups involving UIDs. */
    public int uidCacheMisses()
    {
        return (metrics.cacheMisses() + tag_names.cacheMisses() + tag_values
                .cacheMisses());
    }

    /** Number of cache entries currently in RAM for lookups involving UIDs. */
    public int uidCacheSize()
    {
        return (metrics.cacheSize() + tag_names.cacheSize() + tag_values
                .cacheSize());
    }

    /**
     * Collects the stats and metrics tracked by this instance.
     * 
     * @param collector
     *            The collector to use.
     */
    public void collectStats(final StatsCollector collector)
    {
        collectUidStats(metrics, collector);
        collectUidStats(tag_names, collector);
        collectUidStats(tag_values, collector);

        {
            final Runtime runtime = Runtime.getRuntime();
            collector.record("jvm.ramfree", runtime.freeMemory());
            collector.record("jvm.ramused", runtime.totalMemory());
        }

        collector.addExtraTag("class", "IncomingDataPoints");
        try
        {
            collector.record("hbase.latency", IncomingDataPoints.putlatency,
                    "method=put");
        }
        finally
        {
            collector.clearExtraTag("class");
        }

        // LJY
        /*
         * collector.addExtraTag("class", "CissdQuery"); try {
         * collector.record("hbase.latency", CissdQuery.scanlatency,
         * "method=scan"); } finally { collector.clearExtraTag("class"); }
         */
        collector.record("hbase.root_lookups", client.rootLookupCount());
        collector.record("hbase.meta_lookups",
                client.uncontendedMetaLookupCount(), "type=uncontended");
        collector.record("hbase.meta_lookups",
                client.contendedMetaLookupCount(), "type=contended");

        // LJY
        // compactionq.collectStats(collector);
    }

    /** Returns a latency histogram for Put RPCs used to store data points. */
    public Histogram getPutLatencyHistogram()
    {
        return IncomingDataPoints.putlatency;
    }

    /**
     * Collects the stats for a {@link UniqueId}.
     * 
     * @param uid
     *            The instance from which to collect stats.
     * @param collector
     *            The collector to use.
     */
    private static void collectUidStats(final UniqueId uid,
            final StatsCollector collector)
    {
        collector
                .record("uid.cache-hit", uid.cacheHits(), "kind=" + uid.kind());
        collector.record("uid.cache-miss", uid.cacheMisses(),
                "kind=" + uid.kind());
        collector.record("uid.cache-size", uid.cacheSize(),
                "kind=" + uid.kind());
    }

    /**
     * Returns a new {@link WritableDataPoints} instance suitable for this
     * CISSD.
     * <p>
     * If you want to add a single data-point, consider using {@link #addPoint}
     * instead.
     */
    /*
     * public WritableDataPoints newDataPoints() { return new
     * IncomingDataPoints(this); }
     */

    /**
     * Adds a single integer value data point in the CISSD.
     * 
     * @param metric
     *            A non-empty string.
     * @param timestamp
     *            The timestamp associated with the value.
     * @param value
     *            The value of the data point.
     * @param tags
     *            The tags on this series. This map must be non-empty.
     * @return A deferred object that indicates the completion of the request.
     *         The {@link Object} has not special meaning and can be
     *         {@code null} (think of it as {@code Deferred<Void>}). But you
     *         probably want to attach at least an errback to this
     *         {@code Deferred} to handle failures.
     * @throws IllegalArgumentException
     *             if the timestamp is less than or equal to the previous
     *             timestamp added or 0 for the first timestamp, or if the
     *             difference with the previous timestamp is too large.
     * @throws IllegalArgumentException
     *             if the metric name is empty or contains illegal characters.
     * @throws IllegalArgumentException
     *             if the tags list is empty or one of the elements contains
     *             illegal characters.
     * @throws HBaseException
     *             (deferred) if there was a problem while persisting data.
     */
    public Deferred<Object> addPoint(final String metric, final long timestamp,
            final long value, final Map<String, String> tags)
    {
        final short flags = 0x7; // An int stored on 8 bytes.
        return addPointInternal(metric, timestamp, Bytes.fromLong(value), tags,
                flags);
    }

    /**
     * Adds a single floating-point value data point in the CISSD.
     * 
     * @param metric
     *            A non-empty string.
     * @param timestamp
     *            The timestamp associated with the value.
     * @param value
     *            The value of the data point.
     * @param tags
     *            The tags on this series. This map must be non-empty.
     * @return A deferred object that indicates the completion of the request.
     *         The {@link Object} has not special meaning and can be
     *         {@code null} (think of it as {@code Deferred<Void>}). But you
     *         probably want to attach at least an errback to this
     *         {@code Deferred} to handle failures.
     * @throws IllegalArgumentException
     *             if the timestamp is less than or equal to the previous
     *             timestamp added or 0 for the first timestamp, or if the
     *             difference with the previous timestamp is too large.
     * @throws IllegalArgumentException
     *             if the metric name is empty or contains illegal characters.
     * @throws IllegalArgumentException
     *             if the value is NaN or infinite.
     * @throws IllegalArgumentException
     *             if the tags list is empty or one of the elements contains
     *             illegal characters.
     * @throws HBaseException
     *             (deferred) if there was a problem while persisting data.
     */
    public Deferred<Object> addPoint(final String metric, final long timestamp,
            final float value, final Map<String, String> tags)
    {
        if (Float.isNaN(value) || Float.isInfinite(value))
        {
            throw new IllegalArgumentException("value is NaN or Infinite: "
                    + value + " for metric=" + metric + " timestamp="
                    + timestamp);
        }
        final short flags = Const.FLAG_FLOAT | 0x3; // A float stored on 4
                                                    // bytes.
        return addPointInternal(metric, timestamp,
                Bytes.fromInt(Float.floatToRawIntBits(value)), tags, flags);
    }

    private Deferred<Object> addPointInternal(final String metric,
            final long timestamp, final byte[] value,
            final Map<String, String> tags, final short flags)
    {
        if ((timestamp & 0xFFFFFFFF00000000L) != 0)
        {
            // => timestamp < 0 || timestamp > Integer.MAX_VALUE
            throw new IllegalArgumentException((timestamp < 0 ? "negative "
                    : "bad")
                    + " timestamp="
                    + timestamp
                    + " when trying to add value="
                    + Arrays.toString(value)
                    + '/' + flags + " to metric=" + metric + ", tags=" + tags);
        }

        IncomingDataPoints.checkMetricAndTags(metric, tags);
        final byte[] row = IncomingDataPoints
                .rowKeyTemplate(this, metric, tags);
        final long base_time = (timestamp - (timestamp % Const.MAX_TIMESPAN));
        Bytes.setInt(row, (int) base_time, metrics.width());
        // LJY
        // scheduleForCompaction(row, (int) base_time);
        final short qualifier = (short) ((timestamp - base_time) << Const.FLAG_BITS | flags);
        final PutRequest point = new PutRequest(table, row, FAMILY,
                Bytes.fromShort(qualifier), value);
        // TODO: Add a callback to time the latency of HBase and store the
        // timing in a moving Histogram (once we have a class for this).
        return client.put(point);
    }

    /**
     * Forces a flush of any un-committed in memory data.
     * <p>
     * For instance, any data point not persisted will be sent to HBase.
     * 
     * @return A {@link Deferred} that will be called once all the un-committed
     *         data has been successfully and durably stored. The value of the
     *         deferred object return is meaningless and unspecified, and can be
     *         {@code null}.
     * @throws HBaseException
     *             (deferred) if there was a problem sending un-committed data
     *             to HBase. Please refer to the {@link HBaseException}
     *             hierarchy to handle the possible failures. Some of them are
     *             easily recoverable by retrying, some are not.
     */
    public Deferred<Object> flush() throws HBaseException
    {
        return client.flush();
    }

    /**
     * Gracefully shuts down this instance.
     * <p>
     * This does the same thing as {@link #flush} and also releases all other
     * resources.
     * 
     * @return A {@link Deferred} that will be called once all the un-committed
     *         data has been successfully and durably stored, and all resources
     *         used by this instance have been released. The value of the
     *         deferred object return is meaningless and unspecified, and can be
     *         {@code null}.
     * @throws HBaseException
     *             (deferred) if there was a problem sending un-committed data
     *             to HBase. Please refer to the {@link HBaseException}
     *             hierarchy to handle the possible failures. Some of them are
     *             easily recoverable by retrying, some are not.
     */
    public Deferred<Object> shutdown()
    {
        final class HClientShutdown implements
                Callback<Object, ArrayList<Object>>
        {
            public Object call(final ArrayList<Object> args)
            {
                return client.shutdown();
            }

            public String toString()
            {
                return "shutdown HBase client";
            }
        }
        // First flush the compaction queue, then shutdown the HBase client.
        // LJY
        return client.shutdown();
        /*
         * return enable_compactions ? compactionq.flush().addBoth(new
         * HClientShutdown()) : client.shutdown();
         */
    }

    // ------------------ //
    // Compaction helpers //
    // ------------------ //
    // LJY
    /*
     * final KeyValue compact(final ArrayList<KeyValue> row) { return
     * compactionq.compact(row); }
     */
    /**
     * Schedules the given row key for later re-compaction. Once this row key
     * has become "old enough", we'll read back all the data points in that row,
     * write them back to HBase in a more compact fashion, and delete the
     * individual data points.
     * 
     * @param row
     *            The row key to re-compact later. Will not be modified.
     * @param base_time
     *            The 32-bit unsigned UNIX timestamp.
     */
    /*
     * final void scheduleForCompaction(final byte[] row, final int base_time) {
     * if (enable_compactions) { compactionq.add(row); } }
     */

    // ------------------------ //
    // HBase operations helpers //
    // ------------------------ //

    /** Gets the entire given row from the data table. */
    final Deferred<ArrayList<KeyValue>> get(final byte[] key)
    {
        return client.get(new GetRequest(table, key));
    }

    /** Puts the given value into the data table. */
    final Deferred<Object> put(final byte[] key, final byte[] qualifier,
            final byte[] value)
    {
        return client.put(new PutRequest(table, key, FAMILY, qualifier, value));
    }

    /** Deletes the given cells from the data table. */
    final Deferred<Object> delete(final byte[] key, final byte[][] qualifiers)
    {
        return client.delete(new DeleteRequest(table, key, FAMILY, qualifiers));
    }

}
