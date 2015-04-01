package com.skplanet.monitoring.jmx;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.openmbean.TabularData;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

// for Sun
import com.sun.tools.attach.AgentInitializationException;
import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;
import sun.management.ConnectorAddressLink;
import sun.jvmstat.monitor.HostIdentifier;
import sun.jvmstat.monitor.MonitoredHost;
import sun.jvmstat.monitor.MonitoredVm;
import sun.jvmstat.monitor.MonitoredVmUtil;
import sun.jvmstat.monitor.VmIdentifier;

final class jmxprobe
{
    private static final String LOCAL_CONNECTOR_ADDR = "com.sun.management.jmxremote.localConnectorAddress";
    private static final String MAGIC_STRING = "this.is.jmx.magic";

    private static final String[] JMX_ERRMSG = { "Fatal error", // 0
            "error: Invalid value for --watch: ", // 1
            "error: Missing argument ( -l or JVM specification )", // 2
            "Ignoring exception: ", // 3
            "Ignoring invalid VirtualMachineDescriptor id: ", // 4
            "error: VM not attachable: ", // 5
            "error: Could not attach: ", // 6
            "empty JVM name", // 7
            "error: Failed to attach to ", // 8
            "error: Management agent not found", // 9
            "error: Failed to load the agent into ", // 10
            "error: Failed to initialize the agent into ", // 11
            "error: Error while loading agent into ", // 12
            "error: Failed to detach from ", // 13
            "error: Couldn't start the management agent.", // 14
            "error: No MBean matched your query in ", // 15
            "error: Invalid PID: ", // 16
            "error: Couldn't find a JVM with PID ", // 17
            "error: Invalid regexp: ", // 18
            "error: No JVM matched your regex ", // 19
            " JVMs matched your regexp ", // 20
            ", it's too ambiguous, please refine it.", // 21
            "error: Invalid pattern: ", // 22
            "error: Unexpected Exception: " // 23
    };

    private static void jmxFatal(final int aRes, final String aErrmsg)
    {
        System.err.println(aErrmsg);
        System.exit(aRes);
        throw new AssertionError(JMX_ERRMSG[0]);
    }

    private static void jmxUsage()
    {
        System.out
                .println("Usage:\n"
                        + "  jmxprobe -l                    Lists all reachable VMs.\n"
                        + "  jmxprobe <JVM>                 Lists all MBeans for this JVM (PID or regexp).\n"
                        + "  jmxprobe <JVM> <MBean>         Prints all the attributes of this MBean.\n"
                        + "  jmxprobe <JVM> <MBean> <attr>  Prints the matching attributes of this MBean.\n"
                        + "\n"
                        + "You can pass multiple <MBean> <attr> pairs to match multiple different\n"
                        + "attributes for different MBeans.  For example:\n"
                        + "  jmxprobe --long JConsole Class Count Thread Total Garbage Collection\n"
                        + "  LoadedClassCount	2808	java.lang:type=ClassLoading\n"
                        + "  UnloadedClassCount	0	java.lang:type=ClassLoading\n"
                        + "  TotalLoadedClassCount	2808	java.lang:type=ClassLoading\n"
                        + "  CollectionCount	0	java.lang:type=GarbageCollector,name=ConcurrentMarkSweep\n"
                        + "  CollectionTime	0	java.lang:type=GarbageCollector,name=ConcurrentMarkSweep\n"
                        + "  CollectionCount	1	java.lang:type=GarbageCollector,name=ParNew\n"
                        + "  CollectionTime	19	java.lang:type=GarbageCollector,name=ParNew\n"
                        + "  TotalStartedThreadCount	43	java.lang:type=Threading\n"
                        + "The command above searched for a JVM with `JConsole' in its name, and then searched\n"
                        + "for MBeans with `Class' in the name and `Count' in the attribute (first 3 matches\n"
                        + "in this output), MBeans with `Thread' in the name and `Total' in the attribute (last\n"
                        + "line in the output) and MBeans matching `Garbage' with a `Collection' attribute.\n"
                        + "\n"
                        + "Other flags you can pass:\n"
                        + "  --long                    Print a longer but more explicit output for each value.\n"
                        + "  --timestamp               Print a timestamp at the beginning of each line.\n"
                        + "  --watch N                 Reprint the output every N seconds.\n"
                        + "\n"
                        + "Return value:\n"
                        + "  0: Everything OK.\n"
                        + "  1: Invalid usage or unexpected error.\n"
                        + "  2: No JVM matched.\n"
                        + "  3: No MBean matched.\n"
                        + "  4: No attribute matched for the MBean(s) selected.");
    }

