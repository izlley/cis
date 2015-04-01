package com.skplanet.cissd.comm;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.stumbleupon.async.Callback;
import com.stumbleupon.async.Deferred;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.handler.codec.http.HttpMethod;

import com.skplanet.cissd.core.CISSD;
import com.skplanet.cissd.core.Tags;
import com.skplanet.ciscommon.comm.BadRequestException;
import com.skplanet.ciscommon.stats.StatsCollector;
import com.skplanet.ciscommon.uid.NoSuchUniqueName;

/** Implements the "put" telnet-style command. */
final class PutDataPointRpc implements TelnetRpc, HttpRpc
{

    private static final AtomicLong requests = new AtomicLong();
    private static final AtomicLong hbase_errors = new AtomicLong();
    private static final AtomicLong invalid_values = new AtomicLong();
    private static final AtomicLong illegal_arguments = new AtomicLong();
    private static final AtomicLong unknown_metrics = new AtomicLong();
    private static final String mPutstrSTag = "<putstr>";
    private static final String mPutstrETag = "</putstr>";

    public void execute(final CISSD cissd, final HttpQuery query)
    {
        String errmsg = null;
        try
        {
            if( query.getReqMethod() == HttpMethod.POST )
            {
                requests.incrementAndGet();
                StringBuilder sSb = new StringBuilder();
                ChannelBuffer sBuffer = query.request().getContent();

                for (int i = 0; i < sBuffer.capacity(); i ++)
                {
                    byte b = sBuffer.getByte(i);
                    sSb.append((char) b);
                }

                importDataPoint(cissd, parseXML( sSb ));
            }
            else
            {
                //error
                throw new BadRequestException("Only POST request is available.");
            }
        }
        catch (NumberFormatException x)
        {
            errmsg = "put: invalid value: " + x.getMessage() + '\n';
            invalid_values.incrementAndGet();
        }
        catch (IllegalArgumentException x)
        {
            errmsg = "put: illegal argument: " + x.getMessage() + '\n';
            illegal_arguments.incrementAndGet();
        }
        catch (NoSuchUniqueName x)
        {
            errmsg = "put: unknown metric: " + x.getMessage() + '\n';
            unknown_metrics.incrementAndGet();
        }
        if (errmsg != null)
        {
            query.sendReply(errmsg);
        }
        else
        {
            query.sendReply("put success");
        }
    }
    
    private String[] parseXML( StringBuilder aSrc )
    {
        String[] sRes;
        
        trimDelete( aSrc );
        
        if( aSrc.substring( 0, mPutstrSTag.length() ).equalsIgnoreCase( mPutstrSTag ) )
        {
            aSrc.delete(0, mPutstrSTag.length());
        }
        else
        {
            // parse error
        }
        
        if( aSrc.substring( aSrc.length() - mPutstrETag.length(), aSrc.length() ).
                equalsIgnoreCase(mPutstrETag) )
        {
            aSrc.delete( aSrc.length() - mPutstrETag.length(), aSrc.length() );
        }
        else
        {
            // parse error
        }
        
        trimDelete( aSrc );
        
        if( (aSrc.charAt(0) == '"') && (aSrc.charAt(aSrc.length() - 1) == '"') )
        {
            aSrc.deleteCharAt(0);
            aSrc.deleteCharAt(aSrc.length()-1);
        }
        
        sRes = aSrc.toString().split("[ \n\t]+");
        
        return sRes;
    }

    private void trimDelete(StringBuilder sb) {
        while (sb.length() > 0 && Character.isWhitespace(sb.charAt(0))) 
        {
            sb.deleteCharAt(0);
        }
        while (sb.length() > 0 && Character.isWhitespace(sb.charAt(sb.length() - 1))) 
        {
            sb.deleteCharAt(sb.length() - 1);
        }
    }
    
    public Deferred<Object> execute(final CISSD cissd, final Channel chan,
            final String[] cmd)
    {
        requests.incrementAndGet();
        String errmsg = null;
        try
        {
            final class PutErrback implements Callback<Exception, Exception>
            {
                public Exception call(final Exception arg)
                {
                    if (chan.isConnected())
                    {
                        chan.write("put: HBase error: " + arg.getMessage()
                                + '\n');
                    }
                    hbase_errors.incrementAndGet();
                    return arg;
                }

                public String toString()
                {
                    return "report error to channel";
                }
            }
            return importDataPoint(cissd, cmd).addErrback(new PutErrback());
        }
        catch (NumberFormatException x)
        {
            errmsg = "put: invalid value: " + x.getMessage() + '\n';
            invalid_values.incrementAndGet();
        }
        catch (IllegalArgumentException x)
        {
            errmsg = "put: illegal argument: " + x.getMessage() + '\n';
            illegal_arguments.incrementAndGet();
        }
        catch (NoSuchUniqueName x)
        {
            errmsg = "put: unknown metric: " + x.getMessage() + '\n';
            unknown_metrics.incrementAndGet();
        }
        if (errmsg != null && chan.isConnected())
        {
            chan.write(errmsg);
        }
        return Deferred.fromResult(null);
    }

    /**
     * Collects the stats and metrics tracked by this instance.
     * 
     * @param collector
     *            The collector to use.
     */
    public static void collectStats(final StatsCollector collector)
    {
        collector.record("rpc.received", requests, "type=put");
        collector.record("rpc.errors", hbase_errors, "type=hbase_errors");
        collector.record("rpc.errors", invalid_values, "type=invalid_values");
        collector.record("rpc.errors", illegal_arguments,
                "type=illegal_arguments");
        collector.record("rpc.errors", unknown_metrics, "type=unknown_metrics");
    }

    /**
     * Imports a single data point.
     * 
     * @param cissd
     *            The CISSD to import the data point into.
     * @param words
     *            The words describing the data point to import, in the
     *            following format: {@code [metric, timestamp, value, ..tags..]}
     * @return A deferred object that indicates the completion of the request.
     * @throws NumberFormatException
     *             if the timestamp or value is invalid.
     * @throws IllegalArgumentException
     *             if any other argument is invalid.
     * @throws NoSuchUniqueName
     *             if the metric isn't registered.
     */
    private Deferred<Object> importDataPoint(final CISSD cissd,
            final String[] words)
    {
        words[0] = null; // Ditch the "put".
        if (words.length < 5)
        { // Need at least: metric timestamp value tag
          // ^ 5 and not 4 because words[0] is "put".
            throw new IllegalArgumentException("not enough arguments"
                    + " (need least 4, got " + (words.length - 1) + ')');
        }
        final String metric = words[1];
        if (metric.length() <= 0)
        {
            throw new IllegalArgumentException("empty metric name");
        }
        final long timestamp = Tags.parseLong(words[2]);
        if (timestamp <= 0)
        {
            throw new IllegalArgumentException("invalid timestamp: "
                    + timestamp);
        }
        final String value = words[3];
        if (value.length() <= 0)
        {
            throw new IllegalArgumentException("empty value");
        }
        final HashMap<String, String> tags = new HashMap<String, String>();
        for (int i = 4; i < words.length; i++)
        {
            if (!words[i].isEmpty())
            {
                Tags.parse(tags, words[i]);
            }
        }
        if (value.indexOf('.') < 0)
        { // integer value
            return cissd.addPoint(metric, timestamp, Tags.parseLong(value),
                    tags);
        }
        else
        { // floating point value
            return cissd.addPoint(metric, timestamp, Float.parseFloat(value),
                    tags);
        }
    }
}
