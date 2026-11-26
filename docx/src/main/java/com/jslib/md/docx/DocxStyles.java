package com.jslib.md.docx;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.jslib.md.docx.xml.DocxDocument;
import com.jslib.md.docx.xml.Wml;

/**
 * Style IDs used by generated content and installation of their definitions into document styles. Styles already
 * defined by document template are kept; missing ones are added from style resources.
 */
public final class DocxStyles {
	public static final String NO_SPACING = "NoSpacing";
	public static final String LIST_PARAGRAPH = "ListParagraph";
	public static final String HYPERLINK = "Hyperlink";
	public static final String TABLE = "LightShading-Accent1";
	public static final String QUOTE_CHAR = "QuoteChar";
	public static final String INTENSE_QUOTE = "IntenseQuote";

	/**
	 * Style resources by style ID, in installation order; heading 5 and 6 styles are provided by document template.
	 */
	private static final List<Map.Entry<String, String>> RESOURCES = List.of( //
			Map.entry("Heading1", "/heading1-style.xml"), //
			Map.entry("Heading2", "/heading2-style.xml"), //
			Map.entry("Heading3", "/heading3-style.xml"), //
			Map.entry("Heading4", "/heading4-style.xml"), //
			Map.entry(LIST_PARAGRAPH, "/list-paragraph-style.xml"), //
			Map.entry(TABLE, "/table-style.xml"), //
			Map.entry("Quote", "/quote-style.xml"), //
			Map.entry(INTENSE_QUOTE, "/intense-quote-style.xml"));

	private DocxStyles() {
	}

	/** Heading paragraph style ID for heading level, 1 to 6. */
	public static String heading(int level) {
		if (level < 1 || level > 6) {
			throw new IllegalStateException("Unsupported heading level " + level);
		}
		return "Heading" + level;
	}

	/** Add to document the styles used by generated content, if not already defined by document template. */
	public static void install(DocxDocument document) throws IOException {
		Element styles = document.styles().getDocumentElement();
		Set<String> styleIds = new HashSet<>();
		for (Element style : Wml.children(styles, "style")) {
			styleIds.add(Wml.attr(style, "styleId"));
		}
		for (Map.Entry<String, String> resource : RESOURCES) {
			if (!styleIds.contains(resource.getKey())) {
				addStyles(styles, styleIds, resource.getValue());
			}
		}
	}

	/**
	 * Add styles from resource. Resource root is either a single style or a styles element with related styles, e.g.
	 * paragraph style with its linked character style.
	 */
	private static void addStyles(Element styles, Set<String> styleIds, String styleResource) throws IOException {
		Document resource;
		try (InputStream stream = DocxStyles.class.getResourceAsStream(styleResource)) {
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
}
