package com.jslib.md;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Markdown HTML blocks rendered as plain text, since HTML is not interpreted by document generators, except page break
 * style.
 */
public final class HtmlText {
	private static final Pattern COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
	private static final Pattern PAGE_BREAK = Pattern.compile("(page-)?break-(before|after)\\s*:\\s*(always|page)", Pattern.CASE_INSENSITIVE);
	private static final Pattern BLOCK_TAG = Pattern.compile("</?(p|div|br|li|ul|ol|h[1-6]|tr|table|blockquote|pre|hr)\\b[^>]*>", Pattern.CASE_INSENSITIVE);
	private static final Pattern TAG = Pattern.compile("<[^>]*>");

	private HtmlText() {
	}

	/** True if HTML, comments excluded, has a CSS page break style, e.g. <code>page-break-after: always</code>. */
	public static boolean isPageBreak(String html) {
		return PAGE_BREAK.matcher(withoutComments(html)).find();
	}

	/**
	 * Text lines of HTML, one per HTML block element, without tags and comments and with common entities decoded. Blank
	 * lines are removed, so that HTML without text has no lines.
	 */
	public static List<String> lines(String html) {
		String text = withoutComments(html).replaceAll("\\s+", " ");
		text = BLOCK_TAG.matcher(text).replaceAll("\n");
		text = TAG.matcher(text).replaceAll("");
		text = text.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'").replace("&amp;", "&");

		List<String> lines = new ArrayList<>();
		for (String line : text.split("\n")) {
			if (!line.isBlank()) {
				lines.add(line.strip());
			}
		}
		return lines;
	}

	private static String withoutComments(String html) {
		return COMMENT.matcher(html).replaceAll("");
	}
}
