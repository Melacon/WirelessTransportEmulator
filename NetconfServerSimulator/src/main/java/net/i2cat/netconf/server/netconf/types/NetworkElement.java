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
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
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

	private final Transformer transformer;
	private final String schemaPath;
	private final Console console;
	private Document doc = null;
	private String nePath = null;


	private enum Event {
		PROBLEM_NOTIFICATION, NOTIFICATION, ATTRIBUTE_VALUE_CHANGED_NOTIFICATION
	};

	private enum EventOpenRoadm {
		ALARM_NOTIFICATION, NOTIFICATION, ATTRIBUTE_VALUE_CHANGED_NOTIFICATION, CREATE_TECH_INFO_NOTIFICATION
	};

	/*
	 * --------------------------------------------------------------- Constructor
	 */

	public NetworkElement(String filename, String schemaPath, String uuid, Console console) throws SAXException,
			IOException, ParserConfigurationException, TransformerConfigurationException, XPathExpressionException {

		LOG.debug("Networkelements uses file: " + filename);
		File file = new File(filename);

		FileInputStream fis = new FileInputStream(file);
		doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(fis);
		fis.close();

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
			LOG.info(consoleMessage("Found number of schemas in XML-file: " + schemas.getLength()));
			for (int n = 0; n < schemas.getLength(); n++) {
				node = schemas.item(n);

				String fileName = getFilenameFromNode(schemaPath, node);
				File fileSchema = new File(fileName);
				if (!fileSchema.exists() || fileSchema.isDirectory()) {
					sbNotOk.append("\t" + fileSchema.getName() + "\n");
				} else {
					sbOk.append("\t" + fileSchema.getName() + "\n");
				}
			}
			if (sbOk.length() > 0) {
				LOG.info(consoleMessage("Schemas-OK:\n" + sbOk.toString()));
			}
			if (sbNotOk.length() > 0) {
				LOG.info(consoleMessage("Schemas-NOT OK:\n" + sbNotOk.toString()));
			}

			// Analyse mode version
			Node neNode;
			nePath = "//data/network-element"; // ONF V1.2
			neNode = getNode(doc, nePath);
			if (neNode == null) {
				nePath = "//data/NetworkElement"; // ONF V1.0
				neNode = getNode(doc, nePath);
			}
			if (neNode == null) {
				LOG.error(consoleMessage("Can not find networkelement definition"));
			} else {
				LOG.info(consoleMessage("Network element root: " + nePath));
			}

			// Get UUID
			Node uuidNode = getNode(doc, nePath + "/" + UUIDNAME);
			if (uuidNode != null) {
				if (uuid != null && !uuid.isEmpty()) {
					LOG.info(consoleMessage("Overwrite uuid and name with parameter " + uuid));
					uuidNode.setTextContent(uuid);
					Node nameNode = getNode(doc, "//data/network-element/name/value");
					if (nameNode != null) {
						LOG.info(consoleMessage("Overwrite name with parameter " + uuid));
						nameNode.setTextContent(uuid);
					}
				}
				LOG.info(consoleMessage("device info uuid'" + uuidNode.getTextContent() + "'"));
			} else {
				LOG.info(consoleMessage("no uuid found within xml-File"));
			}

		} else {
			throw new IllegalArgumentException("Invalid schema directory: '" + String.valueOf(schemaPath) + "'");
		}
	}

	public NetworkElement(String filename, String schemaPath) throws SAXException, IOException,
			ParserConfigurationException, TransformerConfigurationException, XPathExpressionException {
		this(filename, schemaPath, null, null);
	}

	/*
	 * --------------------------------------------------------------- Private and
	 * static functions to generate some output
	 */

	/**
	 * Message to console
	 * 
	 * @param msg content
	 * @return again the msg
	 */
	private String consoleMessage(String msg) {
		return console.cliOutput("NE:" + msg);
	}

	/**
	 * Helper function to print set of strings
	 * 
	 * @param set to be converted to string
	 * @return string with output
	 */
	private static String listToString(Set<String> set) {
		StringBuffer sb = new StringBuffer();
		for (String s : set) {
			sb.append(' ');
			sb.append(s);
		}
		return sb.toString();
	}

	/**
	 * Create a list of the actual document structure
	 * 
	 * @param doc       the document
	 * @param xPathRoot the start node that is listed
	 */
	private static void listNodesToConsole(Document doc, String xPathRoot, Console console) {
		Node root;

		try {
			root = getNode(doc, xPathRoot);
			for (Node l1 : getChildElementNodes(root)) {
				console.cliOutput(l1.getNodeName() + "-<root>");
				for (Node l2 : getChildElementNodes(l1)) {
					console.cliOutput(l1.getNodeName() + "-" + l2.getNodeName());
					for (Node l3 : getChildElementNodes(l2)) {
						console.cliOutput(l1.getNodeName() + "-" + l2.getNodeName() + "-" + l3.getNodeName());
					}
				}
			}
		} catch (XPathExpressionException e) {
			LOG.error("(..something..) failed", e); // TODO
		}

	}

	/*
	 * --------------------------------------------------------------- Private and
	 * static functions to modify XML documents
	 */

	/**
	 * Convert XML-String into Document
	 * 
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
	 * get nodelist by xpath Examples xpath:
	 * "//MW_EthernetContainer_Pac/ethernetContainerCurrentProblems" final
	 * XPathExpression xpath =
	 * XPathFactory.newInstance().newXPath().compile("//ethernetContainerCurrentProblems/currentProblemList/sequenceNumber");
	 * 
	 * @param doc  document with xml data
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
	 * 
	 * @param doc  document with xml data
	 * @param root xpath declaration of root
	 * @return the Node
	 * @throws XPathExpressionException if something wrong
	 */
	private static Node getNode(Document doc, String root) throws XPathExpressionException {

		XPathExpression xpath = XPathFactory.newInstance().newXPath().compile(root);
		Node node = (Node) xpath.evaluate(doc, XPathConstants.NODE);
		return node;

	}

	/**
	 * get all child nodes with type ELEMENT_NODE back in a list
	 * 
	 * @param node parent node
	 * @return List with child nodes
	 */
	private static List<Node> getChildElementNodes(Node node) {
		List<Node> res = new ArrayList<>();
		NodeList childs = node.getChildNodes();
		Node item;
		// System.out.println("Query node "+node.getNodeName());
		for (int n = 0; n < childs.getLength(); n++) {
			item = childs.item(n);
			// System.out.println(node.getNodeName()+"-"+item.getNodeName()+"
			// "+item.getNodeType());
			if (item.getNodeType() == Node.ELEMENT_NODE) {
				res.add(childs.item(n));
			}
		}
		return res;
	}

	/**
	 * Subtree access to the XML tree that was created by the xml file. If Element
	 * is a list with more than one element .. all are delivered back.
	 * 
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
			LOG.debug("XML XPath problem: " + e.getMessage());
		} catch (TransformerException e) {
			LOG.debug("XML Transformer problem: " + e.getMessage());
		}

		return sw.toString();
	}

	/**
	 * Subtree access to the XML tree that was created by the xml file. If Element
	 * is a list with more than one element .. all are delivered back.
	 * 
	 * @param root starting node to read subtree
	 * @return List of strings with subtree content with one element for node of the
	 *         expected list
	 */
	private List<String> getXmlSubTreeAsStringList(String root) {
		List<String> res = new ArrayList<>();
		try {
			NodeList nodeList = getNodeList(doc, root);

			StringWriter sw = new StringWriter();
			for (int t = 0; t < nodeList.getLength(); t++) {
				sw = new StringWriter();
				transformer.transform(new DOMSource(nodeList.item(t)), new StreamResult(sw));
				res.add(sw.toString());
			}
		} catch (XPathExpressionException e) {
			LOG.debug("XML XPath problem: " + e.getMessage());
		} catch (TransformerException e) {
			LOG.debug("XML Transformer problem: " + e.getMessage());
		}
		return res;
	}

	/**
	 * Subtree access to the XML tree that was created by the xml file. If Element
	 * is a list with more than one element .. all are delivered back.
	 * 
	 * @param root starting node to read subtree
	 * @return string with subtree content
	 */
	public synchronized String getXmlSubTreeAsString(String root) {
		return getXmlSubTreeAsString(doc, root, transformer);
	}

	/**
	 * Get specific attribute content as String.
	 * 
	 * @param node      The node in question
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
	 * Collect from the given root a list with the given attribute that is used as
	 * univied ID.
	 * 
	 * @param doc          document to search
	 * @param root         starting node to read subtree
	 * @param attibuteName or null if the value itself is the id
	 * @return HashSet with all id.
	 */
	private static HashSet<String> getNodeIds(Document doc, String root, String attibuteName) {
		HashSet<String> res = new HashSet<>();
		try {
			final XPathExpression xpath = XPathFactory.newInstance().newXPath().compile(root);
			final NodeList nodeList = (NodeList) xpath.evaluate(doc, XPathConstants.NODESET);

			String uuidString;
			for (int t = 0; t < nodeList.getLength(); t++) {
				uuidString = attibuteName.isEmpty() ? nodeList.item(t).getTextContent()
						: getNodeAttribute(nodeList.item(t), attibuteName);
				if (!uuidString.isEmpty()) {
					res.add(uuidString);
				}
			}
		} catch (XPathExpressionException e) {
			LOG.debug("XML XPath problem: " + e.getMessage());
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
	 * 
	 * @param indexListFirst  list1 input
	 * @param indexListSecond list2 input
	 * @return All index matching criteria
	 */
	private static HashSet<String> getStringInBoth(HashSet<String> indexListFirst, HashSet<String> indexListSecond) {
		HashSet<String> res = new HashSet<>();
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
	 * 
	 * @param indexListFirst  list1 input
	 * @param indexListSecond list2 input
	 * @return All index matching criteria
	 */
	private static HashSet<String> getStringInFirstNotInSecond(HashSet<String> indexListFirst,
			HashSet<String> indexListSecond) {
		HashSet<String> res = new HashSet<>();
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
	 * 
	 * @param destination node to be appended
	 * @param newChild    element to add
	 * @param idx         null or index information for console output
	 */
	private static void appendChild(Node destination, Node newChild, String idx, Console console) {

		StringBuffer msg = new StringBuffer();
		msg.append(CONSOLEPREFIX1);
		msg.append("append " + newChild.getNodeName() + " to " + destination.getNodeName());
		if (idx != null) {
			msg.append(" idx[" + idx + "]");
		}

		LOG.info(console.cliOutput(msg.toString()));
		Node copy = newChild.cloneNode(true);

		destination.appendChild(copy);
	}

	/**
	 * Replace a node by a node
	 * 
	 * @param destination parent node if child.
	 * @param child       has to be replaced
	 * @param newChild    replace with this content
	 * @param idx         null or index information for console output.
	 */
	private static void replaceChild(Node destination, Node child, Node newChild, String idx, Console console) {

		StringBuffer msg = new StringBuffer();
		msg.append(CONSOLEPREFIX1);
		msg.append("replace " + newChild.getNodeName() + " to " + destination.getNodeName());
		if (idx != null) {
			msg.append(" idx[" + idx + "]");
		}
		LOG.info(console.cliOutput(msg.toString()));

		Node copy = newChild.cloneNode(true);
		destination.replaceChild(copy, child);
	}

	/**
	 * replace or create a child node by a new node. Only nodes with UUID and of the
	 * type ELEMENT_NODE are effected.
	 * 
	 * @param destination has the effected attributes
	 * @param newChild    is the new attribute that replaces an existing one or is
	 *                    added.
	 */
	private static void replaceChild(Node destination, Node newChild, Console console) {

		if (newChild.getNodeType() == Node.ELEMENT_NODE) {

			String newChildUuidString = getNodeAttribute(newChild, UUIDNAME);
			if (!newChildUuidString.isEmpty()) {

				NodeList destinationChilds = destination.getChildNodes();
				String destinationUuidString = EMPTY;
				boolean found = false;
				Node child = null;

				for (int i = 0; i < destinationChilds.getLength(); i++) {
					destinationUuidString = getNodeAttribute(child = destinationChilds.item(i), UUIDNAME);
					if (newChildUuidString.equals(destinationUuidString)) {
						found = true;
						break;
					}
				}

				if (found) {
					replaceChild(destination, child, newChild, newChildUuidString, console);

				} else {
					appendChild(destination, newChild, newChildUuidString, console);
				}
			}
		}
	}

	/**
	 * Replaces attributes of a destination node by new attribute entries in a
	 * source node
	 * 
	 * @param destination attributes to be replaced
	 * @param source      new attributes to replace existing ones or to be added
	 */
	private static void replaceChilds(Node destination, Node source, Console console) {

		for (Node sourceChild : getChildElementNodes(source)) {
			replaceChild(destination, sourceChild, console);
		}

	}

	/**
	 * Get list of notifications to be produced.
	 * 
	 * @param mwNotifications  contains list of specific notifications to be send
	 *                         out
	 * @param transformer      to map to xml string
	 * @param notificationXmls List, to be extended with new notifications
	 * @throws TransformerException Transforming problem
	 */
	private static void getNotifications(Node mwNotifications, Transformer transformer, List<String> notificationXmls)
			throws TransformerException {

		StringWriter sw;
		for (Node item : getChildElementNodes(mwNotifications)) {
			sw = new StringWriter();
			transformer.transform(new DOMSource(item), new StreamResult(sw));
			notificationXmls.add(assembleRpcNotification(sw.toString()));
		}

	}

	/**
	 * Delete the object, referenced by xPath
	 * 
	 * @param deleteCommand Node with xpath as parameter
	 * @throws XPathExpressionException
	 */
	private static void deleteNode(Document doc, Node deleteCommand, Console console) throws XPathExpressionException {

		String path = deleteCommand.getTextContent();
		LOG.info("Try to delete: '" + path + "'");
		if (path != null && !path.isEmpty()) {
			Node toBeDeleted = getNode(doc, path);

			if (toBeDeleted != null) {
				toBeDeleted.getParentNode().removeChild(toBeDeleted);
				LOG.info(console.cliOutput(CONSOLEPREFIX1 + " deleted: '" + path + "'"));
			} else {
				LOG.info("Node to delete not found by xpath: '" + path + "'");
			}
		} else {
			LOG.warn("Delete with xpath null or empty: " + doc.getBaseURI());
		}
	}

	/**
	 * Read file into String
	 * 
	 * @param path filename
	 * @return String with content
	 * @throws IOException if problem during read.
	 */
	private static String readFile(String path) throws IOException {
		byte[] encoded = Files.readAllBytes(Paths.get(path));
		return new String(encoded, StandardCharsets.UTF_8);
	}

	/**
	 * Generate filename for YANG file using idendifier and revision date
	 * 
	 * @param schemaPath
	 * @param schema     with schema 'identifier' and 'version' date
	 * @return filename of schema or empty.
	 */
	private static String getFilenameFromNode(String schemaPath, Node schema) {

		if (schema != null) {
			return schemaPath + "/" + getNodeAttribute(schema, "identifier") + "@" + getNodeAttribute(schema, "version")
					+ ".yang";
		} else {
			return EMPTY;
		}

	}

	/*-----------------------------------------------------------------------
	 * Public functions to processing of messages and modify database
	 */

	/**
	 * Modify data in document. Insert new or update object in document.
	 * 
	 * @param doc              Document with ltp to change
	 * @param node             Document with command and objects to modify
	 * @param transformer      conversion to string
	 * @param notificationXmls contains xml Strings with all created notifications
	 * @throws XPathExpressionException Wrong xpath
	 * @throws TransformerException
	 */
	private static void processDynamic(Document doc, String idx, Transformer transformer, List<String> notificationXmls,
			Console console) throws XPathExpressionException, TransformerException {

		Node node = getNode(doc, idx);

		if (node == null) {
			LOG.warn(console.cliOutput("Can not find any action for node index: '" + idx + "'"));
			return;
		}

		for (Node item : getChildElementNodes(node)) {
			System.out.println("--> Processing node: " + item.getNodeName());

			if (item.getNodeName().equals("network-element")) {
				// Exchange / create LTPs already done in earlier step. Ignore here
				replaceChilds(getNode(doc, "//data/network-element"), item, console);

			} else if (item.getNodeName().equals("mw-notifications")) {
				// Generate Notification .. Ignore here
				getNotifications(item, transformer, notificationXmls);
//			} else if (item.getNodeName().equals("openroadm-notifications")) {
//				// Generate Notification .. Ignore here
//				LOG.info("OpenRoadm Notifications are generated");
//				getNotifications(item, transformer, notificationXmls);

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
	 * Verify all LTS by uuid -> if exists in both lists .. do nothing if exists in
	 * command and not in document .. check if instructions available .. if yes do
	 * create according to instructions. if exists in document .. check if
	 * instructions available .. if yes do remove according to instructions.
	 * 
	 * @param xml String with complete message
	 */

	public synchronized List<String> editconfigElement(String sessionId, String xml) {

		LOG.info("Start processing of edit-config");

		List<String> res = new ArrayList<>();

		try {
			final Document inDoc = loadXMLFromString(xml);

			// Create the first list with the object status
			HashSet<String> firstList;
			firstList = getNodeIds(doc, "//data/network-element/ltp", UUIDNAME);
			firstList.addAll(getNodeIds(doc, "//data/network-element/fd", UUIDNAME));
			firstList.addAll(getXmlAttributes(doc, "//data/network-element/fd/fc"));

			// Create the second list with the goal to reach
			HashSet<String> secondList;
			secondList = getNodeIds(inDoc, "//network-element/ltp", UUIDNAME);
			secondList.addAll(getNodeIds(inDoc, "//network-element/fd", UUIDNAME));
			secondList.addAll(getXmlAttributes(inDoc, "//network-element/fd/fc"));

			// Debug purpose
			LOG.info("List1 doc: " + listToString(firstList));
			LOG.info("List2 doc: " + listToString(secondList));

			// Not in first, but in second => Create
			for (String idx : getStringInFirstNotInSecond(secondList, firstList)) {
				LOG.info(consoleMessage("Create action: " + idx));
				processDynamic(doc, "//" + idx + "/create", transformer, res, console);
			}

			// In first, and in second => Update (Not implemented)
			for (String idx : getStringInBoth(firstList, secondList)) {
				LOG.info("Update: " + idx + " (do not change anything)");
			}

			// In first, but not in second => Remove (Not implemented)
			for (String idx : getStringInFirstNotInSecond(firstList, secondList)) {
				LOG.info(consoleMessage("Remove action: " + idx));
				processDynamic(doc, "//" + idx + "/remove", transformer, res, console);
			}

		} catch (Exception e) {
			LOG.error("(..something..) failed", e);
		}

		// Add positive reply at very beginning
		res.add(0, assembleRpcReplyOk(sessionId));

		return res;
	}

	/**
	 * Deliver the YANG files for the requested schema
	 * 
	 * @param sessionId for generation an answer
	 * @param xml       from received message
	 * @return answer xml
	 */
	public synchronized String getSchema(String sessionId, String xml) {

		LOG.info("Start processing of get-schema");

		StringBuffer res;

		try {
			final Document inDoc = loadXMLFromString(xml);

			Node schema = getNode(inDoc, "//get-schema");
			if (schema != null) {

				String fileName = getFilenameFromNode(schemaPath, schema);
				consoleMessage("Load schema: " + fileName);
				String yang = readFile(fileName);
				// Assemble message
				res = new StringBuffer();
				res.append("<rpc-reply message-id=\"" + sessionId
						+ "\" xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n");
				res.append("<data xmlns=\"urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring\">\n");
				res.append("<![CDATA[");
				res.append(yang);
				res.append("]]>");
				res.append("\n</data>\n");
				appendXmlMessageRpcReplyClose(res);
			} else {
				LOG.warn("Can not find get-schema node");
				res = assembleRpcReplyError("invalid-value", "Can not find get-schma node");
			}
		} catch (Exception e) {

			LOG.warn("(..something..) failed", e);
			res = assembleRpcReplyError("invalid-value", e.getMessage());
		}
		return res.toString();
	}

	/**
	 * User action for sending notification
	 * 
	 * @param command <br>
	 *                'l' to list all notifications or number to send related
	 *                notification<br>
	 *                'nnn' to referenced notification immediately<br>
	 * @return XML content or null
	 */
	public synchronized String doProcessUserAction(String command) {

		LOG.info("-- Notification start -- Command: '" + command + "'");
		Map<Event, List<String>> enumMap = readAllEvents();
		Map<EventOpenRoadm, List<String>> enumMapOpenRoadm = readAllEventsOpenRoadm();
		if (!enumMap.values().stream().allMatch(list -> list.isEmpty())) {
			int idx = 0;
			if (command.startsWith("x")) {
				listNodesToConsole(doc, "//data", console);
			} else if (command.startsWith("l")) {
				consoleMessage("Lists of problems and changes");
				for (Event event : enumMap.keySet()) {
					for (String xmlString : enumMap.get(event)) {
						consoleMessage("[" + idx + "]:" + xmlString);
						idx++;
					}
				}
			} else {
				String input = command.trim();
				try {
					idx = Integer.parseInt(input);
					String xmlSubTree = getEvent(enumMap, idx);
					if (xmlSubTree != null) {
						return xmlSubTree;
					} else {
						LOG.info("No element found for '" + input + "'");
					}
				} catch (NumberFormatException e) {
					LOG.info("Not a number '" + input + "'");
				}
			}
			LOG.info("-- Notification end --");
		} else if (!enumMapOpenRoadm.values().stream().allMatch(list -> list.isEmpty())) {
			int idx = 0;
			if (command.startsWith("x")) {
				listNodesToConsole(doc, "//data", console);
			} else if (command.startsWith("l")) {
				consoleMessage("Lists of problems and changes for OpenRoadm");
				for (Map.Entry<EventOpenRoadm, List<String>> entry : enumMapOpenRoadm.entrySet()) {
					EventOpenRoadm k = entry.getKey();
					List<String> v = entry.getValue();
					consoleMessage("Events still saved are:"+ k);
					for(String s :v) {
						consoleMessage("Values are: " + s);
					}
				}
				for (EventOpenRoadm event : enumMapOpenRoadm.keySet()) {
					consoleMessage("Events Read are " + event.name());
					for (String xmlString : enumMapOpenRoadm.get(event)) {
						consoleMessage("[" + idx + "]:" + xmlString);
						idx++;
					}
				}
			} else {
				String input = command.trim();
				try {
					idx = Integer.parseInt(input);
					String xmlSubTree = getEventOpenRoadm(enumMapOpenRoadm, idx);
					if (xmlSubTree != null) {
						return xmlSubTree;
					} else {
						LOG.info("No element found for '" + input + "'");
					}
				} catch (NumberFormatException e) {
					LOG.info("Not a number '" + input + "'");
				}
			}
			LOG.info("-- Notification end --");
		}

		return null;
	}

	public String getxmlSubTree() {

		return null;
	}

	/**
	 * Get notification information from
	 * 
	 * @param idx of notification from database
	 * @return string with xml data or null if not found
	 */
	public synchronized @Nullable String getEvent(Integer idx) {

		Map<Event, List<String>> enumMap = readAllEvents();
		return getEvent(enumMap, idx);
	}

	/**
	 * Same method as getEvent(Integer idx) , created for OpenRoadmDevices Get
	 * notification information from
	 * 
	 * @param idx of notification from database
	 * @return string with xml data or null if not found
	 */
	public synchronized @Nullable String getEventOpenRoadm(Integer idx) {

		Map<EventOpenRoadm, List<String>> enumMap = readAllEventsOpenRoadm();
		return getEventOpenRoadm(enumMap, idx);
	}

	/**
	 * Collect all events from document
	 * 
	 * @return Map with all events
	 */
	private Map<Event, List<String>> readAllEvents() {

		List<String> xmlSubTreeProblems = getXmlSubTreeAsStringList("//mw-notifications/problem-notification");
		xmlSubTreeProblems.addAll(getXmlSubTreeAsStringList("//MW_Notifications/ProblemNotification"));
		List<String> xmlSubTreeChanges = getXmlSubTreeAsStringList("//mw-notifications/attribute-value-changed-notification");
		xmlSubTreeChanges.addAll(getXmlSubTreeAsStringList("//MW_Notifications/AttributeValueChangedNotification"));
		List<String> xmlSubTreeNotification = getXmlSubTreeAsStringList("//mw-notifications/notification");
		
		Map<Event, List<String>> enumMap = new EnumMap<>(Event.class);
		enumMap.put(Event.PROBLEM_NOTIFICATION, xmlSubTreeProblems);
		enumMap.put(Event.ATTRIBUTE_VALUE_CHANGED_NOTIFICATION, xmlSubTreeChanges);
		enumMap.put(Event.NOTIFICATION, xmlSubTreeNotification);



		return enumMap;
	}

	/**
	 * Same as readAllEvents- Created for OpenRoadm Devices Collect all events from
	 * document
	 * 
	 * @return Map with all events
	 */
	private Map<EventOpenRoadm, List<String>> readAllEventsOpenRoadm() {

		List<String> xmlSubTreeProblems = getXmlSubTreeAsStringList("//notifications/alarm-notification");
		xmlSubTreeProblems.addAll(getXmlSubTreeAsStringList("//Notifications/AlarmNotification"));
		List<String> xmlSubTreeChanges = getXmlSubTreeAsStringList("//notifications/change-notification");
		xmlSubTreeChanges.addAll(getXmlSubTreeAsStringList("//Notifications/ChangeNotification"));
		
		List<String> xmlSubTreecreateTechInfo = getXmlSubTreeAsStringList("//notifications/create-tech-info-notification");
		xmlSubTreecreateTechInfo.addAll(getXmlSubTreeAsStringList("//Notifications/CreateTechInfoNotification"));
		List<String> xmlSubTreeNotification = getXmlSubTreeAsStringList("//notifications/notification");

		Map<EventOpenRoadm, List<String>> enumMapOpenRoadm = new EnumMap<>(EventOpenRoadm.class);
		enumMapOpenRoadm.put(EventOpenRoadm.ALARM_NOTIFICATION, xmlSubTreeProblems);
		enumMapOpenRoadm.put(EventOpenRoadm.ATTRIBUTE_VALUE_CHANGED_NOTIFICATION, xmlSubTreeChanges);
		enumMapOpenRoadm.put(EventOpenRoadm.CREATE_TECH_INFO_NOTIFICATION, xmlSubTreecreateTechInfo);
		
		enumMapOpenRoadm.put(EventOpenRoadm.NOTIFICATION, xmlSubTreeNotification);

		return enumMapOpenRoadm;
	}

	/**
	 * Get event from event table
	 * 
	 * @param enumMap with all messages in doc
	 * @param idx     of the message according to list
	 * @return xml message for index or null
	 */
	private @Nullable String getEvent(Map<Event, List<String>> enumMap, int idx) {
		for (Event event : enumMap.keySet()) {
			List<String> xmlSubTreeEvents = enumMap.get(event);
			if (idx < xmlSubTreeEvents.size()) {
				String xmlMesg = xmlSubTreeEvents.get(idx);
				if (event == Event.NOTIFICATION) {
					return xmlMesg;
				} else {
					return assembleRpcNotification(xmlMesg);
				}
			} else {
				idx = idx - xmlSubTreeEvents.size();
			}
		}
		return null;
	}

	/**
	 * Same method as previous getEvent() for OpenRoadm Devices Get event from event
	 * table
	 * 
	 * @param enumMap with all messages in doc
	 * @param idx     of the message according to list
	 * @return xml message for index or null
	 */
	private @Nullable String getEventOpenRoadm(Map<EventOpenRoadm, List<String>> enumMap, int idx) {
		for (EventOpenRoadm event : enumMap.keySet()) {
			List<String> xmlSubTreeEvents = enumMap.get(event);
			if (idx < xmlSubTreeEvents.size()) {
				String xmlMesg = xmlSubTreeEvents.get(idx);
				if (event == EventOpenRoadm.NOTIFICATION) {
					return xmlMesg;
				} else {
					return assembleRpcNotification(xmlMesg);
				}
			} else {
				idx = idx - xmlSubTreeEvents.size();
			}
		}
		return null;
	}

	/*-----------------------------------------------------------------------
	 * Functions to create READ message content to deliver answers back to the SDN controller.
	 */

	/**
	 * Send hello Answer
	 * 
	 * @param sessionId of message message
	 * @return xml String with result
	 */
	public synchronized String assembleHelloReply(String sessionId) {

		String xmlSubTree = getXmlSubTreeAsString("//capabilities");
		StringBuffer res = new StringBuffer();
		res.append(
				"<hello xmlns:nc=\"urn:ietf:params:xml:ns:netconf:base:1.0\" xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n");
		res.append(xmlSubTree);
		appendXml(res, "session-id", sessionId == null ? "1" : sessionId);
		res.append("</hello>");
		return res.toString();

	}

	/**
	 * Send Reply with no data, normally after get-config request
	 * 
	 * @param id of message message
	 * @return xml String with result
	 */
	public synchronized String assembleRpcReplyEmptyData(String id) {
		StringBuffer res = new StringBuffer();
		appendXmlMessageRpcReplyOpen(res, id);
		res.append("<data/>\n");
		appendXmlMessageRpcReplyClose(res);
		return res.toString();

		/*
		 * return
		 * "<rpc-reply xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\" xmlns:nc=\"urn:ietf:params:xml:ns:netconf:base:1.0\" last-modified=\"2017-03-07T19:32:31Z\" message-id=\""
		 * +id+"\">\n" + "  <data/>\n" + "</rpc-reply>\n";/
		 **/
	}

	/**
	 * Send rcp-reply empty data reply with ok
	 * 
	 * @param id of message message
	 * @return xml String with result
	 */
	public synchronized String assembleRpcReplyEmptyDataOk(String id) {

		StringBuffer res = new StringBuffer();
		appendXmlMessageRpcReplyOpen(res, id);
		res.append("<data/>\n");
		res.append("<ok/>\n");
		appendXmlMessageRpcReplyClose(res);
		return res.toString();

		/*
		 * return
		 * "<rpc-reply xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\" xmlns:nc=\"urn:ietf:params:xml:ns:netconf:base:1.0\" last-modified=\"2017-03-07T19:32:31Z\" message-id=\""
		 * +id+"\">\n" + "  <data/>\n" + "<ok/>\n" + "</rpc-reply>\n";/
		 **/
	}

	/**
	 * Send rcp-reply with ok
	 * 
	 * @param id of message message
	 * @return xml String with result
	 */
	public synchronized String assembleRpcReplyOk(String id) {

		StringBuffer res = new StringBuffer();
		appendXmlMessageRpcReplyOpen(res, id);
		res.append("<ok/>\n");
		appendXmlMessageRpcReplyClose(res);
		return res.toString();
	}

	/**
	 * Assemble rpc-reply message from model according to paramers
	 * 
	 * @param id         MessageId for answer
	 * @param name       Name of root element of subtree
	 * @param namespace  Namespace of root elemenet
	 * @param xmlSubTree xml-data with requested subtree
	 * @return xml String with rpc-reply message
	 */
	public synchronized String assembleRpcReplyMessage(String id, String name, String namespace, String xmlSubTree) {
		StringBuffer res = new StringBuffer();
		appendXmlMessageRpcReplyOpen(res, id);
		res.append("<data/>\n");
		if (xmlSubTree.contains(namespace)) {
			res.append(xmlSubTree);
		} else {
			res.append(xmlSubTree.replaceFirst(name, name + " xmlns=\"" + namespace + "\""));
		}
		res.append("</data>\n");
		appendXmlMessageRpcReplyClose(res);
		return res.toString();

		/*
		 * return
		 * "<rpc-reply xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\" xmlns:nc=\"urn:ietf:params:xml:ns:netconf:base:1.0\" last-modified=\"2017-03-07T19:32:31Z\" message-id=\""
		 * +id+"\">\n" + xmlSubTree.replaceFirst(name, name+" xmlns=\""+namespace+"\"")
		 * + "  </data>\n" + "</rpc-reply>\n";
		 * 
		 * /
		 **/
	}

	/**
	 * Assemble rpc-reply message from model according to paramers
	 * 
	 * @param id   MessageId for answer
	 * @param tags list with element for filter information
	 * @return xml String with rpc-reply message
	 */
	public synchronized String assembleRpcReplyFromFilterMessage(String id, NetconfTagList tags) {
		if (tags.isEmtpy()) {

			return assembleRpcReplyEmptyData(id);

			// log.warn("Taglist shouldn't be empty");
			// return EMPTY;
		} else {

			String xmlSubTreePath = tags.getSubTreePath();
			LOG.info("Subtreepath=" + xmlSubTreePath);
			String xmlSubTree = getXmlSubTreeAsString("//data/" + xmlSubTreePath);


			/*
			 * consoleMessage("Tags: "+tags.asCompactString());
			 * consoleMessage("Ends with leaf: "+tags.endsWithLeave());
			 * consoleMessage("idx: "+String.valueOf(idx));
			 * consoleMessage("Subtreepath: "+xmlSubTreePath);
			 * //consoleMessage("xmlSubTree: "+(xmlSubTree.length() > 100 ?
			 * xmlSubTree.substring(0, 99) : xmlSubTree) );
			 */

			StringBuffer res = new StringBuffer();
			appendXmlMessageRpcReplyOpen(res, id);
			res.append("<data>\n");
			recuresAddContent(tags, res, xmlSubTree, 0);
			res.append("</data>\n");
			appendXmlMessageRpcReplyClose(res);

			return res.toString();
		}
	}
// RPC response for openRoadm Specific RPCs such as led-control
	public synchronized String assembleRpcResponseOpenRoadm(String id,int i) {
		StringBuffer res = new StringBuffer();
		appendXmlMessageRpcReplyOpen(res, id);

		res.append(rpcResponseStatus(i));
		appendXmlMessageRpcReplyClose(res);
//    	String xmlSubTreePath = "org-openroadm-device/circuit-packs";
//    	String xmlSubTree = getXmlSubTreeAsString("//data/" + "org-openroadm-device/circuit-packs");
//    	
//    	LOG.info("TestingXML " + xmlSubTree );
// xmlns=\"http://org/openroadm/device\"
		return res.toString();

		/*
		 * return
		 * "<rpc-reply xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\" xmlns:nc=\"urn:ietf:params:xml:ns:netconf:base:1.0\" last-modified=\"2017-03-07T19:32:31Z\" message-id=\""
		 * +id+"\">\n" + "  <data/>\n" + "<ok/>\n" + "</rpc-reply>\n";/
		 **/
	}
	
	public synchronized String assembleRpcResponseOpenRoadmCreateTechInfo(String id, NetconfTagList tags,int i) {
		
		StringBuffer res = new StringBuffer();
		NetconfTag tag = tags.getList().get(1);
		appendXmlMessageRpcReplyOpen(res, id);		
		res.append("<shelf-id xmlns=\"http://org/openroadm/device\">" + tag.getValue() +"</shelf-id>");
		res.append(rpcResponseStatus(i));
		appendXmlMessageRpcReplyClose(res);
		return res.toString();

	}
	public synchronized String assembleRpcResponseOpenRoadmCOnnPortTrail(String id, int i) {
		StringBuffer res = new StringBuffer();

		appendXmlMessageRpcReplyOpen(res, id);		

		res.append(rpcResponseStatus(i));
		res.append(getXmlSubTreeAsString("//data/" + "org-openroadm-device/circuit-packs/circuit-pack-name:1/0"));
		appendXmlMessageRpcReplyClose(res);
		return res.toString();
		
	}
	
	public synchronized String assembleRpcResponsePmData(String id, NetconfTagList tags, int i) {
		StringBuffer res = new StringBuffer();
		appendXmlMessageRpcReplyOpen(res, id);	
		if(tags.checkTags("collect-historical-pm-file")) {
			res.append("<pm-filename xmlns=\"http://org/openroadm/pm\">opt/dev/historical-pm.xml</pm-filename>\n");
		}
		res.append(rpcResponseStatus(i));		
		appendXmlMessageRpcReplyClose(res);
		return res.toString();
		
	}
	

	
	private String rpcResponseStatus(int i) {
		StringBuffer res = new StringBuffer();
		switch(i) {
		case 1: 
			res.append("<status xmlns=\"http://org/openroadm/device\">Successful</status>\n");
			res.append("<status-message xmlns=\"http://org/openroadm/device\">test</status-message>\n");
			break;
		case 2:
			res.append("<status xmlns=\"http://org/openroadm/de/swdl\">Successful</status>\n");
			res.append("<status-message xmlns=\"http://org/openroadm/de/swdl\">test</status-message>\n");
			break;
		
		case 3:
			res.append("<status xmlns=\"http://org/openroadm/file-transfer\">Successful</status>\n");
			res.append("<status-message xmlns=\"http://org/openroadm/file-transfer\">test</status-message>\n");
			break;
		case 4:
			res.append("<status xmlns=\"http://org/openroadm/fwdl\">Successful</status>\n");
			res.append("<status-message xmlns=\"http://org/openroadm/fwdl\">test</status-message>\n");
			break;
		case 5:
			res.append("<status xmlns=\"http://org/openroadm/pm\">Successful</status>\n");
			res.append("<status-message xmlns=\"http://org/openroadm/pm\">test</status-message>\n");
			break;	
		case 6:
			res.append("<status xmlns=\"http://org/openroadm/de/operations\">Successful</status>\n");
			res.append("<status-message xmlns=\"http://org/openroadm/de/operations\">test</status-message>\n");
			break;	
			
			
		}

		return res.toString();
	
	}
	
	
	/**
	 * Add content according to tag sequence
	 * 
	 * @param tags    tags of received message
	 * @param sb      filled with new answer message content
	 * @param content XML content that has to be delivered back
	 * @param idx     parameter doing the recursion. Start from 0 and increasing
	 */
	private void recuresAddContent(NetconfTagList tags, StringBuffer sb, String content, int idx) {

		if (idx < tags.size()) {
			// Get akt
			NetconfTag tag = tags.getList().get(idx);
			NetconfTag tagidx = null;
			boolean last;
			if (idx + 1 < tags.size()) {
				tagidx = tags.getList().get(idx + 1);
				if (!tagidx.hasOneValue()) {
					tagidx = null; // No index
					last = false;
				} else {
					last = !(idx + 2 < tags.size());
				}
			} else {
				last = true;
			}

			if (last) {
				// Leaf
				consoleMessage("lastLeaf" + content);
				sb.append(content.replaceFirst(tag.getName(), tag.getName() + " xmlns=\"" + tag.getNamespace() + "\""));
				
			} else {
				// Wrapper
				// Wrap Begin
				if (!tag.hasOneValue()) {
					appendXmlTagOpen(sb, tag.getName(), tag.getNamespace());
					recuresAddContent(tags, sb, content, idx + 1);
					// Wrap1 End
					appendXmlTagClose(sb, tag.getName());
					consoleMessage("Content"+ content);
				
				} else {
					appendXml(sb, tag.getName(), tag.getValue());
					recuresAddContent(tags, sb, content, idx + 1);
					consoleMessage("Content2" +content);
		
				}
			}
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

	private static String assembleRpcNotification(String xmlSubTree) {

		StringBuffer res = new StringBuffer();
		res.append("<notification xmlns=\"urn:ietf:params:xml:ns:netconf:notification:1.0\">\n");
//		res.append("<notification xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n");
		res.append("<eventTime>2020-05-05T11:11:11Z</eventTime>\n");
		// res.append("<eventTime>$TIME</eventTime>\n");
		res.append(xmlSubTree);
		res.append("</notification>\n");
		return res.toString();

	}

	/*
	 * ------------------ DEBUG Help from ODL/OPENYUMA log as example for problem
	 * notification <notification
	 * xmlns="urn:ietf:params:xml:ns:netconf:notification:1.0">
	 * <eventTime>2017-03-28T15:11:12Z</eventTime> <ProblemNotification
	 * xmlns="uri:onf:MicrowaveModel-Notifications"> <counter>9648</counter>
	 * <timeStamp>20170328171112.1Z</timeStamp>
	 * <objectIdRef>LP-MWPS-ifIndex1</objectIdRef> <problem>signalIsLost</problem>
	 * <severity>critical</severity> </ProblemNotification> </notification>
	 */

	/*-----------------------------------------------------------------------
	 * Functions to create WRITE message content to deliver answers back to the SDN controller.
	 */

	/*
	 * DEBUG Help with example of ODL netconf write message to mountpoint sim34tdm,
	 * creating something new. Tag fingerprint: rpc edit-config target running
	 * default-operation config 2017-04-24 12:56:37,199 | TRACE | qtp87320730-9024 |
	 * NetconfDeviceCommunicator | 214 -
	 * org.opendaylight.netconf.sal-netconf-connector - 1.4.1.Boron-SR1 |
	 * RemoteDevice{sim34tdm}: Sending message <rpc message-id="m-56"
	 * xmlns="urn:ietf:params:xml:ns:netconf:base:1.0"> <edit-config> <target>
	 * <running/> </target> <default-operation>none</default-operation> <config>
	 * <forwarding-construct xmlns="urn:onf:params:xml:ns:yang:core-model"
	 * xmlns:a="urn:ietf:params:xml:ns:netconf:base:1.0" a:operation="replace">
	 * <uuid>FC1</uuid> <forwarding-direction>bidirectional</forwarding-direction>
	 * <fc-port> <uuid>FCPort2</uuid>
	 * <fc-port-direction>bidirectional</fc-port-direction> </fc-port> <fc-port>
	 * <uuid>FCPort1</uuid> <fc-port-direction>bidirectional</fc-port-direction>
	 * </fc-port> <layer-protocol-name>ETH</layer-protocol-name>
	 * <is-protection-lock-out>true</is-protection-lock-out> </forwarding-construct>
	 * </config> </edit-config> </rpc>
	 * 
	 * Queued RPC Message:NetconfIncommingMessageRepresentation RpcIn
	 * [messageType=rpc, tags=NetconfTagList [tags=[ NetconfTag
	 * [namespace=urn:ietf:params:xml:ns:netconf:base:1.0, name=edit-config,
	 * values=[]], NetconfTag [namespace=urn:ietf:params:xml:ns:netconf:base:1.0,
	 * name=target, values=[]], NetconfTag
	 * [namespace=urn:ietf:params:xml:ns:netconf:base:1.0, name=running, values=[]],
	 * NetconfTag [namespace=urn:ietf:params:xml:ns:netconf:base:1.0,
	 * name=default-operation, values=[none]], NetconfTag
	 * [namespace=urn:ietf:params:xml:ns:netconf:base:1.0, name=config, values=[]],
	 * NetconfTag [namespace=urn:onf:params:xml:ns:yang:microwave-model,
	 * name=mw-ethernet-container-pac, values=[]], NetconfTag
	 * [namespace=urn:onf:params:xml:ns:yang:microwave-model, name=layer-protocol,
	 * values=[ETHC1]], NetconfTag
	 * [namespace=urn:onf:params:xml:ns:yang:microwave-model,
	 * name=ethernet-container-configuration, values=[]], NetconfTag
	 * [namespace=urn:onf:params:xml:ns:yang:microwave-model, name=container-id,
	 * values=[ID17]]]],
	 *
	 * messageUri=urn:ietf:params:xml:ns:netconf:base:1.0,
	 * content={edit-config.target.running.default-operation=none,
	 * edit-config.target.running.default-operation.config.mw-ethernet-container-pac
	 * .layer-protocol.ethernet-container-configuration.container-id=ID17,
	 * edit-config.target.running.default-operation.config.mw-ethernet-container-pac
	 * .layer-protocol=ETHC1},
	 *
	 * getMessageId()=m-65, getCtx()=null, xmlSourceMessage='<?xml version="1.0"
	 * encoding="UTF-8" standalone="no"?>
	 */

	/**
	 * Indicating an error
	 * 
	 * @param errorMsg with error text
	 * @return StringBuffer with error message
	 */
	private static StringBuffer assembleRpcReplyError(String errorTag, String errorMsg) {
		StringBuffer res = new StringBuffer();
		res.append("   <rpc-reply xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n" + "       <rpc-error>\n"
				+ "         <error-type>rpc</error-type>\n" + "         <error-tag>" + errorTag + "</error-tag>\n"
				+ "         <error-severity>error</error-severity>\n" + "         <error-message xml:lang=\"en\">\n"
				+ errorMsg + "\n" + "         </error-message>\n" + "       </rpc-error>\n" + "     </rpc-reply>\n");
		return res;

	}

	/*---------------------------------------------------
	 * Base functions for message creation
	 */

	private static StringBuffer appendXmlMessageRpcReplyOpen(StringBuffer res, String id) {
		res.append(
				"<rpc-reply xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\" xmlns:nc=\"urn:ietf:params:xml:ns:netconf:base:1.0\" last-modified=\"2017-03-07T19:32:31Z\" message-id=\"");
		res.append(id);
		res.append("\">\n");
		return res;
	}

	private static StringBuffer appendXmlMessageRpcReplyClose(StringBuffer res) {
		res.append("</rpc-reply>");
		return res;
	}

	private static StringBuffer appendXmlTagOpen(StringBuffer res, String name, String nameSpace) {
		res.append("<");
		res.append(name);
		res.append(" xmlns=\"");
		res.append(nameSpace);
		res.append("\">\n");
		return res;
	}

	private static StringBuffer appendXmlTagOpen(StringBuffer res, String name) {
		res.append("<");
		res.append(name);
		res.append(">");
		return res;
	}

	private static StringBuffer appendXmlTagClose(StringBuffer res, String name) {
		res.append("</");
		res.append(name);
		res.append(">\n");
		return res;
	}

	private static StringBuffer appendXml(StringBuffer res, String name, String value) {
		appendXmlTagOpen(res, name);
		res.append(value);
		appendXmlTagClose(res, name);
		return res;
	}

}
