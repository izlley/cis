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
USER = "leejy"

MR_TOOL_PATH = "/../lib/mrprobe-0.0.1.jar"
HADOOP_HOME = ""

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
    #dir = os.path.dirname(sys.argv[0])
    dir = os.path.abspath(sys.argv[1])
    jar = os.path.normpath(dir + MR_TOOL_PATH)
    if not os.path.exists(jar):
        print >>sys.stderr, "WTF?!  Can't run, %s doesn't exist" % jar
        return 13
    classpath = [jar]
    for jar in os.listdir(os.environ['HADOOP_HOME']):
        if jar.find("hadoop-core") != -1 :
            classpath.append(os.environ['HADOOP_HOME'] + "/" + jar)
    for jar in os.listdir(os.environ['HADOOP_HOME'] + "/lib"):
        if (jar.find("jackson-core-asl") != -1 or jar.find("jackson-mapper-asl") != -1 or 
           jar.find("commons-configuration") != -1 or jar.find("commons-cli") != -1 or 
           jar.find("commons-logging") != -1 or jar.find("commons-lang") != -1) :
            classpath.append(os.environ['HADOOP_HOME'] + "/lib/" + jar)
    classpath.append(os.environ['HADOOP_HOME'] + "/conf")
    classpath = ":".join(classpath)

    mr = subprocess.Popen(
        ["java", "-enableassertions", "-enablesystemassertions",  # safe++
         "-Xmx64m",  # Low RAM limit, to avoid stealing too much from prod.
         "-cp", classpath, "com.skplanet.monitoring.mapreduce.mrprobe",
         "--watch", "10", "--timestamp"
         ], stdout=subprocess.PIPE, bufsize=1)
    do_on_signal(signal.SIGINT, kill, mr)
    do_on_signal(signal.SIGPIPE, kill, mr)
    do_on_signal(signal.SIGTERM, kill, mr)
    try:
        prev_timestamp = 0
        while True:
            line = mr.stdout.readline()

            if not line and mr.poll() is not None:
                break  # Nothing more to read and process exited.
            elif len(line) < 4:
                print >>sys.stderr, "invalid line (too short): %r" % line
                continue

            timestamp, metric, value, tags = line.split("\t", 3)
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

            tagstr = ""
            for tag in tags.split(None): # default seperators are whitespaces
                if tag.find("job_") != -1:
                    tagstr += "jobid=" + tag + " "
                elif tag.find("task_") != -1:
                    tagstr += "taskid=" + tag + " "
                elif tag.find("attempt_") != -1:
                    tagstr += "attemptid=" + tag + " "
                elif tag.find("/") == 0:
                    tagstr += "machine=" + tag + " "
                elif tag.find("wfid_") == 0:
                    tagstr += "wfid=" + tag.strip("wfid_") +" "
                else:
                    tagstr += "type=" + tag + " "

            sys.stdout.write("%s %d %s %s\n"
                             % (metric, timestamp, value, tagstr))
            sys.stdout.flush()
    finally:
        kill(mr)
        time.sleep(300)
        return 0  # Ask the tcollector to re-spawn us.


if __name__ == "__main__":
    sys.exit(main(sys.argv))
