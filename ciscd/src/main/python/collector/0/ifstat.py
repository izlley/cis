#!/usr/bin/python

"""network interface stats for CIS"""

import os
import sys
import time
import socket
import re


# /proc/net/dev has 16 fields, 8 for receive and 8 for xmit
# The fields we care about are defined here.  The
# ones we want to skip we just leave empty.
# So we can aggregate up the total bytes, packets, etc
# we tag each metric with direction=in or =out
# and iface=

FIELDS = ("bytes", "packets", "errs", "dropped",
           None, None, None, None,)

def main():
    """ifstat main loop"""
    interval = 15

    f_netdev = open("/proc/net/dev", "r")

    # We just care about ethN interfaces.  We specifically
    # want to avoid bond interfaces, because interface
    # stats are still kept on the child interfaces when
    # you bond.  By skipping bond we avoid double counting.
    while True:
        f_netdev.seek(0)
        ts = int(time.time())
        for line in f_netdev:
            m = re.match("\s+(eth\d+):(.*)", line)
            if not m:
                continue
            stats = m.group(2).split(None)
            for i in range(8):
                if FIELDS[i]:
                    print ("proc.net.%s %d %s iface=%s direction=in"
                           % (FIELDS[i], ts, stats[i], m.group(1)))
                    print ("proc.net.%s %d %s iface=%s direction=out"
                           % (FIELDS[i], ts, stats[i+8], m.group(1)))

        sys.stdout.flush()
        time.sleep(interval)

if __name__ == "__main__":
    main()

