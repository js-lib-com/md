package com.jslib.md.xlsx.xml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Worksheet with rows created top to bottom. */
public final class Sheet {
	/** Excel maximum column width, in 1/256 of character width. */
	private static final int MAX_COLUMN_WIDTH = 255 * 256;

	private final String name;
	private final List<Row> rows = new ArrayList<>();
	/** Column widths, in 1/256 of character width, by column index. */
	private final Map<Integer, Integer> columnWidths = new TreeMap<>();
	/** Columns with width fitted to content by {@link #autoSizeColumn(int)}. */
	private final Set<Integer> bestFit = new HashSet<>();

	Sheet(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public Row createRow() {
		Row row = new Row();
		rows.add(row);
		return row;
	}

	public List<Row> getRows() {
		return Collections.unmodifiableList(rows);
	}

	/** Row at given index or null if sheet has no such row. */
	public Row getRow(int index) {
		return index < rows.size() ? rows.get(index) : null;
	}

	/**
	 * Set column width.
	 *
	 * @param column column index, 0 based.
	 * @param width column width in 1/256 of character width.
	 */
	public void setColumnWidth(int column, int width) {
		columnWidths.put(column, width);
	}

	/** Column width in 1/256 of character width or -1 if not set. */
	public int getColumnWidth(int column) {
		return columnWidths.getOrDefault(column, -1);
	}

	/** Set column width to fit its widest cell text, measured with cell style font. Has no effect on empty column. */
	public void autoSizeColumn(int column) {
		double width = -1;
		for (Row row : rows) {
			Cell cell = row.getCell(column);
			if (cell != null && !cell.isBlank()) {
				width = Math.max(width, TextWidth.of(cell.getText(), cell.getStyle().font));
			}
		}
		if (width != -1) {
			setColumnWidth(column, (int) Math.round(Math.min(width * 256, MAX_COLUMN_WIDTH)));
			bestFit.add(column);
		}
	}

	Map<Integer, Integer> columnWidths() {
		return columnWidths;
	}

	boolean isBestFit(int column) {
		return bestFit.contains(column);
	}
}
