import json
import logging
import subprocess
import xml.etree.ElementTree as ET
import copy
import ipaddress

from wireless_emulator.utils import printErrorAndExit
from wireless_emulator.ip import ManagementNetworkIPFactory, InterfaceIPFactory, MacAddressFactory
import wireless_emulator.networkelement as NE
from wireless_emulator.utils import Singleton
from wireless_emulator.topology import Topology

logger = logging.getLogger(__name__)

class Emulator(metaclass=Singleton):

    def __init__(self, topologyFileName = None, xmlConfigFile = None, configFileName = None):
        self.networkElementList = []
        self.neNamesList = []
        self.topologies = []
        self.controllerInfo = {"ip-address" : None, "port" : None, "username" : None, "password" : None}
        self.topoJson = None
        self.configJson = None
        self.xmlConfigFile = xmlConfigFile
        self.xmlStatusFile = None
        self.registerToOdl = False
        if xmlConfigFile is not None:
            self.xmlStatusFile = xmlConfigFile.replace("config", "status")

        if topologyFileName is not None:
            try:
                with open(topologyFileName) as json_data:
                    self.topoJson = json.load(json_data)
            except IOError as err:
                logger.critical("Could not open topology file=%s", topologyFileName)
                logger.critical("I/O error({0}): {1}".format(err.errno, err.strerror))
                printErrorAndExit()

        if configFileName is not None:
            self.configFileName = configFileName
            try:
                with open(configFileName) as json_data:
                    self.configJson = json.load(json_data)
            except IOError as err:
                logger.critical("Could not open configuration file=%s", configFileName)
                logger.critical("I/O error({0}): {1}".format(err.errno, err.strerror))
                printErrorAndExit()

        self.mgmtIpFactory = None
        self.intfIpFactory = None

        if self.configJson['managementIpNetwork'] is not None and self.configJson['linksIpNetwork'] is not None:
            if self.validatePreferedIpNetworks(self.configJson['managementIpNetwork'],
                                            self.configJson['linksIpNetwork']) == False:
                self.mgmtIpFactory = ManagementNetworkIPFactory(self.configJson['managementIpNetwork'])
                self.intfIpFactory = InterfaceIPFactory(self.configJson['linksIpNetwork'])
            else:
                logger.error("Management IP Network and Links IP Network overlap! Starting with default values!")
                print("Management IP Network and Links IP Network overlap! Starting with default values!")
                self.mgmtIpFactory = ManagementNetworkIPFactory('192.168.0.0/16')
                self.intfIpFactory = InterfaceIPFactory('10.10.0.0/16')
        else:
            self.mgmtIpFactory = ManagementNetworkIPFactory('192.168.0.0/16')
            self.intfIpFactory = InterfaceIPFactory('10.10.0.0/16')

        self.macAddressFactory = MacAddressFactory()

        self.saveControllerInfo()

    def validatePreferedIpNetworks(self, mngIpNetwork, linksIpNetwork):
        mngNetwork = ipaddress.ip_network(mngIpNetwork)
        linksNetwork = ipaddress.ip_network(linksIpNetwork)

        return mngNetwork.overlaps(linksNetwork)

    def saveControllerInfo(self):
        self.controllerInfo['ip-address'] = self.configJson['controller']['ip-address']
        self.controllerInfo['port'] = self.configJson['controller']['port']
        self.controllerInfo['username'] = self.configJson['controller']['username']
        self.controllerInfo['password'] = self.configJson['controller']['password']

        if self.controllerInfo['ip-address'] is None or self.controllerInfo['port'] is None \
            or self.controllerInfo['username'] is None or self.controllerInfo['password'] is None:
            logger.error("Could not read controller parameters from the JSON topology file! "
                         "The emulator will not try to register the NEs to the ODL controller")

        self.registerToOdl = True

    def createNetworkElements(self):
        logger.debug("Creating Network Elements")

        neId = 1
        ofPortStart = 1
        for ne in self.topoJson['network-elements']:
            neUuid = ne['network-element']['uuid']
            interfaces = ne['network-element']['interfaces']
            neObj = None
            try:
                neObj = NE.NetworkElement(neUuid, neId, ofPortStart, interfaces)
            except ValueError:
                logger.critical("Could not create Network Element=%s", neUuid)
                printErrorAndExit()
            neObj.addNetworkElement()
            self.networkElementList.append(neObj)
            self.neNamesList.append(neObj.uuid)
            neId += 1

    def createTopologiesList(self):

        logger.debug("Creating topologies list...")

        mwpsTopo = self.topoJson['topologies']['mwps']

        topoObj = Topology(mwpsTopo, "mwps")
        self.topologies.append(topoObj)

    def buildTopologies(self):
        logger.debug("Building topologies...")
        for topo in self.topologies:
            topo.buildTopology()

    def createTopologies(self):
        self.createTopologiesList()
        self.buildTopologies()

    def addInterfacesInDocker(self):
        for ne in self.networkElementList:
            print("Adding relevant interfaces in docker container...")
            ne.addInterfacesInDockerContainer()

    def startEmulator(self):
        self.createNetworkElements()
        self.createTopologies()
        self.addInterfacesInDocker()

    def getNeByName(self, name):
        for ne in self.networkElementList:
            if ne.uuid == name:
                return ne
        return None