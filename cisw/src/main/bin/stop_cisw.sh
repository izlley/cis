#!/bin/bash

me=${0##*/}

usage() {
  echo >&2 "usage: $me [port_number]"
  exit 1
}

if  [ -z $1 ] || `echo $1 | grep -q [^[:digit:]]` 
then
    usage
fi

echo "stop" | nc localhost $1

