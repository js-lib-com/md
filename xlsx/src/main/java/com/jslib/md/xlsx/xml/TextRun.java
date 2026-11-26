package com.jslib.md.xlsx.xml;

import java.util.Objects;

/** Rich text fragment; null font means text formatted with cell style font. */
public final class TextRun {
	final String text;
	final Font font;

	public TextRun(String text, Font font) {
		this.text = text;
		this.font = font;
	}

	@Override
	public boolean equals(Object object) {
		if (this == object) {
			return true;
		}
		if (!(object instanceof TextRun)) {
			return false;
		}
		TextRun other = (TextRun) object;
		return text.equals(other.text) && Objects.equals(font, other.font);
	}

	@Override
	public int hashCode() {
		return Objects.hash(text, font);
	}
}
