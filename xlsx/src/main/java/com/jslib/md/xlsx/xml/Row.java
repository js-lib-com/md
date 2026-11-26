package com.jslib.md.xlsx.xml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Sheet row with cells created left to right. */
public final class Row {
	private final List<Cell> cells = new ArrayList<>();

	Row() {
	}

	public Cell createCell() {
		Cell cell = new Cell(cells.size());
		cells.add(cell);
		return cell;
	}

	public List<Cell> getCells() {
		return Collections.unmodifiableList(cells);
	}

	/** Cell at given column or null if row has no such cell. */
	public Cell getCell(int column) {
		return column < cells.size() ? cells.get(column) : null;
	}
}
