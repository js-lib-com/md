package com.jslib.md.docx;

import java.io.IOException;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.apache.xmlbeans.XmlException;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle;

public class DocxStyles {
	private static final String HEADING1_STYLE_ID = "Heading1";
	private static final String HEADING2_STYLE_ID = "Heading2";
	private static final String HEADING3_STYLE_ID = "Heading3";
	private static final String HEADING4_STYLE_ID = "Heading4";
	private static final String NO_SPACING = "NoSpacing";
	private static final String LIST_STYLE_ID = "ListParagraph";
	private static final String LINK_STYLE_ID = "Hyperlink";
	private static final String TABLE_STYLE_ID = "LightShading-Accent1";
	private static final String QUOTE_STYLE_ID = "Quote";
	private static final String QUOTE_CHAR_STYLE_ID = "QuoteChar";
	private static final String INTENSE_QUOTE_STYLE_ID = "IntenseQuote";
	private static final String INTENSE_QUOTE_CHAR_STYLE_ID = "IntenseQuoteChar";

	private static final String HEADING1_STYLE_RESOURCE = "/heading1-style.xml";
	private static final String HEADING2_STYLE_RESOURCE = "/heading2-style.xml";
	private static final String HEADING3_STYLE_RESOURCE = "/heading3-style.xml";
	private static final String HEADING4_STYLE_RESOURCE = "/heading4-style.xml";
	private static final String LIST_STYLE_RESOURCE = "/list-paragraph-style.xml";
	// private static final String LINK_STYLE_RESOURCE = "/link-style.xml";
	private static final String TABLE_STYLE_RESOURCE = "/table-style.xml";
	private static final String QUOTE_STYLE_RESOURCE = "/quote-style.xml";
	private static final String INTENSE_QUOTE_STYLE_RESOURCE = "/intense-quote-style.xml";

	private final XWPFStyles styles;

	public DocxStyles(XWPFDocument document) throws IOException, XmlException {
		this.styles = document.createStyles();
		addStyle(HEADING1_STYLE_ID, HEADING1_STYLE_RESOURCE);
		addStyle(HEADING2_STYLE_ID, HEADING2_STYLE_RESOURCE);
		addStyle(HEADING3_STYLE_ID, HEADING3_STYLE_RESOURCE);
		addStyle(HEADING4_STYLE_ID, HEADING4_STYLE_RESOURCE);
		addStyle(LIST_STYLE_ID, LIST_STYLE_RESOURCE);
		// addStyle(LINK_STYLE_ID, LINK_STYLE_RESOURCE);
		addStyle(TABLE_STYLE_ID, TABLE_STYLE_RESOURCE);
		addStyle(QUOTE_STYLE_ID, QUOTE_STYLE_RESOURCE);
		addStyle(INTENSE_QUOTE_STYLE_ID, INTENSE_QUOTE_STYLE_RESOURCE);
	}

	private void addStyle(String styleID, String styleResource) throws IOException, XmlException {
		if (!styles.styleExist(styleID)) {
			CTStyle ctStyle = CTStyle.Factory.parse(getClass().getResourceAsStream(styleResource));
			styles.addStyle(new XWPFStyle(ctStyle));
		}
	}

	public static String getHeadingStyleId(int level) {
		switch (level) {
		case 1:
			return HEADING1_STYLE_ID;
		case 2:
			return HEADING2_STYLE_ID;
		case 3:
			return HEADING3_STYLE_ID;
		case 4:
			return HEADING4_STYLE_ID;
		default:
			throw new IllegalStateException("Unsuported level " + level);
		}
	}

	public static String getNoSpacing() {
		return NO_SPACING;
	}

	public static String getHeading1StyleId() {
		return HEADING1_STYLE_ID;
	}

	public static String getHeading2StyleId() {
		return HEADING2_STYLE_ID;
	}

	public static String getHeading3StyleId() {
		return HEADING3_STYLE_ID;
	}

	public static String getHeading4StyleId() {
		return HEADING4_STYLE_ID;
	}

	public static String getListStyleId() {
		return LIST_STYLE_ID;
	}

	public static String getLinkStyleId() {
		return LINK_STYLE_ID;
	}

	public static String getTableStyleId() {
		return TABLE_STYLE_ID;
	}

	public static String getQuoteStyleId() {
		return QUOTE_STYLE_ID;
	}

	public static String getQuoteCharStyleId() {
		return QUOTE_CHAR_STYLE_ID;
	}

	public static String getIntenseQuoteStyleId() {
		return INTENSE_QUOTE_STYLE_ID;
	}

	public static String getIntenseQuoteCharStyleId() {
		return INTENSE_QUOTE_CHAR_STYLE_ID;
	}
}
