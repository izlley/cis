#!/bin/bash

exec sh $CIS_HOME/cisw/bin/cisw cis --verbose --port=2848 --webroot=$CIS_HOME/cisw/webroot --cachedir=$CIS_HOME/cisw/diskcache
#exec sh $CIS_HOME/cisw/bin/cisw cis --verbose --zkquorum 20.20.20.54 --port=2848 --webroot=$CIS_HOME/cisw/webroot --cachedir=$CIS_HOME/cisw/diskcache
