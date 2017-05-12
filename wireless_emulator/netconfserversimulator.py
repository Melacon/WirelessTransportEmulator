import logging
import subprocess
import xml.etree.ElementTree as ET
import copy
import os

import wireless_emulator.emulator
from wireless_emulator.utils import addCoreDefaultValuesToNode, printErrorAndExit, addCoreDefaultStatusValuesToNode
from wireless_emulator.interface import *
from wireless_emulator.odlregistration import registerNeToOdl, registerNeToOdlNewVersion
import wireless_emulator.ethCrossConnect as EthXConn

logger = logging.getLogger(__name__)

class NetconfServerSimulator:

    def __init__(self, neUuid, neId, dockerType = None, neParamObject = None ):
        self.uuid = neUuid
        self.id = neId
        self.dockerName = self.uuid.replace(" ", "")
        self.dockerType = dockerType
        self.ptpEnabled = False
        if (neParamObject.get('xmlFile') is None):
            self.xmlFile = "xmlNeModel/DVM-ETY.xml"
        else:
            self.xmlFile = neParamObject.get('xmlFile')

        self.emEnv = wireless_emulator.emulator.Emulator()

        self.networkIPAddress = self.emEnv.mgmtIpFactory.getFreeManagementNetworkIP()
        if self.networkIPAddress is None:
            logger.critical("Could not retrieve a free Management Network IP address for NE=%s", self.uuid)
            raise ValueError("Invalid Network IP address")
        self.managementIPAddressString = str(self.networkIPAddress[1])

        self.netconfPortNumber = None
        self.sshPortNumber = None
        if self.emEnv.portBasedEmulation is True:
            self.netconfPortNumber = self.emEnv.netconfPortBase + self.id
            self.sshPortNumber = self.emEnv.sshPortBase + self.id
            self.managementIPAddressString = self.emEnv.emulatorIp
        else:
            self.netconfPortNumber = 8300
            self.sshPortNumber = 2200

        self.interfaces = None
        self.interfaceList = []

        # docker network name
        self.networkName = "wte_net_" + str(self.id)

        tree = ET.parse("NetconfServerSimulator/xmlNeModel/DVM-ETY.xml")
        if tree is None:
            logger.critical("Could not parse XML default values configuration file!")
            printErrorAndExit()
        else:
            self.xmlTree = tree

        logger.info("Created NetworkElement object with uuid=%s and id=%s and IP=%s",
                    self.uuid, self.id, self.managementIPAddressString)

    def getNeId(self):
        return self.id

    def getNeUuid(self):
        return self.uuid

    def getInterfaceFromInterfaceUuid(self, reqIntfId):
        for intf in self.interfaceList:
            intfId = intf.getInterfaceUuid()
            if intfId == reqIntfId:
                return intf
        logger.debug("Could not find interface having uuid=%s in ne=%s", reqIntfId, self.uuid)
        return None

    def getInterfaceFromInterfaceName(self, reqIntfName):
        for intf in self.interfaceList:
            intfId = intf.getInterfaceName()
            if intfId == reqIntfName:
                return intf
        logger.debug("Could not find interface having name=%s in ne=%s", reqIntfName, self.uuid)
        return None


    # TODO add support for new docker container
    def createDockerContainer(self):
        print("Creating docker container %s..." % (self.dockerName))

        if self.emEnv.portBasedEmulation is True:
            network=''
        else:
            self.createDockerNetwork()
            network='--network=%s' % self.networkName

        stringCmd = 'docker create -it --privileged -p %s:%s:830 -p %s:%s:22 --name=%s %s -e "UUID=%s" -e "XMLFILE=%s" netconfserversimulator' % \
                          (self.managementIPAddressString, self.netconfPortNumber,
                           self.managementIPAddressString, self.sshPortNumber,
                           self.dockerName, network,
                           self.uuid, self.xmlFile)

        if stringCmd is not None:
            self.emEnv.executeCommandInOS(stringCmd)
            logger.debug("Created docker container %s having IP=%s", self.dockerName, self.managementIPAddressString)

    # TODO add support for new docker container
    def copyXmlConfigFileToDockerContainer(self):
        outFileName = "startup-cfg.xml"
        self.xmlConfigurationTree.write(outFileName)
        targetPath = "/usr/src/OpenYuma"

        stringCmd = "docker cp %s %s:%s" % \
                    (outFileName, self.dockerName, targetPath)
        self.emEnv.executeCommandInOS(stringCmd)

        stringCmd = "rm -f %s" % (outFileName)
        self.emEnv.executeCommandInOS(stringCmd)

    def startDockerContainer(self):
        stringCmd = "docker start %s" % (self.dockerName)
        self.emEnv.executeCommandInOS(stringCmd)

    def saveNetworkNamespace(self):
        stringCmd = "docker inspect -f '{{.State.Pid}}' \"%s\"" % (self.dockerName)
        returnValue = self.emEnv.executeCommandAndGetResultInOS(stringCmd)
        for line in returnValue:
            self.networkNamespace = line.decode("utf-8").rstrip('\n')

    def addNetworkElement(self):
        print("Adding Network element %s..." % (self.uuid))

        self.createDockerContainer()

        #self.copyXmlConfigFileToDockerContainer()

        self.startDockerContainer()
        if self.emEnv.registerToOdl == True:
           try:
               # registerNeToOdl(self.emEnv.controllerInfo, self.uuid, self.managementIPAddressString)
               registerNeToOdlNewVersion(self.emEnv.controllerInfo, self.uuid, self.managementIPAddressString,
                                         self.netconfPortNumber)
           except RuntimeError:
               print("Failed to register NE=%s having IP=%s and port=%s to the ODL controller" %
                     (self.uuid, self.managementIPAddressString, self.netconfPortNumber))

        self.saveNetworkNamespace()

        #debug
        self.xmlTree.write('output-netconfserversimulator-' + self.dockerName + '.xml')

    def addInterfacesInDockerContainer(self):
        return

    def executeCommandInContainer(self, command):
        if command == '' or command is None:
            return
        stringCmd = "docker exec -it %s %s" % (self.dockerName, command)
        cmd = subprocess.Popen(stringCmd, shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)

        for line in cmd.stderr:
            strLine = line.decode("utf-8").rstrip('\n')
            logger.critical("Stderr: %s", strLine)
            raise RuntimeError
        for line in cmd.stdout:
            print(line.decode("utf-8").rstrip('\n'))
