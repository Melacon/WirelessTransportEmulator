import logging
import subprocess
import copy
import datetime

import wireless_emulator.emulator
from wireless_emulator.utils import addCoreDefaultValuesToNode, addCoreDefaultStatusValuesToNode

logger = logging.getLogger(__name__)


class MwpsInterface:

    def __init__(self, intfUuid, interfaceId, ofPort, neObj, supportedAlarms):
        self.IP = None
        self.uuid = intfUuid
        self.id = interfaceId
        self.ofPort = ofPort
        self.supportedAlarms = supportedAlarms

        alarm_list = supportedAlarms.split(",")
        if len(alarm_list) < 6:
            print("Interface %s does not supply at least 6 supported alarms!" % self.uuid)
            raise RuntimeError

        self.neObj = neObj
        self.prefixName = 'mwps-'
        self.interfaceName = self.prefixName + str(self.uuid)
        self.clientLtpNode = None

        self.emEnv = wireless_emulator.emulator.Emulator()

        self.MAC = self.emEnv.macAddressFactory.generateMacAddress(self.neObj.getNeId(), self.id)
        if self.MAC is None:
            logger.critical("No MAC Address created for NE=%s and interface=%s",
                            self.neObj.getNeUuid(), self.uuid)

        self.radioSignalId = self.findRadioSignalId()

        logger.debug("MwpsInterface object having name=%s, ofPort=%d and IP=%s was created",
                     self.interfaceName, self.ofPort, self.IP)

    def getIpAddress(self):
        return self.IP

    def setIpAddress(self, IP):
        self.IP = IP

    def getInterfaceUuid(self):
        return self.uuid

    def getInterfaceName(self):
        return self.interfaceName

    def getNeName(self):
        return self.neObj.dockerName

    def getMacAddress(self):
        return self.MAC

    def buildCoreModelConfigXml(self):
        neNode = self.neObj.networkElementXmlNode
        ltpNode = copy.deepcopy(self.neObj.ltpXmlNode)
        uuid = ltpNode.find('core-model:uuid', self.neObj.namespaces)
        ltpUuid = "ltp-" + self.interfaceName
        uuid.text = ltpUuid
        addCoreDefaultValuesToNode(ltpNode, ltpUuid, self.neObj.namespaces)

        clientLtp = ltpNode.find('core-model:client-ltp', self.neObj.namespaces)
        self.clientLtpNode = copy.deepcopy(clientLtp)
        ltpNode.remove(clientLtp)

        lpNode = ltpNode.find('core-model:lp', self.neObj.namespaces)
        uuid = lpNode.find('core-model:uuid', self.neObj.namespaces)
        lpUuid = "lp-" + self.interfaceName
        uuid.text = lpUuid
        layerProtocolName = lpNode.find('core-model:layer-protocol-name', self.neObj.namespaces)
        layerProtocolName.text = self.prefixName[:-1].upper()
        addCoreDefaultValuesToNode(lpNode, lpUuid, self.neObj.namespaces)

        neNode.append(ltpNode)

    def setCoreModelClientStateXml(self, clientLtpUuid):
        neNode = self.neObj.networkElementXmlNode

        for ltpNode in neNode.findall('core-model:ltp', self.neObj.namespaces):
            uuid = ltpNode.find('core-model:uuid', self.neObj.namespaces)
            logger.debug("Found ltp with ltp=%s", uuid.text)
            if uuid.text == ('ltp-' + self.interfaceName):
                if self.clientLtpNode is not None:
                    newClient = copy.deepcopy(self.clientLtpNode)
                    newClient.text = 'ltp-' + clientLtpUuid
                    ltpNode.append(newClient)

    def buildCoreModelStatusXml(self):
        neStatusNode = self.neObj.neStatusXmlNode

        ltpNode = copy.deepcopy(self.neObj.ltpStatusXmlNode)
        uuid = ltpNode.find('uuid')
        ltpUuid = "ltp-" + self.interfaceName
        uuid.text = ltpUuid
        addCoreDefaultStatusValuesToNode(ltpNode)

        lpNode = ltpNode.find('lp')
        uuid = lpNode.find('uuid')
        lpUuid = "lp-" + self.interfaceName
        uuid.text = lpUuid
        addCoreDefaultStatusValuesToNode(lpNode)

        neStatusNode.append(ltpNode)

    def buildMicrowaveModelXml(self):
        parentNode = self.neObj.microwaveModuleXmlNode

        airInterface = copy.deepcopy(self.neObj.airInterfacePacXmlNode)
        lpUuid = "lp-" + self.interfaceName

        layerProtocol = airInterface.find('microwave-model:layer-protocol', self.neObj.namespaces)
        layerProtocol.text = lpUuid

        airInterfaceConfig = airInterface.find('microwave-model:air-interface-configuration', self.neObj.namespaces)

        if self.radioSignalId is not None:
            radioSignalId = airInterfaceConfig.find('microwave-model:radio-signal-id', self.neObj.namespaces)
            radioSignalId.text = self.radioSignalId

        cryptoKey = airInterfaceConfig.find('microwave-model:cryptographic-key', self.neObj.namespaces)
        cryptoKey.text = '********'

        problemKindSeverityList = airInterfaceConfig.find('microwave-model:problem-kind-severity-list', self.neObj.namespaces)

        problemKindNode = copy.deepcopy(problemKindSeverityList)
        airInterfaceConfig.remove(problemKindSeverityList)

        alarm_list = self.supportedAlarms.split(",")
        for alarm in alarm_list:
            newNode = copy.deepcopy(problemKindNode)
            name = newNode.find('microwave-model:problem-kind-name', self.neObj.namespaces)
            name.text = alarm
            severity = newNode.find('microwave-model:problem-kind-severity', self.neObj.namespaces)
            severity.text = "warning"
            airInterfaceConfig.append(newNode)

        parentNode.append(airInterface)

    def buildMicrowaveModelStatusXml(self):
        parentNode = self.neObj.statusXmlNode

        airInterface = copy.deepcopy(self.neObj.airInterfaceStatusXmlNode)
        lpUuid = "lp-" + self.interfaceName

        layerProtocol = airInterface.find('layer-protocol')
        layerProtocol.text = lpUuid

        supportedAlarms = airInterface.find(
            'air-interface-capability/supported-alarms')
        supportedAlarms.text = self.supportedAlarms

        supportedChPlan = airInterface.find(
            'air-interface-capability/supported-channel-plan-list/supported-channel-plan')
        supportedChPlan.text = "plan_1"

        trModeId = airInterface.find(
            'air-interface-capability/supported-channel-plan-list/transmission-mode-list/transmission-mode-id')
        trModeId.text = "transmission_mode_1"

        seqNum = airInterface.find('air-interface-current-problems/current-problem-list/sequence-number')
        seqNum.text = "1"

        problemName = airInterface.find('air-interface-current-problems/current-problem-list/problem-name')
        alarm_list = self.supportedAlarms.split(",")
        problemName.text = alarm_list[0]

        airInterfaceCurrentPerformance = airInterface.find('air-interface-current-performance')
        self.addCurrentPerformanceXmlValues(airInterfaceCurrentPerformance)

        airInterfaceHistoricalPerformances = airInterface.find('air-interface-historical-performances')
        self.addHistoricalPerformancesXmlValues(airInterfaceHistoricalPerformances)

        parentNode.append(airInterface)

    def addCurrentPerformanceXmlValues(self, parentNode):
        currentPerformanceDataList = parentNode.find('current-performance-data-list')
        savedNode = copy.deepcopy(currentPerformanceDataList)
        parentNode.remove(currentPerformanceDataList)

        currentPerformanceDataList = copy.deepcopy(savedNode)
        node = currentPerformanceDataList.find('scanner-id')
        node.text = "1"
        node = currentPerformanceDataList.find('granularity-period')
        node.text = "period-15min"
        node = currentPerformanceDataList.find('suspect-interval-flag')
        node.text = "false"
        node = currentPerformanceDataList.find('timestamp')
        node.text = datetime.datetime.utcnow().strftime('%Y-%m-%dT%H:%M:%S.%f')[:-5] + "Z"
        parentNode.append(currentPerformanceDataList)

        currentPerformanceDataList = copy.deepcopy(savedNode)
        node = currentPerformanceDataList.find('scanner-id')
        node.text = "2"
        node = currentPerformanceDataList.find('granularity-period')
        node.text = "period-24hours"
        node = currentPerformanceDataList.find('suspect-interval-flag')
        node.text = "false"
        node = currentPerformanceDataList.find('timestamp')
        node.text = datetime.datetime.utcnow().strftime('%Y-%m-%dT%H:%M:%S.%f')[:-5] + "Z"
        parentNode.append(currentPerformanceDataList)

    def addHistoricalPerformancesXmlValues(self, parentNode):
        histPerfDataList = parentNode.find('historical-performance-data-list')
        savedNode = copy.deepcopy(histPerfDataList)
        parentNode.remove(histPerfDataList)

        for i in range(0,96):
            self.addHistoricalPerformances15minutes(parentNode, savedNode, i)

        for i in range(0,7):
            self.addHistoricalPerformances24hours(parentNode, savedNode, i)

    def addHistoricalPerformances15minutes(self, parentNode, savedNode, index):
        histPerfDataList = copy.deepcopy(savedNode)
        timeNow = datetime.datetime.utcnow()

        node = histPerfDataList.find('history-data-id')
        node.text = str(index)
        node = histPerfDataList.find('granularity-period')
        node.text = "period-15min"
        node = histPerfDataList.find('suspect-interval-flag')
        node.text = "false"
        node = histPerfDataList.find('period-end-time')
        timestamp = timeNow - datetime.timedelta(minutes=15*index)
        node.text = timestamp.strftime('%Y-%m-%dT%H:%M:%S.%f')[:-5] + "Z"

        parentNode.append(histPerfDataList)

    def addHistoricalPerformances24hours(self, parentNode, savedNode, index):
        histPerfDataList = copy.deepcopy(savedNode)
        timeNow = datetime.datetime.utcnow()

        node = histPerfDataList.find('history-data-id')
        node.text = str(index + 96)
        node = histPerfDataList.find('granularity-period')
        node.text = "period-24hours"
        node = histPerfDataList.find('suspect-interval-flag')
        node.text = "false"
        node = histPerfDataList.find('period-end-time')
        timestamp = timeNow - datetime.timedelta(days=1*index)
        node.text = timestamp.strftime('%Y-%m-%dT%H:%M:%S.%f')[:-5] + "Z"

        parentNode.append(histPerfDataList)

    def buildXmlFiles(self):
        self.buildCoreModelConfigXml()
        self.buildMicrowaveModelXml()

        self.buildCoreModelStatusXml()
        self.buildMicrowaveModelStatusXml()

    def findRadioSignalId(self):
        for link in self.emEnv.topoJson['topologies']['mwps']['links']:
            if self.neObj.getNeUuid() == link[0]['uuid'] and self.uuid == link[0]['ltp']:
                return link[0]['radio-signal-id']
            elif self.neObj.getNeUuid() == link[1]['uuid'] and self.uuid == link[1]['ltp']:
                return link[1]['radio-signal-id']
        return None


