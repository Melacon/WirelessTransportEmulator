package net.i2cat.netconf.server.netconf.control;

/**
 * Netconf Subsystem
 *
 * @author Julio Carlos Barrera
 *
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import net.i2cat.netconf.server.BehaviourContainer;
import net.i2cat.netconf.server.MessageStore;
import net.i2cat.netconf.server.netconf.types.NetworkElement;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.server.Command;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.SessionAware;
import org.apache.sshd.server.session.ServerSession;

public class NetconfSubsystem implements Command, SessionAware {

    private static final Log    log                    = LogFactory.getLog(NetconfSubsystem.class);

    // subsystem fields
    private ExitCallback        callback;
    private InputStream         in;
    private OutputStream        out;
    private OutputStream        err;

    @SuppressWarnings("unused")
    private Environment            env;
    @SuppressWarnings("unused")
    private ServerSession        session;
    @SuppressWarnings("unused")
    private final BehaviourContainer  behaviourContainer;

    private final MessageStore    messageStore;
    private final NetworkElement ne;

    private NetconfController    netconfProcessor;
    private final NetconfNotifyOriginator netconfNotifyExecutor;

    public NetconfSubsystem(MessageStore messageStore, BehaviourContainer behaviourContainer, NetconfNotifyOriginator netconfNotifyExecutor, NetworkElement ne) {
        this.messageStore = messageStore;
        this.behaviourContainer = behaviourContainer;
        this.ne = ne;
        this.netconfNotifyExecutor = netconfNotifyExecutor;
    }

    public InputStream getInputStream() {
        return in;
    }

    @Override
    public void setInputStream(InputStream in) {
        this.in = in;
    }

    public OutputStream getOutputStream() {
        return out;
    }

    @Override
    public void setOutputStream(OutputStream out) {
        this.out = out;
    }

    public OutputStream getErrorStream() {
        return err;
    }

    @Override
    public void setErrorStream(OutputStream err) {
        this.err = err;
    }

    @Override
    public void setExitCallback(ExitCallback callback) {
        this.callback = callback;
    }

    @Override
    public void start(Environment envParam) throws IOException {
        this.env = envParam;

        // initialize Netconf processor
        netconfProcessor = new NetconfController(in, out, err, callback);

        log.info("Starting new client thread...");
        netconfProcessor.start(messageStore, ne);
        netconfNotifyExecutor.setNetconfNotifyExecutor(netconfProcessor);

    }

    @Override
    public void destroy() {
        netconfProcessor.destroy();
    }

    @Override
    public void setSession(ServerSession session) {
        this.session = session;
    }



    /**
     * Netconf Subsystem Factory
     *
     * @author Julio Carlos Barrera
     *
     */
    public static class Factory implements NamedFactory<Command> {
        private static final Log log2  = LogFactory.getLog(Factory.class);

        private MessageStore        messageStore        = null;
        private BehaviourContainer  behaviourContainer    = null;
        private NetworkElement      ne = null;
        private NetconfNotifyOriginator       notifyFunction = null;

        private Factory(MessageStore messageStore, BehaviourContainer behaviourContainer, NetconfNotifyOriginator notifyFunction, NetworkElement ne) {
            this.messageStore = messageStore;
            this.behaviourContainer = behaviourContainer;
            this.ne = ne;
            this.notifyFunction = notifyFunction;
        }

        public static Factory createFactory(MessageStore messageStore, BehaviourContainer behaviourContainer, NetconfNotifyOriginator notifyFunction, NetworkElement ne) {
            return new Factory(messageStore, behaviourContainer, notifyFunction, ne);
        }

        @Override
        public Command create() {
            log2.info("Creating Netconf Subsystem Factory");
            return new NetconfSubsystem(messageStore, behaviourContainer, notifyFunction, ne);
        }

        @Override
        public String getName() {
            return "netconf";
        }

    }

}
