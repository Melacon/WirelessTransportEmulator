NetconfServerSimulator
======================

This project bases (branches) for simulation of NETCONF devices on (from) package NETCONF-SERVER.
[dana-i2cat-server](https://github.com/dana-i2cat/netconf-server)
An executable is placed in the build sub-directory in the path build

netconf-server
--------------

Netconf server using [netconf4j](https://github.com/dana-i2cat/netconf4j)


Features
--------
 * [RFC 4741](http://tools.ietf.org/html/rfc4741) and [RFC 4742](http://tools.ietf.org/html/rfc4742) based
 * [OSGi](http://www.osgi.org/Main/HomePage) ready
 * Test coverage
 * [Apache Maven](http://maven.apache.org/) based, easy to build & contribute
 * Use of [Apache SSHD](http://mina.apache.org/sshd-project/) as SSH server library

BUILD
=====

JAR
---
Build of jar file is done via maven

        ...
        mvn clean install
        ...

DOCKER
------
Build of docker is using the jar file in the build directory