class MwsInterface:

    def __init__(self, intfUuid, interfaceId, ofPort, neObj, supportedAlarms, serverLtps):
        self.IP = None
        self.uuid = intfUuid
        self.id = interfaceId
        self.ofPort = ofPort
        self.supportedAlarms = supportedAlarms

        alarm_list = supportedAlarms.split(",")
        if len(alarm_list) < 1:
            print("Interface %s does not supply at least 1 supported alarms!" % self.uuid)
            raise RuntimeError

        self.neObj = neObj
        self.prefixName = 'mws-'
        self.interfaceName = self.prefixName + str(self.uuid)
        self.clientLtpNode = None

        self.emEnv = wireless_emulator.emulator.Emulator()

        self.MAC = self.emEnv.macAddressFactory.generateMacAddress(self.neObj.getNeId(), self.id)
        if self.MAC is None:
            logger.critical("No MAC Address created for NE=%s and interface=%s",
                            self.neObj.getNeUuid(), self.uuid)

        self.serverLtps = []
        for ltp in serverLtps:
            self.serverLtps.append(ltp['id'])

        logger.debug("MwsInterface object having name=%s, ofPort=%d and IP=%s was created",
                     self.interfaceName, self.ofPort, self.IP)

    def getIpAddress(self):
        return self.IP

    def setIpAddress(self, IP):
        self.IP = IP

    def getInterfaceUuid(self):
        return self.uuid

    def getInterfaceName(self):
        return self.interfaceName

    def getNeName(self):
        return self.neObj.dockerName

    def getMacAddress(self):
        return self.MAC

    def buildCoreModelConfigXml(self):
        neNode = self.neObj.networkElementXmlNode
        ltpNode = copy.deepcopy(self.neObj.ltpXmlNode)
        uuid = ltpNode.find('core-model:uuid', self.neObj.namespaces)
        ltpUuid = "ltp-" + self.interfaceName
        uuid.text = ltpUuid
        addCoreDefaultValuesToNode(ltpNode, ltpUuid, self.neObj.namespaces)

        clientLtp = ltpNode.find('core-model:client-ltp', self.neObj.namespaces)
        self.clientLtpNode = copy.deepcopy(clientLtp)
        ltpNode.remove(clientLtp)

        serverLtp = ltpNode.find('core-model:server-ltp', self.neObj.namespaces)
        serverLtpNode = copy.deepcopy(serverLtp)
        ltpNode.remove(serverLtp)

        for ltp in self.serverLtps:
            server = copy.deepcopy(serverLtpNode)
            server.text = 'ltp-mwps-' + ltp
            ltpNode.append(server)

            serverInterface = self.neObj.getInterfaceFromInterfaceUuid(ltp)
            serverInterface.setCoreModelClientStateXml(self.interfaceName)

        lpNode = ltpNode.find('core-model:lp', self.neObj.namespaces)
        uuid = lpNode.find('core-model:uuid', self.neObj.namespaces)
        lpUuid = "lp-" + self.interfaceName
        uuid.text = lpUuid
        layerProtocolName = lpNode.find('core-model:layer-protocol-name', self.neObj.namespaces)
        layerProtocolName.text = self.prefixName[:-1].upper()
        addCoreDefaultValuesToNode(lpNode, lpUuid, self.neObj.namespaces)

        neNode.append(ltpNode)

    def setCoreModelClientStateXml(self, clientLtpUuid):
        neNode = self.neObj.networkElementXmlNode

        for ltpNode in neNode.findall('core-model:ltp', self.neObj.namespaces):
            uuid = ltpNode.find('core-model:uuid', self.neObj.namespaces)
            logger.debug("Found ltp with ltp=%s", uuid.text)
            if uuid.text == ('ltp-' + self.interfaceName):
                if self.clientLtpNode is not None:
                    newClient = copy.deepcopy(self.clientLtpNode)
                    newClient.text = 'ltp-' + clientLtpUuid
                    ltpNode.append(newClient)

    def buildCoreModelStatusXml(self):
        neStatusNode = self.neObj.neStatusXmlNode

        ltpNode = copy.deepcopy(self.neObj.ltpStatusXmlNode)
        uuid = ltpNode.find('uuid')
        ltpUuid = "ltp-" + self.interfaceName
        uuid.text = ltpUuid
        addCoreDefaultStatusValuesToNode(ltpNode)

        lpNode = ltpNode.find('lp')
        uuid = lpNode.find('uuid')
        lpUuid = "lp-" + self.interfaceName
        uuid.text = lpUuid
        addCoreDefaultStatusValuesToNode(lpNode)

        neStatusNode.append(ltpNode)

    def buildMicrowaveModelXml(self):
        parentNode = self.neObj.microwaveModuleXmlNode

        pureEthernetStructure = copy.deepcopy(self.neObj.pureEthernetPacXmlNode)
        lpUuid = "lp-" + self.interfaceName

        layerProtocol = pureEthernetStructure.find('microwave-model:layer-protocol', self.neObj.namespaces)
        layerProtocol.text = lpUuid

        pureEthConfig = pureEthernetStructure.find('microwave-model:pure-ethernet-structure-configuration', self.neObj.namespaces)

        problemKindSeverityList = pureEthConfig.find('microwave-model:problem-kind-severity-list', self.neObj.namespaces)

        problemKindNode = copy.deepcopy(problemKindSeverityList)
        pureEthConfig.remove(problemKindSeverityList)

        alarm_list = self.supportedAlarms.split(",")
        for alarm in alarm_list:
            newNode = copy.deepcopy(problemKindNode)
            name = newNode.find('microwave-model:problem-kind-name', self.neObj.namespaces)
            name.text = alarm
            severity = newNode.find('microwave-model:problem-kind-severity', self.neObj.namespaces)
            severity.text = "warning"
            pureEthConfig.append(newNode)

        parentNode.append(pureEthernetStructure)

    def buildMicrowaveModelStatusXml(self):
        parentNode = self.neObj.statusXmlNode

        pureEthernetStructure = copy.deepcopy(self.neObj.pureEthernetStatusXmlNode)
        lpUuid = "lp-" + self.interfaceName

        layerProtocol = pureEthernetStructure.find('layer-protocol')
        layerProtocol.text = lpUuid

        structureId = pureEthernetStructure.find('pure-ethernet-structure-capability/structure-id')
        structureId.text = lpUuid

        supportedAlarms = pureEthernetStructure.find(
            'pure-ethernet-structure-capability/supported-alarms')
        supportedAlarms.text = self.supportedAlarms

        seqNum = pureEthernetStructure.find(
            'pure-ethernet-structure-current-problems/current-problem-list/sequence-number')
        seqNum.text = "1"

        problemName = pureEthernetStructure.find('pure-ethernet-structure-current-problems/current-problem-list/problem-name')
        alarm_list = self.supportedAlarms.split(",")
        problemName.text = alarm_list[0]

        currentPerformance = pureEthernetStructure.find('pure-ethernet-structure-current-performance')
        self.addCurrentPerformanceXmlValues(currentPerformance)

        historicalPerformances = pureEthernetStructure.find('pure-ethernet-structure-historical-performances')
        self.addHistoricalPerformancesXmlValues(historicalPerformances)

        parentNode.append(pureEthernetStructure)

    def addCurrentPerformanceXmlValues(self, parentNode):
        currentPerformanceDataList = parentNode.find('current-performance-data-list')
        savedNode = copy.deepcopy(currentPerformanceDataList)
        parentNode.remove(currentPerformanceDataList)

        currentPerformanceDataList = copy.deepcopy(savedNode)
        node = currentPerformanceDataList.find('scanner-id')
        node.text = "1"
        node = currentPerformanceDataList.find('granularity-period')
        node.text = "period-15min"
        node = currentPerformanceDataList.find('suspect-interval-flag')
        node.text = "false"
        node = currentPerformanceDataList.find('timestamp')
        node.text = datetime.datetime.utcnow().strftime('%Y-%m-%dT%H:%M:%S.%f')[:-5] + "Z"
        parentNode.append(currentPerformanceDataList)

        currentPerformanceDataList = copy.deepcopy(savedNode)
        node = currentPerformanceDataList.find('scanner-id')
        node.text = "2"
        node = currentPerformanceDataList.find('granularity-period')
        node.text = "period-24hours"
        node = currentPerformanceDataList.find('suspect-interval-flag')
        node.text = "false"
        node = currentPerformanceDataList.find('timestamp')
        node.text = datetime.datetime.utcnow().strftime('%Y-%m-%dT%H:%M:%S.%f')[:-5] + "Z"
        parentNode.append(currentPerformanceDataList)

    def addHistoricalPerformancesXmlValues(self, parentNode):
        histPerfDataList = parentNode.find('historical-performance-data-list')
        savedNode = copy.deepcopy(histPerfDataList)
        parentNode.remove(histPerfDataList)

        for i in range(0,96):
            self.addHistoricalPerformances15minutes(parentNode, savedNode, i)

        for i in range(0,7):
            self.addHistoricalPerformances24hours(parentNode, savedNode, i)

    def addHistoricalPerformances15minutes(self, parentNode, savedNode, index):
        histPerfDataList = copy.deepcopy(savedNode)
        timeNow = datetime.datetime.utcnow()

        node = histPerfDataList.find('history-data-id')
        node.text = str(index)
        node = histPerfDataList.find('granularity-period')
        node.text = "period-15min"
        node = histPerfDataList.find('suspect-interval-flag')
        node.text = "false"
        node = histPerfDataList.find('period-end-time')
        timestamp = timeNow - datetime.timedelta(minutes=15*index)
        node.text = timestamp.strftime('%Y-%m-%dT%H:%M:%S.%f')[:-5] + "Z"

        parentNode.append(histPerfDataList)

    def addHistoricalPerformances24hours(self, parentNode, savedNode, index):
        histPerfDataList = copy.deepcopy(savedNode)
        timeNow = datetime.datetime.utcnow()

        node = histPerfDataList.find('history-data-id')
        node.text = str(index + 96)
        node = histPerfDataList.find('granularity-period')
        node.text = "period-24hours"
        node = histPerfDataList.find('suspect-interval-flag')
        node.text = "false"
        node = histPerfDataList.find('period-end-time')
        timestamp = timeNow - datetime.timedelta(days=1*index)
        node.text = timestamp.strftime('%Y-%m-%dT%H:%M:%S.%f')[:-5] + "Z"

        parentNode.append(histPerfDataList)

    def buildXmlFiles(self):
        self.buildCoreModelConfigXml()
        self.buildMicrowaveModelXml()

        self.buildCoreModelStatusXml()
        self.buildMicrowaveModelStatusXml()


