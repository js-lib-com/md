package com.jslib.md.docx;

import java.math.BigInteger;

import org.apache.poi.xwpf.usermodel.XWPFAbstractNum;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFNum;
import org.apache.poi.xwpf.usermodel.XWPFNumbering;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTAbstractNum;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTInd;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTLvl;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTNumLvl;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPrGeneral;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STHint;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STJc;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STNumberFormat;

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
	private static final STNumberFormat.Enum[] ORDERED_FORMATS = { STNumberFormat.DECIMAL, STNumberFormat.LOWER_LETTER, STNumberFormat.LOWER_ROMAN };

	private final XWPFNumbering numbering;
	private final BigInteger orderedAbstractNumId;
	private final BigInteger headingsNumId;
	private final BigInteger bulletsNumId;

	private BigInteger nextNumId;

	public DocxNumbering(XWPFDocument document) {
		this.numbering = document.createNumbering();

		BigInteger nextAbstractNumId = BigInteger.ZERO;
		for (XWPFAbstractNum abstractNum : numbering.getAbstractNums()) {
			nextAbstractNumId = nextAbstractNumId.max(abstractNum.getCTAbstractNum().getAbstractNumId().add(BigInteger.ONE));
		}
		nextNumId = BigInteger.ONE;
		for (XWPFNum num : numbering.getNums()) {
			nextNumId = nextNumId.max(num.getCTNum().getNumId().add(BigInteger.ONE));
		}

		CTAbstractNum headingsAbstractNum = CTAbstractNum.Factory.newInstance();
		BigInteger headingsAbstractNumId = nextAbstractNumId;
		headingsAbstractNum.setAbstractNumId(headingsAbstractNumId);
		StringBuilder headingText = new StringBuilder();
		for (int level = 0; level < HEADING_LEVELS_COUNT; ++level) {
			if (level > 0) {
				headingText.append('.');
			}
			headingText.append('%').append(level + 1);
			addLevel(headingsAbstractNum, level, DocxStyles.getHeadingStyleId(level + 1), STNumberFormat.DECIMAL, headingText.toString(), LEVEL_INDENT);
		}

		CTAbstractNum bulletsAbstractNum = CTAbstractNum.Factory.newInstance();
		BigInteger bulletsAbstractNumId = nextAbstractNumId.add(BigInteger.ONE);
		bulletsAbstractNum.setAbstractNumId(bulletsAbstractNumId);
		for (int level = 0; level < LEVELS_COUNT; ++level) {
			int symbol = level % BULLET_TEXTS.length;
			CTLvl ctLvl = addLevel(bulletsAbstractNum, level, DocxStyles.getListStyleId(), STNumberFormat.BULLET, BULLET_TEXTS[symbol], textIndent(level));
			addFonts(ctLvl, BULLET_FONTS[symbol]);
		}

		CTAbstractNum orderedAbstractNum = CTAbstractNum.Factory.newInstance();
		orderedAbstractNumId = nextAbstractNumId.add(BigInteger.TWO);
		orderedAbstractNum.setAbstractNumId(orderedAbstractNumId);
		for (int level = 0; level < LEVELS_COUNT; ++level) {
			STNumberFormat.Enum format = ORDERED_FORMATS[level % ORDERED_FORMATS.length];
			addLevel(orderedAbstractNum, level, DocxStyles.getListStyleId(), format, "%" + (level + 1) + ".", textIndent(level));
		}

		numbering.addAbstractNum(new XWPFAbstractNum(headingsAbstractNum));
		numbering.addAbstractNum(new XWPFAbstractNum(bulletsAbstractNum));
		numbering.addAbstractNum(new XWPFAbstractNum(orderedAbstractNum));

		headingsNumId = addNum(headingsAbstractNumId);
		bulletsNumId = addNum(bulletsAbstractNumId);
	}

	/**
	 * Create numbering instance for a new ordered list so that every list restarts its counting.
	 *
	 * @param level list nesting level, 0 based.
	 * @param start list start number as declared in markdown source.
	 * @return numbering instance ID.
	 */
	public BigInteger addOrderedNum(int level, int start) {
		BigInteger numId = addNum(orderedAbstractNumId);
		CTNumLvl override = numbering.getNum(numId).getCTNum().addNewLvlOverride();
		override.setIlvl(BigInteger.valueOf(level));
		override.addNewStartOverride().setVal(BigInteger.valueOf(start));
		return numId;
	}

	/** Left indentation, in twips, for the text of a list item at given nesting level, 0 based. */
	public static int textIndent(int level) {
		return LEVEL_INDENT * (Math.min(level, LEVELS_COUNT - 1) + 1);
	}

	public static int maxLevel() {
		return LEVELS_COUNT - 1;
	}

	public BigInteger getHeadingsNumId() {
		return headingsNumId;
	}

	public BigInteger getBulletsNumId() {
		return bulletsNumId;
	}

	private BigInteger addNum(BigInteger abstractNumId) {
		BigInteger numId = nextNumId;
		nextNumId = nextNumId.add(BigInteger.ONE);
		numbering.addNum(abstractNumId, numId);
		return numId;
	}

	private static CTLvl addLevel(CTAbstractNum abstractNum, int ilvl, String style, STNumberFormat.Enum numFmt, String lvlText, int leftIndent) {
		CTLvl ctLvl = abstractNum.addNewLvl();
		ctLvl.setIlvl(BigInteger.valueOf(ilvl));
		ctLvl.addNewStart().setVal(BigInteger.ONE);
		ctLvl.addNewPStyle().setVal(style);
		ctLvl.addNewNumFmt().setVal(numFmt);
		ctLvl.addNewLvlText().setVal(lvlText);
		ctLvl.addNewLvlJc().setVal(STJc.LEFT);

		CTPPrGeneral ppr = ctLvl.addNewPPr();
		CTInd ind = ppr.addNewInd();
		ind.setLeft(BigInteger.valueOf(leftIndent));
		ind.setHanging(BigInteger.valueOf(LEVEL_INDENT));

		return ctLvl;
	}

	private static void addFonts(CTLvl ctLvl, String font) {
		CTRPr rpr = ctLvl.addNewRPr();
		CTFonts rFonts = rpr.addNewRFonts();
		rFonts.setAscii(font);
		rFonts.setHAnsi(font);
		rFonts.setHint(STHint.DEFAULT);
	}
}
