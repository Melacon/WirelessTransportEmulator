import os
import logging
import copy

from wireless_emulator.clean import cleanup

logger = logging.getLogger(__name__)

class Singleton(type):
    _instances = {}
    def __call__(cls, *args, **kwargs):
        if cls not in cls._instances:
            cls._instances[cls] = super(Singleton, cls).__call__(*args, **kwargs)
        return cls._instances[cls]

def ensureRoot():
    if os.getuid() != 0:
        print("##### WirelessTransportEmulator should be run as root #####")
        exit(1)

def printErrorAndExit():
    print("#### There were errors when starting the emulator. Stopping...\n")
    exit(1)

def addCoreDefaultValuesToNode(node, uuidValue, namespaces, neObj=None):
    uuid = node.find('core-model:uuid', namespaces)
    uuid.text = uuidValue
    elem = node.find('core-model:local-id/core-model:value-name', namespaces)
    elem.text = "vLocalId"
    elem = node.find('core-model:local-id/core-model:value', namespaces)
    elem.text = uuidValue
    elem = node.find('core-model:name/core-model:value-name', namespaces)
    elem.text = "vName"
    elem = node.find('core-model:name/core-model:value', namespaces)
    elem.text = uuidValue
    elem = node.find('core-model:label/core-model:value-name', namespaces)
    elem.text = "vLabel"
    elem = node.find('core-model:label/core-model:value', namespaces)
    elem.text = uuidValue

    if neObj is not None:
        addCustomNeExtensions(neObj, node, namespaces)
    else:
        elem = node.find('core-model:extension/core-model:value-name', namespaces)
        elem.text = "vExtension"
        elem = node.find('core-model:extension/core-model:value', namespaces)
        elem.text = uuidValue

    elem = node.find('core-model:administrative-control', namespaces)
    elem.text = "UNLOCK"
    elem = node.find('core-model:lifecycle-state', namespaces)
    elem.text = "INSTALLED"

def addCoreDefaultStatusValuesToNode(node):
    operState = node.find('operational-state')
    operState.text = "ENABLED"
    adminState = node.find('administrative-state')
    adminState.text = "UNLOCKED"

def addCustomNeExtensions(neObj, node, namespaces):
    extensionNode = node.find('core-model:extension', namespaces)
    savedNode = copy.deepcopy(extensionNode)
    node.remove(extensionNode)

    extensionNode = copy.deepcopy(savedNode)
    valName = extensionNode.find('core-model:value-name', namespaces)
    valName.text = "rootEquipment"
    value = extensionNode.find('core-model:value', namespaces)
    value.text = "outdoorUnit, indoorUnit"
    node.append(extensionNode)

    extensionNode = copy.deepcopy(savedNode)
    valName = extensionNode.find('core-model:value-name', namespaces)
    valName.text = "neIpAddress"
    value = extensionNode.find('core-model:value', namespaces)
    value.text = neObj.managementIPAddressString
    node.append(extensionNode)

    extensionNode = copy.deepcopy(savedNode)
    valName = extensionNode.find('core-model:value-name', namespaces)
    valName.text = "neType"
    value = extensionNode.find('core-model:value', namespaces)
    value.text = "Milkyway"
    node.append(extensionNode)

    extensionNode = copy.deepcopy(savedNode)
    valName = extensionNode.find('core-model:value-name', namespaces)
    valName.text = "webUri"
    value = extensionNode.find('core-model:value', namespaces)
    value.text = "https://" + neObj.managementIPAddressString + "/"
    node.append(extensionNode)

    extensionNode = copy.deepcopy(savedNode)
    valName = extensionNode.find('core-model:value-name', namespaces)
    valName.text = "cliAddress"
    value = extensionNode.find('core-model:value', namespaces)
    value.text = "cli@" + neObj.managementIPAddressString
    node.append(extensionNode)

    extensionNode = copy.deepcopy(savedNode)
    valName = extensionNode.find('core-model:value-name', namespaces)
    valName.text = "appCommand"
    value = extensionNode.find('core-model:value', namespaces)
    value.text = ""
    node.append(extensionNode)