    public static void main(final String[] args) throws Exception
    {
        int sCurrentArg = 0;
        int sWatch = 0;
        boolean sLongOutput = false;
        boolean sPrintTS = false;

        if (args.length == 0 || "-h".equals(args[0])
                || "--help".equals(args[0]))
        {
            jmxUsage();
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
                    jmxFatal(1, JMX_ERRMSG[1] + e.getMessage());
                    return;
                }

                if (sWatch < 1)
                {
                    jmxFatal(1, JMX_ERRMSG[1] + sWatch);
                }
                sCurrentArg++;
            }
            else if ("--long".equals(args[sCurrentArg]) == true)
            {
                sLongOutput = true;
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

        if (sCurrentArg == args.length)
        {
            jmxUsage();
            jmxFatal(1, JMX_ERRMSG[2]);
            return;
        }

        HashMap<Integer, JVM> sJvms = getJVMs();
        if ("-l".equals(args[sCurrentArg]) == true)
        {
            jmxPrintVmList(sJvms.values());
            return;
        }

        final JVM sJvm = jmxSelectJVM(args[sCurrentArg++], sJvms);
        sJvms = null;

        final JMXConnector sConnection = JMXConnectorFactory.connect(sJvm
                .getJmxUrl());
        try
        {
            final MBeanServerConnection sMbcon = sConnection
                    .getMBeanServerConnection();
            if (args.length == sCurrentArg)
            {
                for (final ObjectName sMbean : jmxListMBeans(sMbcon))
                {
                    System.out.println(sMbean);
                }
                return;
            }
            /* <MBean name, Attr pattern> */
            final TreeMap<ObjectName, Pattern> sObjs = jmxSelectMBeans(args,
                    sCurrentArg, sMbcon);
            if (sObjs.isEmpty())
            {
                jmxFatal(3, JMX_ERRMSG[15] + sJvm.getName());
                return;
            }
            do
            {
                boolean sFound = false;
                for (final Map.Entry<ObjectName, Pattern> sEntry : sObjs
                        .entrySet())
                {
                    final ObjectName sObj = sEntry.getKey();
                    final MBeanInfo sMbean = sMbcon.getMBeanInfo(sObj);
                    final Pattern sWanted = sEntry.getValue();
                    for (final MBeanAttributeInfo sAttr : sMbean
                            .getAttributes())
                    {
                        if (sWanted == null
                                || sWanted.matcher(sAttr.getName()).find())
                        {
                            jmxDumpMBean(sLongOutput, sPrintTS, sMbcon, sObj,
                                    sAttr);
                            sFound = true;
                        }
                    }
                }
                if (sFound == false)
                {
                    jmxFatal(4, "No attribute of " + sObjs.keySet()
                            + " matched your query in " + sJvm.getName());
                    return;
                }
                System.out.flush();
                Thread.sleep(sWatch * 1000);
            } while (sWatch > 0);
        }
        catch (UnsupportedOperationException e)
        {
            // Ignore
        }
        finally
        {
            sConnection.close();
        }
    }

