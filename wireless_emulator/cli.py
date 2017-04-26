from cmd import Cmd
import sys
from select import poll, POLLIN
import string
from subprocess import call

from wireless_emulator import *
from wireless_emulator.clean import cleanup

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
