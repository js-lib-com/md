package com.jslib.md.docx;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.jslib.md.docx.xml.DocxDocument;
import com.jslib.md.docx.xml.Wml;

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

	private static final String HEADING1_STYLE_RESOURCE = "/heading1-style.xml";
	private static final String HEADING2_STYLE_RESOURCE = "/heading2-style.xml";
	private static final String HEADING3_STYLE_RESOURCE = "/heading3-style.xml";
	private static final String HEADING4_STYLE_RESOURCE = "/heading4-style.xml";
	private static final String LIST_STYLE_RESOURCE = "/list-paragraph-style.xml";
	// private static final String LINK_STYLE_RESOURCE = "/link-style.xml";
	private static final String TABLE_STYLE_RESOURCE = "/table-style.xml";
	private static final String QUOTE_STYLE_RESOURCE = "/quote-style.xml";
	private static final String INTENSE_QUOTE_STYLE_RESOURCE = "/intense-quote-style.xml";

	private final Element styles;
	private final Set<String> styleIds = new HashSet<>();

	public DocxStyles(DocxDocument document) throws IOException {
		this.styles = document.styles().getDocumentElement();
		for (Element style : Wml.children(styles, "style")) {
			styleIds.add(Wml.attr(style, "styleId"));
		}
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

	/**
	 * Add styles from resource, if not already defined by document template. Resource root is either a single style or
	 * a styles element with related styles, e.g. paragraph style with its linked character style.
	 */
	private void addStyle(String styleID, String styleResource) throws IOException {
		if (styleIds.contains(styleID)) {
			return;
		}
		Document resource;
		try (InputStream stream = getClass().getResourceAsStream(styleResource)) {
			if (stream == null) {
				throw new IOException("Missing style resource " + styleResource);
			}
			resource = Wml.parse(stream);
		}
		Element root = resource.getDocumentElement();
		Wml.stripWhitespace(root);
		List<Element> resourceStyles = Wml.is(root, "style") ? List.of(root) : Wml.children(root, "style");
		for (Element style : resourceStyles) {
			if (styleIds.add(Wml.attr(style, "styleId"))) {
				styles.appendChild(styles.getOwnerDocument().importNode(style, true));
			}
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
		case 5:
		case 6:
			// heading 5 and 6 styles are provided by document template
			return "Heading" + level;
		default:
			throw new IllegalStateException("Unsupported level " + level);
		}
	}

	public static String getNoSpacing() {
		return NO_SPACING;
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

	public static String getQuoteCharStyleId() {
		return QUOTE_CHAR_STYLE_ID;
	}

	public static String getIntenseQuoteStyleId() {
		return INTENSE_QUOTE_STYLE_ID;
	}

}
