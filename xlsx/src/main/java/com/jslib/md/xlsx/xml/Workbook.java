package com.jslib.md.xlsx.xml;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/**
 * SpreadsheetML workbook with string cells, written as XLSX package. Cell text is stored in shared strings table and
 * cell styles in workbook styles, both deduplicated on write.
 */
public final class Workbook {
	private static final String MAIN_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
	private static final String R_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
	private static final String REL_NS = "http://schemas.openxmlformats.org/package/2006/relationships";
	private static final String TYPES_NS = "http://schemas.openxmlformats.org/package/2006/content-types";
	private static final String XML_NS = "http://www.w3.org/XML/1998/namespace";
	private static final String CORE_NS = "http://schemas.openxmlformats.org/package/2006/metadata/core-properties";
	private static final String DC_NS = "http://purl.org/dc/elements/1.1/";
	private static final String DCTERMS_NS = "http://purl.org/dc/terms/";
	private static final String XSI_NS = "http://www.w3.org/2001/XMLSchema-instance";
	private static final String EXTENDED_NS = "http://schemas.openxmlformats.org/officeDocument/2006/extended-properties";

	private static final String OFFICE_DOCUMENT_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument";
	private static final String WORKSHEET_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet";
	private static final String STYLES_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles";
	private static final String SHARED_STRINGS_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings";
	private static final String HYPERLINK_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink";
	private static final String CORE_PROPERTIES_REL = "http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties";
	private static final String EXTENDED_PROPERTIES_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties";

	/** Application name stored in document properties, used also as document creator if author is not set. */
	public static final String APPLICATION = "mdt";

	private static final String CONTENT_TYPE_PREFIX = "application/vnd.openxmlformats-officedocument.spreadsheetml.";

	private final List<Sheet> sheets = new ArrayList<>();
	/** Creation time stored in document properties, with seconds precision as required by W3CDTF format. */
	private Instant created = Instant.now().truncatedTo(ChronoUnit.SECONDS);

	/** Author stored in document properties as creator; null if not set. */
	private String author;

	/** Set workbook author; if not set, application name is used as document creator. */
	public void setAuthor(String author) {
		this.author = author;
	}

	/** Title stored in document properties; null if not set. */
	private String title;

	/** Set workbook title; if not set, document properties have no title. */
	public void setTitle(String title) {
		this.title = title;
	}

	/** Subject stored in document properties; null if not set. */
	private String subject;

	/** Set workbook subject; if not set, document properties have no subject. */
	public void setSubject(String subject) {
		this.subject = subject;
	}

	/** Override creation time, e.g. for reproducible output; default is workbook instance creation time. */
	public void setCreated(Instant created) {
		this.created = created.truncatedTo(ChronoUnit.SECONDS);
	}

	public Sheet createSheet(String name) {
		if (getSheet(name) != null) {
			throw new IllegalArgumentException("Duplicated sheet name " + name);
		}
		Sheet sheet = new Sheet(name);
		sheets.add(sheet);
		return sheet;
	}

	public void removeSheet(Sheet sheet) {
		sheets.remove(sheet);
	}

	/** Sheet with given name, case-insensitive like Excel sheet names, or null if not found. */
	public Sheet getSheet(String name) {
		for (Sheet sheet : sheets) {
			if (sheet.getName().equalsIgnoreCase(name)) {
				return sheet;
			}
		}
		return null;
	}

	public List<Sheet> getSheets() {
		return Collections.unmodifiableList(sheets);
	}

