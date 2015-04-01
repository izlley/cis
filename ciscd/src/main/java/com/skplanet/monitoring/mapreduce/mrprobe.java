package com.skplanet.monitoring.mapreduce;

import java.util.ArrayList;
import java.util.HashMap;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.mapred.*;
import org.apache.hadoop.mapred.Counters.Counter;
import org.apache.hadoop.mapred.Counters.Group;
import org.apache.hadoop.fs.FsUrlStreamHandlerFactory;
import org.apache.hadoop.io.IOUtils;
import java.net.*;
import java.io.*;

final class mrprobe extends Configured
{
    static {
        URL.setURLStreamHandlerFactory(new FsUrlStreamHandlerFactory());
    }
    static private HashMap<String, ArrayList<Object>> sRunMap = new HashMap<String, ArrayList<Object>>();
    static private HashMap<String, Integer> sTRunMap = new HashMap<String, Integer>();
    
    //   Hadoop:JobStatus.java    //
    static final int J_RUNNING = 1;
    static final int J_SUCCEEDED = 2;
    static final int J_FAILED = 3;
    static final int J_PREP = 4;
    static final int J_KILLED = 5;
    
    // hadoop:TIPStatus.java
    //  public enum TIPStatus {
    //    PENDING, RUNNING, COMPLETE, KILLED, FAILED;
    //  }
    static final int T_PENDING = 0;
    static final int T_RUNNING = 1;
    static final int T_COMPLETE = 2;
    static final int T_KILLED = 3;
    static final int T_FAILED = 4;
    
    static final int REPEAT_NUM = 5;
    
    static final int RETRY_HTTP = 10;
    static final String mRowtag = "<tr>";
    static final String mColtag = "<td>";
    static final String mLinkendtag = "</a>";
    
    private static void mrUsage()
    {
        System.out.println("Usage:\n"
                + "  mrprobe --watch N          Reprint the output every N seconds.\n"
                + "          --timestamp        Print a timestamp at the beginning of each line.\n" );
    }
    
    private static void mrFatal(final int aRes, final String aErrmsg)
    {
        System.err.println(aErrmsg);
        System.exit(aRes);
        throw new AssertionError("Fatal error");
    }
    
    public static void main( final String[] args ) throws Exception
    {
        int     sCurrentArg = 0;
        int     sWatch = 0;
        boolean sPrintTS = false;

        if (args.length == 0 || "-h".equals(args[0])
            || "--help".equals(args[0]))
        {
            mrUsage();
            System.exit(args.length == 0 ? 1 : 0);
            return;
        }
        
        /*
         * Parse arguments
         */
        while (sCurrentArg < args.length)
        {
            if ("--watch".equals(args[sCurrentArg]) == true)
            {
                sCurrentArg++;
                try
                {
                    sWatch = Integer.parseInt(args[sCurrentArg]);
                }
                catch (NumberFormatException e)
                {
                    mrFatal(1, "error: Invalid value for --watch: " + e.getMessage());
                    return;
                }

                if (sWatch < 1)
                {
                    mrFatal(1, "error: Invalid value for --watch: " + sWatch);
                }
                sCurrentArg++;
            }
            else if ("--timestamp".equals(args[sCurrentArg]) == true)
            {
                sPrintTS = true;
                sCurrentArg++;
            }
            else
            {
                break;
            }
        }
        
        try
        {
            Configuration sConf = new Configuration(true);
            JobClient jobClient = new JobClient( new JobConf( sConf ) );
        
            do
            {
                /*
                 * Probe Cluster info.
                 */
                printClusterMetrics( jobClient, sPrintTS );

                JobStatus[] sJobStatusList = jobClient.getAllJobs();
                if( sJobStatusList == null )
                {
                    //"There is no Job!"
                    return;
                }

                for( JobStatus sJobStatus : sJobStatusList )
                {
                    JobID sJid = sJobStatus.getJobID();
                    RunningJob sRjob = jobClient.getJob( sJid );
                    
                    if( sRjob != null )
                    {
                        /*
                         * Probe Job info.
                         */
                        printJobMetrics( sJid, sRjob, sJobStatus, sPrintTS );

                        /*
                         * Probe Task info.
                         */
                        printTaskMetrics( sJid, jobClient, 0, sPrintTS); // 0 for mapper
                        printTaskMetrics( sJid, jobClient, 1, sPrintTS); // 1 for reducer
                    }
                }
                Thread.sleep( sWatch * 1000 );
            } while( sWatch > 0 );
        }
        catch( Exception e )
        {
            System.err.println(e.getMessage());
            e.printStackTrace();
        }
        finally
        {
            sRunMap.clear();
            sTRunMap.clear();
        }
    }
    
