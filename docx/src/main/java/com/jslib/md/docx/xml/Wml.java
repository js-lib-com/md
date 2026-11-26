package com.jslib.md.docx.xml;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * WordprocessingML DOM helpers. Word rejects documents with child elements out of schema order, so property elements
 * are inserted with {@link #property(Element, String)} that keeps the order declared by schema sequences.
 */
public final class Wml {
	public static final String W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
	public static final String R_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
	public static final String WP_NS = "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing";

	/**
	 * Schema order of child elements for the containers updated by generator. Only elements relevant for ordering are
	 * listed; children not listed, e.g. runs inside a paragraph, are kept after the listed ones.
	 */
	private static final Map<String, List<String>> ORDER = Map.ofEntries( //
			Map.entry("p", List.of("pPr")), //
			Map.entry("r", List.of("rPr")), //
			Map.entry("tbl", List.of("tblPr", "tblGrid")), //
			Map.entry("tc", List.of("tcPr")), //
			Map.entry("numPr", List.of("ilvl", "numId")), //
			Map.entry("pBdr", List.of("top", "left", "bottom", "right", "between", "bar")), //
			Map.entry("pPr", List.of("pStyle", "keepNext", "keepLines", "pageBreakBefore", "framePr", "widowControl", "numPr", "suppressLineNumbers", "pBdr", "shd", "tabs", "suppressAutoHyphens", "kinsoku", "wordWrap", "overflowPunct", "topLinePunct", "autoSpaceDE", "autoSpaceDN", "bidi", "adjustRightInd", "snapToGrid", "spacing", "ind", "contextualSpacing", "mirrorIndents", "suppressOverlap", "jc", "textDirection", "textAlignment", "textboxTightWrap", "outlineLvl", "divId", "cnfStyle", "rPr", "sectPr", "pPrChange")), //
			Map.entry("rPr", List.of("rStyle", "rFonts", "b", "bCs", "i", "iCs", "caps", "smallCaps", "strike", "dstrike", "outline", "shadow", "emboss", "imprint", "noProof", "snapToGrid", "vanish", "webHidden", "color", "spacing", "w", "kern", "position", "sz", "szCs", "highlight", "u", "effect", "bdr", "shd", "fitText", "vertAlign", "rtl", "cs", "em", "lang", "eastAsianLayout", "specVanish", "oMath")), //
			Map.entry("tblPr", List.of("tblStyle", "tblpPr", "tblOverlap", "bidiVisual", "tblStyleRowBandSize", "tblStyleColBandSize", "tblW", "jc", "tblCellSpacing", "tblInd", "tblBorders", "shd", "tblLayout", "tblCellMar", "tblLook", "tblCaption", "tblDescription")), //
			Map.entry("tcPr", List.of("cnfStyle", "tcW", "gridSpan", "hMerge", "vMerge", "tcBorders", "shd", "noWrap", "tcMar", "textDirection", "tcFitText", "vAlign", "hideMark")));

	private Wml() {
	}

	// --------------------------------------------------------------------------------------------
	// parse and serialize

	public static Document parse(InputStream stream) throws IOException {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setExpandEntityReferences(false);
			DocumentBuilder builder = factory.newDocumentBuilder();
			return builder.parse(stream);
		} catch (ParserConfigurationException | SAXException e) {
			throw new IOException(e.getMessage(), e);
		}
	}

	public static byte[] serialize(Document document) throws IOException {
		try {
			document.setXmlStandalone(true);
			Transformer transformer = TransformerFactory.newInstance().newTransformer();
			transformer.setOutputProperty(OutputKeys.ENCODING, UTF_8.name());
			transformer.setOutputProperty(OutputKeys.INDENT, "no");
			ByteArrayOutputStream buffer = new ByteArrayOutputStream();
			transformer.transform(new DOMSource(document), new StreamResult(buffer));
			return buffer.toByteArray();
		} catch (TransformerException e) {
			throw new IOException(e.getMessage(), e);
		}
	}

	/** Remove whitespace only text nodes, e.g. indentation from XML resources. */
	public static void stripWhitespace(Node node) {
		Node child = node.getFirstChild();
		while (child != null) {
			Node next = child.getNextSibling();
			if (child.getNodeType() == Node.TEXT_NODE && child.getNodeValue().isBlank()) {
				node.removeChild(child);
			} else if (child.getNodeType() == Node.ELEMENT_NODE) {
				stripWhitespace(child);
			}
			child = next;
		}
	}

	// --------------------------------------------------------------------------------------------
	// navigation

	/** Test if node is a WordprocessingML element with given local name. */
	public static boolean is(Node node, String localName) {
		return node instanceof Element && W_NS.equals(node.getNamespaceURI()) && localName.equals(node.getLocalName());
	}

	/** First WordprocessingML child element with given local name or null. */
	public static Element child(Element parent, String localName) {
		for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
			if (is(node, localName)) {
				return (Element) node;
			}
		}
		return null;
	}

	public static List<Element> children(Element parent, String localName) {
		return children(parent, W_NS, localName);
	}

	public static List<Element> children(Element parent, String namespace, String localName) {
		List<Element> children = new ArrayList<>();
		for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
			if (node instanceof Element && namespace.equals(node.getNamespaceURI()) && localName.equals(node.getLocalName())) {
				children.add((Element) node);
			}
		}
		return children;
	}

	public static List<Element> descendants(Element parent, String namespace, String localName) {
		NodeList nodes = parent.getElementsByTagNameNS(namespace, localName);
		List<Element> elements = new ArrayList<>(nodes.getLength());
		for (int i = 0; i < nodes.getLength(); ++i) {
			elements.add((Element) nodes.item(i));
		}
		return elements;
	}

	/** Get WordprocessingML attribute value or empty string if missing. */
	public static String attr(Element element, String localName) {
		return element.getAttributeNS(W_NS, localName);
	}

	/**
	 * Plain text of element: text, tab and break elements, in document order. Paragraphs are separated by new line.
	 */
	public static String text(Element element) {
		StringBuilder builder = new StringBuilder();
		text(element, builder);
		return builder.toString();
	}

	private static void text(Element element, StringBuilder builder) {
		for (Node node = element.getFirstChild(); node != null; node = node.getNextSibling()) {
			if (!(node instanceof Element)) {
				continue;
			}
			if (is(node, "t")) {
				builder.append(node.getTextContent());
			} else if (is(node, "tab")) {
				builder.append('\t');
			} else if (is(node, "br") || is(node, "cr")) {
				builder.append('\n');
			} else if (is(node, "p")) {
				if (!builder.isEmpty()) {
					builder.append('\n');
				}
				text((Element) node, builder);
			} else {
				text((Element) node, builder);
			}
		}
	}

	// --------------------------------------------------------------------------------------------
	// build

	/** Create detached WordprocessingML element in the document of context node. */
	public static Element create(Node context, String localName) {
		Document document = context instanceof Document ? (Document) context : context.getOwnerDocument();
		return document.createElementNS(W_NS, "w:" + localName);
	}

	/** Append new WordprocessingML child element. */
	public static Element add(Element parent, String localName) {
		return (Element) parent.appendChild(create(parent, localName));
	}

	/**
	 * Get child element with given local name, creating it, on schema order position, if missing.
	 */
	public static Element property(Element parent, String localName) {
		Element existing = child(parent, localName);
		if (existing != null) {
			return existing;
		}
		Element property = create(parent, localName);
		List<String> order = ORDER.getOrDefault(parent.getLocalName(), List.of());
		int index = order.indexOf(localName);
		Node before = null;
		if (index != -1) {
			for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
				if (node instanceof Element) {
					int nodeIndex = W_NS.equals(node.getNamespaceURI()) ? order.indexOf(node.getLocalName()) : -1;
					if (nodeIndex == -1 || nodeIndex > index) {
						before = node;
						break;
					}
				}
			}
		}
		return (Element) parent.insertBefore(property, before);
	}

	/** Get or create property element and set its w:val attribute. */
	public static Element property(Element parent, String localName, Object value) {
		return attr(property(parent, localName), "val", value);
	}

	/** Set WordprocessingML attribute and return element. */
	public static Element attr(Element element, String localName, Object value) {
		element.setAttributeNS(W_NS, "w:" + localName, String.valueOf(value));
		return element;
	}

	/**
	 * Append text element to run. Leading, trailing or repeated white spaces are preserved.
	 */
	public static void setText(Element run, String text) {
		Element t = add(run, "t");
		if (!text.equals(text.strip()) || text.contains("  ") || text.indexOf('	') != -1) {
			t.setAttributeNS(XMLConstants.XML_NS_URI, "xml:space", "preserve");
		}
		t.setTextContent(text);
	}

	/**
	 * Append break to run; break type is null for line break.
	 */
	public static void addBreak(Element run, String type) {
		Element br = add(run, "br");
		if (type != null) {
			attr(br, "type", type);
		}
	}

	/** Remove WordprocessingML child element, if present. */
	public static void remove(Element parent, String localName) {
		Element child = child(parent, localName);
		if (child != null) {
			parent.removeChild(child);
		}
	}
}
