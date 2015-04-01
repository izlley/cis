#!/usr/bin/python

import os
import pwd
import re
import signal
import subprocess
import sys
import time

# If this user doesn't exist, we'll exit immediately.
# If we're running as root, we'll drop privileges using this user.
USER = "hbase"

JMX_TOOL_PATH = "/../lib/jmxprobe-0.0.1.jar"

# We add those files to the classpath if they exist.
CLASSPATH = [
    os.environ['JAVA_HOME']+'/lib/tools.jar'
]

IGNORED_METRICS = set(["RespInterval","RespIntervalPercent"])

# How many times, maximum, will we attempt to restart the JMX collector.
# If we reach this limit, we'll exit with an error.
MAX_RESTARTS = 10

TOP = False  # Set to True when we want to terminate.
RETVAL = 0    # Return value set by signal handler.


def drop_privileges():
    try:
        ent = pwd.getpwnam(USER)
    except KeyError:
        print >>sys.stderr, "Not running, user '%s' doesn't exist" % USER
        sys.exit(13)

    if os.getuid() != 0:
        return

    os.setgid(ent.pw_gid)
    os.setuid(ent.pw_uid)


def kill(proc):
  """Kills the subprocess given in argument."""
  # Clean up after ourselves.
  proc.stdout.close()
  rv = proc.poll()
  if rv is None:
      os.kill(proc.pid, 15)
      rv = proc.poll()
      if rv is None:
          os.kill(proc.pid, 9)  # Bang bang!
          rv = proc.wait()  # This shouldn't block too long.
  print >>sys.stderr, "warning: proc exited %d" % rv
  return rv


def do_on_signal(signum, func, *args, **kwargs):
  """Calls func(*args, **kwargs) before exiting when receiving signum."""
  def signal_shutdown(signum, frame):
    print >>sys.stderr, "got signal %d, exiting" % signum
    func(*args, **kwargs)
    sys.exit(128 + signum)
  signal.signal(signum, signal_shutdown)


def main(argv):
    drop_privileges()
    # Build the classpath.
    dir = os.path.abspath(sys.argv[1])
    jar = os.path.normpath(dir + JMX_TOOL_PATH)
    if not os.path.exists(jar):
        print >>sys.stderr, "WTF?!  Can't run, %s doesn't exist" % jar
        return 13
    classpath = [jar]
    for jar in CLASSPATH:
        if os.path.exists(jar):
            classpath.append(jar)
    classpath = ":".join(classpath)

    jmx = subprocess.Popen(
        ["java", "-enableassertions", "-enablesystemassertions",  # safe++
         "-Xmx64m",  # Low RAM limit, to avoid stealing too much from prod.
         "-cp", classpath, "com.skplanet.monitoring.jmx.jmxprobe",
         "--watch", "10", "--long", "--timestamp",
         "Cask", # Postino : Pid 
         # The remaining arguments are pairs (mbean_regexp, attr_regexp).
         #"Tomcat", "",
         #"MySQLDataLoadMonitor", "",
         "Cask",""
         ], stdout=subprocess.PIPE, bufsize=1)

    do_on_signal(signal.SIGINT, kill, jmx)
    do_on_signal(signal.SIGPIPE, kill, jmx)
    do_on_signal(signal.SIGTERM, kill, jmx)
    try:
        prev_timestamp = 0
        while True:
            line = jmx.stdout.readline()
            if not line and jmx.poll() is not None:
                break  # Nothing more to read and process exited.
            elif len(line) < 4:
                print >>sys.stderr, "invalid line (too short): %r" % line
                continue

#            print line
            timestamp, metric, value, mbean = line.split("\t", 3)
            # Sanitize the timestamp.
            try:
                timestamp = int(timestamp)
                if timestamp < time.time() - 600:
                    raise ValueError("timestamp too old: %d" % timestamp)
                if timestamp < prev_timestamp:
                    raise ValueError("timestamp out of order: prev=%d, new=%d"
                                     % (prev_timestamp, timestamp))
            except ValueError, e:
                print >>sys.stderr, ("Invalid timestamp on line: %r -- %s"
                                     % (line, e))
                continue
            prev_timestamp = timestamp

            if metric in IGNORED_METRICS:
              continue

            tags = ""

            # mbean is of the form "domain:key=value,...,foo=bar"
            # some tags can have spaces, so we need to fix that.
            mbean_domain, mbean_properties = mbean.rstrip().replace(" ", "_").split(":", 1)
            if mbean_domain not in ("Cask"):
                print >>sys.stderr, ("Unexpected mbean domain = %r on line %r"
                                     % (mbean_domain, line))
                continue
            mbean_properties = dict(prop.split("=", 1)
                                    for prop in mbean_properties.split(","))
           
            mbean_typename = mbean_properties.get("type")
            if mbean_typename.endswith("Monitor"):
                mbean_typename = mbean_typename.rstrip("Monitor")

            metric = mbean_domain.lower() + "." + mbean_typename.lower() + "." + metric.lower()

            sys.stdout.write("%s %d %s%s\n"
                             % (metric, timestamp, value, tags))
            sys.stdout.flush()
    finally:
        kill(jmx)
        #time.sleep(300)
        return 0  # Ask the tcollector to re-spawn us.


if __name__ == "__main__":
    sys.exit(main(sys.argv))

