package com.jslib.md.docx.xml;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * DOCX package loaded in memory. Parts are kept as raw bytes and parsed to DOM on first access; on write, parsed parts
 * are serialized back while all other parts are copied unchanged. Only the parts needed by the generator are modeled:
 * main document, styles, numbering, main document relationships and content types.
 */
public class DocxDocument {
	public static final String REL_NS = "http://schemas.openxmlformats.org/package/2006/relationships";
	public static final String TYPES_NS = "http://schemas.openxmlformats.org/package/2006/content-types";

	public static final String HYPERLINK_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink";
	public static final String IMAGE_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/image";

	private static final String CONTENT_TYPES_PART = "[Content_Types].xml";
	private static final String DOCUMENT_PART = "word/document.xml";
	private static final String DOCUMENT_RELS_PART = "word/_rels/document.xml.rels";
	private static final String STYLES_PART = "word/styles.xml";
	private static final String NUMBERING_PART = "word/numbering.xml";

	private static final Map<String, String> IMAGE_CONTENT_TYPES = Map.ofEntries( //
			Map.entry("png", "image/png"), //
			Map.entry("jpg", "image/jpeg"), //
			Map.entry("jpeg", "image/jpeg"), //
			Map.entry("gif", "image/gif"), //
			Map.entry("bmp", "image/bmp"), //
			Map.entry("dib", "image/bmp"), //
			Map.entry("tif", "image/tiff"), //
			Map.entry("tiff", "image/tiff"), //
			Map.entry("emf", "image/x-emf"), //
			Map.entry("wmf", "image/x-wmf"), //
			Map.entry("pict", "image/pict"), //
			Map.entry("eps", "image/x-eps"), //
			Map.entry("wpg", "image/x-wpg"));

	/** Package parts in archive order, by part name without leading slash. */
	private final Map<String, byte[]> parts = new LinkedHashMap<>();
	/** Parsed parts; serialized back on write. */
	private final Map<String, Document> xmlParts = new HashMap<>();
	/** Relationship ID of already embedded images, by image content digest. */
	private final Map<String, String> imageRelIds = new HashMap<>();

	private int nextRelId;
	private int nextImageIndex;
	private int nextDrawingId;

