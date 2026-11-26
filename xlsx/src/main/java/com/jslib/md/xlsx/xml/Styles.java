package com.jslib.md.xlsx.xml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/**
 * Workbook styles registry. Cell styles are registered while sheets are written and get cell format index; fonts,
 * fills and borders are deduplicated. First records are the defaults required by Excel.
 */
final class Styles {
	private static final String MAIN_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";

	private final Map<Font, Integer> fonts = new LinkedHashMap<>();
	/** Fill indexed color by fill ID; first two fills, none and gray 125, are reserved by Excel. */
	private final List<Integer> fills = new ArrayList<>();
	/** Border color by border ID; first border, no border, is the default. */
	private final List<Integer> borders = new ArrayList<>();
	private final Map<CellStyle, Integer> cellFormats = new LinkedHashMap<>();

	Styles() {
		fonts.put(Font.DEFAULT, 0);
		fills.add(null);
		fills.add(null);
		borders.add(null);
		cellFormats.put(CellStyle.DEFAULT, 0);
	}

	/** Cell format index for given cell style, registering it if not already present. */
	int cellFormat(CellStyle style) {
		Integer index = cellFormats.get(style);
		if (index == null) {
			index = cellFormats.size();
			cellFormats.put(style, index);
			fonts.putIfAbsent(style.font, fonts.size());
		}
		return index;
	}

	private int fillId(CellStyle style) {
		if (style.fill == null) {
			return 0;
		}
		int index = fills.subList(2, fills.size()).indexOf(style.fill);
		return index != -1 ? index + 2 : -1;
	}

	private int borderId(CellStyle style) {
		if (!style.border) {
			return 0;
		}
		// border color is null for automatic color, encoded as -1 since list of borders uses null for no border
		int color = style.borderColor != null ? style.borderColor : -1;
		int index = borders.subList(1, borders.size()).indexOf(color);
		return index != -1 ? index + 1 : -1;
	}

