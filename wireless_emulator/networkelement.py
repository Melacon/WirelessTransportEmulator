import logging
import subprocess
import xml.etree.ElementTree as ET
import copy
import os

import wireless_emulator.emulator
from wireless_emulator.utils import addCoreDefaultValuesToNode, printErrorAndExit, addCoreDefaultStatusValuesToNode
import wireless_emulator.interface as Intf
from wireless_emulator.odlregistration import registerNeToOdl

logger = logging.getLogger(__name__)

class NetworkElement:

    def __init__(self, neUuid, neId, ofPortStart, interfaces, eth_x_conn = None):
        self.uuid = neUuid
        self.id = neId
        self.OFPortStart = ofPortStart
        self.dockerName = self.uuid.replace(" ", "")

        self.emEnv = wireless_emulator.emulator.Emulator()
        self.eth_x_connect = eth_x_conn

        self.xmlConfigurationTree = None
        self.networkElementXmlNode = None
        self.ltpXmlNode = None
        self.microwaveModuleXmlNode = None
        self.airInterfacePacXmlNode = None
        self.pureEthernetPacXmlNode = None
        self.ethernetContainerPacXmlNode = None
        self.forwardingConstructCfgXmlNode = None

        self.xmlStatusTree = None
        self.statusXmlNode = None
        self.airInterfaceStatusXmlNode = None
        self.neStatusXmlNode = None
        self.ltpStatusXmlNode = None
        self.pureEthernetStatusXmlNode = None
        self.ethernetContainerStatusXmlNode = None
        self.forwardingConstructStatusXmlNode = None

        self.networkNamespace = None

        self.networkIPAddress = self.emEnv.mgmtIpFactory.getFreeManagementNetworkIP()
        if self.networkIPAddress is None:
            logger.critical("Could not retrieve a free Management Network IP address for NE=%s", self.uuid)
            raise ValueError("Invalid Network IP address")
        self.managementIPAddressString = str(self.networkIPAddress[1])

        self.interfaces = interfaces

        requiredIntfIpAddresses = 0
        freeIntfIpAddresses = self.emEnv .intfIpFactory.getNumberOfFreeInterfaceIpAddresses()
        for intf in interfaces:
            requiredIntfIpAddresses += len(intf['LTPs'])
        if (requiredIntfIpAddresses > freeIntfIpAddresses):
            logger.critical("Not enough free Interface IP address left for NE=%s", self.uuid)
            raise ValueError("Not enough free Interface IP addresses")

        self.interfaceList = []
        self.networkName = "oywe_net_" + str(self.id)

        tree = ET.parse(self.emEnv.xmlConfigFile)
        if tree is None:
            logger.critical("Could not parse XML default values configuration file!")
            printErrorAndExit()
        else:
            self.xmlConfigurationTree = tree

        tree = ET.parse(self.emEnv.xmlStatusFile)
        if tree is None:
            logger.critical("Could not parse XML default values status file!")
            printErrorAndExit()
        else:
            self.xmlStatusTree = tree

        self.namespaces = {'microwave-model' : 'urn:onf:params:xml:ns:yang:microwave-model',
                           'core-model' : 'urn:onf:params:xml:ns:yang:core-model'}
        self.saveXmlTemplates()

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

    def saveXmlTemplates(self):
        self.saveConfigXmlTemplates()
        self.saveStatusXmlTemplates()

    def saveConfigXmlTemplates(self):
        config = self.xmlConfigurationTree.getroot()

        self.microwaveModuleXmlNode = config
        airInterface = config.find('microwave-model:mw-air-interface-pac', self.namespaces)
        self.airInterfacePacXmlNode = copy.deepcopy(airInterface)
        self.microwaveModuleXmlNode.remove(airInterface)

        self.networkElementXmlNode = config.find('core-model:network-element', self.namespaces)
        ltp = self.networkElementXmlNode.find('core-model:ltp', self.namespaces)
        self.ltpXmlNode = copy.deepcopy(ltp)
        self.networkElementXmlNode.remove(ltp)

        pureEthernet = config.find('microwave-model:mw-pure-ethernet-structure-pac', self.namespaces)
        self.pureEthernetPacXmlNode = copy.deepcopy(pureEthernet)
        self.microwaveModuleXmlNode.remove(pureEthernet)

        ethernetContainer = config.find('microwave-model:mw-ethernet-container-pac', self.namespaces)
        self.ethernetContainerPacXmlNode = copy.deepcopy(ethernetContainer)
        self.microwaveModuleXmlNode.remove(ethernetContainer)

        forwardingConstruct = config.find('core-model:forwarding-construct', self.namespaces)
        self.forwardingConstructCfgXmlNode = copy.deepcopy(forwardingConstruct)
        config.remove(forwardingConstruct)

        uselessNode = config.find('microwave-model:co-channel-group', self.namespaces)
        config.remove(uselessNode)

        uselessNode = config.find('microwave-model:mw-air-interface-hsb-end-point-pac', self.namespaces)
        config.remove(uselessNode)

        uselessNode = config.find('microwave-model:mw-air-interface-hsb-fc-switch-pac', self.namespaces)
        config.remove(uselessNode)

        uselessNode = config.find('microwave-model:mw-air-interface-diversity-pac', self.namespaces)
        config.remove(uselessNode)

        uselessNode = config.find('microwave-model:mw-hybrid-mw-structure-pac', self.namespaces)
        config.remove(uselessNode)

        uselessNode = config.find('microwave-model:mw-tdm-container-pac', self.namespaces)
        config.remove(uselessNode)

        uselessNode = config.find('core-model:operation-envelope', self.namespaces)
        config.remove(uselessNode)

        uselessNode = config.find('core-model:equipment', self.namespaces)
        config.remove(uselessNode)

    def saveStatusXmlTemplates(self):
        status = self.xmlStatusTree.getroot()

        self.statusXmlNode = status
        airInterface = status.find('mw-air-interface-pac')
        self.airInterfaceStatusXmlNode = copy.deepcopy(airInterface)
        self.statusXmlNode.remove(airInterface)

        self.neStatusXmlNode = status.find('network-element')
        ltp = self.neStatusXmlNode.find('ltp')
        self.ltpStatusXmlNode = copy.deepcopy(ltp)
        self.neStatusXmlNode.remove(ltp)

        pureEthernetStructure = status.find('mw-pure-ethernet-structure-pac')
        self.pureEthernetStatusXmlNode = copy.deepcopy(pureEthernetStructure)
        self.statusXmlNode.remove(pureEthernetStructure)

        ethernetContainer = status.find('mw-ethernet-container-pac')
        self.ethernetContainerStatusXmlNode = copy.deepcopy(ethernetContainer)
        self.statusXmlNode.remove(ethernetContainer)

        forwardingConstruct = status.find('forwarding-construct')
        self.forwardingConstructStatusXmlNode = copy.deepcopy(forwardingConstruct)
        status.remove(forwardingConstruct)

        uselessNode = status.find('co-channel-group')
        status.remove(uselessNode)

        uselessNode = status.find('mw-air-interface-hsb-end-point-pac')
        status.remove(uselessNode)

        uselessNode = status.find('mw-air-interface-hsb-fc-switch-pac')
        status.remove(uselessNode)

        uselessNode = status.find('mw-air-interface-diversity-pac')
        status.remove(uselessNode)

        uselessNode = status.find('mw-hybrid-mw-structure-pac')
        status.remove(uselessNode)

        uselessNode = status.find('mw-tdm-container-pac')
        status.remove(uselessNode)

        uselessNode = status.find('operation-envelope')
        status.remove(uselessNode)

        uselessNode = status.find('equipment')
        status.remove(uselessNode)

    def buildCoreModelXml(self):
        node = self.networkElementXmlNode
        uuid = node.find('core-model:uuid', self.namespaces)
        uuid.text = self.uuid
        addCoreDefaultValuesToNode(node,self.uuid, self.namespaces, self)
        self.buildCoreModelForwardingConstructCfgXml()

    def buildCoreModelStatusXml(self):
        node = self.neStatusXmlNode
        addCoreDefaultStatusValuesToNode(node)
        self.buildCoreModelForwardingConstructStatusXml()

    def buildNotificationStatusXml(self):
        node = self.statusXmlNode
        notif = ET.SubElement(node, "notifications")
        timeout = ET.SubElement(notif, "timeout")
        timeout.text = str(self.emEnv.configJson['notificationPeriod'])

    def buildCoreModelForwardingConstructCfgXml(self):
        pass

    def buildCoreModelForwardingConstructStatusXml(self):
        pass

    def createInterfaces(self):
        ofPort = self.OFPortStart
        portNumId = 1
        for intf in self.interfaces:
            intfObj = None
            if intf['layer'] == "MWPS":

                for port in intf['LTPs']:
                    intfObj = Intf.MwpsInterface(port['id'], portNumId, ofPort, self, port['supportedAlarms'])
                    ofPort += 1
                    portNumId += 1
                    intfObj.buildXmlFiles()
                    self.interfaceList.append(intfObj)

            elif intf['layer'] == "MWS":

                for port in intf['LTPs']:
                    intfObj = Intf.MwsInterface(port['id'], portNumId, ofPort, self, port['supportedAlarms'], port['serverLTPs'])
                    ofPort += 1
                    portNumId += 1
                    intfObj.buildXmlFiles()
                    self.interfaceList.append(intfObj)

            elif intf['layer'] == "ETH":

                for port in intf['LTPs']:
                    if port.get('serverLTPs') is not None:
                        intfObj = Intf.EthInterface(port['id'], portNumId, ofPort, self, port['supportedAlarms'], port['serverLTPs'])
                    else:
                        intfObj = Intf.PhyEthInterface(port['id'], portNumId, ofPort, self, port['physical-port-reference'])
                    ofPort += 1
                    portNumId += 1
                    intfObj.buildXmlFiles()
                    self.interfaceList.append(intfObj)

            else:
                logger.critical("Illegal layer value %s found in JSON configuration file for NE=%s",
                                intf['layer'], self.uuid)
                raise ValueError("Illegal layer value")

    def createDockerContainer(self):
        print("Creating docker container %s..." % (self.dockerName))

        stringCmd = "docker create -it --privileged -p %s:8300:830 -p %s:2200:22 --name=%s --network=%s openyuma" % \
                    (self.managementIPAddressString, self.managementIPAddressString, self.dockerName, self.networkName)
        cmd = subprocess.Popen(stringCmd, shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)

        for line in cmd.stderr:
            strLine = line.decode("utf-8").rstrip('\n')
            logger.critical("Stderr: %s", strLine)
            raise RuntimeError

        logger.debug("Created docker container %s having IP=%s", self.dockerName, self.managementIPAddressString)

    def createDockerNetwork(self):

        netAddressString = str(self.networkIPAddress.with_prefixlen)
        print("Creating docker network %s..." % (netAddressString))

        stringCmd = "docker network create -d bridge --subnet=%s --ip-range=%s %s" % \
                    (netAddressString, netAddressString, self.networkName)
        cmd = subprocess.Popen(stringCmd, shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)

        for line in cmd.stderr:
            strLine = line.decode("utf-8").rstrip('\n')
            logger.critical("Stderr: %s", strLine)
            raise RuntimeError

        logger.debug("Created docker network %s having address %s", self.networkName, netAddressString)

    def copyXmlConfigFileToDockerContainer(self):
        outFileName = "startup-cfg.xml"
        self.xmlConfigurationTree.write(outFileName)
        targetPath = "/usr/src/OpenYuma"

        stringCmd = "docker cp %s %s:%s" % \
                    (outFileName, self.dockerName, targetPath)
        cmd = subprocess.Popen(stringCmd, shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)

        for line in cmd.stderr:
            strLine = line.decode("utf-8").rstrip('\n')
            logger.critical("Stderr: %s", strLine)
            raise RuntimeError

        stringCmd = "rm -f %s" % (outFileName)
        cmd = subprocess.Popen(stringCmd, shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)

        for line in cmd.stderr:
            strLine = line.decode("utf-8").rstrip('\n')
            logger.critical("Stderr: %s", strLine)
            raise RuntimeError

    def copyXmlStatusFileToDockerContainer(self):
        outFileName = "microwave-model-status.xml"
        self.xmlStatusTree.write(outFileName)
        targetPath = "/usr/src/OpenYuma"

        stringCmd = "docker cp %s %s:%s" % \
                    (outFileName, self.dockerName, targetPath)
        cmd = subprocess.Popen(stringCmd, shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)

        for line in cmd.stderr:
            strLine = line.decode("utf-8").rstrip('\n')
            logger.critical("Stderr: %s", strLine)
            raise RuntimeError

        stringCmd = "rm -f %s" % (outFileName)
        cmd = subprocess.Popen(stringCmd, shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)

        for line in cmd.stderr:
            strLine = line.decode("utf-8").rstrip('\n')
            logger.critical("Stderr: %s", strLine)
            raise RuntimeError

    def startDockerContainer(self):
        stringCmd = "docker start %s" % (self.dockerName)
        cmd = subprocess.Popen(stringCmd, shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)

        for line in cmd.stderr:
            strLine = line.decode("utf-8").rstrip('\n')
            logger.critical("Stderr: %s", strLine)
            raise RuntimeError

    def copyYangFilesToDockerContainer(self):

        directory = 'yang'
        for filename in os.listdir(directory):
            if filename.endswith(".yang"):
                stringCmd = "docker cp %s %s:%s" % \
                            (os.path.join(directory, filename), self.dockerName, "/usr/share/yuma/modules")
                cmd = subprocess.Popen(stringCmd, shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)

                for line in cmd.stderr:
                    strLine = line.decode("utf-8").rstrip('\n')
                    logger.critical("Stderr: %s", strLine)
                    raise RuntimeError

    def saveNetworkNamespace(self):
        stringCmd = "docker inspect -f '{{.State.Pid}}' \"%s\"" % (self.dockerName)
        cmd = subprocess.Popen(stringCmd, shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)

        for line in cmd.stderr:
            strLine = line.decode("utf-8").rstrip('\n')
            logger.critical("Stderr: %s", strLine)
            raise RuntimeError
        for line in cmd.stdout:
            print(line.decode("utf-8").rstrip('\n'))
            self.networkNamespace = line.decode("utf-8").rstrip('\n')

    def addNetworkElement(self):
        print("Adding Network element %s..." % (self.uuid))
        self.buildCoreModelXml()
        self.buildCoreModelStatusXml()
        self.buildNotificationStatusXml()

        self.createInterfaces()

        self.createDockerNetwork()
        self.createDockerContainer()

        self.copyXmlConfigFileToDockerContainer()
        self.copyXmlStatusFileToDockerContainer()
        self.copyYangFilesToDockerContainer()

        self.startDockerContainer()
        if self.emEnv.registerToOdl == True:
           try:
               registerNeToOdl(self.emEnv.controllerInfo, self.uuid, self.managementIPAddressString)
           except RuntimeError:
               print("Failed to register NE=%s having IP=%s to the ODL controller" % (self.uuid, self.managementIPAddressString))

        self.saveNetworkNamespace()

        #debug
        self.xmlConfigurationTree.write('output-config-' + self.dockerName + '.xml')
        self.xmlStatusTree.write('output-status-' + self.dockerName + '.xml')

    def addInterfacesInDockerContainer(self):

        for intf in self.interfaceList:
            if intf.prefixName == 'mws-':
                self.addMwsInterface(intf)
            elif intf.prefixName == 'eth-':
                self.addEthInterface(intf)

    def addMwsInterface(self, interfaceObj):
        logger.debug("Adding MWS interface %s to docker container %s", interfaceObj.getInterfaceName(), self.uuid)
        print("Adding MWS interface %s to docker container %s" % (interfaceObj.getInterfaceName(), self.uuid))

        command = "ip link add name %s type bond" % interfaceObj.getInterfaceName()
        self.executeCommand(command)

        command = "/bin/bash -c \"echo 0 > /sys/class/net/%s/bonding/mode\"" % interfaceObj.getInterfaceName()
        self.executeCommand(command)

        command = "/bin/bash -c \"echo 100 > /sys/class/net/%s/bonding/miimon\"" % interfaceObj.getInterfaceName()
        self.executeCommand(command)

        for serverLtp in interfaceObj.serverLtps:
            serverObj = self.getInterfaceFromInterfaceUuid(serverLtp)
            serverName = serverObj.getInterfaceName()
            command = "ip link set %s down" % serverName
            self.executeCommand(command)

            command = "ip link set %s master %s" % (serverName, interfaceObj.getInterfaceName())
            self.executeCommand(command)

        command = "ip link set %s up" % interfaceObj.getInterfaceName()
        self.executeCommand(command)

    def addEthInterface(self, interfaceObj):
        if interfaceObj.type is None:
            self.addEthContInterface(interfaceObj)
        else:
            self.addPhyEthInterface(interfaceObj)

    def addEthContInterface(self, interfaceObj):

        logger.debug("Adding ETH interface %s to docker container %s", interfaceObj.getInterfaceName(), self.uuid)
        print("Adding ETH interface %s to docker container %s" % (interfaceObj.getInterfaceName(), self.uuid))

        command = "ip link add name %s type bond" % interfaceObj.getInterfaceName()
        self.executeCommand(command)

        command = "/bin/bash -c \"echo 0 > /sys/class/net/%s/bonding/mode\"" % interfaceObj.getInterfaceName()
        self.executeCommand(command)

        command = "/bin/bash -c \"echo 100 > /sys/class/net/%s/bonding/miimon\"" % interfaceObj.getInterfaceName()
        self.executeCommand(command)

        # command = "/bin/bash -c \"echo 1 > /sys/class/net/%s/bonding/lacp_rate\"" % interfaceObj.getInterfaceName()
        # self.executeCommand(command)

        # command = "/bin/bash -c \"echo 1 > /sys/class/net/%s/bonding/xmit_hash_policy\"" % interfaceObj.getInterfaceName()
        # self.executeCommand(command)

        # command = "cat /sys/class/net/%s/bonding/mode" % interfaceObj.getInterfaceName()
        # self.executeCommand(command)

        for serverLtp in interfaceObj.serverLtps:
            serverObj = self.getInterfaceFromInterfaceUuid(serverLtp)
            serverName = serverObj.getInterfaceName()

            command = "ip link set %s down" % serverName
            self.executeCommand(command)

            command = "ip link set %s master %s" % (serverName, interfaceObj.getInterfaceName())
            self.executeCommand(command)

        ipIntfNetwork = self.emEnv.intfIpFactory.getFreeInterfaceIp()
        interfaceIp = str(ipIntfNetwork[1])
        command = "ip address add %s/30 dev %s" % (interfaceIp, interfaceObj.getInterfaceName())
        self.executeCommand(command)

        interfaceObj.setIpAddress(interfaceIp)

        command = "ip link set %s up" % interfaceObj.getInterfaceName()
        self.executeCommand(command)

    def addPhyEthInterface(self, interfaceObj):
        if self.emEnv.isInterfaceObjPartOfLink(interfaceObj) is True:
            logger.debug("NOT adding PHY-ETH interface %s to docker container %s because it was already added by the link", interfaceObj.getInterfaceName(), self.uuid)
        else:
            logger.debug(
                "Adding PHY-ETH interface %s to docker container %s...",
                interfaceObj.getInterfaceName(), self.uuid)
            print(
                "Adding PHY-ETH interface %s to docker container %s..." %
                (interfaceObj.getInterfaceName(), self.uuid))

            command = "ip link add name %s type dummy" % interfaceObj.getInterfaceName()
            self.executeCommand(command)

            command = "ip address add %s/30 dev %s" % (interfaceObj.getIpAddress(), interfaceObj.getInterfaceName())
            self.executeCommand(command)

            command = "ip link set %s up" % interfaceObj.getInterfaceName()
            self.executeCommand(command)

    def validateEthCrossConnectEnds(self):
        if self.eth_x_connect is None:
            return True

        for xconn in self.eth_x_connect:
            if len(xconn) != 2:
                return False

            intfObj = self.getInterfaceFromInterfaceUuid(xconn[0]['ltp'])
            if intfObj is None:
                return False
            elif intfObj.prefixName != 'eth-':
                return False
            intfObj = self.getInterfaceFromInterfaceUuid(xconn[1]['ltp'])
            if intfObj is None:
                return False
            elif intfObj.prefixName != 'eth-':
                return False

        return True

    def addEthCrossConnects(self):

        if self.validateEthCrossConnectEnds() is False:
            print("Incorrect eth cross connect defined in JSON config file for NE=%s", self.uuid)
            raise ValueError("Incorrect eth cross connect defined in JSON config file for NE=%s" % self.uuid)

        if self.eth_x_connect is not None:
            index = 1
            for xconn in self.eth_x_connect:

                intfObj1 = self.getInterfaceFromInterfaceUuid(xconn[0]['ltp'])
                intfObj2 = self.getInterfaceFromInterfaceUuid(xconn[1]['ltp'])

                bridgeName = 'xconn_br' + str(index)
                index += 1

                print("Adding bridge interface %s to docker container %s..." % (bridgeName, self.uuid))

                command = "ip link add name %s type bridge" % bridgeName
                self.executeCommand(command)

                command = "ip link set dev %s master %s" % (intfObj1.getInterfaceName(), bridgeName)
                self.executeCommand(command)

                command = "ip link set dev %s master %s" % (intfObj2.getInterfaceName(), bridgeName)
                self.executeCommand(command)

                command = "ip link set dev %s up" % bridgeName
                self.executeCommand(command)


    def executeCommand(self, command):
        stringCmd = "docker exec -it %s %s" % (self.dockerName, command)
        cmd = subprocess.Popen(stringCmd, shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)

        for line in cmd.stderr:
            strLine = line.decode("utf-8").rstrip('\n')
            logger.critical("Stderr: %s", strLine)
            raise RuntimeError
        for line in cmd.stdout:
            print(line.decode("utf-8").rstrip('\n'))
