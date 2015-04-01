#!/bin/sh

set -e
stdout=$1
shift
stderr=$1
shift
exec nice gnuplot "$@" >"$stdout" 2>"$stderr"
