import logging
import subprocess
import copy
import datetime

import wireless_emulator.emulator
from wireless_emulator.utils import addCoreDefaultValuesToNode, addCoreDefaultStatusValuesToNode

logger = logging.getLogger(__name__)

class Interface:

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
        self.prefixName = None
        self.interfaceName = None

        self.emEnv = wireless_emulator.emulator.Emulator()

        self.MAC = self.emEnv.macAddressFactory.generateMacAddress(self.neObj.getNeId(), self.id)
        if self.MAC is None:
            logger.critical("No MAC Address created for NE=%s and interface=%s",
                            self.neObj.getNeUuid(), self.uuid)

        self.radioSignalId = self.findRadioSignalId()

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

        lpNode = ltpNode.find('core-model:lp', self.neObj.namespaces)
        uuid = lpNode.find('core-model:uuid', self.neObj.namespaces)
        lpUuid = "lp-" + self.interfaceName
        uuid.text = lpUuid
        layerProtocolName = lpNode.find('core-model:layer-protocol-name', self.neObj.namespaces)
        layerProtocolName.text = self.prefixName[:-1]
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

        airInterface = copy.deepcopy(self.neObj.airInterfacePacXmlNode)
        lpUuid = "lp-" + self.interfaceName

        layerProtocol = airInterface.find('microwave-model:layer-protocol', self.neObj.namespaces)
        layerProtocol.text = lpUuid

        airInterfaceConfig = airInterface.find('microwave-model:air-interface-configuration', self.neObj.namespaces)

        if self.radioSignalId is not None:
            radioSignalId = airInterfaceConfig.find('microwave-model:radio-signal-id', self.neObj.namespaces)
            radioSignalId.text = self.radioSignalId

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

        seqNum = airInterface.find(
            'air-interface-current-problems/current-problem-list/sequence-number')
        seqNum.text = "1"

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


class MwpsInterface(Interface):

    def __init__(self, intfUuid, interfaceId, ofPort, neObj, supportedAlarms):
        Interface.__init__(self, intfUuid, interfaceId, ofPort, neObj, supportedAlarms)
        self.prefixName = 'mwps-'
        self.interfaceName = self.prefixName + str(self.uuid)
        logger.debug("MwpsInterface object having name=%s, ofPort=%d and IP=%s was created",
                     self.interfaceName, self.ofPort, self.IP)

class MwsInterface(Interface):

    def __init__(self, intfUuid, interfaceId, ofPort, neObj, supportedAlarms):
        Interface.__init__(self, intfUuid, interfaceId, ofPort, neObj, supportedAlarms)
        self.prefixName = 'mws-'
        self.interfaceName = self.prefixName + str(self.uuid)
        logger.debug("MwsInterface object having name=%s, ofPort=%d and IP=%s was created",
                     self.interfaceName, self.ofPort, self.IP)

class EthInterface(Interface):

    def __init__(self, intfUuid, interfaceId, ofPort, neObj, supportedAlarms):
        Interface.__init__(self, intfUuid, interfaceId, ofPort, neObj, supportedAlarms)
        self.prefixName = 'eth-'
        self.interfaceName = self.prefixName + str(self.uuid)
        logger.debug("EthInterface object having name=%s, ofPort=%d and IP=%s was created",
                     self.interfaceName, self.ofPort, self.IP)
