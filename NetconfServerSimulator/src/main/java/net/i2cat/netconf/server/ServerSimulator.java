/**
 * Main Netconf server class allowing to create a test Netconf server
 * containing a simulated network element, defined by an XML-file.
 *
 * Bases on Server implementation of Julio Carlos Barrera
 *
 * @author herbert.eiselt@highstreet-technologies.com
 */

package net.i2cat.netconf.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.xpath.XPathExpressionException;
import net.i2cat.netconf.rpc.RPCElement;
import net.i2cat.netconf.server.exceptions.ServerException;
import net.i2cat.netconf.server.netconf.control.NetconfNotifyExecutor;
import net.i2cat.netconf.server.netconf.control.NetconfNotifyOriginator;
import net.i2cat.netconf.server.netconf.control.NetconfSubsystem;
import net.i2cat.netconf.server.netconf.types.NetworkElement;
import net.i2cat.netconf.server.ssh.AlwaysTruePasswordAuthenticator;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.RollingFileAppender;
import org.apache.sshd.SshServer;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.server.Command;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.xml.sax.SAXException;

public class ServerSimulator implements MessageStore, BehaviourContainer, NetconfNotifyOriginator {

    private static final Log    LOG                = LogFactory.getLog(ServerSimulator.class);

    private SshServer           sshd;

    // stored messages
    private boolean             storeMessages    = false;
    private List<RPCElement>    messages;

    // behaviours
    private List<Behaviour>     behaviours;

    private NetconfNotifyExecutor netconfNotifyExecutor = null;

    // hide default constructor, forcing using factory method
    private ServerSimulator() {
    }

    /**
     * Creates a server and store all received messages
     *
     * @param host
     *            host name (use null to listen in all interfaces)
     * @param listeningPort
     *            where the server will listen for SSH connections
     *
     */
    public static ServerSimulator createServer(String host, int listeiningPort, NetworkElement ne) throws IllegalArgumentException {
        if (ne == null) {
            throw new IllegalArgumentException(cliOutput("NetworkElement is null"));
        }

        ServerSimulator server = new ServerSimulator();
        server.messages = new ArrayList<RPCElement>();
        server.storeMessages = false;

        server.initializeServer(host, listeiningPort, ne);

        return server;
    }

    private void initializeServer(String host, int listeningPort, NetworkElement ne) {
        LOG.info(cliOutput("Configuring server..."));
        sshd = SshServer.setUpDefaultServer();
        sshd.setHost(host);
        sshd.setPort(listeningPort);

        LOG.info(cliOutput("Host: '" + host + "', listenig port: " + listeningPort));

        sshd.setPasswordAuthenticator(new AlwaysTruePasswordAuthenticator());
        sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(""));