	public DocxDocument(InputStream stream) throws IOException {
		try (ZipInputStream zip = new ZipInputStream(stream)) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				if (!entry.isDirectory()) {
					parts.put(entry.getName(), zip.readAllBytes());
				}
			}
		}
		for (String required : new String[] { CONTENT_TYPES_PART, DOCUMENT_PART, DOCUMENT_RELS_PART, STYLES_PART, NUMBERING_PART }) {
			if (!parts.containsKey(required)) {
				throw new IOException("Invalid DOCX template. Missing part " + required);
			}
		}

		nextRelId = 1;
		for (Element relationship : Wml.children(relationships().getDocumentElement(), REL_NS, "Relationship")) {
			String id = relationship.getAttribute("Id");
			if (id.matches("rId\\d+")) {
				nextRelId = Math.max(nextRelId, Integer.parseInt(id.substring(3)) + 1);
			}
		}
		nextImageIndex = 1;
		while (hasMediaPart(nextImageIndex)) {
			++nextImageIndex;
		}
		nextDrawingId = 1;
		for (Element docPr : Wml.descendants(document().getDocumentElement(), Wml.WP_NS, "docPr")) {
			try {
				nextDrawingId = Math.max(nextDrawingId, Integer.parseInt(docPr.getAttribute("id")) + 1);
			} catch (NumberFormatException e) {
				// ignore invalid drawing ID from template
			}
		}
	}

	public Document document() {
		return xml(DOCUMENT_PART);
	}

	public Document styles() {
		return xml(STYLES_PART);
	}

	public Document numbering() {
		return xml(NUMBERING_PART);
	}

	private Document relationships() {
		return xml(DOCUMENT_RELS_PART);
	}

	private Document contentTypes() {
		return xml(CONTENT_TYPES_PART);
	}

	public Element body() {
		return Wml.child(document().getDocumentElement(), "body");
	}

	/** Append block element, paragraph or table, to document body, before body section properties. */
	public Element appendBlock(Element block) {
		Element body = body();
		Element sectPr = null;
		for (Node node = body.getLastChild(); node != null; node = node.getPreviousSibling()) {
			if (node instanceof Element) {
				if (Wml.is(node, "sectPr")) {
					sectPr = (Element) node;
				}
				break;
			}
		}
		body.insertBefore(block, sectPr);
		return block;
	}

	/** Add external hyperlink relationship to main document and return relationship ID. */
	public String addHyperlink(String url) {
		return addRelationship(HYPERLINK_REL, url, true);
	}

	/**
	 * Embed image into package and return its main document relationship ID. Identical images are embedded only once.
	 *
	 * @param data image content.
	 * @param extension image file extension, used to derive content type.
	 * @throws IllegalArgumentException if image extension is not supported.
	 */
	public String addImage(byte[] data, String extension) {
		String normalizedExtension = extension.toLowerCase(Locale.ROOT);
		String contentType = IMAGE_CONTENT_TYPES.get(normalizedExtension);
		if (contentType == null) {
			throw new IllegalArgumentException("Unsupported image type " + extension + ". Expected one of " + IMAGE_CONTENT_TYPES.keySet());
		}
		return imageRelIds.computeIfAbsent(digest(data), key -> {
			String partName = mediaPart(nextImageIndex++, normalizedExtension);
			parts.put(partName, data);
			addDefaultContentType(normalizedExtension, contentType);
			return addRelationship(IMAGE_REL, partName.substring("word/".length()), false);
		});
	}

	/** Unique ID for drawing objects, i.e. wp:docPr. */
	public int nextDrawingId() {
		return nextDrawingId++;
	}

	public void write(OutputStream stream) throws IOException {
		try (ZipOutputStream zip = new ZipOutputStream(stream)) {
			for (Map.Entry<String, byte[]> part : parts.entrySet()) {
				Document xml = xmlParts.get(part.getKey());
				zip.putNextEntry(new ZipEntry(part.getKey()));
				zip.write(xml != null ? Wml.serialize(xml) : part.getValue());
				zip.closeEntry();
			}
		}
	}

	public byte[] toByteArray() throws IOException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		write(buffer);
		return buffer.toByteArray();
	}

	// --------------------------------------------------------------------------------------------

	private Document xml(String partName) {
		return xmlParts.computeIfAbsent(partName, name -> {
			byte[] bytes = parts.get(name);
			if (bytes == null) {
				throw new IllegalStateException("Missing DOCX part " + name);
			}
			try {
				return Wml.parse(new ByteArrayInputStream(bytes));
			} catch (IOException e) {
				throw new IllegalStateException("Invalid XML on DOCX part " + name + ": " + e.getMessage(), e);
			}
		});
	}

	private String addRelationship(String type, String target, boolean external) {
		Document relationships = relationships();
		String id = "rId" + nextRelId++;
		Element relationship = relationships.createElementNS(REL_NS, "Relationship");
		relationship.setAttribute("Id", id);
		relationship.setAttribute("Type", type);
		relationship.setAttribute("Target", target);
		if (external) {
			relationship.setAttribute("TargetMode", "External");
		}
		relationships.getDocumentElement().appendChild(relationship);
		return id;
	}

	private void addDefaultContentType(String extension, String contentType) {
		Element types = contentTypes().getDocumentElement();
		for (Element type : Wml.children(types, TYPES_NS, "Default")) {
			if (type.getAttribute("Extension").equalsIgnoreCase(extension)) {
				return;
			}
		}
		Element type = contentTypes().createElementNS(TYPES_NS, "Default");
		type.setAttribute("Extension", extension);
		type.setAttribute("ContentType", contentType);
		// schema requires all Default elements before Override elements
		types.insertBefore(type, types.getFirstChild());
	}

	private boolean hasMediaPart(int index) {
		String prefix = "word/media/image" + index + ".";
		return parts.keySet().stream().anyMatch(name -> name.startsWith(prefix));
	}

	private static String mediaPart(int index, String extension) {
		return "word/media/image" + index + "." + extension;
	}

	private static String digest(byte[] data) {
		try {
			return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(data));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