    private static void printClusterMetrics( JobClient aJobC, boolean aTS )
    throws Exception
    {
        String sMetric = "hadoop.mapred.clusterstatus";
        if( aTS == true )
        {
            final long sTime = System.currentTimeMillis() / 1000;
            sMetric = sTime + "\t" + sMetric;
        }
        ClusterStatus sClusterStatus = aJobC.getClusterStatus();
        
        final int sNumBlacklistTrackers = sClusterStatus.getBlacklistedTrackers();
        System.out.println( sMetric + "\t" + sNumBlacklistTrackers + "\t" + "hmc_blacklistedtracker" );
        
        final int sNumGraylistTrackers  = sClusterStatus.getGraylistedTrackers();
        System.out.println( sMetric + "\t" + sNumGraylistTrackers + "\t" + "hmc_graylistedtracker" );
        
        final int sNumMapTasks          = sClusterStatus.getMapTasks();
        System.out.println( sMetric + "\t" + sNumMapTasks + "\t" + "hmc_nummaptask" );
        
        final int sMaxMapTasks           = sClusterStatus.getMaxMapTasks();
        System.out.println( sMetric + "\t" + sMaxMapTasks + "\t" + "hmc_maxmaptask" );
        
        final long sMaxJTHeapMem      = sClusterStatus.getMaxMemory();
        System.out.println( sMetric + "\t" + sMaxJTHeapMem + "\t" + "hmc_maxjobtrackerheap" );
        
        final long sJTUsedMem            = sClusterStatus.getUsedMemory();
        System.out.println( sMetric + "\t" + sJTUsedMem + "\t" + "hmc_jobtrackerusedheap" );
        
        final int sNumReduceTasks      = sClusterStatus.getReduceTasks();
        System.out.println( sMetric + "\t" + sNumReduceTasks + "\t" + "hmc_numredtask" );
        
        final int sMaxReduceTasks       = sClusterStatus.getMaxReduceTasks();
        System.out.println( sMetric + "\t" + sMaxReduceTasks + "\t" + "hmc_maxredtask" );
        
        final int sNumExcludedNodes    = sClusterStatus.getNumExcludedNodes();
        System.out.println( sMetric + "\t" + sNumExcludedNodes + "\t" + "hmc_excludednode" );
        
        final int sNumTaskTrackers       = sClusterStatus.getTaskTrackers();
        System.out.println( sMetric + "\t" + sNumTaskTrackers + "\t" + "hmc_numtasktracker" );
        
        final long sTTExpiryInterval         = sClusterStatus.getTTExpiryInterval();
        System.out.println( sMetric + "\t" + sTTExpiryInterval + "\t" + "hmc_ttexpiryinterval" );
    }
    
