package com.jslib.md.xlsx.xml;

import java.util.Objects;

/** Immutable font description; equal fonts share the same font record in workbook styles. */
public final class Font {
	public static final String DEFAULT_NAME = "Calibri";
	public static final int DEFAULT_SIZE = 11;

	public static final Font DEFAULT = new Font(DEFAULT_NAME, false, false, false, false, null, null);

	public enum VerticalAlign {
		SUPERSCRIPT("superscript"), SUBSCRIPT("subscript");

		final String value;

		VerticalAlign(String value) {
			this.value = value;
		}
	}

	final String name;
	final boolean bold;
	final boolean italic;
	final boolean underline;
	final boolean strike;
	/** Indexed color or null for automatic color. */
	final Integer color;
	final VerticalAlign verticalAlign;

	private Font(String name, boolean bold, boolean italic, boolean underline, boolean strike, Integer color, VerticalAlign verticalAlign) {
		this.name = name;
		this.bold = bold;
		this.italic = italic;
		this.underline = underline;
		this.strike = strike;
		this.color = color;
		this.verticalAlign = verticalAlign;
	}

	public Font name(String name) {
		return new Font(name, bold, italic, underline, strike, color, verticalAlign);
	}

	public Font bold() {
		return new Font(name, true, italic, underline, strike, color, verticalAlign);
	}

	public Font italic() {
		return new Font(name, bold, true, underline, strike, color, verticalAlign);
	}

	public Font underline() {
		return new Font(name, bold, italic, true, strike, color, verticalAlign);
	}

	public Font strike() {
		return new Font(name, bold, italic, underline, true, color, verticalAlign);
	}

	public Font color(IndexedColor color) {
		return new Font(name, bold, italic, underline, strike, color.index, verticalAlign);
	}

	public Font verticalAlign(VerticalAlign verticalAlign) {
		return new Font(name, bold, italic, underline, strike, color, verticalAlign);
	}

	@Override
	public boolean equals(Object object) {
		if (this == object) {
			return true;
		}
		if (!(object instanceof Font)) {
			return false;
		}
		Font other = (Font) object;
		return bold == other.bold && italic == other.italic && underline == other.underline && strike == other.strike && name.equals(other.name) && Objects.equals(color, other.color) && verticalAlign == other.verticalAlign;
	}

	@Override
	public int hashCode() {
		return Objects.hash(name, bold, italic, underline, strike, color, verticalAlign);
	}
}
