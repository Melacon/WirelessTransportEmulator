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
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;
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
    private static final String TAG_PTPID1 = "$PTPID(";
    private static final String TAG_PTPID2 = ")";
    private static final String TAG_TIME = "$TIME";
    private static final String TAG_COUNTER = "$COUNTER";


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
    private int msgToDiscardCounter = 0;
    private final Console console;
    private Pattern msgPattern = setPattern(null);

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

    private static String substitudePtpId(String xml) {
        int idx, idx2;
        String substringCode, toExchange, base64Result;

        int protect = 20;
        while ((idx = xml.indexOf(TAG_PTPID1)) > 0 && protect-- > 0) {
            idx2 = xml.indexOf(TAG_PTPID2, idx + TAG_PTPID1.length());
            substringCode = xml.substring(idx + TAG_PTPID1.length(), idx2);
            base64Result = Base64.getEncoder().encodeToString(substringCode.getBytes());

            toExchange = TAG_PTPID1+substringCode+TAG_PTPID2;
            xml = xml.replace(toExchange, base64Result);
        }
        if (protect <= 0) {
            log.warn("Problem during conversion of "+TAG_PTPID1+TAG_PTPID2);
        }

        return xml;
    }


    public void send(String xmlMessage) throws IOException {

        xmlMessage = xmlMessage
                .replace(TAG_TIME, NetconfTimeStamp.getTimeStamp() )
                .replace(TAG_COUNTER, String.valueOf(counter++) );

        xmlMessage = substitudePtpId(xmlMessage);

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
            consoleMessage("Hello");

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
            String tagString = receivedMessage.getFilterTags().asCompactString();
            boolean matches = msgPattern.matcher(tagString).matches();
            consoleMessage("Get["+receivedMessage.getMessageId()+"]  "+(matches ? "matches " : "")+tagString);

            if (matches && msgToDiscardCounter > 0) {
                consoleMessage("Discard message: "+receivedMessage.getMessageId());
                msgToDiscardCounter--;
            } else {
                if (matches && msgDelaySeconds > 0) {
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
            consoleMessage("Edit-config ["+receivedMessage.getMessageId()+"] message");
            send( theNe.editconfigElement(
                    receivedMessage.getMessageId(),
                    receivedMessage.getXmlSourceMessage()) );

        } else if (receivedMessage.isRpcGetSchema()) {
            consoleMessage("get-schema ["+receivedMessage.getMessageId()+"] message");
            send( theNe.getSchema(
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
            consoleMessage("Message pattern: '"+msgPattern.pattern()+"'");

        } else if (command.startsWith("dp")) {
            msgPattern = setPattern(command.substring(2));
            consoleMessage("Set message pattern to '"+msgPattern.pattern()+"'");

        } else if (command.startsWith("dn")) {

            consoleMessage("Discard next incoming filtered get-message using pattern: '"+msgPattern.pattern()+"'");
            msgToDiscardCounter = 1;

        } else if (command.startsWith("d")) {

            try {
                msgDelaySeconds = Integer.valueOf(command.substring(1));
                consoleMessage("New delay in seconds: "+msgDelaySeconds+" using pattern: '"+msgPattern.pattern()+"'");
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
        return console.cliOutput("MP"+this.hashCode()+":"+msg);
    }

    /**
     * Return the selected pattern
     * @param regex that should be used
     * @return selected
     */
    private static Pattern setPattern(String regex) {
         if (regex == null || regex.isEmpty()) {
            regex = ".*";
        }
        return Pattern.compile(regex);

    }
}
