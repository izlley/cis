#!/usr/bin/python

# Simple manager for collection scripts that run and gather data.
# The cmanager gathers the data and sends it to the CISSD for storage.

import atexit
import errno
import fcntl
import logging
import os
import re
import signal
import socket
import subprocess
import sys
import threading
import time
import random
from logging.handlers import RotatingFileHandler
from Queue import Queue
from Queue import Empty
from Queue import Full
from optparse import OptionParser


# global variables
COLLECTORS = {} #dictionary
GENERATION = 0
DEFAULT_LOG = './cmanager.log'
COL_PATH = ''
LOG = logging.getLogger('cmanager')
ALIVE = True
READER_QUEUE_SIZE = 100000
DATA_LINE_MAX_SIZE = 1024
SENDER_SLEEP_TIME = 5
VERIFY_CONN_BUF_SIZE = 4096
NEXT_KILL_INTERVAL = 5
ERR_NEXT_KILL_INTERVAL = 300
MAIN_LOOP_INTERVAL = 15
HEARTBEAT_INTERVAL = 60

# If the SenderThread catches more than this many consecutive uncaught
# exceptions, something is not right and cmanager will shutdown.
# Hopefully some kind of supervising daemon will then restart it.
MAX_UNCAUGHT_EXCEPTIONS = 100

def registerCollector(collector):
    """Register a collector with the COLLECTORS global"""
    assert isinstance(collector, Collector), "collector=%r" % (collector,)
    # store it in the global list and initiate a kill for anybody with the
    # same name
    if collector.name in COLLECTORS:
        col = COLLECTORS[collector.name]
        if col.proc is not None:
            LOG.error('%s still has a process (pid=%d) and is being reset,'
                      ' terminating', col.name, col.proc.pid)
            col.shutdown()
    LOG.debug('Register collector : %s', collector.name)
    COLLECTORS[collector.name] = collector

class ReaderQueue(Queue):
    """A Queue for the reader thread"""

    def nput(self, value):
        """A nonblocking put, that simply logs and discards the value when the
        queue is full, and returns false if we dropped"""
        try:
            self.put(value, False)
        except Full:
            LOG.error("DROPPED LINE: %s", value)
            return False
        return True

