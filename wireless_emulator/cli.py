from cmd import Cmd
import sys
from select import poll, POLLIN
import string
from subprocess import call
import psutil, os, time
from timeit import default_timer as timer
from multiprocessing import Process, Manager
import multiprocessing

from wireless_emulator import *
from wireless_emulator.clean import cleanup
from wireless_emulator.odlregistration import registerNeToOdlNewVersion, unregisterNeFromOdlNewVersion

class CLI(Cmd):
    prompt = 'WirelessTransportEmulator>'
    identchars = string.ascii_letters + string.digits + '_' + '-'

    def __init__(self, emulator, stdin=sys.stdin):
        self.emulator = emulator
        self.inPoller = poll()
        self.inPoller.register(stdin)
        Cmd.__init__(self)
        print( '*** Starting CLI:\n' )

        self.run()

    def run(self):
        while True:
            try:
                # Make sure no nodes are still waiting
                self.cmdloop()
                break
            except KeyboardInterrupt:
                # Output a message - unless it's also interrupted
                # pylint: disable=broad-except
                try:
                    print( '\nKeyboard interrupt. Use quit or exit to shotdown the emulator.\n' )
                except Exception:
                    pass

    def default(self, line):
        """Called on an input line when the command prefix is not recognized.
                   Overridden to run shell commands when a node is the first
                   CLI argument.  Past the first CLI argument, node names are
                   automatically replaced with corresponding IP addrs."""

        first, args, line = self.parseline(line)
        node = self.emulator.getNeByName(first)

        if node is not None:
            rest = args.split(' ')
            node.executeCommandInContainer(args)
        else:
            print('Node %s not found' % first)

    def emptyline( self ):
        "Don't repeat last command when you hit return."
        pass

    def do_exit(self, _line):
        "Exit"
        cleanup(self.emulator.configFileName)
        return 'exited by user command'

    def do_quit(self, line):
        "Exit"
        return self.do_exit(line)

    def do_print_nodes(self, _line):
        "Prints the names of all the Network Elements emulated"
        print('Available NEs are:')
        for neObj in self.emulator.networkElementList:
            print('%s' % neObj.uuid)

    def do_print_node_info(self, line):
        "Prints the information of the specified Network Element"
        args = line.split()
        if len(args) != 1:
            print('ERROR: usage: print_node_info <NE_UUID>')
            return

        node = self.emulator.getNeByName(args[0])

        if node is not None:
            print('#########################################')
            print('#### Network Element UUID: \'%s\'' % node.uuid)
            print('#### Network Element management IP: %s' % node.managementIPAddressString)
            print('#### Network Element port: %s' % node.netconfPortNumber)
            print('########### Interfaces: ###########')
            for intf in node.interfaceList:
                print('Interface: UUID=\'%s\' having type=%s Linux Interface Name=\'%s\'' %
                      (intf.uuid, intf.layer, intf.interfaceName))
            print('#########################################')
        else:
            print('Node %s not found' % args[0])

    def do_dump_nodes(self, _line):
        "Dumps the information about all of the available Network Elements"
        for node in self.emulator.networkElementList:
            print('#########################################')
            print('#### Network Element UUID: \'%s\'' % node.uuid)
            print('#### Network Element management IP: %s' % node.managementIPAddressString)
            print('#### Network Element port: %s' % node.netconfPortNumber)
            print('########### Interfaces: ###########')
            for intf in node.interfaceList:
                print('Interface: UUID=\'%s\' having type=%s Linux Interface Name=\'%s\'' %
                      (intf.uuid, intf.layer, intf.interfaceName))
        print('#########################################')

    def do_dump_links(self, _line):
        "Dumps the links available in the network"
        for topo in self.emulator.topologies:
            print('#################### %s #####################' % topo.topologyLayer)
            for link in topo.linkList:
                print('## Link=%d ## \'%s\': \'%s\' <-------> \'%s\':\'%s\'' %
                      (link.linkId, link.interfacesObj[0].getNeName(), link.interfacesObj[0].getInterfaceName(),
                       link.interfacesObj[1].getInterfaceName(), link.interfacesObj[1].getNeName()))
            print('#########################################')

    def do_xterm(self, line):
        "Starts an xterm inside the specified Network Element"
        args = line.split(' ')
        if len(args) == 0:
            print('ERROR: usage: xterm <list_of_ne_uuids>')
            return

        for arg in args:
            node = self.emulator.getNeByName(arg)

            if node is not None:
                command = 'xterm -xrm \'XTerm.vt100.allowTitleOps: false\' -T %s -e \"docker exec -it %s /bin/bash\"' % \
                          (node.dockerName, node.dockerName)
                self.emulator.executeCommandInOSNoReturn(command)
            else:
                print('ERROR: Node %s not found' % arg)
                print('Usage: xterm <list_of_ne_uuids>')

    def do_mount(self, line):
        "Triggers a mount command in ODL for the specified NEs"
        args = line.split(' ')
        if len(args) != 1:
            print('ERROR: usage: mount <all> for mounting all NEs')
            print('ERROR: usage: mount <NE_uuid> for mounting a single network element')
            return

        nodeUuid = args[0]

        if nodeUuid == 'all':
            for node in self.emulator.networkElementList:
                if node is not None:
                    try:
                        registerNeToOdlNewVersion(self.emulator.controllerInfo, node.uuid,
                                                  node.managementIPAddressString,
                                                  node.netconfPortNumber)
                    except:
                        print("Failed to register NE=%s having IP=%s and port=%s to the ODL controller" %
                              (node.uuid, node.managementIPAddressString, node.netconfPortNumber))
                else:
                    print('ERROR: Node %s not found' % node)
                    print('ERROR: usage: mount <all> for mounting all NEs')
                    print('ERROR: usage: mount <NE_uuid> for mounting a single network element')
        else:
            node = self.emulator.getNeByName(nodeUuid)

            if node is not None:
                try:
                    registerNeToOdlNewVersion(self.emulator.controllerInfo, node.uuid, node.managementIPAddressString,
                                              node.netconfPortNumber)
                except:
                    print("Failed to register NE=%s having IP=%s and port=%s to the ODL controller" %
                          (node.uuid, node.managementIPAddressString, node.netconfPortNumber))
            else:
                print('ERROR: Node %s not found' % node)
                print('ERROR: usage: mount <all> for mounting all NEs')
                print('ERROR: usage: mount <NE_uuid> for mounting a single network element')

    def do_unmount(self, line):
        "Triggers an unmount command in ODL for the specified NEs"
        args = line.split(' ')
        if len(args) != 1:
            print('ERROR: usage: unmount <all> for unmounting all NEs')
            print('ERROR: usage: unmount <NE_uuid> for unmounting a single network element')
            return

        nodeUuid = args[0]

        if nodeUuid == 'all':
            for node in self.emulator.networkElementList:
                if node is not None:
                    try:
                        unregisterNeFromOdlNewVersion(self.emulator.controllerInfo, node.uuid)
                    except:
                        print("Failed to unregister NE=%s having IP=%s and port=%s from the ODL controller" %
                              (node.uuid, node.managementIPAddressString, node.netconfPortNumber))
                else:
                    print('ERROR: Node %s not found' % node)
                    print('ERROR: usage: unmount <all> for unmounting all NEs')
                    print('ERROR: usage: unmount <NE_uuid> for unmounting a single network element')
        else:
            node = self.emulator.getNeByName(nodeUuid)

            if node is not None:
                try:
                    unregisterNeFromOdlNewVersion(self.emulator.controllerInfo, node.uuid)
                except:
                    print("Failed to unregister NE=%s having IP=%s and port=%s from the ODL controller" %
                          (node.uuid, node.managementIPAddressString, node.netconfPortNumber))
            else:
                print('ERROR: Node %s not found' % node)
                print('ERROR: usage: unmount <all> for unmounting all NEs')
                print('ERROR: usage: unmount <NE_uuid> for unmounting a single network element')

    def do_print_resource_usage(self, line):
        "Compute the average amount of resources (CPU and Memory) consumed by the WTE in a specified interval"
        memory_percent = 0.0
        args = line.split()

        start = timer()

        interval = 10
        if len(args) > 0:
            interval = int(args[0])

        print("Computing CPU and memory usage by taking %d samples. Please wait..." % interval)

        # processes = []
        # results = Manager().dict()
        # for i, ne in enumerate(self.emulator.networkElementList):
        #     p = Process(target=ne.getCpuUsage, args=(interval, i, results))
        #     processes.append(p)
        #     p.start()
        # for p in processes:
        #     p.join()

        cpu_percent = 0.0
        for i in range(0, interval):
            cpu_percent += self.emulator.getCpuUsage()

        cpu_percent /= float(interval)

        cpu_percent /= float(multiprocessing.cpu_count())

        # for ne in self.emulator.networkElementList:
        #     cmd = "ps -g `docker inspect -f '{{.State.Pid}}' %s` --no-headers -o \"pmem\"" % ne.dockerName
        #     output = self.emulator.executeCommandAndGetResultInOS(cmd)
        #     for line in output:
        #         memory_percent += float(line)

        memory_percent = self.emulator.getMemUsage()

        end = timer()

        print('CPU Usage: %2.2f%%' % cpu_percent)
        print('Memory usage: %2.2f%%' % memory_percent)
        print('Command took %6.3f seconds' % (end - start))