    private static JVM jmxSelectJVM(final String aSelector,
            final HashMap<Integer, JVM> aJvms)
    {
        String sErr = null;
        try
        {
            final int sPid = Integer.parseInt(aSelector);
            if (sPid < 2)
            {
                throw new IllegalArgumentException(JMX_ERRMSG[16] + sPid);
            }
            final JVM sJvm = aJvms.get(sPid);
            if (sJvm != null)
            {
                return sJvm;
            }
            sErr = JMX_ERRMSG[17] + sPid;
        }
        catch (NumberFormatException eIgnore)
        {
        }

        /* it's not a number format */
        if (sErr == null)
        {
            try
            {
                final Pattern sPat = jmxCompileRegex(aSelector);
                final ArrayList<JVM> sMatches = new ArrayList<JVM>(2);
                for (final JVM sJvm : aJvms.values())
                {
                    if (sPat.matcher(sJvm.getName()).find())
                    {
                        sMatches.add(sJvm);
                    }
                }

                /* Exclude ourselves */
                System.setProperty(MAGIC_STRING,
                        "LOL Java processes can't get their own PID");
                final String sSelf = jmxprobe.class.getName();
                final Iterator<JVM> sIter = sMatches.iterator();
                while (sIter.hasNext())
                {
                    final JVM sJvm = sIter.next();
                    final String sName = sJvm.getName();

                    if (sName.contains("--watch") && sName.contains(sSelf))
                    {
                        sIter.remove();
                        continue;
                    }

                    final VirtualMachine sVm = VirtualMachine.attach(String
                            .valueOf(sJvm.getPid()));
                    try
                    {
                        if (sVm.getSystemProperties().containsKey(MAGIC_STRING))
                        {
                            sIter.remove();
                            continue;
                        }
                    }
                    finally
                    {
                        sVm.detach();
                    }
                }

                System.clearProperty(MAGIC_STRING);
                if (sMatches.size() == 0)
                {
                    sErr = JMX_ERRMSG[19] + aSelector;
                }
                else if (sMatches.size() > 1)
                {
                    jmxPrintVmList(sMatches);
                    sErr = sMatches.size() + JMX_ERRMSG[20] + aSelector
                            + JMX_ERRMSG[21];
                }
                else
                {
                    return sMatches.get(0);
                }
            }
            catch (PatternSyntaxException e)
            {
                sErr = JMX_ERRMSG[22] + aSelector + " : " + e.getMessage();
            }
            catch (Exception e)
            {
                e.printStackTrace();
                sErr = JMX_ERRMSG[23] + e.getMessage();
            }
        }
        jmxFatal(2, sErr);
        return null;
    }

    private static Pattern jmxCompileRegex(final String aRegex)
    {
        try
        {
            return Pattern.compile(aRegex);
        }
        catch (PatternSyntaxException e)
        {
            jmxFatal(1, JMX_ERRMSG[18] + aRegex + ", " + e.getMessage());
            throw new AssertionError();
        }
    }

    /*
     * Returns a map from PID to JVM
     */
    private static HashMap<Integer, JVM> getJVMs() throws Exception
    {
        final HashMap<Integer, JVM> sJvms = new HashMap<Integer, JVM>();
        getMonitoredVMs(sJvms);
        getAttachableVMs(sJvms);
        return sJvms;
    }

    private static void getMonitoredVMs(final HashMap<Integer, JVM> aJvms)
            throws Exception
    {
        /*
         * null string is interpreted to mean a local connection to the local
         * host and is equivalent to the string local://localhost
         */
        final MonitoredHost sHost = MonitoredHost
                .getMonitoredHost(new HostIdentifier((String) null));
        @SuppressWarnings("unchecked")
        final Set<Integer> sJvms = sHost.activeVms();
        for (final Integer sPid : sJvms)
        {
            try
            {
                final VmIdentifier sVmid = new VmIdentifier(sPid.toString());
                final MonitoredVm sVm = sHost.getMonitoredVm(sVmid);
                aJvms.put(sPid, new JVM(sPid, MonitoredVmUtil.commandLine(sVm),
                        ConnectorAddressLink.importFrom(sPid)));
                sVm.detach();
            }
            catch (Exception e)
            {
                System.err.println(JMX_ERRMSG[3]);
                e.printStackTrace();
            }
        }
    }