	void write(XMLStreamWriter xml) throws XMLStreamException {
		// fills and borders are collected from registered cell styles
		for (CellStyle style : cellFormats.keySet()) {
			if (style.fill != null && fillId(style) == -1) {
				fills.add(style.fill);
			}
			if (style.border && borderId(style) == -1) {
				borders.add(style.borderColor != null ? style.borderColor : -1);
			}
		}

		Workbook.startMain(xml, "styleSheet");

		xml.writeStartElement(MAIN_NS, "fonts");
		xml.writeAttribute("count", Integer.toString(fonts.size()));
		for (Font font : fonts.keySet()) {
			xml.writeStartElement(MAIN_NS, "font");
			writeFontProperties(xml, font, "name");
			if (font.equals(Font.DEFAULT)) {
				value(xml, "family", "2");
			}
			xml.writeEndElement();
		}
		xml.writeEndElement();

		xml.writeStartElement(MAIN_NS, "fills");
		xml.writeAttribute("count", Integer.toString(fills.size()));
		for (int i = 0; i < fills.size(); ++i) {
			xml.writeStartElement(MAIN_NS, "fill");
			if (i < 2) {
				xml.writeEmptyElement(MAIN_NS, "patternFill");
				xml.writeAttribute("patternType", i == 0 ? "none" : "gray125");
			} else {
				xml.writeStartElement(MAIN_NS, "patternFill");
				xml.writeAttribute("patternType", "solid");
				xml.writeEmptyElement(MAIN_NS, "fgColor");
				xml.writeAttribute("indexed", Integer.toString(fills.get(i)));
				xml.writeEmptyElement(MAIN_NS, "bgColor");
				xml.writeAttribute("indexed", "64");
				xml.writeEndElement();
			}
			xml.writeEndElement();
		}
		xml.writeEndElement();

		xml.writeStartElement(MAIN_NS, "borders");
		xml.writeAttribute("count", Integer.toString(borders.size()));
		for (Integer color : borders) {
			xml.writeStartElement(MAIN_NS, "border");
			for (String side : new String[] { "left", "right", "top", "bottom" }) {
				if (color == null) {
					xml.writeEmptyElement(MAIN_NS, side);
				} else {
					xml.writeStartElement(MAIN_NS, side);
					xml.writeAttribute("style", "thin");
					// automatic border color has no color element
					if (color != -1) {
						xml.writeEmptyElement(MAIN_NS, "color");
						xml.writeAttribute("indexed", Integer.toString(color));
					}
					xml.writeEndElement();
				}
			}
			xml.writeEmptyElement(MAIN_NS, "diagonal");
			xml.writeEndElement();
		}
		xml.writeEndElement();

		xml.writeStartElement(MAIN_NS, "cellStyleXfs");
		xml.writeAttribute("count", "1");
		xml.writeEmptyElement(MAIN_NS, "xf");
		xml.writeAttribute("numFmtId", "0");
		xml.writeAttribute("fontId", "0");
		xml.writeAttribute("fillId", "0");
		xml.writeAttribute("borderId", "0");
		xml.writeEndElement();

		xml.writeStartElement(MAIN_NS, "cellXfs");
		xml.writeAttribute("count", Integer.toString(cellFormats.size()));
		for (CellStyle style : cellFormats.keySet()) {
			int fontId = fonts.get(style.font);
			int fillId = fillId(style);
			int borderId = borderId(style);
			if (style.hasAlignment()) {
				xml.writeStartElement(MAIN_NS, "xf");
			} else {
				xml.writeEmptyElement(MAIN_NS, "xf");
			}
			xml.writeAttribute("numFmtId", "0");
			xml.writeAttribute("fontId", Integer.toString(fontId));
			xml.writeAttribute("fillId", Integer.toString(fillId));
			xml.writeAttribute("borderId", Integer.toString(borderId));
			xml.writeAttribute("xfId", "0");
			if (fontId != 0) {
				xml.writeAttribute("applyFont", "1");
			}
			if (fillId != 0) {
				xml.writeAttribute("applyFill", "1");
			}
			if (borderId != 0) {
				xml.writeAttribute("applyBorder", "1");
			}
			if (style.hasAlignment()) {
				xml.writeAttribute("applyAlignment", "1");
				xml.writeEmptyElement(MAIN_NS, "alignment");
				if (style.centered) {
					xml.writeAttribute("horizontal", "center");
				}
				if (style.top) {
					xml.writeAttribute("vertical", "top");
				}
				if (style.wrap) {
					xml.writeAttribute("wrapText", "1");
				}
				xml.writeEndElement();
			}
		}
		xml.writeEndElement();

		xml.writeStartElement(MAIN_NS, "cellStyles");
		xml.writeAttribute("count", "1");
		xml.writeEmptyElement(MAIN_NS, "cellStyle");
		xml.writeAttribute("name", "Normal");
		xml.writeAttribute("xfId", "0");
		xml.writeAttribute("builtinId", "0");
		xml.writeEndElement();

		xml.writeEndElement();
	}

	/**
	 * Write font properties, shared by style fonts and rich text run properties that differ only by font name element.
	 */
	static void writeFontProperties(XMLStreamWriter xml, Font font, String nameElement) throws XMLStreamException {
		if (font.bold) {
			xml.writeEmptyElement(MAIN_NS, "b");
		}
		if (font.italic) {
			xml.writeEmptyElement(MAIN_NS, "i");
		}
		if (font.strike) {
			xml.writeEmptyElement(MAIN_NS, "strike");
		}
		if (font.underline) {
			value(xml, "u", "single");
		}
		if (font.verticalAlign != null) {
			value(xml, "vertAlign", font.verticalAlign.value);
		}
		value(xml, "sz", Integer.toString(Font.DEFAULT_SIZE));
		if (font.color != null) {
			xml.writeEmptyElement(MAIN_NS, "color");
			xml.writeAttribute("indexed", Integer.toString(font.color));
		}
		value(xml, nameElement, font.name);
	}

	private static void value(XMLStreamWriter xml, String element, String value) throws XMLStreamException {
		xml.writeEmptyElement(MAIN_NS, element);
		xml.writeAttribute("val", value);
	}
}