	public void write(OutputStream stream) throws IOException {
		Styles styles = new Styles();
		Map<Object, Integer> strings = new LinkedHashMap<>();
		try (ZipOutputStream zip = new ZipOutputStream(stream)) {
			part(zip, "[Content_Types].xml", this::writeContentTypes);
			part(zip, "_rels/.rels", xml -> writeRelationships(xml, List.of( //
					new String[] { OFFICE_DOCUMENT_REL, "xl/workbook.xml", null }, //
					new String[] { CORE_PROPERTIES_REL, "docProps/core.xml", null }, //
					new String[] { EXTENDED_PROPERTIES_REL, "docProps/app.xml", null })));
			part(zip, "docProps/core.xml", this::writeCoreProperties);
			part(zip, "docProps/app.xml", this::writeExtendedProperties);
			part(zip, "xl/workbook.xml", this::writeWorkbook);

			List<String[]> relationships = new ArrayList<>();
			for (int i = 0; i < sheets.size(); ++i) {
				relationships.add(new String[] { WORKSHEET_REL, "worksheets/sheet" + (i + 1) + ".xml", null });
			}
			relationships.add(new String[] { STYLES_REL, "styles.xml", null });
			relationships.add(new String[] { SHARED_STRINGS_REL, "sharedStrings.xml", null });
			part(zip, "xl/_rels/workbook.xml.rels", xml -> writeRelationships(xml, relationships));

			for (int i = 0; i < sheets.size(); ++i) {
				Sheet sheet = sheets.get(i);
				boolean selected = i == 0;
				List<String[]> links = new ArrayList<>();
				part(zip, "xl/worksheets/sheet" + (i + 1) + ".xml", xml -> writeSheet(xml, sheet, selected, styles, strings, links));
				if (!links.isEmpty()) {
					part(zip, "xl/worksheets/_rels/sheet" + (i + 1) + ".xml.rels", xml -> writeRelationships(xml, links));
				}
			}
			// styles and shared strings are collected while writing sheets
			part(zip, "xl/styles.xml", styles::write);
			part(zip, "xl/sharedStrings.xml", xml -> writeSharedStrings(xml, strings));
		}
	}

	// --------------------------------------------------------------------------------------------

	private interface PartWriter {
		void write(XMLStreamWriter xml) throws XMLStreamException;
	}

	private static void part(ZipOutputStream zip, String name, PartWriter writer) throws IOException {
		zip.putNextEntry(new ZipEntry(name));
		try {
			XMLStreamWriter xml = XMLOutputFactory.newFactory().createXMLStreamWriter(new NonClosingStream(zip), "UTF-8");
			xml.writeStartDocument("UTF-8", "1.0");
			writer.write(xml);
			xml.writeEndDocument();
			xml.flush();
			xml.close();
		} catch (XMLStreamException e) {
			throw new IOException("Fail to write XLSX part " + name + ": " + e.getMessage(), e);
		}
		zip.closeEntry();
	}

	private void writeContentTypes(XMLStreamWriter xml) throws XMLStreamException {
		xml.setDefaultNamespace(TYPES_NS);
		xml.writeStartElement(TYPES_NS, "Types");
		xml.writeDefaultNamespace(TYPES_NS);
		contentType(xml, "Default", "Extension", "rels", "application/vnd.openxmlformats-package.relationships+xml");
		contentType(xml, "Default", "Extension", "xml", "application/xml");
		contentType(xml, "Override", "PartName", "/docProps/core.xml", "application/vnd.openxmlformats-package.core-properties+xml");
		contentType(xml, "Override", "PartName", "/docProps/app.xml", "application/vnd.openxmlformats-officedocument.extended-properties+xml");
		contentType(xml, "Override", "PartName", "/xl/workbook.xml", CONTENT_TYPE_PREFIX + "sheet.main+xml");
		for (int i = 0; i < sheets.size(); ++i) {
			contentType(xml, "Override", "PartName", "/xl/worksheets/sheet" + (i + 1) + ".xml", CONTENT_TYPE_PREFIX + "worksheet+xml");
		}
		contentType(xml, "Override", "PartName", "/xl/styles.xml", CONTENT_TYPE_PREFIX + "styles+xml");
		contentType(xml, "Override", "PartName", "/xl/sharedStrings.xml", CONTENT_TYPE_PREFIX + "sharedStrings+xml");
		xml.writeEndElement();
	}