class Collector(object):
    """A Collector is a script that is run that gathers some data
       and prints it out in standard format on STDOUT.  This
       class maintains all of the state information for a given
       collector and gives us utility methods for working with
       it."""

    def __init__(self, colname, interval, filename, mtime=0, lastspawn=0):
        """Construct a new Collector"""
        self.name = colname
        self.interval = interval
        self.filename = filename
        self.lastspawn = lastspawn
        self.proc = None
        self.nextkill = 0
        self.killstate = 0
        self.dead = False
        self.mtime = mtime
        self.generation = GENERATION
        self.buffer = ""
        self.datalines = [] #List
        # Maps (metric, tags) to (value, repeated, line, timestamp) where
        # value : Last value seen
        # repeated : boolean, whether the last value was seen more than once
        # line : The last line that was read from that collector
        # timestamp : Time at which we saw the value for the first time
        # This dict is used to keep track of and remove duplicate values
        # Regularly call evictOldkeys() to remove old entries
        self.values = {}
        self.linesSent = 0
        self.linesRecv = 0
        self.linesInvalid = 0

    def read(self):
        """Read bytes from our subprocess and store them in our temp line
        storage buffer. non-blocking"""
        # now read stderr for log messages, we could buffer here but since
        # we're just logging the messages, I don't care to
        try:
            out = self.proc.stderr.read()
            if out:
                LOG.debug('reading %s got %d bytes on stderr', self.name,
                          len(out))
                for line in out.splitlines():
                    LOG.warning('%s: %s', self.name, line)
        except IOError as err:
            if err.errno != errno.EAGAIN:
                # allowing a caller to handle the exception as well
                raise
        except:
            LOG.exception('uncaught exception in stderr read')

        # This read call is non-blocking
        try:
            self.buffer += self.proc.stdout.read()
            if len(self.buffer):
                LOG.debug('reading %s, buffer now %d bytes',
                          self.name, len(self.buffer))
        except IOError as err:
            if err.errno != errno.EAGAIN:
                raise
        except:
            # sometimes the process goes away in another thread and we don't
            # have it anymore
            LOG.exception('uncaught exception in stdout read')
            return

        # iterate for each line we have
        while self.buffer:
            idx = self.buffer.find('\n')
            if idx == -1:
                break

            line = self.buffer[0:idx].strip()
            if line:
                self.datalines.append(line)
            self.buffer = self.buffer[idx+1:]

    def collect(self):
        """Reads input from the collector and returns the lines up to whomever
        is calling us."""
        while self.proc is not None:
            self.read()
            if not len(self.datalines):
                return
            while len(self.datalines):
                # pop the first node of list
                yield self.datalines.pop(0)

    def shutdown(self):
        """Cleanly shutdown the collector"""

        if not self.proc:
            return
        try:
            if self.proc.poll() is None:
                kill(self.proc)
                for attempt in range(5):
                    if self.proc.poll() is not None:
                        return
                    LOG.info('Waiting %dth for PID %d to exit...'
                             % (5 - attempt, self.proc.pid))
                    time.sleep(1)
                kill(self.proc, signal.SIGKILL)
                self.proc.wait()
        except:
            LOG.exception('ignoring uncaught exception while shutting down')

    def evictOldkeys(self, cutOff):
        """Remove old entries from the cache used to detect duplicate values.

        Args:
            cutOff: A UNIX timestamp. Any value that's older than this will be 
            removed from the cache"""
        for key in self.values.keys():
            time = self.values[key][3]
            if time < cutOff:
                del self.values[key]

class StdinCollector(Collector):
    """A StdinCollector simply reads from STDIN and provides the
       data. Unlike a normal collector, read()/collect()
       will be blocking."""

    def __init__(self):
        super(StdinCollector, self).__init__('stdin', 0, '<stdin>')
        self.proc = True

    def read(self):
        """Read lines from STDIN and store them.  We allow this to
           be blocking because there should only ever be one
           StdinCollector and no normal collectors, so the ReaderThread
           is only serving us and we're allowed to block it."""
        global ALIVE
        line = sys.stdin.readline()
        if line:
            self.datalines.append(line.rstrip())
        else:
            ALIVE = False

    def shutdown(self):
        pass