        List<NamedFactory<Command>> subsystemFactories = new ArrayList<NamedFactory<Command>>();
        subsystemFactories.add(NetconfSubsystem.Factory.createFactory(this, this, this, ne));
        sshd.setSubsystemFactories(subsystemFactories);
        LOG.info(cliOutput("Server configured."));
    }

    @Override
    public void defineBehaviour(Behaviour behaviour) {
        if (behaviours == null) {
            behaviours = new ArrayList<Behaviour>();
        }
        synchronized (behaviours) {
            behaviours.add(behaviour);
        }
    }

    @Override
    public List<Behaviour> getBehaviours() {
        if (behaviours == null) {
            return null;
        }
        synchronized (behaviours) {
            return behaviours;
        }
    }

    public void startServer() throws ServerException {
        LOG.info(cliOutput("Starting server..."));
        try {
            sshd.start();
        } catch (IOException e) {
            LOG.error(cliOutput("Error starting server!"+e.getMessage()));
            throw new ServerException("Error starting server", e);
        }
        LOG.info(cliOutput("Server started."));
    }

    public void stopServer() throws ServerException {
        LOG.info(cliOutput("Stopping server..."));
        try {
            sshd.stop();
        } catch (InterruptedException e) {
            LOG.error(cliOutput("Error stopping server!"+e));
            throw new ServerException("Error stopping server", e);
        }
        LOG.info(cliOutput("Server stopped."));
    }

    @Override
    public void storeMessage(RPCElement message) {
        if (messages != null) {
            synchronized (messages) {
                LOG.info("Storing message "+message.getMessageId());
                messages.add(message);
            }
        }
    }

    @Override
    public List<RPCElement> getStoredMessages() {
            synchronized (messages) {
                return Collections.unmodifiableList(messages);
            }
    }

    @Override
    public void setNetconfNotifyExecutor(NetconfNotifyExecutor executor) {
        this.netconfNotifyExecutor = executor;
    }

    private void notify(String command) {
        if (netconfNotifyExecutor != null) {
            netconfNotifyExecutor.notify(command);
        } else {
            System.out.println("No notifier registered.");
        }

    }

    private static void initDebug( String debugFilename ) {
        BasicConfigurator.configure();
        Logger.getRootLogger().getLoggerRepository().resetConfiguration();

        ConsoleAppender console = new ConsoleAppender(); //create appender
        //configure the appender
        String PATTERN = "%d [%p|%c|%C{1}] %m%n";
        console.setLayout(new PatternLayout(PATTERN));
        console.setThreshold(Level.WARN);
        console.activateOptions();
        //add appender to any Logger (here is root)
        Logger.getRootLogger().addAppender(console);

        RollingFileAppender fa = new RollingFileAppender();
        fa.setName("FileLogger");
        fa.setFile(debugFilename);
        fa.setLayout(new PatternLayout("%d %-5p [%c{1}] %m%n"));
        fa.setThreshold(Level.ALL);
        fa.setMaximumFileSize(1000000);
        fa.setAppend(true);

        fa.activateOptions();
        //add appender to any Logger (here is root)
        Logger.getRootLogger().addAppender(fa);
        //repeat with all other desired appenders

    }

    public static void main(String[] args) throws IOException {

        if (args.length < 3) {
            cliOutput("To less parameters. Command: Server xmlFilename port [pathToYang]");
            return;
        }

        int port = Integer.valueOf(args[1]);
        String xmlFilename = args[0];
        String debugFile = "debug"+String.valueOf(port) +".log";
        String yangPath = args.length >= 3 ? args[2] : "yang/yangNeModel";
        String uuid = args.length >= 4 ? args[3] : "";

        cliOutput("Start parameters are:");
        cliOutput("\tFilename: "+xmlFilename);
        cliOutput("\tPort: "+port);
        cliOutput("\tDebuginfo and communication is in file: "+debugFile);
        cliOutput("\tYang files in directory: "+yangPath);
        cliOutput("\tUuid: "+uuid);

        initDebug(debugFile);

        LOG.info(cliOutput("Netconf NE simulator\n"));

        NetworkElement ne;
        try {
            ne = new NetworkElement(xmlFilename, yangPath, uuid);
            ServerSimulator server = ServerSimulator.createServer("0.0.0.0", port, ne);
            server.startServer();

            // read lines form input
            BufferedReader buffer = new BufferedReader(new InputStreamReader(System.in));
            String command;

            while (true) {
                command = buffer.readLine().toLowerCase();

                if (command.equals("list")) {
                    cliOutput("Messages received(" + server.getStoredMessages().size() + "):");
                    for (RPCElement rpcElement : server.getStoredMessages()) {
                        cliOutput("#####  BEGIN message #####\n" +
                                rpcElement.toXML() + '\n' +
                                "#####   END message  #####");
                    }
                } else  if (command.equals("size")) {
                    cliOutput("Messages received(" + server.getStoredMessages().size() + "):");

                } else  if (command.equals("quit")) {
                    cliOutput("Stop server");
                    server.stopServer();
                    break;
                } else  if (command.equals("info")) {
                    cliOutput("Port: "+port+" File: "+xmlFilename);
                } else  if (command.equals("status")) {
                    cliOutput("Status: not implemented");
                } else if (command.startsWith("n")) {
                    String notifyCommand = command.substring(1);
                    cliOutput("Notification: "+notifyCommand);
                    server.notify(notifyCommand);
                } else {
                    cliOutput("NETCONF Simulator V3.0");
                    cliOutput("Available commands: status, quit, info, list, size, nZZ, nl, nx");
                    cliOutput("\tnx: list internal XML doc tree");
                    cliOutput("\tnl: list available notifications");
                    cliOutput("\tnZZ: send notification with number ZZ");
                }
            }
        } catch (SAXException e) {
            LOG.error(cliOutput("(..something..) failed"+e.getMessage()));
        } catch (ParserConfigurationException e) {
            LOG.error(cliOutput("(..something..) failed"+e.getMessage()));
        } catch (TransformerConfigurationException e) {
            LOG.error("(..something..) failed", e);
        } catch (ServerException e) {
            LOG.error("(..something..) failed", e);
        } catch (XPathExpressionException e) {
            LOG.error("(..something..) failed", e); // TODO
        }

        LOG.info("Exiting");
        System.exit(0);
    }

    static String cliOutput(String msg) {
        System.out.println(msg);
        return msg;
    }
}