    private static void printJobMetrics( JobID      aJobID,
                                         RunningJob aRjob,
                                         JobStatus  aJobStatus,
                                         boolean aTS )
    throws Exception
    {
        InputStream sIn = null;
        OutputStream sOutput = new OutputStream()
        {
            private StringBuilder string = new StringBuilder();
            @Override
            public void write(int b) throws IOException
            {
                this.string.append((char)b);
            }
            public String toString(){
                return this.string.toString();
            }
        };
        
        String sMetric = "hadoop.mapred.job.";
        final int sJobState = aJobStatus.getRunState();
        
        //get Workflow InstID
        /*sIn = new URL(aRjob.getJobFile()).openStream();
        IOUtils.copyBytes( sIn, sOutput, 4096, true ); // 4k
        System.out.println(sOutput.toString());*/
        
        if( aTS == true )
        {
            final long sTime = System.currentTimeMillis() / 1000;
            sMetric = sTime + "\t" + sMetric;
        }

        switch( sJobState )
        {
            case J_FAILED :
            case J_KILLED :
            case J_SUCCEEDED :
                
                if( sRunMap.containsKey( aJobID.toString() ) == true )
                {
                    ArrayList<Object> sAL = (ArrayList<Object>)sRunMap.get( aJobID.toString() );
                    int sV = (Integer)sAL.get(0);
                    if( sV < REPEAT_NUM )
                    {
                        sAL.set(0, new Integer(sV+1));
                        sRunMap.put( aJobID.toString(), sAL);
                    }
                    else
                    {
                        sRunMap.remove( aJobID.toString() );
                        break;
                    }
                }
                else
                {
                    // dog ignore
                    break;
                }
            case J_PREP :
            case J_RUNNING :
                if( sRunMap.containsKey( aJobID.toString() ) == false )
                {
                    ArrayList<Object> sAL = new ArrayList<Object>(2);
                    sAL.add(new Integer(0));
                    
                    try
                    {
                        sIn = new URL(aRjob.getJobFile()).openStream();
                        IOUtils.copyBytes(sIn, sOutput, 4096, true); // 4k
                        // System.out.println(sOutput.toString());
                        String sJobXML = sOutput.toString();
                        StringBuilder sVal = new StringBuilder();

                        int sSInd = sJobXML
                                .indexOf("circus.action.instance.id");
                        if (sSInd != -1)
                        {
                            int sVInd = sJobXML.indexOf("<value>", sSInd);
                            if (sVInd != -1)
                            {
                                sVInd = sJobXML.indexOf('>', sVInd) + 1;
                                for (int i = sVInd, j = 0; sJobXML.charAt(i) != '<'; i++)
                                {
                                    char sCh = sJobXML.charAt(i);
                                    if (sCh == '.')
                                    {
                                        j++;
                                    }
                                    if (j == 2)
                                    {
                                        break;
                                    }
                                    else
                                    {
                                        sVal.append(sCh);
                                    }
                                }
                            }
                            sAL.add(sVal.toString());
                        }
                        else
                        {
                            sAL.add(new String(""));
                        }
                    }
                    catch( FileNotFoundException e )
                    {
                        sAL.add( new String("") );
                    }
                    
                    sRunMap.put( aJobID.toString(), sAL );
                }
                String sWfId = (String)(((ArrayList<Object>)sRunMap.get( aJobID.toString() )).get(1));
                System.out.println( sMetric + "status" + "\t" + sJobState + "\t" + aJobID.toString() +
                        ((sWfId.length()>0) ? ("\twfid_"+sWfId) : "") );
                
                Counters sInterCounters = aRjob.getCounters();
                for( Group sCgroup : sInterCounters )
                {
                    String sMetric2 = sMetric;
                    String sTagsep = "";
                    String[] sTokens = sCgroup.getName().split( "[.]" );
                    if( sTokens[sTokens.length - 1].contains("$") )
                    {
                        String[] sSubToks = sTokens[sTokens.length - 1].split("[$]");
                        if( sSubToks[0].toLowerCase().contains("file") )
                        {
                            sMetric2 += "fileio";
                            sTagsep = "jf_";
                        }
                        else if( sSubToks[0].toLowerCase().contains("task") )
                        {
                            sMetric2 += "task";
                            sTagsep = "jt_";
                        }
                        else
                        {
                            sMetric2 += sSubToks[0].toLowerCase();
                        }
                    }
                    else
                    {
                        if( sTokens[sTokens.length - 1].toLowerCase().contains("file") )
                        {
                            sMetric2 += "fileio";
                            sTagsep = "jf_";
                        }
                        else if( sTokens[sTokens.length - 1].toLowerCase().contains("task") )
                        {
                            sMetric2 += "task";
                            sTagsep = "jt_";
                        }
                        else
                        {
                            sMetric2 += sTokens[sTokens.length - 1].toLowerCase();
                        }
                    }
                
                    for( Counter sCounter : sCgroup )
                    {
                        System.out.println( sMetric2 + "\t" + sCounter.getValue() + "\t" +
                                sTagsep + sCounter.getName().toLowerCase() + "\t" + aJobID.toString() +
                                ( (sWfId.length()>0) ? ("\twfid_"+sWfId) : "") );
                    }
                }
                break;
            default:
                break;
        } //switch
    }
    