class EthInterface:

    def __init__(self, intfUuid, interfaceId, ofPort, neObj, supportedAlarms, serverLtps):
        self.IP = None
        self.uuid = intfUuid
        self.id = interfaceId
        self.ofPort = ofPort
        self.supportedAlarms = supportedAlarms
        self.type = None

        alarm_list = supportedAlarms.split(",")
        if len(alarm_list) < 2:
            print("Interface %s does not supply at least 2 supported alarms!" % self.uuid)
            raise RuntimeError

        self.neObj = neObj
        self.prefixName = 'eth-'
        self.interfaceName = self.prefixName + str(self.uuid)

        self.emEnv = wireless_emulator.emulator.Emulator()

        self.MAC = self.emEnv.macAddressFactory.generateMacAddress(self.neObj.getNeId(), self.id)
        if self.MAC is None:
            logger.critical("No MAC Address created for NE=%s and interface=%s",
                            self.neObj.getNeUuid(), self.uuid)

        self.serverLtps = []
        for ltp in serverLtps:
            self.serverLtps.append(ltp['id'])

        logger.debug("EthInterface object having name=%s, ofPort=%d and IP=%s was created",
                     self.interfaceName, self.ofPort, self.IP)

    def getIpAddress(self):
        return self.IP

    def setIpAddress(self, IP):
        self.IP = IP

    def getInterfaceUuid(self):
        return self.uuid

    def getInterfaceName(self):
        return self.interfaceName

    def getNeName(self):
        return self.neObj.dockerName

    def getMacAddress(self):
        return self.MAC

    def buildCoreModelConfigXml(self):
        neNode = self.neObj.networkElementXmlNode
        ltpNode = copy.deepcopy(self.neObj.ltpXmlNode)
        uuid = ltpNode.find('core-model:uuid', self.neObj.namespaces)
        ltpUuid = "ltp-" + self.interfaceName
        uuid.text = ltpUuid
        addCoreDefaultValuesToNode(ltpNode, ltpUuid, self.neObj.namespaces)

        serverLtp = ltpNode.find('core-model:server-ltp', self.neObj.namespaces)
        serverLtpNode = copy.deepcopy(serverLtp)
        ltpNode.remove(serverLtp)

        for ltp in self.serverLtps:
            server = copy.deepcopy(serverLtpNode)
            server.text = 'ltp-mws-' + ltp
            ltpNode.append(server)

            serverInterface = self.neObj.getInterfaceFromInterfaceUuid(ltp)
            serverInterface.setCoreModelClientStateXml(self.interfaceName)

        lpNode = ltpNode.find('core-model:lp', self.neObj.namespaces)
        uuid = lpNode.find('core-model:uuid', self.neObj.namespaces)
        lpUuid = "lp-" + self.interfaceName
        uuid.text = lpUuid
        layerProtocolName = lpNode.find('core-model:layer-protocol-name', self.neObj.namespaces)
        layerProtocolName.text = self.prefixName[:-1].upper()
        addCoreDefaultValuesToNode(lpNode, lpUuid, self.neObj.namespaces)

        neNode.append(ltpNode)

    def buildCoreModelStatusXml(self):
        neStatusNode = self.neObj.neStatusXmlNode

        ltpNode = copy.deepcopy(self.neObj.ltpStatusXmlNode)
        uuid = ltpNode.find('uuid')
        ltpUuid = "ltp-" + self.interfaceName
        uuid.text = ltpUuid
        addCoreDefaultStatusValuesToNode(ltpNode)

        lpNode = ltpNode.find('lp')
        uuid = lpNode.find('uuid')
        lpUuid = "lp-" + self.interfaceName
        uuid.text = lpUuid
        addCoreDefaultStatusValuesToNode(lpNode)

        neStatusNode.append(ltpNode)

    def buildMicrowaveModelXml(self):
        parentNode = self.neObj.microwaveModuleXmlNode

        ethernetContainer = copy.deepcopy(self.neObj.ethernetContainerPacXmlNode)
        lpUuid = "lp-" + self.interfaceName

        layerProtocol = ethernetContainer.find('microwave-model:layer-protocol', self.neObj.namespaces)
        layerProtocol.text = lpUuid

        ethContainerConfig = ethernetContainer.find('microwave-model:ethernet-container-configuration', self.neObj.namespaces)

        cryptoKey = ethContainerConfig.find('microwave-model:cryptographic-key', self.neObj.namespaces)
        cryptoKey.text = '********'

        problemKindSeverityList = ethContainerConfig.find('microwave-model:problem-kind-severity-list', self.neObj.namespaces)

        problemKindNode = copy.deepcopy(problemKindSeverityList)
        ethContainerConfig.remove(problemKindSeverityList)

        alarm_list = self.supportedAlarms.split(",")
        for alarm in alarm_list:
            newNode = copy.deepcopy(problemKindNode)
            name = newNode.find('microwave-model:problem-kind-name', self.neObj.namespaces)
            name.text = alarm
            severity = newNode.find('microwave-model:problem-kind-severity', self.neObj.namespaces)
            severity.text = "warning"
            ethContainerConfig.append(newNode)

        segmentsIdList = ethContainerConfig.find('microwave-model:segments-id-list',
                                                          self.neObj.namespaces)
        segmentsIdListSaved = copy.deepcopy(segmentsIdList)
        ethContainerConfig.remove(segmentsIdList)

        for struct in self.serverLtps:
            newSegmentsIdList = copy.deepcopy(segmentsIdListSaved)
            structureIdRef = newSegmentsIdList.find('microwave-model:structure-id-ref', self.neObj.namespaces)
            structureIdRef.text = 'lp-mws-' + struct
            segmentIdRef = newSegmentsIdList.find('microwave-model:segment-id-ref', self.neObj.namespaces)
            segmentIdRef.text = '1'
            ethContainerConfig.append(newSegmentsIdList)

        parentNode.append(ethernetContainer)

    def buildMicrowaveModelStatusXml(self):
        parentNode = self.neObj.statusXmlNode

        ethernetContainer = copy.deepcopy(self.neObj.ethernetContainerStatusXmlNode)
        lpUuid = "lp-" + self.interfaceName

        layerProtocol = ethernetContainer.find('layer-protocol')
        layerProtocol.text = lpUuid

        supportedAlarms = ethernetContainer.find(
            'ethernet-container-capability/supported-alarms')
        supportedAlarms.text = self.supportedAlarms

        seqNum = ethernetContainer.find(
            'ethernet-container-current-problems/current-problem-list/sequence-number')
        seqNum.text = "1"

        problemName = ethernetContainer.find('ethernet-container-current-problems/current-problem-list/problem-name')
        alarm_list = self.supportedAlarms.split(",")
        problemName.text = alarm_list[0]

        currentPerformance = ethernetContainer.find('ethernet-container-current-performance')
        self.addCurrentPerformanceXmlValues(currentPerformance)

        historicalPerformances = ethernetContainer.find('ethernet-container-historical-performances')
        self.addHistoricalPerformancesXmlValues(historicalPerformances)

        parentNode.append(ethernetContainer)

    def addCurrentPerformanceXmlValues(self, parentNode):
        currentPerformanceDataList = parentNode.find('current-performance-data-list')
        savedNode = copy.deepcopy(currentPerformanceDataList)
        parentNode.remove(currentPerformanceDataList)

        currentPerformanceDataList = copy.deepcopy(savedNode)
        node = currentPerformanceDataList.find('scanner-id')
        node.text = "1"
        node = currentPerformanceDataList.find('granularity-period')
        node.text = "period-15min"
        node = currentPerformanceDataList.find('suspect-interval-flag')
        node.text = "false"
        node = currentPerformanceDataList.find('timestamp')
        node.text = datetime.datetime.utcnow().strftime('%Y-%m-%dT%H:%M:%S.%f')[:-5] + "Z"
        parentNode.append(currentPerformanceDataList)

        currentPerformanceDataList = copy.deepcopy(savedNode)
        node = currentPerformanceDataList.find('scanner-id')
        node.text = "2"
        node = currentPerformanceDataList.find('granularity-period')
        node.text = "period-24hours"
        node = currentPerformanceDataList.find('suspect-interval-flag')
        node.text = "false"
        node = currentPerformanceDataList.find('timestamp')
        node.text = datetime.datetime.utcnow().strftime('%Y-%m-%dT%H:%M:%S.%f')[:-5] + "Z"
        parentNode.append(currentPerformanceDataList)

    def addHistoricalPerformancesXmlValues(self, parentNode):
        histPerfDataList = parentNode.find('historical-performance-data-list')
        savedNode = copy.deepcopy(histPerfDataList)
        parentNode.remove(histPerfDataList)

        for i in range(0,96):
            self.addHistoricalPerformances15minutes(parentNode, savedNode, i)

        for i in range(0,7):
            self.addHistoricalPerformances24hours(parentNode, savedNode, i)

    def addHistoricalPerformances15minutes(self, parentNode, savedNode, index):
        histPerfDataList = copy.deepcopy(savedNode)
        timeNow = datetime.datetime.utcnow()

        node = histPerfDataList.find('history-data-id')
        node.text = str(index)
        node = histPerfDataList.find('granularity-period')
        node.text = "period-15min"
        node = histPerfDataList.find('suspect-interval-flag')
        node.text = "false"
        node = histPerfDataList.find('period-end-time')
        timestamp = timeNow - datetime.timedelta(minutes=15*index)
        node.text = timestamp.strftime('%Y-%m-%dT%H:%M:%S.%f')[:-5] + "Z"

        parentNode.append(histPerfDataList)

    def addHistoricalPerformances24hours(self, parentNode, savedNode, index):
        histPerfDataList = copy.deepcopy(savedNode)
        timeNow = datetime.datetime.utcnow()

        node = histPerfDataList.find('history-data-id')
        node.text = str(index + 96)
        node = histPerfDataList.find('granularity-period')
        node.text = "period-24hours"
        node = histPerfDataList.find('suspect-interval-flag')
        node.text = "false"
        node = histPerfDataList.find('period-end-time')
        timestamp = timeNow - datetime.timedelta(days=1*index)
        node.text = timestamp.strftime('%Y-%m-%dT%H:%M:%S.%f')[:-5] + "Z"

        parentNode.append(histPerfDataList)

    def buildXmlFiles(self):
        self.buildCoreModelConfigXml()
        self.buildMicrowaveModelXml()

        self.buildCoreModelStatusXml()
        self.buildMicrowaveModelStatusXml()


