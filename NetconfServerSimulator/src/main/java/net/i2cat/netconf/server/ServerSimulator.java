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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
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
import org.apache.sshd.server.SshServer;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.server.Command;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.xml.sax.SAXException;

public class ServerSimulator implements MessageStore, BehaviourContainer, NetconfNotifyOriginator, Console {

    private static final Log   LOG                = LogFactory.getLog(ServerSimulator.class);
    private static final SimpleDateFormat DATEFORMAT = new SimpleDateFormat("yyyyMMdd'T'HHmmss");
    private static final String NAME = "NETCONF - NE Simulator";
    private static final String VERSION = "3.0";

    private SshServer           sshd;

    // stored messages
    @SuppressWarnings("unused")
    private boolean             storeMessages    = false;
    private List<RPCElement>    messages;

    // behaviours
    private List<Behaviour>     behaviours;

    private final List<NetconfNotifyExecutor> netconfNotifyExecutor = new ArrayList<>();

    // hide default constructor, forcing using factory method
    private ServerSimulator() {
    }

    /**
     * Creates a server and no not store messages
     * @return a class representing the server
     */
    public static ServerSimulator createServer()  {
        ServerSimulator server = new ServerSimulator();
        server.messages = new ArrayList<>();
        server.storeMessages = false;

        return server;
    }

    /**
    * @param host name or ip used by SSH Server (use 0.0.0.0 to listen in all interfaces)
    * @param listeiningPort where the SSH server will listen for SSH connections
    * @param ne contains the model that is simulated
    * @throws IllegalArgumentException if mandatory NE is null
    */
    private void initializeServer(String host, int listeningPort, NetworkElement ne) throws IllegalArgumentException {
        LOG.info(staticCliOutput("Configuring server..."));
        sshd = SshServer.setUpDefaultServer();
        sshd.setHost(host);
        sshd.setPort(listeningPort);

        LOG.info(staticCliOutput("Host: '" + host + "', listenig port: " + listeningPort));

        sshd.setPasswordAuthenticator(new AlwaysTruePasswordAuthenticator());
        sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());

