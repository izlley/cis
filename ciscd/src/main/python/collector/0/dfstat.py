#!/usr/bin/python

# dfstat.py
#
# df.1kblocks.total      total size of fs
# df.1kblocks.used       blocks used
# df.1kblocks.available  blocks available
# df.inodes.total        number of inodes
# df.inodes.used        number of inodes
# df.inodes.free        number of inodes

# All metrics are tagged with mount= and fstype=
# This makes it easier to exclude stuff like
# tmpfs mounts from disk usage reports.

# Because cis does not like slashes in tags, slashes will
# be replaced by underscores in the mount= tag.  In theory
# this could cause problems if you have a mountpoint of
# "/foo/bar/" and "/foo_bar/".


import os
import socket
import subprocess
import sys
import time


COLLECTION_INTERVAL = 60  # seconds

def main():
    """dfstats main loop"""

    while True:
        ts = int(time.time())
        # 1kblocks
        df_proc = subprocess.Popen(["df", "-PlTk"], stdout=subprocess.PIPE)
        stdout, _ = df_proc.communicate()
        if df_proc.returncode == 0:
            for line in stdout.split("\n"): # pylint: disable=E1103
                fields = line.split()
                # skip header/blank lines
                if not line or not fields[2].isdigit():
                    continue
                # Skip mounts/types we don't care about.
                # Most of this stuff is of type tmpfs, but we don't
                # want to blacklist all tmpfs since sometimes it's
                # used for active filesystems (/var/run, /tmp)
                # that we do want to track.
                if fields[1] in ("debugfs", "devtmpfs"):
                    continue
                if fields[6] == "/dev":
                    continue
                # /dev/shm, /lib/init_rw, /lib/modules, etc
                #if fields[6].startswith(("/lib/", "/dev/")):  # python2.5+
                if fields[6].startswith("/lib/"):
                    continue
                if fields[6].startswith("/dev/"):
                    continue

                mount = fields[6]
                print ("df.1kblocks.total %d %s mount=%s fstype=%s"
                       % (ts, fields[2], mount, fields[1]))
                print ("df.1kblocks.used %d %s mount=%s fstype=%s"
                       % (ts, fields[3], mount, fields[1]))
                print ("df.1kblocks.free %d %s mount=%s fstype=%s"
                       % (ts, fields[4], mount, fields[1]))
        else:
            print >> sys.stderr, "df -Pltk returned %r" % df_proc.returncode

        ts = int(time.time())
        # inodes
        df_proc = subprocess.Popen(["df", "-PlTi"], stdout=subprocess.PIPE)
        stdout, _ = df_proc.communicate()
        if df_proc.returncode == 0:
            for line in stdout.split("\n"): # pylint: disable=E1103
                fields = line.split()
                if not line or not fields[2].isdigit():
                    continue

                mount = fields[6]
                print ("df.inodes.total %d %s mount=%s fstype=%s"
                       % (ts, fields[2], mount, fields[1]))
                print ("df.inodes.used %d %s mount=%s fstype=%s"
                       % (ts, fields[3], mount, fields[1]))
                print ("df.inodes.free %d %s mount=%s fstype=%s"
                       % (ts, fields[4], mount, fields[1]))
        else:
            print >> sys.stderr, "df -Plti returned %r" % df_proc.returncode

        sys.stdout.flush()
        time.sleep(COLLECTION_INTERVAL)

if __name__ == "__main__":
    main()
