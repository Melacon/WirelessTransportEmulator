#!/bin/bash
set -e -x
if [ -z "$PORT" ];then
  PORT=830
fi
java -classpath NetconfServerSimulator.jar net.i2cat.netconf.server.ServerSimulator $XMLFILE $PORT yang/yangNeModel $UUID $SSHPORT
