package com.jslib.md.docx;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;

import com.jslib.md.ProjectProperties;
import com.jslib.md.TableColumns;
import com.jslib.md.docx.xml.Wml;

/** WordprocessingML tables: table properties and columns width. */
final class DocxTables {
	private DocxTables() {
	}

	/**
	 * Create detached table element, with table style, filling page content width.
	 *
	 * @param context node from document the table belongs to,
	 * @param contentWidth page content width, in twips.
	 */
	static Element create(Element context, int contentWidth) {
		Element table = Wml.create(context, "tbl");
		Element tblPr = Wml.property(table, "tblPr");
		Wml.property(tblPr, "tblStyle", DocxStyles.TABLE);
		Element width = Wml.property(tblPr, "tblW");
		Wml.attr(width, "w", contentWidth);
		Wml.attr(width, "type", "dxa");
		Wml.property(tblPr, "tblLook", "04A0");
		return table;
	}

	/**
	 * Set columns width once table rows are complete. With auto-fit enabled by project properties, columns width is
	 * proportional with the quantity of text in every column; otherwise all but last column fit their content without
	 * wrapping and last column takes remaining page width.
	 */
	static void setColumnsWidth(Element table, int contentWidth, ProjectProperties projectProperties) {
		List<Element> rows = Wml.children(table, "tr");
		if (rows.isEmpty()) {
			return;
		}
		int columnsCount = Wml.children(rows.get(0), "tc").size();
		if (projectProperties.tableAutoFit) {
			autoFit(table, columnsCount, contentWidth, projectProperties);
			return;
		}
		for (Element row : rows) {
			List<Element> cells = Wml.children(row, "tc");
			for (int column = 0; column < Math.min(columnsCount - 1, cells.size()); ++column) {
				Wml.property(Wml.property(cells.get(column), "tcPr"), "noWrap");
			}
		}
	}

	/**
	 * Set columns width, in percent, proportional with the quantity of text in every column but not less than minimal
	 * column width from project properties. Table layout is fixed so that word processors use computed widths.
	 */
	private static void autoFit(Element table, int columnsCount, int contentWidth, ProjectProperties projectProperties) {
		List<List<String>> texts = new ArrayList<>();
		for (Element row : Wml.children(table, "tr")) {
			List<String> rowTexts = new ArrayList<>();
			for (Element cell : Wml.children(row, "tc")) {
				rowTexts.add(Wml.text(cell));
			}
			texts.add(rowTexts);
		}
		int[] widths = TableColumns.widths(texts, projectProperties.tableColumnMinWidth, projectProperties.tableColumnOutliers);

		Element tblPr = Wml.property(table, "tblPr");
		Wml.attr(Wml.property(tblPr, "tblLayout"), "type", "fixed");

		// fixed layout uses table grid, in twips, computed from page content width
		Element grid = Wml.property(table, "tblGrid");
		while (grid.getFirstChild() != null) {
			grid.removeChild(grid.getFirstChild());
		}
		for (int width : widths) {
			Wml.attr(Wml.add(grid, "gridCol"), "w", (long) contentWidth * width / TableColumns.FULL_WIDTH);
		}

		// cell widths are in fiftieths of a percent
		for (Element row : Wml.children(table, "tr")) {
			List<Element> cells = Wml.children(row, "tc");
			for (int column = 0; column < Math.min(columnsCount, cells.size()); ++column) {
				Element tcW = Wml.property(Wml.property(cells.get(column), "tcPr"), "tcW");
				Wml.attr(tcW, "w", widths[column]);
				Wml.attr(tcW, "type", "pct");
			}
		}
	}
}
