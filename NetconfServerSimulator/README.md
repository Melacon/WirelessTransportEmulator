# NetconfServerSimulator

### Description

The NetconfServerSimulator is a Java based approach to simulate netconf managed devices. It is available as stand alone Java simulator or in the environment of the WirelessTransportEmulator.

### Prerequisites

The description bases on the environment: Ubuntu 16.04, Maven 3.3.9, Java 1.8

#### Java

Verify maven version (mvn --version).

Install Java 1.8 environment

```commandline
sudo apt-get install openjdk-8-jre icedtea-8-plugin openjdk-8-jdk
```
#### WirelessTransportEmulator

All the *Prerequisites* installation of the specific description have to be executed.

### Build and Install

**These steps need to be done after every git pull to reflect the new changes**

Builds the executable jar and the docker container for the NetconfServerSimulator.

  * The executable NetconfServerSimulator.jar is placed in the /build subdirectory on replaces the previouse version.
  * The docker container with the name "netconfserversimulator" is created in the local docker repository.
  * The java and docker build instructions are in the pom.xml. Additionally the docker build is within the file. *makeDockerContainer.sh*


```commandline
cd NetconfServerSimulator
mvn clean install
```
### Usage

#### With  WirelessTransportEmulator

In the *topology.json* file within the network-element definition section the *type* parameter indicates to use the *NetconfServerSimulator* as shown in the example below. In the actual version of this simulator the xmlFile can be selected and the uuid assigned. Simulated Interfaces and links for this container are not supported.

```JSON
{
  "network-elements" : [
    {
      "network-element" :
      {
        "uuid" : "JavaSimulator-1",
        "type" : "JavaNetconfServer",
        "xmlFile" : "xmlNeModel/DVM-ETY.xml"
      }
    }
  ]
}
```
In the *config.json* file there are no specific configurations required.

The jar proved a command line after it is started. Type "help" to get a list of commands.


#### Stand alone

The NetconfServerSimulator can be directly started from the command line. It is recommended to use screen for remote ssh/putty sessions.
The jar parameter are:
    1. filename (mandatory) with xml definition of the simulated NE
    2. port (mandatory) number to access the simulator
    3. directory (mandatory) with all yang files, used by the simulation
    4. uuid (optional) to provide an individual id

```Script
java -classpath NetconfServerSimulator.jar net.i2cat.netconf.server.ServerSimulator $1 $2 $3 $4
```
In the *build* directory there are some examples for start commands.

```Script
./NetconfServerSimulator.sh ../xmlNeModel/DVM_MWCore10_BasicAir.xml 2224 ../yang/yangNeModel
```

### Common information

This project bases (branches) for simulation of NETCONF devices on (from) package NETCONF-SERVER.
Netconf server simulator using [netconf4j](https://github.com/dana-i2cat/netconf4j)
[dana-i2cat-server](https://github.com/dana-i2cat/netconf-server)

Specifications

 * [RFC 4741](http://tools.ietf.org/html/rfc4741) and [RFC 4742](http://tools.ietf.org/html/rfc4742) based
 * [OSGi](http://www.osgi.org/Main/HomePage) ready
 * Test coverage
 * [Apache Maven](http://maven.apache.org/) based, easy to build & contribute
 * Use of [Apache SSHD](http://mina.apache.org/sshd-project/) as SSH server library

