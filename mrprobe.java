package com.skplanet.monitoring.mapreduce;

import java.util.HashMap;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.mapred.*;

final class mrprobe extends Configured
{
    static private HashMap<String, Integer> sRunMap = new HashMap<String, Integer>();
    
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
            JobClient jobClient = new JobClient( new JobConf( new Configuration(true) ) );
        
            do
            {
                /*
                 * Probe Cluster info.
                 */
                printClusterMetrics( jobClient, sPrintTS );

                JobStatus[] sJobStatusList = jobClient.getAllJobs();
                if( sJobStatusList == null )
                {
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
        }
    }
    
    private static void printClusterMetrics( JobClient aJobC, boolean aTS )
    throws Exception
    {
        String sMetric = "hadoop.mapred.clusterstatus";
        if( aTS == true )
        {
            final long sTime = System.currentTimeMillis() / 1000;
            sMetric = sTime + '\t' + sMetric;
        }
        ClusterStatus sClusterStatus = aJobC.getClusterStatus();
        
        final int sNumBlacklistTrackers = sClusterStatus.getBlacklistedTrackers();
        System.out.println( sMetric + '\t' + sNumBlacklistTrackers + '\t' + "hmc_blacklistedtracker" );
        
        final int sNumGraylistTrackers  = sClusterStatus.getGraylistedTrackers();
        System.out.println( sMetric + '\t' + sNumGraylistTrackers + '\t' + "hmc_graylistedtracker" );
        
        final int sNumMapTasks          = sClusterStatus.getMapTasks();
        System.out.println( sMetric + '\t' + sNumMapTasks + '\t' + "hmc_nummaptask" );
        
        final int sMaxMapTasks           = sClusterStatus.getMaxMapTasks();
        System.out.println( sMetric + '\t' + sMaxMapTasks + '\t' + "hmc_maxmaptask" );
        
        final long sMaxJTHeapMem      = sClusterStatus.getMaxMemory();
        System.out.println( sMetric + '\t' + sMaxJTHeapMem + '\t' + "hmc_maxjobtrackerheap" );
        
        final long sJTUsedMem            = sClusterStatus.getUsedMemory();
        System.out.println( sMetric + '\t' + sJTUsedMem + '\t' + "hmc_jobtrackerusedheap" );
        
        final int sNumReduceTasks      = sClusterStatus.getReduceTasks();
        System.out.println( sMetric + '\t' + sNumReduceTasks + '\t' + "hmc_numredtask" );
        
        final int sMaxReduceTasks       = sClusterStatus.getMaxReduceTasks();
        System.out.println( sMetric + '\t' + sMaxReduceTasks + '\t' + "hmc_maxredtask" );
        
        final int sNumExcludedNodes    = sClusterStatus.getNumExcludedNodes();
        System.out.println( sMetric + '\t' + sNumExcludedNodes + '\t' + "hmc_excludednode" );
        
        final int sNumTaskTrackers       = sClusterStatus.getTaskTrackers();
        System.out.println( sMetric + '\t' + sNumTaskTrackers + '\t' + "hmc_numtasktracker" );
        
        final long sTTExpiryInterval         = sClusterStatus.getTTExpiryInterval();
        System.out.println( sMetric + '\t' + sTTExpiryInterval + '\t' + "hmc_ttexpiryinterval" );
    }
    
