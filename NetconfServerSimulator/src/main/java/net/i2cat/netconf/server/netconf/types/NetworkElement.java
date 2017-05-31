/**
 * NetworkElement is the database for the simulation. NetworkElement is getting responses from the XML file that defines the
 * Behavior of the simulated device. The answers are XML strings, that are directly produced from the contents of the XML inpt file.
 *
 * @author herbert.eiselt@highstreet-technologies.com
 *
 */

package net.i2cat.netconf.server.netconf.types;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import net.i2cat.netconf.server.Console;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class NetworkElement {

    private static final Log LOG = LogFactory.getLog(NetworkElement.class);
    private static final String EMPTY = "";

    private static final String UUIDNAME = "uuid";
    private static final String CONSOLEPREFIX1 = "\tdoc-change: ";

    private Document doc = null;
    private final Transformer transformer;
    private final String schemaPath;
    private final Console console;

    /* ---------------------------------------------------------------
     * Constructor
     */

    public NetworkElement(String filename, String schemaPath, String uuid, Console console) throws SAXException, IOException, ParserConfigurationException, TransformerConfigurationException, XPathExpressionException {

        LOG.debug("Networkelements uses file: "+filename);
        File file = new File(filename);

        FileInputStream fis = new FileInputStream(file);
        doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(fis);

        TransformerFactory tf = TransformerFactory.newInstance();
        transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

        this.schemaPath = schemaPath;
        this.console = console;

        File schemaPathDirectory = new File(this.schemaPath);
        if (schemaPathDirectory.exists() && schemaPathDirectory.isDirectory()) {

            LOG.info(consoleMessage("Verify yang schemas listed in netconf-state/schemas definition: "));

            NodeList schemas = getNodeList(doc, "//data/netconf-state/schemas/schema");
            Node node;
            StringBuffer sbNotOk = new StringBuffer();
            StringBuffer sbOk = new StringBuffer();
            for (int n = 0; n < schemas.getLength(); n++) {
                node = schemas.item(n);

                String fileName = getFilenameFromNode(schemaPath, node);
                File fileSchema = new File(fileName);
                if (!fileSchema.exists() || fileSchema.isDirectory()) {
                    sbNotOk.append("\t"+fileSchema.getName()+"\n");
                } else {
                    sbOk.append("\t"+fileSchema.getName()+"\n");
                }
            }
            LOG.info(consoleMessage("OK:\n"+sbOk.toString()));
            LOG.info(consoleMessage("Not OK:\n"+sbNotOk.toString()));

            Node uuidNode = getNode(doc, "//data/network-element/uuid");
            if (uuidNode != null) {
                if (uuid != null && !uuid.isEmpty()) {
                    LOG.info(consoleMessage("Overwrite uuid and name with parameter "+uuid));
                    uuidNode.setTextContent(uuid);
                    Node nameNode = getNode(doc, "//data/network-element/name/value");
                    if (nameNode != null) {
                        LOG.info(consoleMessage("Overwrite name with parameter "+uuid));
                        nameNode.setTextContent(uuid);
                    }
                }
                LOG.info(consoleMessage("device info uuid'"+uuidNode.getTextContent()+"'"));
            } else {
                LOG.info(consoleMessage("no uuid found within xml-File"));
            }

        } else {
            throw new IllegalArgumentException("Invalid schema directory: '"+String.valueOf(schemaPath)+"'");
        }
    }

    public NetworkElement(String filename, String schemaPath) throws SAXException, IOException, ParserConfigurationException, TransformerConfigurationException, XPathExpressionException {
        this(filename, schemaPath, null, null);
    }

    /* ---------------------------------------------------------------
     * Private and static functions to generate some output
     */

    /**
     * Message to console
     * @param msg content
     * @return again the msg
     */
    private String consoleMessage(String msg) {
        return console.cliOutput(msg);
    }

    /**
     * Helper function to print set of strings
     * @param set to be converted to string
     * @return string with output
     */
    private static String listToString(Set<String> set ) {
        StringBuffer sb = new StringBuffer();
        for (String s : set) {
            sb.append(' ');
            sb.append(s);
        }
        return sb.toString();
    }

    /**
     * Create a list of the actual document structure
     * @param doc the document
     * @param xPathRoot the start node that is listed
     */
    private static void listNodesToConsole(Document doc, String xPathRoot, Console console) {
        Node root;

        try {
            root = getNode(doc, xPathRoot);
            for (Node l1 : getChildElementNodes(root)) {
                console.cliOutput(l1.getNodeName()+"-<root>");
                for (Node l2 : getChildElementNodes(l1)) {
                    console.cliOutput(l1.getNodeName()+"-"+l2.getNodeName());
                    for (Node l3 : getChildElementNodes(l2)) {
                        console.cliOutput(l1.getNodeName()+"-"+l2.getNodeName()+"-"+l3.getNodeName());
                    }
                }
            }
        } catch (XPathExpressionException e) {
            LOG.error("(..something..) failed", e); // TODO
        }

    }

    /* ---------------------------------------------------------------
     * Private and static functions to modify XML documents
     */

    /**
     * Convert XML-String into Document
     * @param xml input string
     * @return xml as document
     * @throws Exception different types could occur during conversion
     */

    private static Document loadXMLFromString(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        InputSource is = new InputSource(new StringReader(xml));
        return builder.parse(is);
    }

    /**
     * get nodelist by xpath
     * Examples xpath:
     *  "//MW_EthernetContainer_Pac/ethernetContainerCurrentProblems"
     *  final XPathExpression xpath = XPathFactory.newInstance().newXPath().compile("//ethernetContainerCurrentProblems/currentProblemList/sequenceNumber");
     * @param doc document with xml data
     * @param root xpath declaration of root
     * @return the NodeList
     * @throws XPathExpressionException if something wrong
     */
    private static NodeList getNodeList(Document doc, String root) throws XPathExpressionException {

        XPathExpression xpath = XPathFactory.newInstance().newXPath().compile(root);
        NodeList nodeList = (NodeList) xpath.evaluate(doc, XPathConstants.NODESET);
        return nodeList;

    }

    /**
     * get Node by xpath
     * @param doc document with xml data
     * @param root xpath declaration of root
     * @return the Node
     * @throws XPathExpressionException if something wrong
     */
    private static Node getNode(Document doc, String root) throws XPathExpressionException {

        XPathExpression xpath = XPathFactory.newInstance().newXPath().compile(root);
        Node node = (Node)xpath.evaluate(doc, XPathConstants.NODE);
        return node;

    }

    /**
     * get all child nodes with type ELEMENT_NODE back in a list
     * @param node parent node
     * @return List with child nodes
     */
    private static List<Node> getChildElementNodes(Node node) {
        List<Node> res = new ArrayList<Node>();
        NodeList childs = node.getChildNodes();
        Node item;
        //System.out.println("Query node "+node.getNodeName());
        for (int n=0; n < childs.getLength(); n++) {
            item = childs.item(n);
            //System.out.println(node.getNodeName()+"-"+item.getNodeName()+" "+item.getNodeType());
            if (item.getNodeType() == Node.ELEMENT_NODE) {
                res.add(childs.item(n));
            }
        }
        return res;
    }

    /**
     * Subtree access to the XML tree that was created by the xml file.
     * If Element is a list with more than one element .. all are delivered back.
     * @param root starting node to read subtree
     * @return string with subtree content
     */
    public static String getXmlSubTreeAsString(Document doc, String root, Transformer transformer) {
        StringWriter sw = new StringWriter();

        try {
            NodeList nodeList = getNodeList(doc, root);

            for (int t = 0; t < nodeList.getLength(); t++) {
                transformer.transform(new DOMSource(nodeList.item(t)), new StreamResult(sw));
            }
        } catch (XPathExpressionException e) {
            LOG.debug("XML XPath problem: "+e.getMessage());
        } catch (TransformerException e) {
            LOG.debug("XML Transformer problem: "+e.getMessage());
        }

        return sw.toString();
    }


    /**
     * Subtree access to the XML tree that was created by the xml file.
     * If Element is a list with more than one element .. all are delivered back.
     * @param root starting node to read subtree
     * @return List of strings with subtree content with one element for node of the expected list
     */
    private List<String> getXmlSubTreeAsStringList(String root) {
        List<String> res = new ArrayList<String>();
        try {
            NodeList nodeList = getNodeList(doc, root);

            StringWriter sw = new StringWriter();
            for (int t = 0; t < nodeList.getLength(); t++) {
                sw = new StringWriter();
                transformer.transform(new DOMSource(nodeList.item(t)), new StreamResult(sw));
                res.add(sw.toString());
            }
        } catch (XPathExpressionException e) {
            LOG.debug("XML XPath problem: "+e.getMessage());
        } catch (TransformerException e) {
            LOG.debug("XML Transformer problem: "+e.getMessage());
        }
        return res;
    }

    /**
     * Subtree access to the XML tree that was created by the xml file.
     * If Element is a list with more than one element .. all are delivered back.
     * @param root starting node to read subtree
     * @return string with subtree content
     */
    public String getXmlSubTreeAsString(String root) {
        return getXmlSubTreeAsString(doc, root, transformer);
    }

    /**
     * Get specific attribute content as String.
     * @param node The node in question
     * @param attribute the attribute name. Only ELEMENT_NODEs are considered
     * @return String or EMPTY string
     */
    private static String getNodeAttribute(Node node, String attribute) {

        for (Node nodeChild : getChildElementNodes(node)) {

            if (nodeChild.getNodeName().equals(attribute)) {
                return nodeChild.getTextContent();
            }

        }
        return EMPTY;
    }

    /**
     * Collect from the given root a list with the given attribute that is used as univied ID.
     * @param doc document to search
     * @param root starting node to read subtree
     * @param attibuteName or null if the value itself is the id
     * @return HashSet with all id.
     */
    private static HashSet<String> getNodeIds(Document doc, String root, String attibuteName) {
        HashSet<String> res = new HashSet<String>();
        try {
             final XPathExpression xpath = XPathFactory.newInstance().newXPath().compile(root);
             final NodeList nodeList = (NodeList) xpath.evaluate(doc, XPathConstants.NODESET);

             String uuidString;
             for (int t = 0; t < nodeList.getLength(); t++) {
                 uuidString = attibuteName.isEmpty() ? nodeList.item(t).getTextContent() : getNodeAttribute(nodeList.item(t), attibuteName);
                 if (! uuidString.isEmpty() ) {
                     res.add(uuidString);
                 }
             }
        } catch (XPathExpressionException e) {
            LOG.debug("XML XPath problem: "+e.getMessage());
        }
        return res;
    }

    /**
     * See {getXmlAttributes(Document, String, String)
     */
    private static HashSet<String> getXmlAttributes(Document doc, String root) {
        return getNodeIds(doc, root, EMPTY);
    }


    /**
     * Deliver if in both lists
     * @param indexListFirst list1 input
     * @param indexListSecond list2 input
     * @return All index matching criteria
     */
    private static HashSet<String> getStringInBoth(HashSet<String> indexListFirst, HashSet<String> indexListSecond) {
        HashSet<String> res = new HashSet<String>();
        for (String value1 : indexListFirst) {
            for (String value2 : indexListSecond) {
                if (value1.equals(value2)) {
                    res.add(value1);
                    continue;
                }
            }
        }
        return res;
    }

    /**
     * Deliver if in first, but not in second
     * @param indexListFirst list1 input
     * @param indexListSecond list2 input
     * @return All index matching criteria
     */
    private static HashSet<String> getStringInFirstNotInSecond(HashSet<String> indexListFirst, HashSet<String> indexListSecond) {
        HashSet<String> res = new HashSet<String>();
        boolean found;
        for (String value1 : indexListFirst) {
            found = false;
            for (String value2 : indexListSecond) {
                if (found = value1.equals(value2)) {
                    break;
                }
            }
            if (!found) {
                res.add(value1);
            }
        }
        return res;
    }

    /**
     * Add a new child to a node
     * @param destination node to be appended
     * @param newChild element to add
     * @param idx null or index information for console output
     */
    private static void appendChild(Node destination, Node newChild, String idx, Console console) {

        StringBuffer msg = new StringBuffer();
        msg.append(CONSOLEPREFIX1);
        msg.append("append "+newChild.getNodeName()+" to "+destination.getNodeName());
        if (idx != null) {
            msg.append(" idx["+idx+"]");
        }

        LOG.info( console.cliOutput(msg.toString()) );
        Node copy = newChild.cloneNode(true);

        destination.appendChild(copy);
    }

    /**
     * Replace a node by a node
     * @param destination parent node if child.
     * @param child has to be replaced
     * @param newChild replace with this content
     * @param idx null or index information for console output.
     */
    private static void replaceChild(Node destination, Node child, Node newChild, String idx, Console console) {

        StringBuffer msg = new StringBuffer();
        msg.append(CONSOLEPREFIX1);
        msg.append("replace "+newChild.getNodeName()+" to "+destination.getNodeName());
        if (idx != null) {
            msg.append(" idx["+idx+"]");
        }
        LOG.info( console.cliOutput(msg.toString()));

        Node copy = newChild.cloneNode(true);
        destination.replaceChild(copy, child);
    }


    /**
     * replace or create a child node by a new node.
     * Only nodes with UUID and of the type ELEMENT_NODE are effected.
     * @param destination has the effected attributes
     * @param newChild is the new attribute that replaces an existing one or is added.
     */
    private static void replaceChild(Node destination, Node newChild, Console console ) {

        if (newChild.getNodeType() == Node.ELEMENT_NODE) {

            String newChildUuidString = getNodeAttribute(newChild, UUIDNAME);
            if (!newChildUuidString.isEmpty()) {

                NodeList destinationChilds = destination.getChildNodes();
                String destinationUuidString = EMPTY;
                boolean found = false;
                Node child = null;

                for (int i=0; i < destinationChilds.getLength(); i++ ) {
                    destinationUuidString = getNodeAttribute(child = destinationChilds.item(i), UUIDNAME);
                    if (newChildUuidString.equals(destinationUuidString)) {
                        found = true;
                        break;
                    }
                }

                if (found) {
                    replaceChild(destination, child, newChild, newChildUuidString, console);

                } else {
                    appendChild(destination, newChild, newChildUuidString, console );
                }
            }
        }
    }

    /**
     * Replaces attributes of a destination node by new attribute entries in a source node
     * @param destination attributes to be replaced
     * @param source new attributes to replace existing ones or to be added
     */
    private static void replaceChilds(Node destination, Node source, Console console) {

        for (Node sourceChild : getChildElementNodes(source)) {
            replaceChild(destination, sourceChild,console );
        }

    }


    /**
     * Get list of notifications to be produced.
     * @param mwNotifications contains list of specific notifications to be send out
     * @param transformer to map to xml string
     * @param notificationXmls List, to be extended with new notifications
     * @throws TransformerException Transforming problem
     */
    private static void getNotifications(Node mwNotifications, Transformer transformer, List<String> notificationXmls) throws TransformerException {

        StringWriter sw;
        for (Node item : getChildElementNodes(mwNotifications)) {
            sw = new StringWriter();
            transformer.transform(new DOMSource(item), new StreamResult(sw));
            notificationXmls.add(assembleRpcNotification(sw.toString()));
        }

    }

    /**
     * Delete the object, referenced by xPath
     * @param deleteCommand Node with xpath as parameter
     * @throws XPathExpressionException
     */
    private static void deleteNode(Document doc, Node deleteCommand, Console console) throws XPathExpressionException {

        String path = deleteCommand.getTextContent();
        LOG.info("Try to delete: '"+path+"'");
        if (path != null && ! path.isEmpty()) {
            Node toBeDeleted = getNode(doc, path);

            if (toBeDeleted != null) {
                toBeDeleted.getParentNode().removeChild(toBeDeleted);
                LOG.info(console.cliOutput(CONSOLEPREFIX1+" deleted: '"+path+"'"));
            } else {
                LOG.info("Node to delete not found by xpath: '"+path+"'");
            }
        } else {
            LOG.warn("Delete with xpath null or empty: "+doc.getBaseURI());
        }
    }

    /**
     * Read file into String
     * @param path filename
     * @return String with content
     * @throws IOException
     */
    private static String readFile(String path) throws IOException {
        byte[] encoded = Files.readAllBytes(Paths.get(path));
        return new String(encoded, StandardCharsets.UTF_8);
    }

    /**
     * Generate filename for YANG file using idendifier and revision date
     * @param schemaPath
     * @param schema
     * @return
     */
    private static String getFilenameFromNode( String schemaPath, Node schema ) {

        if (schema != null) {
            return schemaPath + "/"+getNodeAttribute(schema, "identifier")+"@"+getNodeAttribute(schema, "version")+".yang";
        } else {
            return EMPTY;
        }

    }

    /*-----------------------------------------------------------------------
     * Public functions to processing of messages and modify database
     */

    /**
     * Modify data in document. Insert new or update object in document.
     * @param doc Document with ltp to change
     * @param node Document with command and objects to modify
     * @param transformer conversion to string
     * @param notificationXmls contains xml Strings with all created notifications
     * @throws XPathExpressionException Wrong xpath
     * @throws TransformerException
     */
    private static void processDynamic(Document doc, String idx, Transformer transformer, List<String> notificationXmls, Console console) throws XPathExpressionException, TransformerException {

        Node node = getNode(doc, idx);

        if (node == null) {
            LOG.warn( console.cliOutput("Can not find any action for node index: '"+idx+"'"));
            return;
        }

        for (Node item : getChildElementNodes(node)) {
            System.out.println("--> Processing node: "+item.getNodeName());

            if (item.getNodeName().equals("network-element")) {
                // Exchange / create LTPs already done in earlier step. Ignore here
                replaceChilds( getNode(doc, "//data/network-element"), item, console );

            } else if (item.getNodeName().equals("mw-notifications")) {
                // Generate Notification .. Ignore here
                getNotifications( item, transformer, notificationXmls);

            } else if (item.getNodeName().equals("delete")) {
                // Delete from doc
                deleteNode(doc, item, console);

            } else {
                // Create remaining objects
                appendChild(getNode(doc, "//data"), item, null, console);

            }

        }
    }

    /***
     * Execute NETCONF 'edit-config'-command against documents network-element.
     * Verify all LTS by uuid ->
     *      if exists in both lists .. do nothing
     *       if exists in command and not in document .. check if instructions available .. if yes do create according to instructions.
     *       if exists in document ..  check if instructions available .. if yes do remove according to instructions.
     * @param xml String with complete message
     */

    public List<String> editconfigElement(String sessionId, String xml) {

        LOG.info("Start processing of edit-config");

        List<String> res = new ArrayList<String>();

        try {
            final Document inDoc = loadXMLFromString(xml);

            //Create the first list with the object status
            HashSet<String> firstList;
            firstList = getNodeIds(doc, "//data/network-element/ltp",UUIDNAME);
            firstList.addAll(getNodeIds(doc, "//data/network-element/fd",UUIDNAME));
            firstList.addAll(getXmlAttributes(doc, "//data/network-element/fd/fc"));

            //Create the second list with the goal to reach
            HashSet<String> secondList;
            secondList = getNodeIds(inDoc, "//network-element/ltp",UUIDNAME);
            secondList.addAll(getNodeIds(inDoc, "//network-element/fd",UUIDNAME));
            secondList.addAll(getXmlAttributes(inDoc, "//network-element/fd/fc"));

            //Debug purpose
            LOG.info("List1 doc: "+listToString(firstList));
            LOG.info("List2 doc: "+listToString(secondList));

            //Not in first, but in second => Create
            for ( String idx : getStringInFirstNotInSecond( secondList, firstList ) ) {
                LOG.info(consoleMessage("Create action: "+idx));
                processDynamic(doc, "//"+idx+"/create", transformer, res, console);
            }

            //In first, and in second => Update (Not implemented)
            for ( String idx : getStringInBoth( firstList, secondList ) ) {
                LOG.info("Update: "+idx+" (do not change anything)");
            }

            //In first, but not in second => Remove (Not implemented)
            for ( String idx : getStringInFirstNotInSecond( firstList, secondList ) ) {
                LOG.info(consoleMessage("Remove action: "+idx));
                processDynamic(doc, "//"+idx+"/remove", transformer, res, console);
            }

        } catch (Exception e) {
            LOG.error("(..something..) failed", e);
        }

        //Add positive reply at very beginning
        res.add(0, assembleRpcReplyOk(sessionId));

        return res;
    }


    /**
     * Deliver the YANG files for the requested schema
     * @param sessionId for generatin an answer
     * @param xml from received message
     * @return answer xml
     */
    public String getSchema(String sessionId, String xml) {

        LOG.info("Start processing of get-schema");

        StringBuffer res = new StringBuffer();

        try {
            final Document inDoc = loadXMLFromString(xml);

            Node schema = getNode(inDoc, "//get-schema");
            if (schema != null) {
                String fileName = getFilenameFromNode(schemaPath, schema);
                System.out.println("Load schema: "+fileName);
                String yang = readFile(fileName);

                appendXmlMessageRpcReplyOpen(res, sessionId);
                res.append("<data xmlns=\"urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring\">");
                //res.append("<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">");
                res.append(yang);
                //res.append("</xs:schema>");
                appendXmlMessageRpcReplyClose(res);
            }
        } catch (Exception e) {
            LOG.error("(..something..) failed", e);
        }


        return res.toString();
    }

    /**
     * User action for sending notification
     * @param command 'l' to list all notifications or number to send related notification
     * @return XML content or null
     */
    public String doProcessUserAction(String command) {

        LOG.info("-- Notification start -- Command: '"+command+"'");

        List<String> xmlSubTreeProblems = getXmlSubTreeAsStringList("//mw-notifications/problem-notification");
        xmlSubTreeProblems.addAll(getXmlSubTreeAsStringList("//MW_Notifications/ProblemNotification"));
        List<String> xmlSubTreeChanges = getXmlSubTreeAsStringList("//mw-notifications/attribute-value-changed-notification");
        xmlSubTreeChanges.addAll(getXmlSubTreeAsStringList("//MW_Notifications/AttributeValueChangedNotification"));
        int idx = 0;

        if (command.startsWith("x")) {

            listNodesToConsole(doc, "//data", console);

        } else if (command.startsWith("l")) {
            consoleMessage("Lists of problems and changes");
            for (String xmlString : xmlSubTreeProblems) {
                consoleMessage("["+idx+"]:"+xmlString);
                idx++;
            }
            for (String xmlString : xmlSubTreeChanges) {
                consoleMessage("["+idx+"]:"+xmlString);
                idx++;
            }
         } else {

            idx = Integer.parseInt(command.trim());
            String xmlSubTree = null;

            if (idx < xmlSubTreeProblems.size()) {
                xmlSubTree = xmlSubTreeProblems.get(idx);
            } else {
                idx = idx - xmlSubTreeProblems.size();
                if (idx < xmlSubTreeChanges.size()) {
                    xmlSubTree = xmlSubTreeChanges.get(idx);
                }
            }
            if (xmlSubTree != null ) {
                return assembleRpcNotification(xmlSubTree);
            }
        }
           LOG.info("-- Notification end --");
        return null;
    }

    /*-----------------------------------------------------------------------
     * Functions to create READ message content to deliver answers back to the SDN controller.
     */

    /**
     * Send hello Answer
     * @param sessionId of message message
     * @return xml String with result
     */
    public String assembleHelloReply(String sessionId) {

        String xmlSubTree = getXmlSubTreeAsString("//capabilities");
        StringBuffer res = new StringBuffer();
        res.append("<hello xmlns:nc=\"urn:ietf:params:xml:ns:netconf:base:1.0\" xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n");
        res.append(xmlSubTree);
        appendXml(res, "session-id", sessionId == null ? "1" : sessionId);
        res.append("</hello>");
        return res.toString();

    }

    /**
     * Send Reply with no data, normally after get-config request
     * @param id of message message
     * @return xml String with result
     */
    public String assembleRpcReplyEmptyData(String id) {
        StringBuffer res = new StringBuffer();
        appendXmlMessageRpcReplyOpen(res, id);
        res.append("<data/>\n");
        appendXmlMessageRpcReplyClose(res);
        return res.toString();

        /*return "<rpc-reply xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\" xmlns:nc=\"urn:ietf:params:xml:ns:netconf:base:1.0\" last-modified=\"2017-03-07T19:32:31Z\" message-id=\""+id+"\">\n" +
                "  <data/>\n" +
                "</rpc-reply>\n";/**/
    }

    /**
     * Send rcp-reply empty data reply with ok
     * @param id of message message
     * @return xml String with result
     */
    public String assembleRpcReplyEmptyDataOk(String id) {

        StringBuffer res = new StringBuffer();
        appendXmlMessageRpcReplyOpen(res, id);
        res.append("<data/>\n");
        res.append("<ok/>\n");
        appendXmlMessageRpcReplyClose(res);
        return res.toString();

        /*return "<rpc-reply xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\" xmlns:nc=\"urn:ietf:params:xml:ns:netconf:base:1.0\" last-modified=\"2017-03-07T19:32:31Z\" message-id=\""+id+"\">\n" +
                "  <data/>\n" +
                "<ok/>\n" +
                "</rpc-reply>\n";/**/
    }

    /**
     * Send rcp-reply with ok
     * @param id of message message
     * @return xml String with result
     */
    public String assembleRpcReplyOk(String id) {

        StringBuffer res = new StringBuffer();
        appendXmlMessageRpcReplyOpen(res, id);
        res.append("<ok/>\n");
        appendXmlMessageRpcReplyClose(res);
        return res.toString();
    }


    /**
     * Assemble rpc-reply message from model according to paramers
     * @param id    MessageId for answer
     * @param name Name of root element of subtree
     * @param namespace Namespace of root elemenet
     * @param xmlSubTree xml-data with requested subtree
     * @return xml String with rpc-reply message
     */
    public String assembleRpcReplyMessage(String id, String name, String namespace, String xmlSubTree) {
        StringBuffer res = new StringBuffer();
        appendXmlMessageRpcReplyOpen(res, id);
        res.append("<data/>\n");
        res.append(xmlSubTree.replaceFirst(name, name+" xmlns=\""+namespace+"\""));
        res.append("</data>\n");
        appendXmlMessageRpcReplyClose(res);
        return res.toString();

        /*return "<rpc-reply xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\" xmlns:nc=\"urn:ietf:params:xml:ns:netconf:base:1.0\" last-modified=\"2017-03-07T19:32:31Z\" message-id=\""+id+"\">\n" +
                xmlSubTree.replaceFirst(name, name+" xmlns=\""+namespace+"\"") +
                "  </data>\n" +
                "</rpc-reply>\n";

        /**/
    }

    /**
     * Assemble rpc-reply message from model according to paramers
     * @param id    MessageId for answer
     * @param tags list with element for filter information
     * @return xml String with rpc-reply message
     */
   public String assembleRpcReplyFromFilterMessage(String id, NetconfTagList tags) {
        if (tags.isEmtpy() ) {

            return assembleRpcReplyEmptyData(id);

            //log.warn("Taglist shouldn't be empty");
            //return EMPTY;
        } else {
            NetconfTag root = tags.getRoot();
            String xmlSubTreePath = tags.getSubTreePath();
            LOG.info("Subtreepath="+xmlSubTreePath);
            String xmlSubTree = getXmlSubTreeAsString("//data/"+xmlSubTreePath);
            NetconfTag idx = tags.getIdx(0);
            StringBuffer res = new StringBuffer();

            appendXmlMessageRpcReplyOpen(res, id);
            res.append("<data>\n");
            if (idx != null) {

                //Open Example: "    <MW_AirInterface_Pac xmlns=\"uri:onf:MicrowaveModel-ObjectClasses-AirInterface\">\n" +
                appendXmlTagOpen( res, root.getName(), root.getNamespace() );
                //Example: "<layerProtocol>ffffffffff</layerProtocol>
                appendXml( res, idx.getName(), idx.getValue());
                //Subtree
                if (root.getName().equals(idx.getName())) {
                    //Root is requested
                    res.append(xmlSubTree.replaceFirst("<"+root.getName()+">", "").replaceFirst("</"+root.getName()+">",""));
                } else {
                    res.append(xmlSubTree);
                }
                //Close
                appendXmlTagClose(res, root.getName());
            } else {
                res.append(xmlSubTree.replaceFirst(root.getName(), root.getName()+" xmlns=\""+root.getNamespace()+"\""));
            }
            res.append("</data>\n");
            appendXmlMessageRpcReplyClose(res);
            return res.toString();
        }
    }

    /*------------------
     * DEBUG Help from ODL/OPENYUMA log as example for attribute change notification
       <notification xmlns="urn:ietf:params:xml:ns:netconf:notification:1.0">
          <eventTime>2017-03-28T15:11:12Z</eventTime>
          <AttributeValueChangedNotification xmlns="uri:onf:MicrowaveModel-Notifications">
            <counter>9648</counter>
            <timeStamp>20170328171112.1Z</timeStamp>
            <objectIdRef>LP-MWPS-ifIndex1</objectIdRef>
            <attributeName>airInterfaceStatus/modulationCur</attributeName>
            <newValue>128</newValue>
          </AttributeValueChangedNotification>
        </notification>
     */

    private static String assembleRpcNotification(String xmlSubTree ) {

        StringBuffer res = new StringBuffer();
        res.append("<notification xmlns=\"urn:ietf:params:xml:ns:netconf:notification:1.0\">\n");
        res.append("<eventTime>2011-11-11T11:11:11Z</eventTime>\n");
        //res.append("<eventTime>$TIME</eventTime>\n");
        res.append(xmlSubTree);
        res.append("</notification>\n");
        return res.toString();

    }


    /* ------------------
     * DEBUG Help from ODL/OPENYUMA log as example for problem notification
        <notification xmlns="urn:ietf:params:xml:ns:netconf:notification:1.0">
        <eventTime>2017-03-28T15:11:12Z</eventTime>
        <ProblemNotification xmlns="uri:onf:MicrowaveModel-Notifications">
          <counter>9648</counter>
          <timeStamp>20170328171112.1Z</timeStamp>
          <objectIdRef>LP-MWPS-ifIndex1</objectIdRef>
          <problem>signalIsLost</problem>
          <severity>critical</severity>
        </ProblemNotification>
      </notification>
    */

    /*-----------------------------------------------------------------------
     * Functions to create WRITE message content to deliver answers back to the SDN controller.
     */

    /* DEBUG Help with example of ODL netconf write message to mountpoint sim34tdm, creating something new.
     * Tag fingerprint: rpc edit-config target running default-operation config
        2017-04-24 12:56:37,199 | TRACE | qtp87320730-9024 | NetconfDeviceCommunicator        | 214 - org.opendaylight.netconf.sal-netconf-connector - 1.4.1.Boron-SR1 |
        RemoteDevice{sim34tdm}: Sending message
        <rpc message-id="m-56" xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
        <edit-config>
        <target>
        <running/>
        </target>
        <default-operation>none</default-operation>
        <config>
            <forwarding-construct xmlns="urn:onf:params:xml:ns:yang:core-model" xmlns:a="urn:ietf:params:xml:ns:netconf:base:1.0" a:operation="replace">
            <uuid>FC1</uuid>
            <forwarding-direction>bidirectional</forwarding-direction>
            <fc-port>
            <uuid>FCPort2</uuid>
            <fc-port-direction>bidirectional</fc-port-direction>
            </fc-port>
            <fc-port>
            <uuid>FCPort1</uuid>
            <fc-port-direction>bidirectional</fc-port-direction>
            </fc-port>
            <layer-protocol-name>ETH</layer-protocol-name>
            <is-protection-lock-out>true</is-protection-lock-out>
            </forwarding-construct>
        </config>
        </edit-config>
        </rpc>

    * Queued RPC Message:NetconfIncommingMessageRepresentation RpcIn [messageType=rpc,
    * tags=NetconfTagList [tags=[
    *     NetconfTag [namespace=urn:ietf:params:xml:ns:netconf:base:1.0, name=edit-config, values=[]],
    *     NetconfTag [namespace=urn:ietf:params:xml:ns:netconf:base:1.0, name=target, values=[]],
    *     NetconfTag [namespace=urn:ietf:params:xml:ns:netconf:base:1.0, name=running, values=[]],
    *     NetconfTag [namespace=urn:ietf:params:xml:ns:netconf:base:1.0, name=default-operation, values=[none]],
    *     NetconfTag [namespace=urn:ietf:params:xml:ns:netconf:base:1.0, name=config, values=[]],
    *     NetconfTag [namespace=urn:onf:params:xml:ns:yang:microwave-model, name=mw-ethernet-container-pac, values=[]],
    *     NetconfTag [namespace=urn:onf:params:xml:ns:yang:microwave-model, name=layer-protocol, values=[ETHC1]],
    *     NetconfTag [namespace=urn:onf:params:xml:ns:yang:microwave-model, name=ethernet-container-configuration, values=[]],
    *     NetconfTag [namespace=urn:onf:params:xml:ns:yang:microwave-model, name=container-id, values=[ID17]]]],
    *
    * messageUri=urn:ietf:params:xml:ns:netconf:base:1.0,
    * content={edit-config.target.running.default-operation=none, edit-config.target.running.default-operation.config.mw-ethernet-container-pac.layer-protocol.ethernet-container-configuration.container-id=ID17, edit-config.target.running.default-operation.config.mw-ethernet-container-pac.layer-protocol=ETHC1},
    *
    *     getMessageId()=m-65, getCtx()=null, xmlSourceMessage='<?xml version="1.0" encoding="UTF-8" standalone="no"?>
    */



    /*---------------------------------------------------
     * Base functions for message creation
     */

    private static StringBuffer appendXmlMessageRpcReplyOpen( StringBuffer res, String id ) {
        res.append("<rpc-reply xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\" xmlns:nc=\"urn:ietf:params:xml:ns:netconf:base:1.0\" last-modified=\"2017-03-07T19:32:31Z\" message-id=\"");
        res.append(id);
        res.append("\">\n");
        return res;
    }

    private static StringBuffer appendXmlMessageRpcReplyClose( StringBuffer res ) {
        res.append("</rpc-reply>");
        return res;
    }

    private static StringBuffer appendXmlTagOpen( StringBuffer res, String name, String nameSpace ) {
        res.append("<");
        res.append(name);
        res.append(" xmlns=\"");
        res.append(nameSpace);
        res.append("\">\n");
        return res;
    }

    private static StringBuffer appendXmlTagOpen( StringBuffer res, String name ) {
        res.append("<");
        res.append(name);
        res.append(">");
        return res;
    }

    private static StringBuffer appendXmlTagClose( StringBuffer res, String name ) {
        res.append("</");
        res.append(name);
        res.append(">\n");
        return res;
    }

    private static StringBuffer appendXml( StringBuffer res, String name, String value ) {
        appendXmlTagOpen(res, name);
        res.append(value);
        appendXmlTagClose(res, name);
        return res;
    }

}