class ReaderThread(threading.Thread):
    """The main ReaderThread is responsible for reading from the collectors
    and assuring that we always read from the input no matter what.
    All data read is put into the readerq Queue, which is consumed by the
    SenderThread"""

    def __init__(self, repeatinterval, evictinterval):
        """ repeatinterval: If a metric sends the same value over successive
                intervals, suppress sending the same value to the CISSD until
                this many seconds have elapsed.  This helps graphs over narrow
                time ranges still see timeseries with suppressed datapoints.
            evictinterval: In order to implement the behavior above, the
                code needs to keep track of the last value seen for each
                combination of (metric, tags).  Values older than
                evictinterval will be removed from the cache to save memory.
                Invariant: evictinterval > dedupinterval """

        assert evictinterval > repeatinterval, "%r <= %r" % (evictinterval,
                                                             repeatinterval)
        super(ReaderThread, self).__init__()

        self.readerq = ReaderQueue(READER_QUEUE_SIZE)
        self.linesCollected = 0
        self.linesDropped = 0
        self.repeatinterval = repeatinterval
        self.evictinterval = evictinterval

    def run(self):
        """Main loop for this thread"""
        LOG.debug("ReaderThread up and running")

        lastevictTime = 0
        while ALIVE:
            for col in allLivingCollectors():
                for line in col.collect():
                    self.processLine(col, line)
            now = int(time.time())
            if now - lastevictTime > self.evictinterval:
                lastevictTime = now
                now -= self.evictinterval
                for col in allCollectors():
                    col.evictOldkeys(now)
            # BUGBUG : not good
            time.sleep(1)

    def processLine(self, col, line):
        """Parses the given line and appends the result to the reader queue"""
        col.linesRecv += 1
        # BUGBUG : Limit should be fixed
        if len(line) >= DATA_LINE_MAX_SIZE:
            LOG.warning('%s line too long: %s', col.name, line)
            col.linesInvalid += 1
            return
        parsed = re.match('^([-_./a-zA-Z0-9]+)\s+' # Metric name
                          '(\d+)\s+'               # Timestamp
                          '(\S+?)'                 # Value (int or float)
                          '((?:\s+[-_./a-zA-Z0-9]+=[-_./a-zA-Z0-9]+)*)$', # Tags
                          line)
        if parsed is None:
            LOG.warning('%s sent invalid data: %s', col.name, line)
            col.linesInvalid += 1
            return
        metric, timestamp, value, tags = parsed.groups()
        timestamp = int(timestamp)

        # eliminating repeated value logic
        key = (metric, tags)
        if key in col.values:
            if timestamp <= col.values[key][3]:
                LOG.error("Timestamp out of order: metric=%s%s,"
                          " old_ts=%d >= new_ts=%d - ignoring data point"
                          " (value=%r, collector=%s)", metric, tags,
                          col.values[key][3], timestamp, value, col.name)
                col.linesInvalid += 1
                return

            if (col.values[key][0] == value and
                (timestamp - col.values[key][3] < self.repeatinterval)):
                # don't update the old timestamp
                col.values[key] = (value, True, line, col.values[key][3])
                return

            if ((col.values[key][1] or
                 # BUGBUG : this may always False
                (timestamp - col.values[key][3] >= self.repeatinterval))
                and
                col.values[key][0] != value):
                col.linesSent += 1
                if not self.readerq.nput(col.values[key][2]):
                    self.linesDropped += 1

        # case 1 : (old_val == new_val) and (>=repeatinterval)
        # case 2 : (old_val != new_val) and (<repeatinterval) and values[][1]== False
        col.values[key] = (value, False, line, timestamp)
        col.linesSent += 1
        if not self.readerq.nput(line):
            self.linesDropped += 1

