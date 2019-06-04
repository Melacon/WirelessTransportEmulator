package net.i2cat.netconf.server.cli;

import net.i2cat.netconf.rpc.RPCElement;
import net.i2cat.netconf.server.Console;
import net.i2cat.netconf.server.ServerSimulator;

public class SimulatorCommandlineInterpreter implements CommandLineProcessor {

    private String info = "";
    private ServerSimulator server = null;

    private boolean quit = false;
    private Console console;

    public SimulatorCommandlineInterpreter(String info) {
        this.info = info;
    }

    public SimulatorCommandlineInterpreter setServer(ServerSimulator server, Console console) {
        this.server = server;
        this.console = console;
        return this;
    }

    @Override
    public void doExecute(String command) {

        if (command == null || command.isEmpty()) {
            //Do nothing
        } else if (command.startsWith("h")) {
            console.cliOutput("NETCONF Simulator "+server.getVersion());
            console.cliOutput("Available commands: status, quit, info, list, size, n[ZZ | l | x | dZZ]");
            console.cliOutput("\tnl: list available notifications");
            console.cliOutput("\tnZZ: send notification with number ZZ");
            console.cliOutput("\tnx: list internal XML doc tree");
            console.cliOutput("\tndZZ: Introduce delay of ZZ seconds before answer is send to next get-message");
            console.cliOutput("\tndl: list actual delay and pattern");
            console.cliOutput("\tndp??: set tag filter regex pattern (.* = any)");
            console.cliOutput("\tndn: Discard next get message.");
            console.cliOutput("\tntZZ M S: send notification ZZ for M times every S seconds");
            console.cliOutput("\tntrZZ M S: send notification ZZ for M times every S seconds with randomized delay");
            console.cliOutput("\tnt: provide status");
            console.cliOutput("\tntx: stop execution");
        } else if (command.equals("info")) {
            console.cliOutput(info);
        } else if (command.equals("status")) {
            console.cliOutput("Status: not implemented");
        } else if (server == null) {
            console.cliOutput("Server not running jet");
        } else {
            if (command.equals("list")) {
                console.cliOutput("Messages received(" + server.getStoredMessages().size() + "):");
                for (RPCElement rpcElement : server.getStoredMessages()) {
                    console.cliOutput("#####  BEGIN message #####\n" +
                            rpcElement.toXML() + '\n' +
                            "#####   END message  #####");
                }
            } else if (command.equals("size")) {
                console.cliOutput("Messages received(" + server.getStoredMessages().size() + "):");

            } else if (command.equals("quit")) {
                console.cliOutput("Stop server");
                server.stopServer();
                quit = true;
            } else if (command.startsWith("n")) {
                String notifyCommand = command.substring(1);
                console.cliOutput("User command: "+notifyCommand);
                server.notify(notifyCommand);
                console.cliOutput("Unknown command '"+command+"' (h for help)");
            }
        }
    }

    @Override
    public boolean isQuit() {
        return quit;
    }

    @Override
    public void doWelcome() {
        console.cliOutput(info);
    }

}