    private static void printTaskMetrics( JobID     aJobID,
                                          JobClient aJobC,
                                          final int aType,
                                          boolean aTS )
    throws Exception
    {
        String sMetric = "";
        String sTagsep = "";
        TaskReport[] sTaskReports;
        boolean sIsMapper;

        switch( aType )
        {
            case 0:
                sMetric = "hadoop.mapred.task.map.";
                sTaskReports = aJobC.getMapTaskReports( aJobID );
                sIsMapper = true;
                break;
            case 1:
                sMetric = "hadoop.mapred.task.red.";
                sTaskReports = aJobC.getReduceTaskReports( aJobID );
                sIsMapper = false;
                break;
            default:
                throw new AssertionError();
        }
        
        if( aTS == true )
        {
            final long sTime = System.currentTimeMillis() / 1000;
            sMetric = sTime + "\t" + sMetric;
        }
        
        for( TaskReport sTaskr : sTaskReports )
        {
            TIPStatus sState = sTaskr.getCurrentStatus();
            String sTId = sTaskr.getTaskID().toString();

            for( TaskAttemptID sAttemptID : sTaskr.getRunningTaskAttempts() )
            {
                String sIds = aJobID.toString() + "\t" + sTId + "\t" + sAttemptID.toString();

                switch( sState.ordinal() )
                {
                    case T_FAILED:
                    case T_KILLED:
                    case T_COMPLETE:
                        if( sTRunMap.containsKey( sTId ) == true )
                        {
                            int sV = sTRunMap.get( sTId );
                            if( sV < REPEAT_NUM )
                            {
                                sTRunMap.put( sTId, sV + 1);
                            }
                            else
                            {
                                sTRunMap.remove( sTId );
                                break;
                            }
                        }
                        else
                        {
                            // dog ignore
                            break;
                        }
                    case T_PENDING:
                    case T_RUNNING:
                        if( sTRunMap.containsKey( sTId.toString() ) == false )
                        {
                            sTRunMap.put( sTId.toString(), 0 );
                        }
                        
                        String sMachine;
                        if( (sMachine = getMachine( sAttemptID.toString() )) != null )
                        {
                            sIds += "\t/" + sMachine;
                        }
                        String sWfId = (String)(((ArrayList<Object>)sRunMap.get( aJobID.toString() )).get(1));
                        if( sWfId.length() != 0 )
                        {
                            sIds += "\twfid_" + sWfId;
                        }
                        System.out.println( sMetric + "status" + "\t" + sState.ordinal() + "\t" + sIds );
                        System.out.println( sMetric + "progess" + "\t" + sTaskr.getProgress() + "\t" + sIds );
                        if( (sTaskr.getFinishTime() == 0) && (sTaskr.getStartTime() != 0) )
                        {
                            System.out.println( sMetric + "excution_time" + "\t" +
                                    (System.currentTimeMillis() - sTaskr.getStartTime()) + "\t" + sIds );
                        }
                        else
                        {
                            System.out.println( sMetric + "excution_time" + "\t" +
                                    (sTaskr.getFinishTime() - sTaskr.getStartTime()) + "\t" + sIds );
                        }

                        Counters sTaskCounters = sTaskr.getCounters();
                        for( Group sCgroup : sTaskCounters )
                        {
                            String sMetric2 = sMetric;
                            String[] sTokens = sCgroup.getName().split( "[.]" );
                            if( sTokens[sTokens.length - 1].contains("$") )
                            {
                                String[] sSubToks = sTokens[sTokens.length - 1].split("[$]");
                                if( sSubToks[0].toLowerCase().contains("file") )
                                {
                                    sMetric2 += "fileio";
                                    if( sIsMapper == true )
                                    {
                                        sTagsep = "tmf_";
                                    }
                                    else
                                    {
                                        sTagsep = "trf_";
                                    }
                                }
                                else if( sSubToks[0].toLowerCase().contains("task") )
                                {
                                    sMetric2 += "task";
                                    if( sIsMapper == true )
                                    {
                                        sTagsep = "tmt_";
                                    }
                                    else
                                    {
                                        sTagsep = "trt_";
                                    }
                                }
                                else
                                {
                                    sMetric2 += sSubToks[0].toLowerCase();
                                    if( sIsMapper == true )
                                    {
                                        sTagsep = "tm_";
                                    }
                                    else
                                    {
                                        sTagsep = "tr_";
                                    }
                                }
                            }
                            else
                            {
                                if( sTokens[sTokens.length - 1].toLowerCase().contains("file") )
                                {
                                    sMetric2 += "fileio";
                                    if( sIsMapper == true )
                                    {
                                        sTagsep = "tmf_";
                                    }
                                    else
                                    {
                                        sTagsep = "trf_";
                                    }
                                }
                                else if( sTokens[sTokens.length - 1].toLowerCase().contains("task") )
                                {
                                    sMetric2 += "task";
                                    if( sIsMapper == true )
                                    {
                                        sTagsep = "tmt_";
                                    }
                                    else
                                    {
                                        sTagsep = "trt_";
                                    }
                                }
                                else
                                {
                                    sMetric2 += sTokens[sTokens.length - 1].toLowerCase();
                                    if( sIsMapper == true )
                                    {
                                        sTagsep = "tm_";
                                    }
                                    else
                                    {
                                        sTagsep = "tr_";
                                    }
                                }
                            }

                            for( Counter sCounter : sCgroup )
                            {
                                System.out.println( sMetric2 + "\t" + sCounter.getValue() +
                                        "\t" + sTagsep + sCounter.getName().toLowerCase() + "\t" + sIds );
                            }
                        }
                        break;
                    default:
                        break;
                }
            }
        }
    }
    
