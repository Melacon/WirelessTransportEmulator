package net.i2cat.netconf.server.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Queue;
import net.i2cat.netconf.server.util.Util;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.sshd.server.Command;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;

public class CommandSshConnection implements Command {

    private static final Log LOG = LogFactory.getLog(CommandSshConnection.class);

    private static byte CR = 13;
    private static byte LF = 10;
    private static byte[] CRLF = { CR, LF };
    private static byte BS = 8;
    private static byte DEL = 127;

    private final Queue<String> cmdQueue;
    private final Queue<CommandSshConnection> connections;
    private final CommandLineProcessor commandLineProcessor;

    private OutputStream sshOutputStream = null;
    private InputStream sshInputStream = null;
    @SuppressWarnings("unused")
    private OutputStream sshErrorOutputStream = null;
    @SuppressWarnings("unused")
    private ExitCallback sshExitCallback;
    private Thread thread = null;
    private boolean runThread = true;

    /**
     * Manage a SSL Command line connection
     * @param connections List of existing connection to add this session.
     * @param cmdQueue If to add the input commands.
     * @param commandLineProcessor Processor to execute a command.
     */
    public CommandSshConnection(Queue<CommandSshConnection> connections, Queue<String> cmdQueue, CommandLineProcessor commandLineProcessor) {
        this.cmdQueue = cmdQueue;
        this.connections = connections;
        this.commandLineProcessor = commandLineProcessor;
        this.connections.add(this);
    }

    @Override
    public void setInputStream(InputStream inputStream) {
        System.out.println("Open ssh session");
        this.sshInputStream = inputStream;

        thread = new Thread(() -> {
            readLineFromInputStream();
        });
        thread.start();
    }

    @Override
    public void setOutputStream(OutputStream outputStream) {
        this.sshOutputStream = outputStream;
    }

    @Override
    public void setErrorStream(OutputStream outputStream) {
        this.sshErrorOutputStream = outputStream;
    }

    @Override
    public void setExitCallback(ExitCallback exitCallback) {
        this.sshExitCallback = exitCallback;
    }

    @Override
    public void destroy() throws Exception {
        runThread = false;
        connections.remove(this);
    }

    @Override
    public void start(Environment environment) throws IOException {
        System.out.println("New ssh session for user: "+environment.getEnv().get(Environment.ENV_USER));
        commandLineProcessor.doWelcome();
    }

    public void println(String outputMessage) {
        write(outputMessage.getBytes());
        write(CRLF);
    }

    private void readLineFromInputStream() {
        while (runThread) {
            if (sshInputStream != null) {
                byte c;
                StringBuffer line = new StringBuffer();
                do {
                    try {
                        c = (byte)sshInputStream.read();
                        if (c > 0) {
                            if (c == CR) {
                                write(CRLF);
                                cmdQueue.add(line.toString());
                                break;
                            } if (c == DEL) {
                                if (line.length() > 0) {
                                    write(BS);
                                    line.deleteCharAt(line.length()-1);
                                }
                            } else {
                                write(c);
                                line.append((char)c);
                            }
                        }
                    } catch (IOException e) {
                        LOG.warn(e.getMessage());
                    }
                } while (true);
            } else {
                Util.sleepMilliseconds(500);
            }
        }
    }

    private void write(byte ... c) {
        if (sshOutputStream != null) {
            try {
                for (byte b : c) {
                    if (b == LF) {
                        sshOutputStream.write( CRLF );
                    } else if (b == CR) {
                    } else {
                        sshOutputStream.write( b );
                    }
                }
                sshOutputStream.flush();
            } catch (Exception e) {
            }
        }
    }
}
