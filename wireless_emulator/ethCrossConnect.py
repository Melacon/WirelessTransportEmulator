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


class EthCrossConnect:

    def __init__(self, id, neObj, ethCrossConnect):
        self.id = id
        self.neObj = neObj
        self.hostAvailable = ethCrossConnect['host']
        self.uuid = 'fc-eth-' + str(self.id)

        self.fcPortList = ethCrossConnect['fcPorts']
        self.fcRoute = ethCrossConnect['fcRoute']

        self.interfacesObj = []

        if len(self.fcPortList) != 2:
            logger.critical("Incorrect ethernet cross connection defined in JSON config file. It does not contain exactly two interfaces!")
            raise ValueError("Incorrect ethernet cross connection defined in JSON config file. It does not contain exactly two interfaces!")

        if self.validateXConnEnds() is False:
            logger.critical("Interfaces defining eth cross connect not valid: ", self.fcPortList[0], self.fcPortList[1])
            raise ValueError("Interfaces defining eth cross connect not valid: ", self.fcPortList[0], self.fcPortList[1])

        logger.debug("Link object was created")

    def validateXConnEnds(self):

        intfObj = self.neObj.getInterfaceFromInterfaceUuid(self.fcPortList[0]['ltp'])
        if intfObj is not None:
            if intfObj.layer == 'ETH':
                self.interfacesObj.append(intfObj)
            else:
                logger.critical("Interface=%s is not of type ETH in NE=%s", self.fcPortList[0]['ltp'], self.neObj.uuid)
        else:
            logger.critical("Interface=%s not found in NE=%s", self.fcPortList[0]['ltp'], self.neObj.uuid)

        intfObj = self.neObj.getInterfaceFromInterfaceUuid(self.fcPortList[1]['ltp'])

        if intfObj is not None:
            if intfObj.layer == 'ETH':
                self.interfacesObj.append(intfObj)
            else:
                logger.critical("Interface=%s is not of type ETH in NE=%s", self.fcPortList[1]['ltp'], self.neObj.uuid)
        else:
            logger.critical("Interface=%s not found in NE=%s", self.fcPortList[1]['ltp'], self.neObj.uuid)

        if len(self.interfacesObj) != 2:
            return False

        return True

    #TODO implement host functionality
    def addXConn(self):

        bridgeName = 'xconn_br' + str(self.id)

        print("Adding bridge interface %s to docker container %s..." % (bridgeName, self.neObj.uuid))

        command = "ip link add name %s type bridge" % bridgeName
        self.neObj.executeCommandInContainer(command)

        command = "ip link set dev %s master %s" % (self.interfacesObj[0].getInterfaceName(), bridgeName)
        self.neObj.executeCommandInContainer(command)

        command = "ip link set dev %s master %s" % (self.interfacesObj[1].getInterfaceName(), bridgeName)
        self.neObj.executeCommandInContainer(command)

        command = "ip link set dev %s up" % bridgeName
        self.neObj.executeCommandInContainer(command)

    def buildXmlFiles(self):
        self.buildConfigXmlFiles()
        self.buildStatusXmlFiles()

    def buildConfigXmlFiles(self):
        parentNode = self.neObj.configRootXmlNode

        forwardingConstruct = copy.deepcopy(self.neObj.forwardingConstructConfigXmlNode)

        uuid = forwardingConstruct.find('core-model:uuid', self.neObj.namespaces)
        uuid.text = self.uuid

        layerProtocolName = forwardingConstruct.find('core-model:layer-protocol-name', self.neObj.namespaces)
        layerProtocolName.text = 'ETH'

        fcRoute = forwardingConstruct.find('core-model:fc-route', self.neObj.namespaces)
        fcRoute.text = self.fcRoute

        fcPort = forwardingConstruct.find('core-model:fc-port', self.neObj.namespaces)

        fcPortSaved = copy.deepcopy(fcPort)
        forwardingConstruct.remove(fcPort)

        for i in range(0,2):
            fcPort = copy.deepcopy(fcPortSaved)
            fcPortUuid = self.interfacesObj[i].getInterfaceUuid() + '_' +  str(i)

            uuid = fcPort.find('core-model:uuid', self.neObj.namespaces)
            uuid.text = fcPortUuid

            ltpNode = fcPort.find('core-model:ltp', self.neObj.namespaces)
            ltpNode.text = "ltp-" + self.interfacesObj[i].getInterfaceName()

            addCoreDefaultValuesToNode(fcPort, fcPortUuid, self.neObj.namespaces)

            forwardingConstruct.append(fcPort)

        uselessNode = forwardingConstruct.find('core-model:fc-switch', self.neObj.namespaces)
        forwardingConstruct.remove(uselessNode)

        addCoreDefaultValuesToNode(forwardingConstruct, self.uuid, self.neObj.namespaces)

        parentNode.append(forwardingConstruct)

    def buildStatusXmlFiles(self):
        parentNode = self.neObj.statusRootXmlNode

        forwardingConstruct  = copy.deepcopy(self.neObj.forwardingConstructStatusXmlNode)
        uuid = forwardingConstruct.find('uuid')
        uuid.text = self.uuid

        fcPort = forwardingConstruct.find('fc-port')

        fcPortSaved = copy.deepcopy(fcPort)
        forwardingConstruct.remove(fcPort)

        for i in range(0, 2):
            fcPort = copy.deepcopy(fcPortSaved)
            fcPortUuid = self.interfacesObj[i].getInterfaceUuid() + '_' + str(i)

            uuid = fcPort.find('uuid')
            uuid.text = fcPortUuid

            addCoreDefaultStatusValuesToNode(fcPort)

            forwardingConstruct.append(fcPort)

        addCoreDefaultStatusValuesToNode(forwardingConstruct)

        parentNode.append(forwardingConstruct)