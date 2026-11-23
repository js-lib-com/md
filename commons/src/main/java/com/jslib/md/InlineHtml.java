package com.jslib.md;

import java.util.Arrays;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Inline HTML support shared by document generators: tag parsing, mapping of formatting tags to text formats and open
 * formats tracking. HTML is not interpreted beyond these tags.
 */
public final class InlineHtml {
	public enum Format {
		BOLD, ITALIC, UNDERLINE, STRIKE, CODE, SUPERSCRIPT, SUBSCRIPT, HIGHLIGHT
	}

	private static final Map<String, Format> FORMAT_TAGS = Map.ofEntries( //
			Map.entry("b", Format.BOLD), Map.entry("strong", Format.BOLD), //
			Map.entry("i", Format.ITALIC), Map.entry("em", Format.ITALIC), Map.entry("cite", Format.ITALIC), Map.entry("var", Format.ITALIC), //
			Map.entry("u", Format.UNDERLINE), Map.entry("ins", Format.UNDERLINE), //
			Map.entry("s", Format.STRIKE), Map.entry("del", Format.STRIKE), Map.entry("strike", Format.STRIKE), //
			Map.entry("code", Format.CODE), Map.entry("kbd", Format.CODE), Map.entry("samp", Format.CODE), Map.entry("tt", Format.CODE), //
			Map.entry("sup", Format.SUPERSCRIPT), Map.entry("sub", Format.SUBSCRIPT), //
			Map.entry("mark", Format.HIGHLIGHT));

	private static final Pattern TAG = Pattern.compile("^<\\s*(/?)\\s*([a-zA-Z][a-zA-Z0-9]*)\\b([^>]*)>$", Pattern.DOTALL);
	private static final Pattern HREF = Pattern.compile("\\bhref\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))", Pattern.CASE_INSENSITIVE);

	private InlineHtml() {
	}

	/** Parse inline HTML literal as a single tag; return null for comments, processing instructions and alike. */
	public static Tag parse(String literal) {
		Matcher matcher = TAG.matcher(literal);
		if (!matcher.matches()) {
			return null;
		}
		return new Tag(matcher.group(2).toLowerCase(), !matcher.group(1).isEmpty(), matcher.group(3));
	}

	public static final class Tag {
		/** Lower case tag name. */
		public final String name;
		public final boolean closing;
		private final String attributes;

		private Tag(String name, boolean closing, String attributes) {
			this.name = name;
			this.closing = closing;
			this.attributes = attributes;
		}

		public boolean isSelfClosing() {
			return attributes.strip().endsWith("/");
		}

		/** Text format for formatting tags or null. */
		public Format format() {
			return FORMAT_TAGS.get(name);
		}

		public boolean isLineBreak() {
			return name.equals("br");
		}

		public boolean isAnchor() {
			return name.equals("a");
		}

		/** Value of href attribute, with ampersand entity decoded, or null if missing. */
		public String href() {
			Matcher matcher = HREF.matcher(attributes);
			if (!matcher.find()) {
				return null;
			}
			String href = matcher.group(1) != null ? matcher.group(1) : matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
			return href.replace("&amp;", "&");
		}
	}

	/** Open formats with nesting depth. Unbalanced closing tags are ignored. */
	public static final class Formats {
		private final int[] depths = new int[Format.values().length];

		public boolean isOpen(Format format) {
			return depths[format.ordinal()] > 0;
		}

		/** Update open formats for a formatting tag; return false if tag is not a formatting one. */
		public boolean apply(Tag tag) {
			Format format = tag.format();
			if (format == null) {
				return false;
			}
			if (tag.closing) {
				if (depths[format.ordinal()] > 0) {
					--depths[format.ordinal()];
				}
			} else if (!tag.isSelfClosing()) {
				++depths[format.ordinal()];
			}
			return true;
		}

		public void reset() {
			Arrays.fill(depths, 0);
		}
	}
}