class SenderThread(threading.Thread):
    """The SenderThread is responsible for maintaining a connection
    to the CISSD and sending the data we're getting over to it."""

    def __init__(self, reader, dryrun, host, port, selfStats, tags):
        """Args:
            reader: A reference to a ReaderThread
            dryrun: If true, data points will be printed no stdout instead of
                being sent to the CISSD
            host: The hostname of the CISSD to connect to
            port: The port of the CISSD to connect to
            selfStats: If true, the reader thread will insert its own stats
                into the metrics reported to CISSD, as if those metrics had been
                read from a collector
            tags: A string containing tags to append at for every data point
            """
        super(SenderThread, self).__init__()

        self.dryrun = dryrun
        self.host = host
        self.port = port
        self.reader = reader
        self.tagstr = tags
        self.cissd = None
        self.lastVerify = 0
        self.sendq = []
        self.selfStats = selfStats

    def run(self):
        """ Main loop. Loop waiting for 5 seconds for data on the queue.
        If there's no data, just loop and make sure our connection is still
        open. If there is data, wait 5 more seconds and grab all of the
        pending data and send it. """
        errors = 0
        while ALIVE:
            try:
                self.maintainConn()
                try:
                    line = self.reader.readerq.get(True, 5)
                except Empty:
                    continue
                self.sendq.append(line)
                time.sleep(SENDER_SLEEP_TIME)
                while True:
                    try:
                        line = self.reader.readerq.get(False)
                    except Empty:
                        break
                    self.sendq.append(line)

                self.sendData()
                errors = 0
            except (ArithmeticError, EOFError, EnvironmentError, LookupError,
                    ValueError), e:
                errors += 1
                if errors > MAX_UNCAUGHT_EXCEPTIONS:
                    shutdown()
                    raise
                LOG.exception('Uncaught exception in SenderThread, ignoring')
                time.sleep(1)
                continue
            except:
                LOG.exception('Uncaught exception in SenderThread, ignoring')
                time.sleep(1)
                continue

    def verifyConn(self):
        """Periodically verify that connection to the CISSD is OK and that
        the CISSD is alive"""
        if self.cissd is None:
            return False

        if self.lastVerify > time.time() - HEARTBEAT_INTERVAL:
            return True

        LOG.debug('verifying CISSD connection is alive')
        try:
            self.cissd.sendall('version\n')
        except socket.error, msg:
            self.cissd = None
            return False

        bufsize = VERIFY_CONN_BUF_SIZE
        while ALIVE:
            try:
                buf = self.cissd.recv(bufsize)
            except socket.error, msg:
                self.cissd = None
                return False

            if not buf:
                self.cissd = None
                return False

            # We read everything CISSD sent us looping once more
            if len(buf) == bufsize:
                continue

            # If everything is good, send out our selfstats data.
            if self.selfStats:
                strs = [
                        ('reader.lines_collected',
                         '', self.reader.linesCollected),
                        ('reader.lines_dropped',
                         '', self.reader.linesDropped)
                        ]

                for col in allLivingCollectors():
                    strs.append(('collector.lines_sent', 'collector='
                                 + col.name, col.linesSent))
                    strs.append(('collector.lines_received', 'collector='
                                 + col.name, col.linesRecv))
                    strs.append(('collector.lines_invalid', 'collector='
                                 +col.name, col.linesInvalid))

                    ts = int(time.time())
                    strout = ["cmanager.%s %d %d %s"
                              % (x[0], ts, x[2], x[1]) for x in strs]
                    for line in strout:
                        self.sendq.append(line)
            break

        self.lastVerify = time.time()
        return True

    def maintainConn(self):
        """Safely connect to the CISSD and ensure that it's up and running
        and that we're not talking to a ghost connection (no response)"""

        if self.dryrun:
            return

        # We should verify connection
        tryDelay = 1
        while ALIVE:
            if self.verifyConn():
                return

            # Make new socket connection
            # increase the try delay by some amount and some random value
            # delay at most ~10m
            tryDelay *= 1 + random.random()
            if tryDelay > 600:
                tryDelay *= 0.5
            LOG.debug('SenderThread blocking %0.2f seconds', tryDelay)
            time.sleep(tryDelay)

            addresses = socket.getaddrinfo(self.host, self.port, socket.AF_UNSPEC,
                                          socket.SOCK_STREAM, 0)
            for family, socktype, proto, canonname, sockaddr in addresses:
                try:
                    self.cissd = socket.socket(family, socktype, proto)
                    self.cissd.settimeout(15)
                    self.cissd.connect(sockaddr)
                    break
                except socket.error, msg:
                    LOG.warning('Connection attempt failed to %s:%d: %s',
                                self.host, self.port, msg)
                self.cissd.close()
                self.cissd = None
            if not self.cissd:
                LOG.error('Failed to connect to %s:%d', self.host, self.port)

    def sendData(self):
        """Sends data in self.sendq to the CISSD in one operation"""
        out = ''
        for line in self.sendq:
            line = 'put ' + line + self.tagstr
            out += line + '\n'
            LOG.debug('SENDING: %s', line)

        if not out:
            LOG.debug('no data in sendq?')
            return

        try:
            if self.dryrun:
                print out
            else:
                self.cissd.sendall(out)
            self.sendq = []
        # If an exception occurs, try sending data again next time
        except socket.error, msg:
            LOG.error('failed to send data: %s', msg)
            try:
                self.cissd.close()
            except socket.error:
                pass
            self.cissd = None

