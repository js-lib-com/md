package com.jslib.md.xlsx.xml;

import java.util.Objects;

/** Immutable cell format; equal styles share the same cell format record in workbook styles. */
public final class CellStyle {
	public static final CellStyle DEFAULT = new CellStyle(Font.DEFAULT, null, false, null, false, false, false);

	final Font font;
	/** Solid fill indexed color or null for no fill. */
	final Integer fill;
	/** Thin border on all cell sides. */
	final boolean border;
	/** Border indexed color or null for automatic color. */
	final Integer borderColor;
	final boolean centered;
	final boolean wrap;
	final boolean top;

	private CellStyle(Font font, Integer fill, boolean border, Integer borderColor, boolean centered, boolean wrap, boolean top) {
		this.font = font;
		this.fill = fill;
		this.border = border;
		this.borderColor = borderColor;
		this.centered = centered;
		this.wrap = wrap;
		this.top = top;
	}

	public Font font() {
		return font;
	}

	public CellStyle font(Font font) {
		return new CellStyle(font, fill, border, borderColor, centered, wrap, top);
	}

	public CellStyle fill(IndexedColor fill) {
		return new CellStyle(font, fill.index, border, borderColor, centered, wrap, top);
	}

	public CellStyle border() {
		return new CellStyle(font, fill, true, null, centered, wrap, top);
	}

	public CellStyle border(IndexedColor borderColor) {
		return new CellStyle(font, fill, true, borderColor.index, centered, wrap, top);
	}

	public CellStyle centered() {
		return new CellStyle(font, fill, border, borderColor, true, wrap, top);
	}

	/** Wrap cell text, aligned on cell top. */
	public CellStyle wrap() {
		return new CellStyle(font, fill, border, borderColor, centered, true, true);
	}

	boolean hasAlignment() {
		return centered || wrap || top;
	}

	@Override
	public boolean equals(Object object) {
		if (this == object) {
			return true;
		}
		if (!(object instanceof CellStyle)) {
			return false;
		}
		CellStyle other = (CellStyle) object;
		return border == other.border && centered == other.centered && wrap == other.wrap && top == other.top && font.equals(other.font) && Objects.equals(fill, other.fill) && Objects.equals(borderColor, other.borderColor);
	}

	@Override
	public int hashCode() {
		return Objects.hash(font, fill, border, borderColor, centered, wrap, top);
	}
}