	private static void contentType(XMLStreamWriter xml, String element, String keyName, String key, String contentType) throws XMLStreamException {
		xml.writeEmptyElement(TYPES_NS, element);
		xml.writeAttribute(keyName, key);
		xml.writeAttribute("ContentType", contentType);
	}

	/** Write relationships part; every relationship is type, target and target mode, the last one null if internal. */
	private static void writeRelationships(XMLStreamWriter xml, List<String[]> relationships) throws XMLStreamException {
		xml.setDefaultNamespace(REL_NS);
		xml.writeStartElement(REL_NS, "Relationships");
		xml.writeDefaultNamespace(REL_NS);
		for (int i = 0; i < relationships.size(); ++i) {
			String[] relationship = relationships.get(i);
			xml.writeEmptyElement(REL_NS, "Relationship");
			xml.writeAttribute("Id", "rId" + (i + 1));
			xml.writeAttribute("Type", relationship[0]);
			xml.writeAttribute("Target", relationship[1]);
			if (relationship[2] != null) {
				xml.writeAttribute("TargetMode", relationship[2]);
			}
		}
		xml.writeEndElement();
	}

	/** Core properties: creator, optional title and subject, creation and modification time. */
	private void writeCoreProperties(XMLStreamWriter xml) throws XMLStreamException {
		xml.setPrefix("cp", CORE_NS);
		xml.setPrefix("dc", DC_NS);
		xml.setPrefix("dcterms", DCTERMS_NS);
		xml.setPrefix("xsi", XSI_NS);
		xml.writeStartElement(CORE_NS, "coreProperties");
		xml.writeNamespace("cp", CORE_NS);
		xml.writeNamespace("dc", DC_NS);
		xml.writeNamespace("dcterms", DCTERMS_NS);
		xml.writeNamespace("xsi", XSI_NS);

		xml.writeStartElement(DC_NS, "creator");
		xml.writeCharacters(author != null ? author : APPLICATION);
		xml.writeEndElement();
		if (title != null) {
			xml.writeStartElement(DC_NS, "title");
			xml.writeCharacters(title);
			xml.writeEndElement();
		}
		if (subject != null) {
			xml.writeStartElement(DC_NS, "subject");
			xml.writeCharacters(subject);
			xml.writeEndElement();
		}
		for (String element : new String[] { "created", "modified" }) {
			xml.writeStartElement(DCTERMS_NS, element);
			xml.writeAttribute("xsi", XSI_NS, "type", "dcterms:W3CDTF");
			xml.writeCharacters(DateTimeFormatter.ISO_INSTANT.format(created));
			xml.writeEndElement();
		}
		xml.writeEndElement();
	}

	/** Extended properties: application name. */
	private void writeExtendedProperties(XMLStreamWriter xml) throws XMLStreamException {
		xml.setDefaultNamespace(EXTENDED_NS);
		xml.writeStartElement(EXTENDED_NS, "Properties");
		xml.writeDefaultNamespace(EXTENDED_NS);
		xml.writeStartElement(EXTENDED_NS, "Application");
		xml.writeCharacters(APPLICATION);
		xml.writeEndElement();
		xml.writeEndElement();
	}

	private void writeWorkbook(XMLStreamWriter xml) throws XMLStreamException {
		startMain(xml, "workbook");
		if (!sheets.isEmpty()) {
			xml.writeStartElement(MAIN_NS, "bookViews");
			xml.writeEmptyElement(MAIN_NS, "workbookView");
			xml.writeAttribute("activeTab", "0");
			xml.writeEndElement();
		}
		xml.writeStartElement(MAIN_NS, "sheets");
		for (int i = 0; i < sheets.size(); ++i) {
			xml.writeEmptyElement(MAIN_NS, "sheet");
			xml.writeAttribute("name", sheets.get(i).getName());
			xml.writeAttribute("sheetId", Integer.toString(i + 1));
			xml.writeAttribute(R_NS, "id", "rId" + (i + 1));
		}
		xml.writeEndElement();
		xml.writeEndElement();
	}