    private static void printJobMetrics( JobID      aJobID,
                                         RunningJob aRjob,
                                         JobStatus  aJobStatus,
                                         boolean aTS )
    throws Exception
    {
        String sMetric = "hadoop.mapred.job.";
        final int sJobState = aJobStatus.getRunState();
        
        if( aTS == true )
        {
            final long sTime = System.currentTimeMillis() / 1000;
            sMetric = sTime + '\t' + sMetric;
        }
        
        System.out.println( sMetric + "status" + '\t' + sJobState + '\t' + aJobID.toString() );

        switch( sJobState )
        {
            case J_FAILED :
            case J_KILLED :
            case J_SUCCEEDED :
                if( sRunMap.containsKey( aJobID.toString() ) == true )
                {
                    int sV = sRunMap.get( aJobID.toString() );
                    if( sV < REPEAT_NUM )
                    {
                        sRunMap.put( aJobID.toString(), sV + 1);
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
                    sRunMap.put( aJobID.toString(), 0 );
                }
                
                Counters sInterCounters = aRjob.getCounters();
                for( Counters.Group sCgroup : sInterCounters )
                {
                    String sMetric2 = sMetric;
                    String[] sTokens = sCgroup.getName().split( "[.]" );
                    if( sTokens[sTokens.length - 1].contains("$") )
                    {
                        String[] sSubToks = sTokens[sTokens.length - 1].split("[$]");
                        if( sSubToks[0].toLowerCase().contains("file") )
                        {
                            sMetric2 += "fileio";
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
                        }
                        else
                        {
                            sMetric2 += sTokens[sTokens.length - 1].toLowerCase();
                        }
                    }
                
                    for( Counters.Counter sCounter : sCgroup )
                    {
                        System.out.println( sMetric2 + '\t' + sCounter.getValue() +
                                '\t' + aJobID.toString() + '\t' + sCounter.getName().toLowerCase()  );
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
        String sMetric;
        TaskReport[] sTaskReports;

        switch( aType )
        {
            case 0:
                sMetric = "hadoop.mapred.task.map.";
                sTaskReports = aJobC.getMapTaskReports( aJobID );
                break;
            case 1:
                sMetric = "hadoop.mapred.task.red.";
                sTaskReports = aJobC.getReduceTaskReports( aJobID );
                break;
            default:
                throw new AssertionError();
        }
        
        if( aTS == true )
        {
            final long sTime = System.currentTimeMillis() / 1000;
            sMetric = sTime + '\t' + sMetric;
        }
        
        for( TaskReport sTaskr : sTaskReports )
        {
            TIPStatus sState = sTaskr.getCurrentStatus();
            String sTId = sTaskr.getTaskID().toString();
            String sIds = aJobID.toString() + '\t' + sTId;
            
            System.out.println( sMetric + "status" + '\t' + sState.ordinal() + '\t' + sIds );
            if( (sTaskr.getFinishTime() == 0) && (sTaskr.getStartTime() != 0) )
            {
                System.out.println( sMetric + "excution_time" + '\t' +
                        (System.currentTimeMillis() - sTaskr.getStartTime()) + '\t' + sIds );
            }
            else
            {
                System.out.println( sMetric + "excution_time" + '\t' +
                        (sTaskr.getFinishTime() - sTaskr.getStartTime()) + '\t' + sIds );
            }
            System.out.println( sMetric + "progess" + '\t' + sTaskr.getProgress() + '\t' + sIds );
            
            switch( sState.ordinal() )
            {
                case T_FAILED:
                case T_KILLED:
                case T_COMPLETE:
                    if( sRunMap.containsKey( sTId ) == true )
                    {
                        int sV = sRunMap.get( sTId );
                        if( sV < REPEAT_NUM )
                        {
                            sRunMap.put( sTId, sV + 1);
                        }
                        else
                        {
                            sRunMap.remove( sTId );
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
                    Counters sTaskCounters = sTaskr.getCounters();
                    for( Counters.Group sCgroup : sTaskCounters )
                    {
                        String sMetric2 = sMetric;
                        String[] sTokens = sCgroup.getName().split( "[.]" );
                        if( sTokens[sTokens.length - 1].contains("$") )
                        {
                            String[] sSubToks = sTokens[sTokens.length - 1].split("[$]");
                            if( sSubToks[0].toLowerCase().contains("file") )
                            {
                                sMetric2 += "fileio";
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
                            }
                            else
                            {
                                sMetric2 += sTokens[sTokens.length - 1].toLowerCase();
                            }
                        }
                    
                        for( Counters.Counter sCounter : sCgroup )
                        {
                            System.out.println( sMetric2 + '\t' + sCounter.getValue() +
                                    '\t' + sIds + '\t' + sCounter.getName().toLowerCase() );
                        }
                    }
                    break;
                default:
                    break;
            }
        }
    }
}
