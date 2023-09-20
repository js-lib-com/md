package com.jslib.md.docx;

import java.math.BigInteger;

import org.apache.poi.xwpf.usermodel.XWPFAbstractNum;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFNumbering;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTAbstractNum;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTInd;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTLvl;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPrGeneral;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STHint;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STJc;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STNumberFormat;

public class DocxNumbering {
	private static final BigInteger HEADERS_ABSTRACT_NUM_ID = BigInteger.valueOf(0);
	private static final BigInteger HEADERS_INSTANCE_NUM_ID = BigInteger.valueOf(1);

	private static final BigInteger BULLETS_ABSTRACT_NUM_ID = BigInteger.valueOf(1);
	private static final BigInteger BULLETS_INSTANCE_NUM_ID = BigInteger.valueOf(2);

	public DocxNumbering(XWPFDocument document) {
		CTAbstractNum headersAbstractNum = CTAbstractNum.Factory.newInstance();
		headersAbstractNum.setAbstractNumId(HEADERS_ABSTRACT_NUM_ID);
		addLevel(headersAbstractNum, 0, DocxStyles.getHeading1StyleId(), STNumberFormat.DECIMAL, "%1");
		addLevel(headersAbstractNum, 1, DocxStyles.getHeading2StyleId(), STNumberFormat.DECIMAL, "%1.%2");
		addLevel(headersAbstractNum, 2, DocxStyles.getHeading3StyleId(), STNumberFormat.DECIMAL, "%1.%2.%3");
		addLevel(headersAbstractNum, 3, DocxStyles.getHeading4StyleId(), STNumberFormat.DECIMAL, "%1.%2.%3.%4");

		CTAbstractNum bulletsAbstractNum = CTAbstractNum.Factory.newInstance();
		bulletsAbstractNum.setAbstractNumId(BULLETS_ABSTRACT_NUM_ID);
		CTLvl ctLvl = addLevel(bulletsAbstractNum, 0, DocxStyles.getListStyleId(), STNumberFormat.BULLET, "");
		addFonts(ctLvl, "Symbol", "Symbol", STHint.DEFAULT);

		XWPFNumbering numbering = document.createNumbering();
		numbering.addAbstractNum(new XWPFAbstractNum(headersAbstractNum));
		numbering.addAbstractNum(new XWPFAbstractNum(bulletsAbstractNum));
		numbering.addNum(HEADERS_ABSTRACT_NUM_ID, HEADERS_INSTANCE_NUM_ID);
		numbering.addNum(BULLETS_ABSTRACT_NUM_ID, BULLETS_INSTANCE_NUM_ID);
	}

	private static CTLvl addLevel(CTAbstractNum abstractNum, int ilvl, String style, STNumberFormat.Enum numFmt, String lvlText) {
		CTLvl ctLvl = abstractNum.addNewLvl();
		ctLvl.setIlvl(BigInteger.valueOf(ilvl));
		ctLvl.addNewStart().setVal(BigInteger.ONE);
		ctLvl.addNewPStyle().setVal(style);
		ctLvl.addNewNumFmt().setVal(numFmt);
		ctLvl.addNewLvlText().setVal(lvlText);
		ctLvl.addNewLvlJc().setVal(STJc.LEFT);

		CTPPrGeneral ppr = ctLvl.addNewPPr();
		CTInd ind = ppr.addNewInd();
		ind.setLeft(BigInteger.valueOf(432));
		ind.setHanging(BigInteger.valueOf(432));

		return ctLvl;
	}

	private static void addFonts(CTLvl ctLvl, String ascii, String hAnsi, STHint.Enum hint) {
		CTRPr rpr = ctLvl.addNewRPr();
		CTFonts rFonts = rpr.addNewRFonts();
		rFonts.setAscii(ascii);
		rFonts.setHAnsi(hAnsi);
		rFonts.setHint(hint);
	}

	public BigInteger getHeadingsNumId() {
		return HEADERS_INSTANCE_NUM_ID;
	}

	public BigInteger getBulletsNumId() {
		return BULLETS_INSTANCE_NUM_ID;
	}
}