def setupLogging(logfile=DEFAULT_LOG, maxBytes=None, backupCount=None):
    LOG.setLevel(logging.INFO)
    if backupCount is not None and maxBytes is not None:
        assert backupCount > 0
        assert maxBytes > 0
        ch = RotatingFileHandler(logfile, 'a', maxBytes, backupCount)
    else:
        ch = logging.StreamHandler(sys.stdout)

    ch.setFormatter(logging.Formatter('%(asctime)s %(name)s[%(process)d] '
                                      '%(levelname)s: %(message)s'))
    LOG.addHandler(ch)

def parseCmdline(argv):
    defaultCdir = os.path.join(os.path.dirname(os.path.realpath(sys.argv[0])),
                               'collectors')
    parser = OptionParser(description='Manages collectors which gather '
                          'data and report back.')
    parser.add_option('-c', '--collector-dir', dest='cdir', metavar='DIR',
                      default=defaultCdir,
                      help='Directory where the collectors are located.')
    parser.add_option('-d', '--dry-run', dest='dryrun', action='store_true',
                      default=False, help='Do not actually send anything to '
                      'the CISSD, just print the datapoints.')
    parser.add_option('-H', '--host', dest='host', default='localhost',
                      metavar='HOST', help='Hostname to connect to the CISSD.')
    parser.add_option('--no-cmanager-stats', dest='noCmanagerStats',
                      default=False, action='store_true',
                      help='Prevent cmanager from reporting its own stats to CISSD.')
    parser.add_option('-s', '--stdin', dest='stdin', action='store_true',
                      default=False, help='Run once, read data points from stdin.')
    parser.add_option('-p', '--port', dest='port', type='int', default=8282,
                      metavar='PORT', help='Port to connect to the CISSD. '
                      'default=%default')
    parser.add_option('-v', dest='verbose', action='store_true', default=False,
                      help='Verbose mode (log debug messages).')
    parser.add_option('-t', '--tag', dest='tags', action='append',
                      default=[], metavar='TAG',
                      help='Tags to append to all timeseries we send, '
                      'e.g.: -t TAG=VALUE -t TAG2=VALUE2')
    parser.add_option('-P', '--pidfile', dest='pidfile',
                      default='./cmanager.pid',
                      metavar='FILE', help='Write our pidfile')
    parser.add_option('--repeatval-interval', dest='repeatvalinterval', type='int',
                      default=300, metavar='INTERVAL',
                      help='Number of seconds in which successive duplicate '
                           'datapoints are suppressed before sending to the CISSD. '
                           'default=%default')
    parser.add_option('--evict-interval', dest='evictinterval', type='int',
                      default=6000, metavar='INVERVAL',
                      help='Number of seconds after which to remove cached '
                           'values of old data points to save memory. '
                           'default=%default')
    parser.add_option('--log-max-bytes', dest='maxBytes', type='int',
                      default=64*1024*1024,
                      help='Maximum bytes per a logfile.')
    parser.add_option('--log-backup-count', dest='backupCount', type='int',
                      default=0, help='Maximum number of logfiles to backup.')
    parser.add_option('--logfile', dest='logfile', type='str',
                      default=DEFAULT_LOG, help='Filename where logs are written to.')
    (options, args) = parser.parse_args(args=argv[1:])
    if options.repeatvalinterval < 2:
        parser.error('--repeatval-interval must be at least 2 seconds')
    if options.evictinterval <= options.repeatvalinterval:
        parser.error('--evict-interval must be greater than --repeatval-interval')
    return (options, args)

