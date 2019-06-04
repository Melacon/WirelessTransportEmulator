package net.i2cat.netconf.server.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.i2cat.netconf.server.util.Util;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;

/**
 * Command line for local keyboard and remote SSH login
 */
public class CommandLineControl {

    private static final Log LOG = LogFactory.getLog(CommandLineControl.class);

    private final CommandLineProcessor commandLineProcessor;
    private final Queue<String> cmdQueue = new ConcurrentLinkedQueue<>();
    private final Queue<CommandSshConnection> connections = new ConcurrentLinkedQueue<>();

    private SshServer sshd;
    private BufferedReader consoleInputStreamReader = null;

    public CommandLineControl(String host, int port, CommandLineProcessor commandLineProcessor) throws IOException, InterruptedException {

        this.commandLineProcessor = commandLineProcessor;
        this.consoleInputStreamReader = new BufferedReader(new InputStreamReader(System.in));

        sshd = null;
        if (port > 1024) {
            System.out.println("Start ssh cli on port "+port);
            sshd = SshServer.setUpDefaultServer();
            sshd.setHost(host);
            sshd.setPort(port);
            sshd.setPasswordAuthenticator((username, password, session) -> true);
            sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());
            sshd.setShellFactory(() -> new CommandSshConnection(connections, cmdQueue, commandLineProcessor));
            sshd.start();
        } else {
            System.out.println("ssh cli disabled");
        }

        Thread thread2 = new Thread (() -> {
            readLineFromConsole();
        });
        thread2.start();
    }

    public void doProcessCommandLine() {
        System.out.println("Running");

        String cmdLine;
        while (true) {
            if (cmdQueue.isEmpty()) {
                Util.sleepMilliseconds(1000);
            } else {
                cmdLine = cmdQueue.poll();
                commandLineProcessor.doExecute(cmdLine);
                if (commandLineProcessor.isQuit()) {
                    break;
                }
            }
        };
    }

    public void println(String outputMessage) {
        for (CommandSshConnection connection : connections) {
            connection.println(outputMessage);
        }
    }

    /**
     * @return true indicates that ssh is connected
     */
    public boolean isConnected() {
        return !connections.isEmpty();
    }

    private void readLineFromConsole() {
        String command;
        while (true) {
            try {
                command = consoleInputStreamReader.readLine();
                if (command == null || command.isEmpty()) {
                    Util.sleepMilliseconds(500);
                } else {
                    cmdQueue.add(command);
                }
            } catch (IOException e) {
                LOG.warn(e.getMessage());
            }
        }
    }

}
