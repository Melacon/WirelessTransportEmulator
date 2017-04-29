import ipaddress
import logging

logger = logging.getLogger(__name__)

class ManagementNetworkIPFactory:

    def __init__(self, preferedNetwork):
        self.freeNetworkIpList = list(ipaddress.ip_network(preferedNetwork).subnets(new_prefix=30))

        logger.debug("ManagementNetworkIPFactory was initialized with Network IP %s "
                     "and has %d free management IP addresses", preferedNetwork, len(self.freeNetworkIpList))

    def getFreeManagementNetworkIP(self):
        if len(self.freeNetworkIpList) > 0:
            return self.freeNetworkIpList.pop(0)
        else:
            logger.critical("No more free Network IP addresses left!")
            return None

class InterfaceIPFactory:

    def __init__(self, preferedNetwork):
        self.freeInterfaceIpList = list(ipaddress.ip_network(preferedNetwork).hosts())
        self.netmask = ipaddress.ip_network(preferedNetwork).netmask
        logger.debug("InterfaceIPFactory was initialized with Network IP %s "
                     "and has %d free management IP addresses", preferedNetwork, len(self.freeInterfaceIpList))

    def getFreeInterfaceIp(self):
        if len(self.freeInterfaceIpList) > 0:
            return self.freeInterfaceIpList.pop(0)
        else:
            logger.critical("No more free Interface IP addresses left!")
            return None

    def returnBackUnusedIp(self, ipNetworkAddress):
        self.freeInterfaceIpList.append(ipNetworkAddress)

    def getNumberOfFreeInterfaceIpAddresses(self):
        return len(self.freeInterfaceIpList)


class MacAddressFactory:

    def __init__(self):
        self.generatedAddresses = []

    def generateMacAddress(self, neId, portId):
        logger.debug("Generating MAC address for neId=%d and portId=%d", neId, portId)
        #generate 5 bytes of MAC address from neID
        s = hex(neId).lstrip('0x').zfill(10)
        neMacStr = ':'.join([s[i:i+2] for i in range(0, len(s), 2)])
        #generate 1 byte of MAC address from portId
        portMacStr = hex(portId).lstrip('0x').zfill(2)
        #concatenate for full MAC address
        macAddress = neMacStr + ":" + portMacStr
        logger.debug("Generated MAC address: %s", macAddress)
        if macAddress in self.generatedAddresses:
            logger.error("Generated MAC address %s for neId=%d and portId=%d already exists!",
                         macAddress, neId, portId)
            return None
        else:
            self.generatedAddresses.append(macAddress)
            return macAddress