def main(argv):
    """The main cmanager entry point and loop."""
    options, args = parseCmdline(argv)
    setupLogging(options.logfile, options.maxBytes or None,
                 options.backupCount or None)

    if options.verbose:
        LOG.setLevel(logging.DEBUG)

    if options.pidfile:
        writePid(options.pidfile)

    tags = {}
    for tag in options.tags:
        if re.match('^[-_.a-z0-9]+=\S+$', tag, re.IGNORECASE) is None:
            assert False, 'Tag string "%s" is invalid.' % tag
        k, v = tag.split('=', 1)
        if k in tags:
            assert False, 'Tag "%s" already declared.' % k
        tags[k] = v

    options.cdir = os.path.realpath(options.cdir)
    COL_PATH = options.cdir

    if not os.path.isdir(options.cdir):
        LOG.fatal('No such directory: %s', options.cdir)
        return 1
    # call etc module's onload function
    modules = loadEtcDir(options, tags)

    # set host tag
    if not 'host' in tags and not options.stdin:
        tags['host'] = socket.gethostname()
        LOG.warning('Tag "host" not specified, defaulting to %s.', tags['host'])

    tagstr = ''
    if tags:
        tagstr = ' '.join('%s=%s' % (k, v) for k, v in tags.iteritems())
        tagstr = ' ' + tagstr.strip()

    # gracefully handle death for normal termination paths and abnormal
    atexit.register(shutdown)
    for sig in (signal.SIGTERM, signal.SIGINT):
        signal.signal(sig, shutdownSignal)

    # start the ReaderThread
    reader = ReaderThread(options.repeatvalinterval, options.evictinterval)
    reader.start()
    LOG.info('ReaderThread startup complete')

    # start the SenderThread
    sender = SenderThread(reader, options.dryrun, options.host, options.port,
                          not options.noCmanagerStats, tagstr)
    sender.start()
    LOG.info('SenderThread startup complete')

    if options.stdin:
        registerCollector(StdinCollector())
        stdinLoop(options, modules, sender, tags)
    else:
        sys.stdin.close()
        mainLoop(options, modules, sender, tags)
    LOG.debug('Shutting down : joining the reader thread.')
    reader.join()
    LOG.debug('Shutting down : joining the sender thread.')
    sender.join()

def stdinLoop(options, modules, sender, tags):
    global ALIVE
    nextHeartbeat = int(time.time() + 600)
    while ALIVE:
        time.sleep(15)
        reloadChangedConfigModules(modules, options, sender, tags)
        now = int(time.time())
        if now >= nextHeartbeat:
            LOG.info('Heartbeat (%d collectors running)'
                     % sum(1 for col in allLivingCollectors()))
            nextHeartbeat = now + 600

def mainLoop(options, modules, sender, tags):
    """The main loop of the program that runs when we're not in stdin mode."""

    nextHeartbeat = int(time.time() + 600)
    while True:
        populateCollectors(options.cdir)
        reloadChangedConfigModules(modules, options, sender, tags)
        reapChildren()
        spawnChildren()
        time.sleep(MAIN_LOOP_INTERVAL)
        now = int(time.time())
        if now >= nextHeartbeat:
            LOG.info('Heartbeat (%d collectors running)'
                     % sum(1 for col in allLivingCollectors()))
            next_heartbeat = now + 600

def listConfigModules(etcdir):
    """Returns an iterator that yields the name of all the config modules"""
    if not os.path.isdir(etcdir):
        return iter(())
    return (name for name in os.listdir(etcdir)
            if (name.endswith('.py')
                and os.path.isfile(os.path.join(etcdir, name)))
            )

def loadEtcDir(options, tags):
    """Loads any Python modules in etc directory
    Returns: { path:(module, timestamp) }
    """
    etcdir = os.path.join(options.cdir, 'etc')
    # to import modules from the etc dir
    sys.path.append(etcdir)
    modules = {}
    for name in listConfigModules(etcdir):
        path = os.path.join(etcdir, name)
        module = loadConfigModule(name, options, tags)
        modules[path] = (module, os.path.getmtime(path))
    return modules

def loadConfigModule(name, options, tags):
    """Imports the config modules of the given name. If the module has an
    'onload' function, call it.
    
    Args:
        name: string or module object (for reload)
    """
    if isinstance(name, str):
        LOG.info('Loading %s', name)
        d = {}
        module = __import__(name[:-3], d, d)
    else:
        module = reload(name)
    onload = module.__dict__.get('onload')
    if callable(onload):
        try:
            onload(options, tags)
        except:
            LOG.fatal('Exception while loading %s', name)
            raise
    return module

