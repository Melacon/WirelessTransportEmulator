package net.i2cat.netconf.server.transport;

import net.i2cat.netconf.messageQueue.MessageQueue;
import net.i2cat.netconf.server.netconf.types.NetconfIncommingMessageRepresentation;
import net.i2cat.netconf.transport.TransportContentParser;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.ext.DefaultHandler2;

/**
 * Transport content parser for Netconf servers based on {@link TransportContentParser}
 *
 * @author Herbert Eiselt
 *
 */
public class ServerTransportContentParser extends DefaultHandler2 {

    private final Log log = LogFactory.getLog(ServerTransportContentParser.class);
    private final static String EMPTY = "";


    private MessageQueue  messageQueue; //Output queue after message handling

    private NetconfIncommingMessageRepresentation                     rpcIn;        //Actual created message repesentation
    private String xmlSourceMessage = EMPTY;        //Actual handled sourcemessage -> Only one Mesaage at a time

    private int level = 0;


    public void setMessageQueue(MessageQueue queue) {
        this.messageQueue = queue;
    }

    public void setXmlSourceMessage(String xmlSourceMessage) {
        this.xmlSourceMessage = xmlSourceMessage;
    }


    /****************************************************
     * Document call back handlers to add information
     */

    @Override
    public void startDocument() {
        log.trace("Start document");
        level = 0;
        rpcIn = new NetconfIncommingMessageRepresentation();
        rpcIn.setXmlSourceMessage(xmlSourceMessage);
    }

    @Override
    public void endDocument() {
        messageQueue.put(rpcIn);
        rpcIn = null;
        xmlSourceMessage = EMPTY;
        log.trace("End document");
    }

    @Override
    public void startPrefixMapping (String prefix, String uri) throws SAXException {
        log.trace("Start namespace '"+prefix+"' "+uri);
    }

    @Override
    public void endPrefixMapping (String prefix) throws SAXException {
        log.trace("End namespace "+prefix);
    }


    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        super.startElement(uri, localName, qName, attributes);

        StringBuffer sb = new StringBuffer();
        for (int t=0; t < attributes.getLength();t++ ) {
            sb.append("["+t+"]");
            sb.append(attributes.getQName(t));
            sb.append("=");
            sb.append(attributes.getValue(t));
            sb.append(" ");
       }
        log.trace("Start message uri["+uri+"] localname["+localName+"] Att: "+sb.toString()+" Level="+level);
        if (level == 0) {
               rpcIn.setMessageUri(uri);
                rpcIn.setMessageType(localName);
                String messageId = attributes.getValue("message-id");
                if (messageId == null) {
                    messageId = attributes.getValue("ns0:message-id");
                    if (messageId != null) {
                        log.info("Found ns0 message id");
                    }
                }
                if (messageId != null) {
                    rpcIn.setMessageId(messageId);
                }
        } else {
            rpcIn.pushTag(localName, uri);
        }
        level++;

    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        super.characters(ch, start, length);

        rpcIn.addCollectionValue(level, new String(ch, start, length) );
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        super.endElement(uri, localName, qName);

        log.trace("End message uri["+uri+"] localname["+localName+" Level="+level+"]");
        if (level > 0) {
            level--;
        }
    }

    @Override
    public void error(SAXParseException e) throws SAXException {
        log.warn(e.getMessage());
    }

    @Override
    public void fatalError(SAXParseException e) throws SAXException {
        log.warn(e.getMessage());
    }
}
