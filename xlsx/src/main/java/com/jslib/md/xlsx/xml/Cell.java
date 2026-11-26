package com.jslib.md.xlsx.xml;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

/** Sheet cell with string value, blank if value is not set. */
public final class Cell {
	private final int column;
	private String text;
	private List<TextRun> runs;
	private CellStyle style = CellStyle.DEFAULT;
	private String hyperlink;

	Cell(int column) {
		this.column = column;
	}

	public void setValue(String text) {
		this.text = text;
		this.runs = null;
	}

	/** Set rich text value; cell text is the concatenation of runs text. */
	public void setValue(List<TextRun> runs) {
		StringBuilder builder = new StringBuilder();
		runs.forEach(run -> builder.append(run.text));
		this.text = builder.toString();
		this.runs = List.copyOf(runs);
	}

	public void setStyle(CellStyle style) {
		this.style = style;
	}

	/**
	 * Set external hyperlink.
	 *
	 * @throws IllegalArgumentException if destination is not a valid URI.
	 */
	public void setHyperlink(String destination) {
		try {
			new URI(destination);
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException("Address of hyperlink must be a valid URI: " + e.getMessage(), e);
		}
		this.hyperlink = destination;
	}

	public int getColumn() {
		return column;
	}

	/** Cell text or empty string if cell is blank. */
	public String getText() {
		return text != null ? text : "";
	}

	public CellStyle getStyle() {
		return style;
	}

	boolean isBlank() {
		return text == null;
	}

	/** Shared string table key: plain text or rich text runs. */
	Object stringKey() {
		return runs != null ? runs : text;
	}

	List<TextRun> runs() {
		return runs;
	}

	String hyperlink() {
		return hyperlink;
	}
}
