# Wireless Transport Emulator

OpenYuma Wireless Transport Emulator (WTE) is a wireless transport topology emulation with [OpenYuma](https://github.com/OpenClovis/OpenYuma) NETCONF server,
based on [ONF TR-532](https://www.opennetworking.org/images/stories/downloads/sdn-resources/technical-reports/TR-532-Microwave-Information-Model-V1.pdf).

### Description

WTE takes as input three files:
 * a JSON topology file, with a specific structure, describing the network
to be emulated.
 * a JSON configuration file giving details about the SDN controller information,
 ranges for the NEs management IP addresses and for the hosts connected to the NEs
 * the XML configuration file based on the YANG models
to be used by each emulated Network Element as a startup configuration and
an XML file containing the status parameters of the model. These two files can
be automatically generated using a custom version of the [pyang](https://github.com/mbj4668/pyang) utility,
that is available [here](https://github.com/Melacon/pyang-we).

Each NE is emulated as a docker container and exposes a NETCONF server based on the OpenYuma
framework, reflecting an information model based on TR-532 from ONF. Links between different NEs, as
described in the JSON topology file, are emulated as connections through veth pairs. The
high-level architecture of the emulator is shown below.

As extension of Wireless Transport Emulator the java based netconf simulator [NetconfServerSimulator](NetconfServerSimulator/README.md) can be used.

![logo](./Architecture.png)

### Prerequisites

These instructions are for Ubuntu.

#### Docker

Install Docker following the instructions from [here](https://store.docker.com/editions/community/docker-ce-server-ubuntu/plans/docker-ce-server-ubuntu-tier?tab=instructions).

Make sure that you follow the instructions also for **Manage Docker as a non-root user** from [here](https://docs.docker.com/engine/installation/linux/linux-postinstall/).

Summary:

```commandline
sudo apt-get -y install \
  apt-transport-https \
  ca-certificates \
  curl \
  software-properties-common
```

```commandline
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo apt-key add -
```

```commandline
sudo add-apt-repository \
       "deb [arch=amd64] https://download.docker.com/linux/ubuntu \
       $(lsb_release -cs) \
       stable"
```

```commandline
sudo apt-get update
```

```commandline
sudo apt-get -y install docker-ce
```

Test that the docker installation was successful:

```commandline
sudo docker run hello-world
```

Run docker without sudo:

```commandline
sudo groupadd docker
```

```commandline
sudo usermod -aG docker $USER
```

Log out and log back in so that your group membership is re-evaluated.

```commandline
docker run hello-world
```

#### Python 3.6

Install version 3.6 for python:

```
sudo add-apt-repository ppa:jonathonf/python-3.6; \
sudo apt-get update; \
sudo apt-get install python3.6
```

### Install

####These steps need to be done after every git pull to reflect the new changes

Build the docker container located in the OpenYuma folder:

```buildoutcfg
cd OpenYuma
docker build -t openyuma .
```


From the base directory run the following commands:

```
python3.6 setup.py build
sudo python3.6 setup.py install
```

### Usage

The JSON topology configuration file `topology.json` for the topology in the previous figure
looks like this:

```JSON
{
  "network-elements" : [
    {
      "network-element" :
      {
        "uuid" : "Simulator-1",
        "type" : "OpenYuma",
        "interfaces" : [
          {
            "layer" : "MWPS",
            "LTPs" : [
                      { "id": "airIntf1",
                        "supportedAlarms" : "signalIsLost, rslIsExceeded, temperatureIsExceeded, modemIsFaulty, radioIsFaulty, modulationIsDownShifted",
                        "physical-port-reference" : "shelf1:slot2:card-type:port1",
                        "conditional-package" : "mw-air-interface-pac"
                      },
                      { "id": "airIntf2",
                        "supportedAlarms" : "signalIsLost, rslIsExceeded, temperatureIsExceeded, modemIsFaulty, radioIsFaulty, modulationIsDownShifted",
                        "physical-port-reference" : "shelf1:slot3:card-type:port1",
                        "conditional-package" : "mw-air-interface-pac"
                      }
                    ]
          },
          {
            "layer" : "MWS",
            "LTPs" : [
                      { "id" : "pureEth1",
                        "supportedAlarms" : "structureAlarm",
                        "serverLTPs" : [{"id" : "airIntf1"}],
                        "conditional-package" : "mw-pure-ethernet-structure-pac"
                      },
                      { "id" : "pureEth2",
                        "supportedAlarms" : "structureAlarm",
                        "serverLTPs" : [{"id" : "airIntf2"}],
                        "conditional-package" : "mw-pure-ethernet-structure-pac"
                      }
                    ]
          },
          {
            "layer" : "ETC",
            "LTPs" : [
                      { "id" : "mwEtc1",
                        "supportedAlarms" : "framingIsFaulty, containerIsDown",
                        "serverLTPs" : [{"id" : "pureEth1"}, {"id" : "pureEth2"}],
                        "conditional-package" : "mw-ethernet-container-pac"
                      }
                    ]
          },
          {
            "layer" : "ETY",
            "LTPs" : [
                      {"id" : "ety1", "physical-port-reference" : "shelf1:slot1:card-type:port1"},
                      {"id" : "ety2", "physical-port-reference" : "shelf1:slot1:card-type:port2"},
                      {"id" : "ety3", "physical-port-reference" : "shelf1:slot1:card-type:port3"},
                      {"id" : "ety4", "physical-port-reference" : "shelf1:slot1:card-type:port4"}
                    ]
          },
          {
            "layer" : "ETH",
            "LTPs" : [{"id" : "eth1", "serverLTPs" : [{"id" : "ety1"}], "conditional-package" : "ethernet-pac"},
                      {"id" : "eth2", "serverLTPs" : [{"id" : "ety2"}], "conditional-package" : "ethernet-pac"},
                      {"id" : "eth3", "serverLTPs" : [{"id" : "ety3"}], "conditional-package" : "ethernet-pac"},
                      {"id" : "eth4", "serverLTPs" : [{"id" : "ety4"}], "conditional-package" : "ethernet-pac"},
                      {"id" : "eth5", "serverLTPs" : [{"id" : "mwEtc1"}], "conditional-package" : "ethernet-pac"}
                    ]
          }
          ],
        "eth-cross-connections" : [
          {"fcPorts" : [{"ltp" : "eth1", "vlan-id" : "26"}, {"ltp" : "eth5", "vlan-id" : "26"}], "host" : true, "fcRoute" : "service1"}
          ],
        "ptp-clock" : [0]
      }
    },
    {
      "network-element" :
      {
        "uuid" : "Simulator-2",
        "type" : "OpenYuma",
        "interfaces" : [
          {
            "layer" : "MWPS",
            "LTPs" : [
                      { "id": "airIntf1",
                        "supportedAlarms" : "signalIsLost, rslIsExceeded, temperatureIsExceeded, modemIsFaulty, radioIsFaulty, modulationIsDownShifted",
                        "physical-port-reference" : "shelf1:slot2:card-type:port1",
                        "conditional-package" : "mw-air-interface-pac"
                      },
                      { "id": "airIntf2",
                        "supportedAlarms" : "signalIsLost, rslIsExceeded, temperatureIsExceeded, modemIsFaulty, radioIsFaulty, modulationIsDownShifted",
                        "physical-port-reference" : "shelf1:slot3:card-type:port1",
                        "conditional-package" : "mw-air-interface-pac"
                      },
                      { "id": "airIntf3",
                        "supportedAlarms" : "signalIsLost, rslIsExceeded, temperatureIsExceeded, modemIsFaulty, radioIsFaulty, modulationIsDownShifted",
                        "physical-port-reference" : "shelf1:slot4:card-type:port1",
                        "conditional-package" : "mw-air-interface-pac"
                      },
                      { "id": "airIntf4",
                        "supportedAlarms" : "signalIsLost, rslIsExceeded, temperatureIsExceeded, modemIsFaulty, radioIsFaulty, modulationIsDownShifted",
                        "physical-port-reference" : "shelf1:slot5:card-type:port1",
                        "conditional-package" : "mw-air-interface-pac"
                      }
                    ]
          },
          {
            "layer" : "MWS",
            "LTPs" : [
                      { "id" : "pureEth1",
                        "supportedAlarms" : "structureAlarm",
                        "serverLTPs" : [{"id" : "airIntf1"}],
                        "conditional-package" : "mw-pure-ethernet-structure-pac"
                      },
                      { "id" : "pureEth2",
                        "supportedAlarms" : "structureAlarm",
                        "serverLTPs" : [{"id" : "airIntf2"}],
                        "conditional-package" : "mw-pure-ethernet-structure-pac"
                      },
                      { "id" : "pureEth3",
                        "supportedAlarms" : "structureAlarm",
                        "serverLTPs" : [{"id" : "airIntf3"}],
                        "conditional-package" : "mw-pure-ethernet-structure-pac"
                      },
                      { "id" : "pureEth4",
                        "supportedAlarms" : "structureAlarm",
                        "serverLTPs" : [{"id" : "airIntf4"}],
                        "conditional-package" : "mw-pure-ethernet-structure-pac"
                      }
                    ]
          },
          {
            "layer" : "ETC",
            "LTPs" : [
                      { "id" : "mwEtc1",
                        "supportedAlarms" : "framingIsFaulty, containerIsDown",
                        "serverLTPs" : [{"id" : "pureEth1"}, {"id" : "pureEth2"}],
                        "conditional-package" : "mw-ethernet-container-pac"
                      },
                      { "id" : "mwEtc2",
                        "supportedAlarms" : "framingIsFaulty, containerIsDown",
                        "serverLTPs" : [{"id" : "pureEth3"}, {"id" : "pureEth4"}],
                        "conditional-package" : "mw-ethernet-container-pac"
                      }
                    ]
          },
          {
            "layer" : "ETY",
            "LTPs" : [
                      {"id" : "ety1", "physical-port-reference" : "shelf1:slot1:card-type:port1"},
                      {"id" : "ety2", "physical-port-reference" : "shelf1:slot1:card-type:port2"},
                      {"id" : "ety3", "physical-port-reference" : "shelf1:slot1:card-type:port3"},
                      {"id" : "ety4", "physical-port-reference" : "shelf1:slot1:card-type:port4"}
                    ]
          },
          {
            "layer" : "ETH",
            "LTPs" : [{"id" : "eth1", "serverLTPs" : [{"id" : "ety1"}], "conditional-package" : "ethernet-pac"},
                      {"id" : "eth2", "serverLTPs" : [{"id" : "ety2"}], "conditional-package" : "ethernet-pac"},
                      {"id" : "eth3", "serverLTPs" : [{"id" : "ety3"}], "conditional-package" : "ethernet-pac"},
                      {"id" : "eth4", "serverLTPs" : [{"id" : "ety4"}], "conditional-package" : "ethernet-pac"},
                      {"id" : "eth5", "serverLTPs" : [{"id" : "mwEtc1"}], "conditional-package" : "ethernet-pac"},
                      {"id" : "eth6", "serverLTPs" : [{"id" : "mwEtc2"}], "conditional-package" : "ethernet-pac"}
                    ]
          }
          ],
        "eth-cross-connections" : [
          {"fcPorts" : [{"ltp" : "eth5", "vlan-id" : "26"}, {"ltp" : "eth6", "vlan-id" : "26"}], "host" : false, "fcRoute" : "service1"}
          ]
      }
    },
    {
      "network-element" :
      {
        "uuid" : "Simulator-3",
        "type" : "OpenYuma",
        "interfaces" : [
          {
            "layer" : "MWPS",
            "type" : "OpenYuma",
            "LTPs" : [
                      { "id": "airIntf1",
                        "supportedAlarms" : "signalIsLost, rslIsExceeded, temperatureIsExceeded, modemIsFaulty, radioIsFaulty, modulationIsDownShifted",
                        "physical-port-reference" : "shelf1:slot2:card-type:port1",
                        "conditional-package" : "mw-air-interface-pac"
                      },
                      { "id": "airIntf2",
                        "supportedAlarms" : "signalIsLost, rslIsExceeded, temperatureIsExceeded, modemIsFaulty, radioIsFaulty, modulationIsDownShifted",
                        "physical-port-reference" : "shelf1:slot3:card-type:port1",
                        "conditional-package" : "mw-air-interface-pac"
                      }
                    ]
          },
          {
            "layer" : "MWS",
            "LTPs" : [
                      { "id" : "pureEth1",
                        "supportedAlarms" : "structureAlarm",
                        "serverLTPs" : [{"id" : "airIntf1"}],
                        "conditional-package" : "mw-pure-ethernet-structure-pac"
                      },
                      { "id" : "pureEth2",
                        "supportedAlarms" : "structureAlarm",
                        "serverLTPs" : [{"id" : "airIntf2"}],
                        "conditional-package" : "mw-pure-ethernet-structure-pac"
                      }
                    ]
          },
          {
            "layer" : "ETC",
            "LTPs" : [
                      { "id" : "mwEtc1",
                        "supportedAlarms" : "framingIsFaulty, containerIsDown",
                        "serverLTPs" : [{"id" : "pureEth1"}, {"id" : "pureEth2"}],
                        "conditional-package" : "mw-ethernet-container-pac"
                      }
                    ]
          },
          {
            "layer" : "ETY",
            "LTPs" : [
                      {"id" : "ety1", "physical-port-reference" : "shelf1:slot1:card-type:port1"},
                      {"id" : "ety2", "physical-port-reference" : "shelf1:slot1:card-type:port2"},
                      {"id" : "ety3", "physical-port-reference" : "shelf1:slot1:card-type:port3"},
                      {"id" : "ety4", "physical-port-reference" : "shelf1:slot1:card-type:port4"}
                    ]
          },
          {
            "layer" : "ETH",
            "LTPs" : [{"id" : "eth1", "serverLTPs" : [{"id" : "ety1"}], "conditional-package" : "ethernet-pac"},
                      {"id" : "eth2", "serverLTPs" : [{"id" : "ety2"}], "conditional-package" : "ethernet-pac"},
                      {"id" : "eth3", "serverLTPs" : [{"id" : "ety3"}], "conditional-package" : "ethernet-pac"},
                      {"id" : "eth4", "serverLTPs" : [{"id" : "ety4"}], "conditional-package" : "ethernet-pac"},
                      {"id" : "eth5", "serverLTPs" : [{"id" : "mwEtc1"}], "conditional-package" : "ethernet-pac"}
                    ]
          }
          ],
        "eth-cross-connections" : [
          {"fcPorts" : [{"ltp" : "eth1", "vlan-id" : "26"}, {"ltp" : "eth5", "vlan-id" : "26"}], "host" : true, "fcRoute" : "service1"}
          ]
      }
    }
    ],
  "topologies" : {
    "mwps" : {
      "links" : [
        [{"uuid" : "Simulator-1", "ltp" : "airIntf1", "radio-signal-id" : "26"}, {"uuid" : "Simulator-2", "ltp" : "airIntf1", "radio-signal-id" : "26"}],
        [{"uuid" : "Simulator-1", "ltp" : "airIntf2", "radio-signal-id" : "27"}, {"uuid" : "Simulator-2", "ltp" : "airIntf2", "radio-signal-id" : "27"}],
        [{"uuid" : "Simulator-2", "ltp" : "airIntf3", "radio-signal-id" : "28"}, {"uuid" : "Simulator-3", "ltp" : "airIntf1", "radio-signal-id" : "28"}],
        [{"uuid" : "Simulator-2", "ltp" : "airIntf4", "radio-signal-id" : "29"}, {"uuid" : "Simulator-3", "ltp" : "airIntf2", "radio-signal-id" : "29"}]
        ]
    },
    "eth" : {
      "links" : [
        [{"uuid" : "Simulator-2", "ltp" : "ety1", "vlan-id" : "30"}, {"uuid" : "Simulator-3", "ltp" : "ety1", "vlan-id" : "30"}]
      ]
    }
  }
}
```

An example emulator configuration file `config.json` is shown below:

```JSON
{
  "controller" :
  {
    "ip-address" : "192.168.254.254",
    "port" : 8181,
    "username" : "admin",
    "password" : "admin"
  },
  "managementIpNetwork" : "192.168.0.0/16",
  "hostIpNetwork" : "10.10.0.0/16",
  "notificationPeriod" : 10,
  "automatic-odl-registration" : true,
  "portBasedEmulation" : true,
  "netconfPortBase" : 12000,
  "sshPortBase" : 15000,
  "emulatorIpAddress" : "192.168.254.253"
}
```

The information about the SDN controller, where the emulated NEs automatically
register, is available under the `controller` object.

The range for the management IP of the network elements is given by the
`managementIpNetwork` object, and the range for the IPs of the hosts connected to the NEs
is configurable through the `linksIpNetwork` element.

Each  NE is capable of generating a random `problem-notification` NETCONF nofiticaion,
from its available `supported-alarms`. The value of `notificationPeriod` object
represents the amount of **seconds** between each such  random notifications.

* Starting the emulator is done with the following command (asuming you are in the base folder):

`sudo wtemulator --config=config.json --topo=topology.json --xml=yang/microwave-model-config.xml`

* Stopping the emulator is done with the commands `quit` or `exit` executed from the CLI. This will delete
all the previously created docker containers, docker networks or OVS Bridges or ports.

* Cleaning might be necessary if previous runs were not terminated correctly. This will delete
all the previously created docker containers, docker networks or OVS Bridges or ports.

`sudo wtemulator --config=config.json --clean`

### Contact

alex.stancu@radio.pub.ro

alexandrus@ceragon.com