class PhyEthInterface:

    def __init__(self, intfUuid, interfaceId, ofPort, neObj, phyPortRef):
        self.IP = None
        self.uuid = intfUuid
        self.id = interfaceId
        self.ofPort = ofPort
        self.type = 'phy'

        self.neObj = neObj
        self.prefixName = 'eth-'
        self.interfaceName = self.prefixName + str(self.uuid)

        self.emEnv = wireless_emulator.emulator.Emulator()

        self.phyPortRef = phyPortRef

        self.MAC = self.emEnv.macAddressFactory.generateMacAddress(self.neObj.getNeId(), self.id)
        if self.MAC is None:
            logger.critical("No MAC Address created for NE=%s and interface=%s",
                            self.neObj.getNeUuid(), self.uuid)

        self.ipInterfaceNetwork = self.emEnv.intfIpFactory.getFreeInterfaceIp()

        firstIpOfLink = str(self.ipInterfaceNetwork[1])

        self.IP = firstIpOfLink

        logger.debug("PhyEthInterface object having name=%s, ofPort=%d and IP=%s was created",
                     self.interfaceName, self.ofPort, self.IP)

    def getIpAddress(self):
        return self.IP

    #maybe we should pass also the ipInterfaceNetwork ??
    def setIpAddress(self, IP):
        if self.IP is not None:
            self.emEnv.intfIpFactory.returnBackUnusedIp(self.ipInterfaceNetwork)
            self.IP = IP
        else:
            self.IP = IP

    def getInterfaceUuid(self):
        return self.uuid

    def getInterfaceName(self):
        return self.interfaceName

    def getNeName(self):
        return self.neObj.dockerName

    def getMacAddress(self):
        return self.MAC

    def buildCoreModelConfigXml(self):
        neNode = self.neObj.networkElementXmlNode
        ltpNode = copy.deepcopy(self.neObj.ltpXmlNode)
        uuid = ltpNode.find('core-model:uuid', self.neObj.namespaces)
        ltpUuid = "ltp-" + self.interfaceName
        uuid.text = ltpUuid
        addCoreDefaultValuesToNode(ltpNode, ltpUuid, self.neObj.namespaces)

        lpNode = ltpNode.find('core-model:lp', self.neObj.namespaces)
        uuid = lpNode.find('core-model:uuid', self.neObj.namespaces)
        lpUuid = "lp-" + self.interfaceName
        uuid.text = lpUuid
        layerProtocolName = lpNode.find('core-model:layer-protocol-name', self.neObj.namespaces)
        layerProtocolName.text = self.prefixName[:-1].upper()
        addCoreDefaultValuesToNode(lpNode, lpUuid, self.neObj.namespaces)

        physicalPortRef = ltpNode.find('core-model:physical-port-reference', self.neObj.namespaces)
        physicalPortRef.text = self.phyPortRef

        neNode.append(ltpNode)

    def buildCoreModelStatusXml(self):
        neStatusNode = self.neObj.neStatusXmlNode

        ltpNode = copy.deepcopy(self.neObj.ltpStatusXmlNode)
        uuid = ltpNode.find('uuid')
        ltpUuid = "ltp-" + self.interfaceName
        uuid.text = ltpUuid
        addCoreDefaultStatusValuesToNode(ltpNode)

        lpNode = ltpNode.find('lp')
        uuid = lpNode.find('uuid')
        lpUuid = "lp-" + self.interfaceName
        uuid.text = lpUuid
        addCoreDefaultStatusValuesToNode(lpNode)

        neStatusNode.append(ltpNode)

    def buildXmlFiles(self):
        self.buildCoreModelConfigXml()

        self.buildCoreModelStatusXml()
