package com.jslib.md.docx;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.jslib.md.docx.xml.DocxDocument;
import com.jslib.md.docx.xml.Wml;

/**
 * Numbering definitions for headings, bullet lists and ordered lists. Identifiers are allocated after those already
 * defined by document template so that template numbering is never shadowed by duplicated identifiers.
 */
public class DocxNumbering {
	/** Indentation step, in twips, for every list nesting level. */
	private static final int LEVEL_INDENT = 432;
	/** Word supports at most 9 numbering levels. */
	private static final int LEVELS_COUNT = 9;
	/** Markdown supports at most 6 heading levels. */
	private static final int HEADING_LEVELS_COUNT = 6;

	private static final String[] BULLET_TEXTS = { "", "o", "" };
	private static final String[] BULLET_FONTS = { "Symbol", "Courier New", "Wingdings" };
	private static final String[] ORDERED_FORMATS = { "decimal", "lowerLetter", "lowerRoman" };

	private final Element numbering;
	private final int orderedAbstractNumId;
	private final int headingsNumId;
	private final int bulletsNumId;

	private int nextNumId;

	public DocxNumbering(DocxDocument document) {
		this.numbering = document.numbering().getDocumentElement();

		int nextAbstractNumId = 0;
		for (Element abstractNum : Wml.children(numbering, "abstractNum")) {
			nextAbstractNumId = Math.max(nextAbstractNumId, Integer.parseInt(Wml.attr(abstractNum, "abstractNumId")) + 1);
		}
		nextNumId = 1;
		for (Element num : Wml.children(numbering, "num")) {
			nextNumId = Math.max(nextNumId, Integer.parseInt(Wml.attr(num, "numId")) + 1);
		}

		int headingsAbstractNumId = nextAbstractNumId;
		Element headingsAbstractNum = addAbstractNum(headingsAbstractNumId);
		StringBuilder headingText = new StringBuilder();
		for (int level = 0; level < HEADING_LEVELS_COUNT; ++level) {
			if (level > 0) {
				headingText.append('.');
			}
			headingText.append('%').append(level + 1);
			addLevel(headingsAbstractNum, level, DocxStyles.getHeadingStyleId(level + 1), "decimal", headingText.toString(), LEVEL_INDENT);
		}

		int bulletsAbstractNumId = nextAbstractNumId + 1;
		Element bulletsAbstractNum = addAbstractNum(bulletsAbstractNumId);
		for (int level = 0; level < LEVELS_COUNT; ++level) {
			int symbol = level % BULLET_TEXTS.length;
			Element lvl = addLevel(bulletsAbstractNum, level, DocxStyles.getListStyleId(), "bullet", BULLET_TEXTS[symbol], textIndent(level));
			addFonts(lvl, BULLET_FONTS[symbol]);
		}

		orderedAbstractNumId = nextAbstractNumId + 2;
		Element orderedAbstractNum = addAbstractNum(orderedAbstractNumId);
		for (int level = 0; level < LEVELS_COUNT; ++level) {
			String format = ORDERED_FORMATS[level % ORDERED_FORMATS.length];
			addLevel(orderedAbstractNum, level, DocxStyles.getListStyleId(), format, "%" + (level + 1) + ".", textIndent(level));
		}

		headingsNumId = addNum(headingsAbstractNumId).numId;
		bulletsNumId = addNum(bulletsAbstractNumId).numId;
	}

	/**
	 * Create numbering instance for a new ordered list so that every list restarts its counting.
	 *
	 * @param level list nesting level, 0 based.
	 * @param start list start number as declared in Markdown source.
	 * @return numbering instance ID.
	 */
	public int addOrderedNum(int level, int start) {
		Num num = addNum(orderedAbstractNumId);
		// <w:lvlOverride w:ilvl="1">
		// ..<w:startOverride w:val="3" />
		// </w:lvlOverride>
		Element override = Wml.attr(Wml.add(num.element, "lvlOverride"), "ilvl", level);
		Wml.property(override, "startOverride", start);
		return num.numId;
	}

	/** Left indentation, in twips, for the text of a list item at given nesting level, 0 based. */
	public static int textIndent(int level) {
		return LEVEL_INDENT * (Math.min(level, LEVELS_COUNT - 1) + 1);
	}

	public static int maxLevel() {
		return LEVELS_COUNT - 1;
	}

	public int getHeadingsNumId() {
		return headingsNumId;
	}

	public int getBulletsNumId() {
		return bulletsNumId;
	}

	// --------------------------------------------------------------------------------------------

	/** Abstract numbering definitions precede all numbering instances, as required by schema. */
	private Element addAbstractNum(int abstractNumId) {
		Element abstractNum = Wml.attr(Wml.create(numbering, "abstractNum"), "abstractNumId", abstractNumId);
		Node before = Wml.child(numbering, "num");
		if (before == null) {
			before = Wml.child(numbering, "numIdMacAtCleanup");
		}
		numbering.insertBefore(abstractNum, before);
		return abstractNum;
	}

	private Num addNum(int abstractNumId) {
		int numId = nextNumId++;
		// <w:num w:numId="5">
		// ..<w:abstractNumId w:val="3" />
		// </w:num>
		Element num = Wml.attr(Wml.create(numbering, "num"), "numId", numId);
		Wml.property(num, "abstractNumId", abstractNumId);
		numbering.insertBefore(num, Wml.child(numbering, "numIdMacAtCleanup"));
		return new Num(numId, num);
	}

	private static Element addLevel(Element abstractNum, int ilvl, String style, String numFmt, String lvlText, int leftIndent) {
		// child elements order is imposed by schema
		Element lvl = Wml.attr(Wml.add(abstractNum, "lvl"), "ilvl", ilvl);
		Wml.attr(Wml.add(lvl, "start"), "val", 1);
		Wml.attr(Wml.add(lvl, "numFmt"), "val", numFmt);
		Wml.attr(Wml.add(lvl, "pStyle"), "val", style);
		Wml.attr(Wml.add(lvl, "lvlText"), "val", lvlText);
		Wml.attr(Wml.add(lvl, "lvlJc"), "val", "left");

		Element ind = Wml.add(Wml.add(lvl, "pPr"), "ind");
		Wml.attr(ind, "left", leftIndent);
		Wml.attr(ind, "hanging", LEVEL_INDENT);
		return lvl;
	}

	private static void addFonts(Element lvl, String font) {
		Element rFonts = Wml.add(Wml.add(lvl, "rPr"), "rFonts");
		Wml.attr(rFonts, "ascii", font);
		Wml.attr(rFonts, "hAnsi", font);
		Wml.attr(rFonts, "hint", "default");
	}

	private static class Num {
		final int numId;
		final Element element;

		Num(int numId, Element element) {
			this.numId = numId;
			this.element = element;
		}
	}
}
