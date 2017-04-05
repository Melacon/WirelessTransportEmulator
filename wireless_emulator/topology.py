import logging

from wireless_emulator.link import Link

logger=logging.getLogger(__name__)

class Topology:

    def __init__(self, topologyDescription, topologyLayer):
        self.topologyDescription = topologyDescription

        self.linkList = []

        self.topologyLayer = topologyLayer
        logger.debug("Topology object was created")

    def buildTopology(self):

        if self.topologyLayer == 'mwps':
            for link in self.topologyDescription['links']:
                logger.debug("Creating link...")
                linkObj = Link(link)
                linkObj.addLink()
                self.linkList.append(linkObj)
                logger.debug("Link added to linkList...")
        elif self.topologyLayer == 'eth':
            for link in self.topologyDescription['links']:
                logger.debug("Creating link...")
                linkObj = Link(link)
                linkObj.addLinkWithIp()
                self.linkList.append(linkObj)
                logger.debug("Link added to linkList...")

    def isInterfaceObjPartOfLink(self, intfObj):

        for link in self.linkList:
            if link.isIntfPartOfLink(intfObj) is True:
                return True
        return False