    private static void getAttachableVMs(final HashMap<Integer, JVM> aJvms)
    {
        int sPid;

        for (final VirtualMachineDescriptor sVmd : VirtualMachine.list())
        {
            try
            {
                sPid = Integer.parseInt(sVmd.id());
            }
            catch (NumberFormatException e)
            {
                System.err.println(JMX_ERRMSG[4] + sVmd.id() + " "
                        + e.getMessage());
                continue;
            }

            if (aJvms.containsKey(sPid))
            {
                continue;
            }

            try
            {
                final VirtualMachine sVm = VirtualMachine.attach(sVmd);
                aJvms.put(
                        sPid,
                        new JVM(sPid, String.valueOf(sPid), (String) sVm
                                .getAgentProperties().get(LOCAL_CONNECTOR_ADDR)));
                sVm.detach();
            }
            catch (AttachNotSupportedException e)
            {
                System.err.println(JMX_ERRMSG[5] + sVmd.id() + " "
                        + e.getMessage());
            }
            catch (IOException e)
            {
                System.err.println(JMX_ERRMSG[6] + sVmd.id() + " "
                        + e.getMessage());
            }
        }
    }

    private static void jmxPrintVmList(final Collection<JVM> aVms)
    {
        final ArrayList<JVM> sVms = new ArrayList<JVM>(aVms);
        Collections.sort(sVms, new Comparator<JVM>()
        {
            // Ascending order
            public int compare(final JVM a, final JVM b)
            {
                return a.getPid() - b.getPid();
            }
        });
        for (final JVM sJvm : sVms)
        {
            System.out.println(sJvm.getPid() + "\t" + sJvm.getName());
        }
    }

    private static final class JVM
    {
        final int mPid;
        final String mName;
        String mAddress;

        public JVM(final int aPid, final String aName, final String aAddress)
        {
            if (aName.isEmpty() == true)
            {
                throw new IllegalArgumentException(JMX_ERRMSG[7]);
            }
            this.mPid = aPid;
            this.mName = aName;
            this.mAddress = aAddress;
        }

        public int getPid()
        {
            return mPid;
        }

        public String getName()
        {
            return mName;
        }

        public JMXServiceURL getJmxUrl()
        {
            if (mAddress == null)
            {
                ensureManagementAgentStarted();
            }
            try
            {
                return new JMXServiceURL(mAddress);
            }
            catch (Exception e)
            {
                throw new RuntimeException("Error", e);
            }
        }

        public void ensureManagementAgentStarted()
        {
            if (mAddress != null)
            {
                return;
            }

            VirtualMachine sVm;
            try
            {
                sVm = VirtualMachine.attach(String.valueOf(mPid));
            }
            catch (AttachNotSupportedException e)
            {
                throw new RuntimeException(JMX_ERRMSG[8] + this, e);
            }
            catch (IOException e)
            {
                throw new RuntimeException(JMX_ERRMSG[8] + this, e);
            }
            try
            {
                final String sHome = sVm.getSystemProperties().getProperty(
                        "java.home");

                // Normally in ${java.home}/jre/lib/management-agent.jar but
                // might
                // be in ${java.home}/lib in build environments.
                String sAgent = sHome + File.separator + "jre" + File.separator
                        + "lib" + File.separator + "management-agent.jar";
                File sFile = new File(sAgent);
                if (!sFile.exists())
                {
                    sAgent = sHome + File.separator + "lib" + File.separator
                            + "management-agent.jar";
                    sFile = new File(sAgent);
                    if (!sFile.exists())
                    {
                        throw new RuntimeException(JMX_ERRMSG[9]);
                    }
                }

                sAgent = sFile.getCanonicalPath();
                try
                {
                    sVm.loadAgent(sAgent, "com.sun.management.jmxremote");
                }
                catch (AgentLoadException e)
                {
                    throw new RuntimeException(JMX_ERRMSG[10] + this, e);
                }
                catch (AgentInitializationException e)
                {
                    throw new RuntimeException(JMX_ERRMSG[11] + this, e);
                }
                mAddress = (String) sVm.getAgentProperties().get(
                        LOCAL_CONNECTOR_ADDR);
            }
            catch (IOException e)
            {
                throw new RuntimeException(JMX_ERRMSG[12] + this, e);
            }
            finally
            {
                try
                {
                    sVm.detach();
                }
                catch (IOException e)
                {
                    throw new RuntimeException(JMX_ERRMSG[13] + sVm + " = "
                            + this, e);
                }
            }

            if (mAddress == null)
            {
                throw new RuntimeException(JMX_ERRMSG[14]);
            }
        }

