/**
 * Main Netconf server class allowing to create a test Netconf server
 * containing a simulated network element, defined by an XML-file.
 *
 * Bases on Server implementation of Julio Carlos Barrera
 *
 * @author herbert.eiselt@highstreet-technologies.com
 */

package net.i2cat.netconf.server;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.xpath.XPathExpressionException;
import net.i2cat.netconf.rpc.RPCElement;
import net.i2cat.netconf.server.cli.SimulatorCommandlineInterpreter;
import net.i2cat.netconf.server.cli.CommandLineControl;
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
/**
 * Command parameters of ServerSimulator
 * args[]
 *         0:xmlFilename
 *         1:portnumber
 *         2:yang-mode files directory
 *         3:(o)-uuid=uuid
 */
public class ServerSimulator implements MessageStore, BehaviourContainer, NetconfNotifyOriginator, Console {

    private static final Log   LOG                = LogFactory.getLog(ServerSimulator.class);
    private static final SimpleDateFormat DATEFORMAT = new SimpleDateFormat("yyyyMMdd'T'HHmmss");
    private static final String NAME = "NETCONF - NE Simulator";
    private static final String VERSION = "4.2.2";
    private static final String SSHHOST = "0.0.0.0";
    private static CommandLineControl cliSsh = null;

    private SshServer           sshd;

    // stored messages
    @SuppressWarnings("unused")
    private boolean             storeMessages    = false;
    private final List<RPCElement>    messages;

    // behaviours
    private List<Behaviour>     behaviours;

    private final List<NetconfNotifyExecutor> netconfNotifyExecutor = new ArrayList<>();


    // hide default constructor, forcing using factory method
    private ServerSimulator() {
        messages = new ArrayList<>();
        storeMessages = false;
    }

    /**
    * @param host name or ip used by SSH Server (use 0.0.0.0 to listen in all interfaces)
    * @param listeiningPort where the SSH server will listen for SSH connections
    * @param ne contains the model that is simulated
    * @throws IllegalArgumentException if mandatory NE is null
    */
    private void initializeServer(String host, int listeningPort, NetworkElement ne) throws IllegalArgumentException {
        LOG.info(cliOutput("Configuring server..."));
        sshd = SshServer.setUpDefaultServer();
        sshd.setHost(host);
        sshd.setPort(listeningPort);

        LOG.info(cliOutput("Host: '" + host + "', listenig port: " + listeningPort));

        sshd.setPasswordAuthenticator(new AlwaysTruePasswordAuthenticator());
        sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());

        List<NamedFactory<Command>> subsystemFactories = new ArrayList<>();
        subsystemFactories.add(NetconfSubsystem.Factory.createFactory(this, this, this, ne, this));
        sshd.setSubsystemFactories(subsystemFactories);
        LOG.info(cliOutput("Server configured."));
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
        } catch (IOException e) {
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
    public synchronized void setNetconfNotifyExecutor(NetconfNotifyExecutor executor) {
        this.netconfNotifyExecutor.add(executor);
        cliOutput("Register user command listener: "+executor.hashCode());
    }

    public void notify(String command) {
        if (netconfNotifyExecutor.isEmpty()) {
            cliOutput("No user command listerner registered. No open SSH stream.");
        } else {
            for (NetconfNotifyExecutor executor : netconfNotifyExecutor) {
                executor.notify(command);
            }
        }
    }

    private void initDebug( String debugFilename ) {

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
        fa.setThreshold(Level.WARN);
        fa.setMaxBackupIndex(10);
        fa.setMaximumFileSize(1000000);
        fa.setAppend(true);

        fa.activateOptions();
        //add appender to any Logger (here is root)
        Logger.getRootLogger().addAppender(fa);
        //repeat with all other desired appenders

    }

    @Override
    public String cliOutput(String msg) {
        String outputMessage = DATEFORMAT.format(new Date())+" "+msg;
        if (cliSsh != null && cliSsh.isConnected()) {
            cliSsh.println(outputMessage);
        } else {
            System.out.println(outputMessage);
        }
        return msg;
    }

    public String getVersion() {
        return VERSION;
    }

    private String getMainArg(String prefix, String defaulValue, String[] args) {
        for (String arg : args) {
            if (arg.startsWith(prefix)) {
                return arg.substring(prefix.length());
            }
        }
        return defaulValue;
    }

    public void serverMain(String[] args) {
        System.out.println("---------------------------------------");
        System.out.println(NAME);
        System.out.println("Version: "+VERSION);
        System.out.println("---------------------------------------");

        if (args.length < 3) {
            cliOutput("To less parameters. Command: Server xmlFilename port [pathToYang]");
            return;
        }
        int port = Integer.valueOf(args[1]);
        String xmlFilename = args[0];
        String yangPath = args[2];

        String[] optionalArgs = Arrays.copyOfRange(args, 1, args.length);
        String uuid = getMainArg("-uuid=", "", optionalArgs);
        int sshPort = Integer.valueOf(getMainArg("-sshport=", "-1", optionalArgs));

        // Initialization
        String debugFile = "debug"+String.valueOf(port) +".log";
        initDebug(debugFile);

        String startupParameters = NAME+" Version: "+VERSION+
                "\nStart parameters are:"+
                "\n\tFilename: "+xmlFilename+
                "\n\tPort: "+port+
                "\n\tDebuginfo and communication is in file: "+debugFile+
                "\n\tYang files in directory: "+yangPath+
                "\n\tUuid-parameter: '"+uuid+"'";

        SimulatorCommandlineInterpreter simulatorCommandLine = new SimulatorCommandlineInterpreter(startupParameters);
        simulatorCommandLine.setServer(this, this);
        try {
            cliSsh = new CommandLineControl(SSHHOST, sshPort, simulatorCommandLine);
            cliOutput(startupParameters);

            NetworkElement ne = new NetworkElement(xmlFilename, yangPath, uuid, this);
            initializeServer(SSHHOST, port, ne);
            startServer();

            cliSsh.doProcessCommandLine();

        } catch (TransformerConfigurationException | XPathExpressionException | SAXException | IOException
                | ParserConfigurationException | InterruptedException e) {
            LOG.error("Failed", e);
        }

        LOG.info(cliOutput("Exiting"));
        System.exit(0);
    }

    /*----------------------------------------------------
     * Static startup point
     */

    public static void main(String[] args) throws InterruptedException {
        ServerSimulator server = new ServerSimulator();
        server.serverMain(args);
    }

}