    private static String getMachine( String aAttemptID )
    {
        for( int sRe = 0 ; RETRY_HTTP > sRe ; sRe++ )
        {
            try
            {
                URL sTrackerURL = new URL( "http://127.0.0.1:50030/taskdetails.jsp?attemptid=" + aAttemptID );
                URLConnection sConn = sTrackerURL.openConnection();
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(
                                sConn.getInputStream()));

                String sHtml = "";
                String inputLine;
                while (( inputLine = in.readLine() ) != null)
                {
                    sHtml += inputLine;
                }
                in.close();

                int sInd = 0;
                sInd = sHtml.indexOf(mRowtag, sInd);
                if( sInd == -1 )
                {
                    // HTTP ERROR 500 or job not found
                    Thread.sleep(100);
                    continue;
                }
                sInd += mRowtag.length();
                sInd = sHtml.indexOf(mRowtag, sInd);
                sInd += mRowtag.length();

                sInd = sHtml.indexOf(mColtag, sInd);
                sInd += mColtag.length();
                sInd = sHtml.indexOf(mColtag, sInd);
                sInd += mColtag.length();

                if( sHtml.charAt(sInd + 1) == 'a' )
                {
                    sInd = sHtml.indexOf(mLinkendtag, sInd);
                    int i = sInd - 1;
                    for( ; (i > 0) &&
                            (sHtml.charAt(i) != '/') &&
                            (sHtml.charAt(i) != '>')
                            ; i-- )
                    {
                    }
                    return sHtml.substring( i + 1, sInd );
                }
                else
                {
                    Thread.sleep(100);
                    continue;
                }
            }
            catch( Exception e )
            {
                //ignore
                continue;
            }
        }
        return null;
    }
}