	private static void writeSheet(XMLStreamWriter xml, Sheet sheet, boolean selected, Styles styles, Map<Object, Integer> strings, List<String[]> links) throws XMLStreamException {
		startMain(xml, "worksheet");

		List<Row> rows = sheet.getRows();
		int columnsCount = rows.stream().mapToInt(row -> row.getCells().size()).max().orElse(0);
		if (columnsCount > 0) {
			xml.writeEmptyElement(MAIN_NS, "dimension");
			xml.writeAttribute("ref", "A1:" + reference(columnsCount - 1, rows.size() - 1));
		}

		xml.writeStartElement(MAIN_NS, "sheetViews");
		xml.writeEmptyElement(MAIN_NS, "sheetView");
		if (selected) {
			xml.writeAttribute("tabSelected", "1");
		}
		xml.writeAttribute("workbookViewId", "0");
		xml.writeEndElement();

		xml.writeEmptyElement(MAIN_NS, "sheetFormatPr");
		xml.writeAttribute("defaultRowHeight", "15");

		if (!sheet.columnWidths().isEmpty()) {
			xml.writeStartElement(MAIN_NS, "cols");
			for (Map.Entry<Integer, Integer> width : sheet.columnWidths().entrySet()) {
				String column = Integer.toString(width.getKey() + 1);
				xml.writeEmptyElement(MAIN_NS, "col");
				xml.writeAttribute("min", column);
				xml.writeAttribute("max", column);
				xml.writeAttribute("width", Double.toString(width.getValue() / 256.0));
				xml.writeAttribute("customWidth", "1");
				if (sheet.isBestFit(width.getKey())) {
					xml.writeAttribute("bestFit", "1");
				}
			}
			xml.writeEndElement();
		}

		List<String[]> hyperlinks = new ArrayList<>();
		xml.writeStartElement(MAIN_NS, "sheetData");
		for (int rowIndex = 0; rowIndex < rows.size(); ++rowIndex) {
			xml.writeStartElement(MAIN_NS, "row");
			xml.writeAttribute("r", Integer.toString(rowIndex + 1));
			for (Cell cell : rows.get(rowIndex).getCells()) {
				String reference = reference(cell.getColumn(), rowIndex);
				int style = styles.cellFormat(cell.getStyle());
				if (cell.isBlank()) {
					xml.writeEmptyElement(MAIN_NS, "c");
					xml.writeAttribute("r", reference);
					xml.writeAttribute("s", Integer.toString(style));
				} else {
					xml.writeStartElement(MAIN_NS, "c");
					xml.writeAttribute("r", reference);
					xml.writeAttribute("s", Integer.toString(style));
					xml.writeAttribute("t", "s");
					xml.writeStartElement(MAIN_NS, "v");
					xml.writeCharacters(Integer.toString(strings.computeIfAbsent(cell.stringKey(), key -> strings.size())));
					xml.writeEndElement();
					xml.writeEndElement();
				}
				if (cell.hyperlink() != null) {
					links.add(new String[] { HYPERLINK_REL, cell.hyperlink(), "External" });
					hyperlinks.add(new String[] { reference, "rId" + links.size() });
				}
			}
			xml.writeEndElement();
		}
		xml.writeEndElement();

		if (!hyperlinks.isEmpty()) {
			xml.writeStartElement(MAIN_NS, "hyperlinks");
			for (String[] hyperlink : hyperlinks) {
				xml.writeEmptyElement(MAIN_NS, "hyperlink");
				xml.writeAttribute("ref", hyperlink[0]);
				xml.writeAttribute(R_NS, "id", hyperlink[1]);
			}
			xml.writeEndElement();
		}

		xml.writeEmptyElement(MAIN_NS, "pageMargins");
		xml.writeAttribute("left", "0.7");
		xml.writeAttribute("right", "0.7");
		xml.writeAttribute("top", "0.75");
		xml.writeAttribute("bottom", "0.75");
		xml.writeAttribute("header", "0.3");
		xml.writeAttribute("footer", "0.3");

		xml.writeEndElement();
	}

