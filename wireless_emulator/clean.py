import logging
import subprocess
import json
from wireless_emulator.odlregistration import unregisterNeFromOdl, unregisterNeFromOdlNewVersion
import wireless_emulator.emulator

logger = logging.getLogger(__name__)

def cleanup(configFileName = None):

    dockerNames = getDockerNames()
    dockerNetworks = getDockerNetworks()

    stopAndRemoveDockerContainers(dockerNames)
    removeDockerNetworks(dockerNetworks)

    removeLinkBridges()

    if configFileName is not None:
        try:
            with open(configFileName) as json_data:
                configJson = json.load(json_data)
                autoReg = configJson['automatic-odl-registration']
                if autoReg is True:
                    controllerInfo = configJson['controller']
                    unregisterNesFromOdl(controllerInfo, dockerNames)
        except IOError as err:
            logger.critical("Could not open configuration file=%s", configFileName)
            logger.critical("I/O error({0}): {1}".format(err.errno, err.strerror))

    print("All cleaned up!")
    return True

#TODO add support for new docker container
def getDockerNames():
    dockerNamesList = []

    stringCmd = "docker ps -a | grep openyuma | awk '{print $NF}'"

    cmd = subprocess.Popen(stringCmd, shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)

    for line in cmd.stderr:
        strLine = line.decode("utf-8").rstrip('\n')
        logger.critical("Could not get names of docker containers having image openyuma.\n Stderr: %s", strLine)
        raise RuntimeError("Could not get docker container names")

    for line in cmd.stdout:
        strLine = line.decode("utf-8").rstrip('\n')
        dockerNamesList.append(strLine)

    #Changed from javasimulator to netconfserversimulator
    stringCmd = "docker ps -a | grep "+dockerName+" | awk '{print $NF}'"

    cmd = subprocess.Popen(stringCmd, shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)

    for line in cmd.stderr:
        strLine = line.decode("utf-8").rstrip('\n')
        logger.critical("Could not get names of docker containers having image "+dockerName+".\n Stderr: %s", strLine)
        raise RuntimeError("Could not get docker container names")

    for line in cmd.stdout:
        strLine = line.decode("utf-8").rstrip('\n')
        dockerNamesList.append(strLine)

    return dockerNamesList


def getDockerNetworks():
    dockerNetworksList = []

    stringCmd = "docker network ls | grep wte_net | awk '{print $2}'"

    cmd = subprocess.Popen(stringCmd, shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)

    for line in cmd.stderr:
        strLine = line.decode("utf-8").rstrip('\n')
        logger.critical("Could not get names of docker networks having names oywe.\n Stderr: %s", strLine)
        raise RuntimeError("Could not get docker networks")

    for line in cmd.stdout:
        strLine = line.decode("utf-8").rstrip('\n')
        dockerNetworksList.append(strLine)

    return dockerNetworksList

def stopAndRemoveDockerContainers(dockerNames):
    for container in dockerNames:
        print("Stopping docker container %s" % container)
        stringCmd = "docker stop %s" % (container)

        cmd = subprocess.Popen(stringCmd, shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)

        for line in cmd.stderr:
            strLine = line.decode("utf-8").rstrip('\n')
            logger.critical("Could not stop docker container %s\n Stderr: %s", container, strLine)
            raise RuntimeError("Could not stop docker container %s" % container)

        print("Removing docker container %s" % container)
        stringCmd = "docker rm %s" % (container)

        cmd = subprocess.Popen(stringCmd, shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)

        for line in cmd.stderr:
            strLine = line.decode("utf-8").rstrip('\n')
            logger.critical("Could not remove docker container %s\n Stderr: %s", container, strLine)
            raise RuntimeError("Could not remove docker container ")

def removeDockerNetworks(dockerNetworks):
    for network in dockerNetworks:
        print("Removing docker network %s" % network)
        stringCmd = "docker network rm %s" % (network)

        cmd = subprocess.Popen(stringCmd, shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)

        for line in cmd.stderr:
            strLine = line.decode("utf-8").rstrip('\n')
            logger.critical("Could not remove docker network %s\n Stderr: %s", network, strLine)
            raise RuntimeError("Could not remove docker network %s", network)

def removeLinkBridges():
    cmd = subprocess.Popen('ovs-vsctl list-br | grep -i oywe-br', shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)

    for line in cmd.stdout:
        bridge = line.decode("utf-8").rstrip('\n')
        stringCmd = "ovs-vsctl del-br %s" % (bridge)
        print("Removing OVS bridge %s" % bridge)
        cmd = subprocess.Popen(stringCmd, shell=True, stdout=subprocess.PIPE,
                               stderr=subprocess.PIPE)
        logger.info("Bridge %s deleted!", bridge)
        print("Bridge %s deleted..." % bridge)

def unregisterNesFromOdl(controllerInfo, neNamesList):

    if controllerInfo is None or \
        controllerInfo['ip-address'] is None or \
        controllerInfo['port'] is None or \
        controllerInfo['username'] is None or \
        controllerInfo['password'] is None:

        return True

    for uuid in neNamesList:
        try:
            unregisterNeFromOdlNewVersion(controllerInfo, uuid)
            # unregisterNeFromOdl(controllerInfo, uuid)
        except RuntimeError:
            print("Failed to unregister NE=%s from ODL controller" % uuid)
