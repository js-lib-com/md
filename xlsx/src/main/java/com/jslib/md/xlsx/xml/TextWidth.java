package com.jslib.md.xlsx.xml;

import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;
import java.text.AttributedString;

/**
 * Text width measured with AWT font metrics, in units of default font digit width, the unit used by SpreadsheetML
 * column widths. Same algorithm as Apache POI sheet column auto-size: every line is measured with one extra digit as
 * padding and the widest line is used.
 */
final class TextWidth {
	private static final FontRenderContext FONT_RENDER_CONTEXT = new FontRenderContext(null, true, true);
	private static final char DEFAULT_CHAR = '0';

	/** Default font digit width, truncated to integer pixels like Apache POI does. */
	private static int defaultCharWidth;

	private TextWidth() {
	}

	static double of(String text, Font font) {
		double width = -1;
		for (String line : text.split("\\n")) {
			String padded = line + DEFAULT_CHAR;
			TextLayout layout = new TextLayout(attributed(padded, font).getIterator(), FONT_RENDER_CONTEXT);
			Rectangle2D bounds = layout.getBounds();
			width = Math.max(width, (bounds.getX() + bounds.getWidth()) / defaultCharWidth());
		}
		return width;
	}

	private static synchronized int defaultCharWidth() {
		if (defaultCharWidth == 0) {
			TextLayout layout = new TextLayout(attributed(String.valueOf(DEFAULT_CHAR), Font.DEFAULT).getIterator(), FONT_RENDER_CONTEXT);
			defaultCharWidth = (int) layout.getAdvance();
		}
		return defaultCharWidth;
	}

	private static AttributedString attributed(String text, Font font) {
		AttributedString string = new AttributedString(text);
		string.addAttribute(TextAttribute.FAMILY, font.name);
		string.addAttribute(TextAttribute.SIZE, (float) Font.DEFAULT_SIZE);
		if (font.bold) {
			string.addAttribute(TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD);
		}
		if (font.italic) {
			string.addAttribute(TextAttribute.POSTURE, TextAttribute.POSTURE_OBLIQUE);
		}
		if (font.underline) {
			string.addAttribute(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
		}
		return string;
	}
}