        public String toString()
        {
            return "JVM(" + mPid + ", \"" + mName + "\", "
                    + (mAddress == null ? null : '"' + mAddress + '"') + ')';
        }
    }

    private static ArrayList<ObjectName> jmxListMBeans(
            final MBeanServerConnection aMbcon) throws IOException
    {
        ArrayList<ObjectName> sMbeans = new ArrayList<ObjectName>(
                aMbcon.queryNames(null, null));
        Collections.sort(sMbeans, new Comparator<ObjectName>()
        {
            public int compare(final ObjectName a, final ObjectName b)
            {
                return a.toString().compareTo(b.toString());
            }
        });
        return sMbeans;
    }

    private static TreeMap<ObjectName, Pattern> jmxSelectMBeans(
            final String[] args, final int aCurrentArg,
            final MBeanServerConnection aMbcon) throws IOException
    {
        final TreeMap<ObjectName, Pattern> sMbeans = new TreeMap<ObjectName, Pattern>();
        for (int i = aCurrentArg; i < args.length; i += 2)
        {
            final Pattern sObjRegex = jmxCompileRegex(args[i]);
            final Pattern sAttrRegex = (i + 1) < args.length ? jmxCompileRegex(args[i + 1])
                    : null;
            for (final ObjectName sO : jmxListMBeans(aMbcon))
            {
                if (sObjRegex.matcher(sO.toString()).find())
                {
                    sMbeans.put(sO, sAttrRegex);
                }
            }
        }
        return sMbeans;
    }

    private static void jmxDumpMBean(final boolean aLongOutput,
            final boolean aPrintTS, final MBeanServerConnection aMbcon,
            final ObjectName aObjname, final MBeanAttributeInfo aAttr)
            throws Exception
    {
        final String sName = aAttr.getName();
        Object sValue = aMbcon.getAttribute(aObjname, sName);
        if (sValue instanceof TabularData)
        {
            final TabularData sTab = (TabularData) sValue;
            int i = 0;
            for (final Object sO : sTab.keySet())
            {
                jmxDumpMBeanValue(aLongOutput, aPrintTS, aObjname, sName + "."
                        + i, sO);
                i++;
            }
        }
        else
        {
            jmxDumpMBeanValue(aLongOutput, aPrintTS, aObjname, sName, sValue);
        }
    }

    private static void jmxDumpMBeanValue(final boolean aLongOutput,
            final boolean aPrintTS, final ObjectName aObjname,
            final String aAttrname, final Object aValue)
    {
        int i = 0;
        boolean sBad = true;
        final StringBuilder sBuf = new StringBuilder();
        final long sTS = System.currentTimeMillis() / 1000;

        if (aValue instanceof Object[])
        {
            for (final Object sO : (Object[]) aValue)
            {
                if (sO instanceof String)
                {
                    if (((String) sO).length() == 0)
                    {
                        continue;
                    }
                    else
                    {
                        if ((i++ == 0) && (aPrintTS == true))
                        {
                            sBuf.append(sTS).append('\t');
                        }
                        sBuf.append(sO).append('\t');
                        sBad = false;
                    }
                }
                else
                {
                    if ((i++ == 0) && (aPrintTS == true))
                    {
                        sBuf.append(sTS).append('\t');
                    }
                    sBuf.append(sO).append('\t');
                    sBad = false;
                }
            }
            if (sBad != false)
            {
                // no values
                return;
            }
            sBuf.setLength(sBuf.length() - 1);
        }
        else
        {
            if (aPrintTS == true)
            {
                sBuf.append(sTS).append('\t');
            }
            sBuf.append(aAttrname).append('\t').append(aValue);
        }
        if (aLongOutput == true)
        {
            sBuf.append('\t').append(aObjname);
        }
        sBuf.append('\n');
        System.out.print(sBuf);
    }
}