        List<NamedFactory<Command>> subsystemFactories = new ArrayList<>();
        subsystemFactories.add(NetconfSubsystem.Factory.createFactory(this, this, this, ne, this));
        sshd.setSubsystemFactories(subsystemFactories);
        LOG.info(staticCliOutput("Server configured."));
    }

    @Override
    public void defineBehaviour(Behaviour behaviour) {
        if (behaviours == null) {
            behaviours = new ArrayList<>();
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
        LOG.info(staticCliOutput("Starting server..."));
        try {
            sshd.start();
        } catch (IOException e) {
            LOG.error(staticCliOutput("Error starting server!"+e.getMessage()));
            throw new ServerException("Error starting server", e);
        }
        LOG.info(staticCliOutput("Server started."));
    }

    public void stopServer() throws ServerException {
        LOG.info(staticCliOutput("Stopping server..."));
        try {
            sshd.stop();
        } catch (IOException e) {
            LOG.error(staticCliOutput("Error stopping server!"+e));
            throw new ServerException("Error stopping server", e);
        }
        LOG.info(staticCliOutput("Server stopped."));
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
    public synchronized void setNetconfNotifyExecutor(NetconfNotifyExecutor executor) {
        this.netconfNotifyExecutor.add(executor);
        staticCliOutput("Register user command listener: "+executor.hashCode());
    }

    private void notify(String command) {
        if (netconfNotifyExecutor.isEmpty()) {
            staticCliOutput("No user command listerner registered. No open SSH stream.");
        } else {
            for (NetconfNotifyExecutor executor : netconfNotifyExecutor) {
                executor.notify(command);
            }
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

    @Override
    public String cliOutput(String msg) {
        return staticCliOutput(msg);
    }

    public static void main(String[] args) {


        System.out.println("---------------------------------------");
        System.out.println(NAME);
        System.out.println("Version: "+VERSION);
        System.out.println("---------------------------------------");

        if (args.length < 3) {
            staticCliOutput("To less parameters. Command: Server xmlFilename port [pathToYang]");
            return;
        }
        int port = Integer.valueOf(args[1]);
        String xmlFilename = args[0];
        String yangPath = args.length >= 3 ? args[2] : "yang/yangNeModel";
        String uuid = args.length >= 4 ? args[3] : "";

        String debugFile = "debug"+String.valueOf(port) +".log";
        initDebug(debugFile);

        LOG.info(staticCliOutput(NAME+" Version: "+VERSION));

        staticCliOutput("Start parameters are:");
        staticCliOutput("\tFilename: "+xmlFilename);
        staticCliOutput("\tPort: "+port);
        staticCliOutput("\tDebuginfo and communication is in file: "+debugFile);
        staticCliOutput("\tYang files in directory: "+yangPath);
        staticCliOutput("\tUuid-parameter: '"+uuid+"'");



        try {
            ServerSimulator server = ServerSimulator.createServer();
            NetworkElement ne = new NetworkElement(xmlFilename, yangPath, uuid, server);

            server.initializeServer("0.0.0.0", port, ne);
            server.startServer();

            // read lines form input
            BufferedReader buffer = new BufferedReader(new InputStreamReader(System.in));
            String command;

            while (true) {
                command = buffer.readLine();
                if (command == null) {
                    staticCliOutput("Command <null>");
                } else if (command.isEmpty()) {
                    //Do nothing
                } else if (command.equals("list")) {
                    staticCliOutput("Messages received(" + server.getStoredMessages().size() + "):");
                    for (RPCElement rpcElement : server.getStoredMessages()) {
                        staticCliOutput("#####  BEGIN message #####\n" +
                                rpcElement.toXML() + '\n' +
                                "#####   END message  #####");
                    }
                } else  if (command.equals("size")) {
                    staticCliOutput("Messages received(" + server.getStoredMessages().size() + "):");

                } else  if (command.equals("quit")) {
                    staticCliOutput("Stop server");
                    server.stopServer();
                    break;
                } else  if (command.equals("info")) {
                    staticCliOutput("Port: "+port+" File: "+xmlFilename);
                } else  if (command.equals("status")) {
                    staticCliOutput("Status: not implemented");
                } else if (command.startsWith("n")) {
                    String notifyCommand = command.substring(1);
                    staticCliOutput("User command: "+notifyCommand);
                    server.notify(notifyCommand);
                } else if (command.startsWith("h")) {
                    staticCliOutput("NETCONF Simulator V4.1");
                    staticCliOutput("Available commands: status, quit, info, list, size, n[ZZ | l | x | dZZ]");
                    staticCliOutput("\tnl: list available notifications");
                    staticCliOutput("\tnZZ: send notification with number ZZ");
                    staticCliOutput("\tnx: list internal XML doc tree");
                    staticCliOutput("\tndZZ: Introduce delay of ZZ seconds before answer is send to next get-message");
                    staticCliOutput("\tndl: list actual delay and pattern");
                    staticCliOutput("\tndp??: set tag filter regex pattern (.* = any)");
                    staticCliOutput("\tndn: Discard next get message.");
                    staticCliOutput("\tntZZ M S: send notification ZZ for M times every S seconds");
                    staticCliOutput("\tnt: provide status");
                    staticCliOutput("\tntx: stop execution");

                } else {
                    staticCliOutput("Unknown command '"+command+"' (h for help)");
                }
            }
        } catch (SAXException e) {
            LOG.error(staticCliOutput("(..something..) failed"+e.getMessage()));
        } catch (ParserConfigurationException e) {
            LOG.error(staticCliOutput("(..something..) failed"+e.getMessage()));
        } catch (TransformerConfigurationException e) {
            LOG.error("(..something..) failed", e);
        } catch (ServerException e) {
            LOG.error("(..something..) failed", e);
        } catch (XPathExpressionException e) {
            LOG.error("(..something..) failed", e);
        } catch (IOException e1) {
            LOG.error("(..something..) failed", e1);
        }

        LOG.info(staticCliOutput("Exiting"));
        System.exit(0);
    }

    public static String staticCliOutput(String msg) {
        System.out.println(DATEFORMAT.format(new Date())+" "+msg);
        return msg;
    }


}
