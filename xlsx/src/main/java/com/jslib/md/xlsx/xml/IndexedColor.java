package com.jslib.md.xlsx.xml;

/** Colors from SpreadsheetML legacy indexed palette used by generator. */
public enum IndexedColor {
	BLACK(8), WHITE(9), BLUE(12), YELLOW(13), TEAL(21), GREY_25_PERCENT(22);

	public final int index;

	IndexedColor(int index) {
		this.index = index;
	}
}