def reloadChangedConfigModules(modules, options, sender, tags):
    """Reloads any changed modules from the 'etc' directory.
    Args:
        modules: { path:(module, timestamp) }
    Returns:
        whether or not anything has changed.
    """
    etcdir = os.path.join(options.cdir, 'etc')
    currentModules = set(listConfigModules(etcdir))
    currentPaths = set(os.path.join(etcdir, name)
                       for name in currentModules)
    changed = False

    # Reload any midule that has changed
    for path, (module, timestamp) in modules.iteritems():
        if path not in currentPaths:
            continue
        curmtime = os.path.getmtime(path)
        if curmtime > timestamp:
            LOG.info('Reloading %s, file has changed', path)
            module = loadConfigModule(module, options, tags)
            modules[path] = (module, curmtime)
            changed = True

    # Remove any module that has been removed
    for path in set(modules).difference(currentPaths):
        LOG.info('%s has been removed, cmanager should be restarted', path)
        del modules[path]
        changed = True

    # Check for any added module
    for name in currentModules:
        path = os.path.join(etcdir, name)
        if path not in modules:
            module = loadConfigModule(name, options, tags)
            modules[path] = (module, os.path.getmtime(path))
            changed = True

    if changed:
        sender.tagstr = ' '.join('%s=%s' % (k, v) for k, v in tags.iteritems())
        sender.tagstr = ' ' + sender.tagstr.strip()
    return changed

def writePid(pidfile):
    f = open(pidfile, "w")
    try:
        f.write(str(os.getpid()))
    finally:
        f.close()

def allCollectors():
    return COLLECTORS.itervalues()

def allValidCollectors():
    """Generator to return all defined collectors that haven't been marked
    dead in the past hour"""
    now = int(time.time())
    for col in allCollectors():
        if not col.dead or (now - col.lastspawn > 3600):
            yield col

def allLivingCollectors():
    for col in allCollectors():
        if col.proc is not None:
            yield col

def shutdownSignal(signum, frame):
    """Called when we get a signal and need to terminate"""
    LOG.warning("shutting down, got signal %d", signum)
    shutdown()

def kill(proc, signum=signal.SIGTERM):
    os.kill(proc.pid, signum)

def shutdown():
    """Called by atexit and when we receive a signal"""

    global ALIVE
    if not ALIVE:
        return
    ALIVE = False

    LOG.info('shutting down children')

    # tell everyone to die
    for col in allLivingCollectors():
        col.shutdown()

    LOG.info('exiting')
    sys.exit(1)

def reapChildren():
    """When a child process dies, we have to determine why it died and whether
    or not we need to restart it."""

    for col in allLivingCollectors():
        now = int(time.time())
        status = col.proc.poll()
        if status is None:
            # The process hasn't terminated yet
            continue
        col.proc = None

        # behavior based on status. a code 0 is normal termination, code 13
        # is used to indicate that we don't want to restart this collector.
        # any other status code is an error and is logged.
        if status == 13:
            LOG.info('removing %s from the list of collectors (by request)',
                     col.name)
            col.dead = True
        elif status != 0:
            LOG.warning('collector %s terminated after %d seconds with '
                        'status code %d, marking dead', col.name,
                        now - col.lastspawn, status)
            col.dead = True
        else:
            LOG.debug('Reap collector : %s', col.name)
            registerCollector(Collector(col.name, col.interval, col.filename,
                                        col.mtime, col.lastspawn))

def setNonblocking(fd):
    fl = fcntl.fcntl(fd, fcntl.F_GETFL) | os.O_NONBLOCK
    fcntl.fcntl(fd, fcntl.F_SETFL, fl)