	@SuppressWarnings("unchecked")
	private static void writeSharedStrings(XMLStreamWriter xml, Map<Object, Integer> strings) throws XMLStreamException {
		startMain(xml, "sst");
		xml.writeAttribute("uniqueCount", Integer.toString(strings.size()));
		for (Object key : strings.keySet()) {
			xml.writeStartElement(MAIN_NS, "si");
			if (key instanceof String) {
				text(xml, (String) key);
			} else {
				for (TextRun run : (List<TextRun>) key) {
					xml.writeStartElement(MAIN_NS, "r");
					if (run.font != null) {
						xml.writeStartElement(MAIN_NS, "rPr");
						Styles.writeFontProperties(xml, run.font, "rFont");
						xml.writeEndElement();
					}
					text(xml, run.text);
					xml.writeEndElement();
				}
			}
			xml.writeEndElement();
		}
		xml.writeEndElement();
	}

	/** Write text element, preserving white spaces if needed. */
	private static void text(XMLStreamWriter xml, String text) throws XMLStreamException {
		xml.writeStartElement(MAIN_NS, "t");
		if (!text.equals(text.strip()) || text.contains("  ") || text.indexOf('\n') != -1 || text.indexOf('\t') != -1) {
			xml.writeAttribute("xml", XML_NS, "space", "preserve");
		}
		xml.writeCharacters(escapeControlChars(text));
		xml.writeEndElement();
	}

	/** Control characters are not allowed in XML; SpreadsheetML encodes them as _xHHHH_. */
	static String escapeControlChars(String text) {
		StringBuilder builder = null;
		for (int i = 0; i < text.length(); ++i) {
			char c = text.charAt(i);
			boolean invalid = c < 0x20 && c != '\t' && c != '\n' && c != '\r';
			if (invalid && builder == null) {
				builder = new StringBuilder(text.substring(0, i));
			}
			if (builder != null) {
				builder.append(invalid ? String.format("_x%04X_", (int) c) : String.valueOf(c));
			}
		}
		return builder != null ? builder.toString() : text;
	}

	static void startMain(XMLStreamWriter xml, String root) throws XMLStreamException {
		xml.setDefaultNamespace(MAIN_NS);
		xml.setPrefix("r", R_NS);
		xml.writeStartElement(MAIN_NS, root);
		xml.writeDefaultNamespace(MAIN_NS);
		xml.writeNamespace("r", R_NS);
	}

	/** Cell reference, e.g. B3, from 0 based column and row indexes. */
	static String reference(int column, int row) {
		StringBuilder letters = new StringBuilder();
		for (int number = column + 1; number > 0; number = (number - 1) / 26) {
			letters.insert(0, (char) ('A' + (number - 1) % 26));
		}
		return letters.toString().toUpperCase(Locale.ROOT) + (row + 1);
	}

	/** Zip entry stream that is not closed by XML writer. */
	private static final class NonClosingStream extends OutputStream {
		private final OutputStream stream;

		NonClosingStream(OutputStream stream) {
			this.stream = stream;
		}

		@Override
		public void write(int b) throws IOException {
			stream.write(b);
		}

		@Override
		public void write(byte[] bytes, int offset, int length) throws IOException {
			stream.write(bytes, offset, length);
		}

		@Override
		public void flush() throws IOException {
			stream.flush();
		}

		@Override
		public void close() throws IOException {
			stream.flush();
		}
	}
}
