/**
 * Netconf Message processor.
 *
 * Reads the message queue and executes related actions within a Thread.
 * Owns the network element class which simulates the NETCONF NE behavior.
 * Processes also other messages, like user commands.
 *
 * @author Herbert (herbert.eiselt@highstreet-technologies.com)
 *
 */

package net.i2cat.netconf.server.netconf.streamProcessing;

import java.io.IOException;
import java.util.List;
import net.i2cat.netconf.messageQueue.MessageQueue;
import net.i2cat.netconf.rpc.Query;
import net.i2cat.netconf.rpc.QueryFactory;
import net.i2cat.netconf.rpc.RPCElement;
import net.i2cat.netconf.server.Console;
import net.i2cat.netconf.server.MessageStore;
import net.i2cat.netconf.server.netconf.types.NetconfIncommingMessageRepresentation;
import net.i2cat.netconf.server.netconf.types.UserCommand;
import net.i2cat.netconf.server.netconf.types.NetconfSender;
import net.i2cat.netconf.server.netconf.types.NetconfSessionStatus;
import net.i2cat.netconf.server.netconf.types.NetconfSessionStatusHolder;
import net.i2cat.netconf.server.netconf.types.NetconfTagList;
import net.i2cat.netconf.server.netconf.types.NetconfTimeStamp;
import net.i2cat.netconf.server.netconf.types.NetworkElement;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class NetconfMessageProcessorThread extends Thread  {

    private static final Log log  = LogFactory.getLog(NetconfMessageProcessorThread.class);
    // status fields
    private final NetconfSessionStatusHolder status;
    private final NetconfSender sender;
    private final MessageQueue messageQueue;
    private MessageStore messageStore = null;
    // message counter
    private int   messageCounter = 100;

    private final NetworkElement theNe;

    private int counter = 0;
    private int msgDelaySeconds = 0;
    private int msgDiscard = 0;
    private final Console console;


    public NetconfMessageProcessorThread(String name, NetconfSessionStatusHolder status, NetconfSender sender,
            MessageQueue messageQueue, MessageStore messageStore, NetworkElement ne, Console console) {
        super(name);
        this.status = status;
        this.sender = sender;
        this.messageQueue = messageQueue;
        this.messageStore = messageStore;
        this.theNe = ne;
        this.console = console;
   }

    public void send(String xmlMessage) throws IOException {

        xmlMessage = xmlMessage
                .replace("$TIME", NetconfTimeStamp.getTimeStamp() )
                .replace("$COUNTER", String.valueOf(counter++) );

        sender.send(xmlMessage);
    }

    public void send(List<String> xmlMessages) throws IOException {

        for (String xmlMessage : xmlMessages) {
            send(xmlMessage);
        }

    }

    public void setMessageStore(MessageStore messageStore) {
    }

    @Override
    public void run() {
        while (status.less(NetconfSessionStatus.SESSION_CLOSED)) {

            RPCElement message = messageQueue.blockingConsume();

            if (message == null) {
                log.debug("Received received: null");
            } else {
                log.debug("Message received: " + message.getClass().getSimpleName()+" "+message.toString());
            }

            // store message if necessary
            if (messageStore != null) {
                messageStore.storeMessage(message);
            }

            // avoid message processing when session is already closed
            if (status.equals(NetconfSessionStatus.SESSION_CLOSED)) {
                log.warn("Session is closing or is already closed, message will not be processed");
                return;
            }

            // process message
            try {
                if (message instanceof NetconfIncommingMessageRepresentation) {
                    doMessageProcessing((NetconfIncommingMessageRepresentation)message);
                } else if (message instanceof UserCommand) {
                    doNotificationProcessing((UserCommand)message);
                } else {
                    log.warn("Unknown message: " + message.toString());
                }
            } catch (IOException e) {
                log.error("Error sending reply", e);
                break;
            }
        }
        log.trace("Message processor ended");
    }

    /*********************************************************************
     * Private message processing
     */

    private void doMessageProcessing(NetconfIncommingMessageRepresentation receivedMessage) throws IOException {

        if (receivedMessage.isHello()) {
            consoleMessage("Hello ["+receivedMessage.getMessageId()+"]");

            if (status.less(NetconfSessionStatus.HELLO_RECEIVED)) {
                status.change(NetconfSessionStatus.HELLO_RECEIVED);
                // send hello
                log.debug("Sending answer to hello...");
                String sessionId = String.valueOf((int) (Math.random() * Integer.MAX_VALUE));
                send(theNe.assembleHelloReply(sessionId));

            } else {
                log.error("Hello already received. Aborting");
                sendCloseSession();
                status.change(NetconfSessionStatus.CLOSING_SESSION);
            }

        } else if (receivedMessage.isRpcCreateSubscription()) {
            consoleMessage("CreateSubscription["+receivedMessage.getMessageId()+"]"+receivedMessage.getFilterTags().asCompactString());
            send(theNe.assembleRpcReplyEmptyDataOk(receivedMessage.getMessageId()));

        } else if (receivedMessage.isRpcGetFilter()) {
            consoleMessage("Get["+receivedMessage.getMessageId()+"] "+receivedMessage.getFilterTags().asCompactString());
            if (msgDiscard > 0) {
                consoleMessage("Discard message: "+receivedMessage.getMessageId());
                msgDiscard--;
            } else {
                if (msgDelaySeconds > 0) {
                    consoleMessage("Wait seconds: "+msgDelaySeconds+" for msg "+receivedMessage.getMessageId());
                    sleepSeconds(msgDelaySeconds);
                    msgDelaySeconds = 0;
                    consoleMessage("Proceed");
                }
                send( theNe.assembleRpcReplyFromFilterMessage(
                        receivedMessage.getMessageId(),
                        receivedMessage.getFilterTags() ));
            }

        } else if (receivedMessage.isRpcGetConfigSourceRunningFilter()) {
            NetconfTagList tags = receivedMessage.getFilterTags();
            if (! tags.isEmtpy()) { //Do not indicate polls
                consoleMessage("Get-config ["+receivedMessage.getMessageId()+"] running "+receivedMessage.getFilterTags().asCompactString());

            }
           send( theNe.assembleRpcReplyFromFilterMessage(
                    receivedMessage.getMessageId(),
                    receivedMessage.getFilterTags() ));

        } else if (receivedMessage.isRpcLockTargetRunning()) {
            consoleMessage("Lock ["+receivedMessage.getMessageId()+"] running");
            send( theNe.assembleRpcReplyOk(
                    receivedMessage.getMessageId()) );

        } else if (receivedMessage.isRpcUnlockTargetRunning()) {
            consoleMessage("Unlock ["+receivedMessage.getMessageId()+"] running");
            send( theNe.assembleRpcReplyOk(
                    receivedMessage.getMessageId()) );

        } else if (receivedMessage.isRpcEditConfigTargetRunningDefaultOperationConfig()){
            consoleMessage("Edit-config ]"+receivedMessage.getMessageId()+"] message");
            send( theNe.editconfigElement(
                    receivedMessage.getMessageId(),
                    receivedMessage.getXmlSourceMessage()) );
        } else {
            consoleMessage("NO RULE for source message with id "+receivedMessage.getMessageId());
            consoleMessage(receivedMessage.getXmlSourceMessage());
        }
    }

    private static void sleepSeconds(int delaySeconds) {
        if (delaySeconds > 0) {
            try {
                Thread.sleep(delaySeconds * 1000);
            } catch (InterruptedException e) {
                log.error("(..something..) failed", e);
            }
        }
    }

    private void sendCloseSession() throws IOException {
        log.debug("Sending close session.");
        Query query = QueryFactory.newCloseSession();
        query.setMessageId(String.valueOf(messageCounter++));
        send(query.toXML());
    }

    private void doNotificationProcessing(UserCommand receivedMessage) throws IOException {
        log.info("User initiated Notification: " + receivedMessage.toString());
        String command = receivedMessage.getCommand();

        if (command.startsWith("dl")) {
            consoleMessage("Delay in seconds: "+msgDelaySeconds);

        } else if (command.startsWith("dn")) {

            consoleMessage("Discard next incoming get-message");
            msgDiscard = 1;

        } else if (command.startsWith("d")) {

            try {
                msgDelaySeconds = Integer.valueOf(command.substring(1));
                consoleMessage("New delay in seconds: "+msgDelaySeconds);
            } catch (NumberFormatException e) {
                consoleMessage("Not a number. Unchanged delay in seconds: "+msgDelaySeconds);
            }

        } else {
            String msg = theNe.doProcessUserAction(receivedMessage.getCommand());
            if (msg != null) {
                send( msg );        //Test purpose
                consoleMessage("Notification: "+msg);
            }
        }
    }

    /**
     * Message to console
     * @param msg content
     * @return again the msg
     */
    private String consoleMessage(String msg) {
        return console.cliOutput(msg);
    }


}