def spawnCollector(col):
    """Takes a Collector object and creates a process for it"""
    LOG.info('%s (interval=%d) needs to be spawned', col.name, col.interval)

    #BUGBUG: better to loading the python module directly instead of using a subprocess
    try:
        args = []
        args.append(col.filename)
        if col.name.find('_jmx') != -1 or col.name.find('_mr') != -1:
            args.append(COL_PATH)
        col.proc = subprocess.Popen(args, stdout=subprocess.PIPE,
                                    stderr=subprocess.PIPE)
    except OSError, e:
        if e.errno == 13:
            LOG.error('failed to spawn collector %s: permission denied' %
                      col.filename)
            return
        elif e.errno == 2:
            LOG.error('failed to spawn collector %s: no such file or directory' %
                      col.filename)
        else:
            raise
    col.lastspwn = int(time.time())
    setNonblocking(col.proc.stdout.fileno())
    setNonblocking(col.proc.stderr.fileno())
    if col.proc.pid <= 0:
        LOG.error('failed to spawn collector: %s', col.filename)
        return
    col.dead = False
    LOG.info('spawned %s (pid=%d)', col.name, col.proc.pid)

def spawnChildren():
    """Iterates over our defined collectors and performs the logic to determine
    if we need to spawn, kill, or otherwise take some action on them"""
    for col in allValidCollectors():
        now = int(time.time())
        if col.interval == 0:
            if col.proc is None:
                spawnCollector(col)
        elif col.interval <= now - col.lastspawn:
            if col.proc is None:
                spawnCollector(col)
                continue
            if col.nextkill > now:
                continue
            if col.killstate == 0:
                LOG.warning('warning: %s (inverval=%d, pid=%d) overstayed '
                            'its welcome, SIGTERM sent',
                            col.name, col.interval, col.proc.pid)
                kill(col.proc)
                col.nextkill = now + NEXT_KILL_INTERVAL
                col.killstate = 1
            elif col.killstate == 1:
                LOG.error('error: %s (interval=%d, pid=%d) still not dead, '
                           'SIGKILL sent',
                           col.name, col.interval, col.proc.pid)
                kill(col.proc, signal.SIGKILL)
                col.nextkill = now + NEXT_KILL_INTERVAL
                col.killstate = 2
            else:
                LOG.error('error: %s (interval=%d, pid=%d) needs manual '
                          'intervention to kill it',
                          col.name, col.interval, col.proc.pid)
                col.nextkill = now + ERR_NEXT_KILL_INTERVAL

def populateCollectors(coldir):
    """Maintains our internal list of valid collectors. This walks the collector
    directory and looks for files"""
    global GENERATION
    GENERATION += 1

    #get numeric from scriptdir
    for interval in os.listdir(coldir):
        if not interval.isdigit():
            continue
        interval = int(interval)

        for colname in os.listdir('%s/%d' % (coldir, interval)):
            if colname.startswith('.'):
                continue
            filename = '%s/%d/%s' % (coldir, interval, colname)
            if os.path.isfile(filename):
                mtime = os.path.getmtime(filename)
                # if this collector is already 'known', then check if it's been
                # updated so we can kill off the old one (only if it's interval 0,
                # else we'll just get it next time it runs)
                if colname in COLLECTORS:
                    col = COLLECTORS[colname]
                    # if we get a dupe, then ignore the one we're trying to add now
                    if col.interval != interval:
                        LOG.error('two collectors with the same name %s and'
                                  ' different intervals %d and %d',
                                  colname, interval, col.interval)
                        continue
                    col.generation = GENERATION
                    if col.mtime < mtime:
                        LOG.info('%s has been updated no disk', col.name)
                        col.mtime = mtime
                        if not col.interval:
                            col.shutdown()
                            LOG.info('Respawning %s', col.name)
                            registerCollector(Collector(colname, interval,
                                                        filename, mtime))
                else:
                    registerCollector(Collector(colname, interval, filename,
                                                mtime))
    toDelete = []
    for col in allCollectors():
        if col.generation < GENERATION:
            LOG.info('collector %s removed from the filesystem, forgetting',
                     col.name)
            col.shutdown()
            toDelete.append(col.name)
    for name in toDelete:
        del COLLECTORS[name]

if __name__ == '__main__':
    sys.exit(main(sys.argv))

