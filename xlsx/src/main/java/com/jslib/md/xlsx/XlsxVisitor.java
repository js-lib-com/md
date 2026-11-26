package com.jslib.md.xlsx;

import java.util.ArrayList;
import java.util.List;

import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableHead;
import org.commonmark.ext.gfm.tables.TableRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jslib.md.CustomVisitor;
import com.jslib.md.InlineHtml.Format;
import com.jslib.md.xlsx.xml.Cell;
import com.jslib.md.xlsx.xml.CellStyle;
import com.jslib.md.xlsx.xml.Font;
import com.jslib.md.xlsx.xml.IndexedColor;
import com.jslib.md.xlsx.xml.Row;
import com.jslib.md.xlsx.xml.Sheet;
import com.jslib.md.xlsx.xml.TextRun;

/** Render Markdown tables of a file into a sheet: first table header becomes sheet header, all table rows follow. */
public class XlsxVisitor extends CustomVisitor {
	private static final Logger log = LoggerFactory.getLogger(XlsxVisitor.class);

	private final Sheet sheet;
	private final CellStyle headerStyle;
	private final CellStyle cellStyle;

	/** Wrap all cells text since columns width is computed by table auto-fit. */
	private final boolean wrapAll;

	/** Current sheet row and the number of rows created so far, header included. */
	private Row sheetRow;
	private int rowsCount;

	public XlsxVisitor(Sheet sheet, boolean tableAutoFit) {
		this.sheet = sheet;
		this.wrapAll = tableAutoFit;

		Font headerFont = Font.DEFAULT.color(IndexedColor.WHITE);
		this.headerStyle = CellStyle.DEFAULT.font(headerFont).fill(IndexedColor.TEAL).border().centered();
		this.cellStyle = CellStyle.DEFAULT.border(IndexedColor.GREY_25_PERCENT);
	}

	/** Only the first table header of a file is used, as sheet header; headers of next tables are skipped. */
	@Override
	public void visit(TableHead head) {
		if (rowsCount == 0) {
			super.visit(head);
		}
	}

	@Override
	public void visit(TableRow row) {
		sheetRow = sheet.createRow();
		++rowsCount;
		super.visit(row);
	}

	@Override
	public void visit(TableCell cell) {
		boolean header = rowsCount == 1;
		CellContent content = CellContent.of(cell);
		String text = content.text();

		Cell sheetCell = sheetRow.createCell();
		// empty Markdown cell is left blank
		if (!text.isEmpty()) {
			if (content.isFormatted()) {
				sheetCell.setValue(richText(content, header));
			} else {
				sheetCell.setValue(text);
			}
		}

		// line breaks are visible only if cell text is wrapped
		boolean wrap = wrapAll || text.indexOf('\n') != -1;
		// Excel cannot highlight part of a cell text so the whole cell is filled; header keeps its own fill for readability
		boolean highlight = content.isHighlighted() && !header;
		sheetCell.setStyle(style(header ? headerStyle : cellStyle, wrap, highlight));

		if (content.linkDestination() != null) {
			hyperlink(sheetCell, content.linkDestination());
		}
	}

	private List<TextRun> richText(CellContent content, boolean header) {
		List<TextRun> runs = new ArrayList<>();
		for (CellContent.Segment segment : content.segments()) {
			// segments without font of their own use cell style font
			runs.add(new TextRun(segment.text, segment.needsFont() ? font(segment, header) : null));
		}
		return runs;
	}

	private static Font font(CellContent.Segment segment, boolean header) {
		Font font = Font.DEFAULT;
		if (header) {
			font = font.color(IndexedColor.WHITE);
		}
		if (segment.formats.contains(Format.BOLD)) {
			font = font.bold();
		}
		if (segment.formats.contains(Format.ITALIC)) {
			font = font.italic();
		}
		if (segment.formats.contains(Format.UNDERLINE) || segment.link) {
			font = font.underline();
		}
		if (segment.formats.contains(Format.STRIKE)) {
			font = font.strike();
		}
		if (segment.formats.contains(Format.CODE)) {
			font = font.name("Consolas");
		}
		if (segment.formats.contains(Format.SUPERSCRIPT)) {
			font = font.verticalAlign(Font.VerticalAlign.SUPERSCRIPT);
		} else if (segment.formats.contains(Format.SUBSCRIPT)) {
			font = font.verticalAlign(Font.VerticalAlign.SUBSCRIPT);
		}
		if (segment.link && !header) {
			font = font.color(IndexedColor.BLUE);
		}
		return font;
	}


	/** Base cell style variant with text wrapping and / or highlight fill; created once and reused. */
	private static CellStyle style(CellStyle base, boolean wrap, boolean highlight) {
		CellStyle style = base;
		if (wrap) {
			// cells with multiple lines in the same row are aligned on their first line
			style = style.wrap();
		}
		if (highlight) {
			style = style.fill(IndexedColor.YELLOW);
		}
		return style;
	}

	/** Set cell hyperlink; Excel supports a single hyperlink per cell so only the first cell link is used. */
	private static void hyperlink(Cell cell, String destination) {
		try {
			cell.setHyperlink(destination);
		} catch (IllegalArgumentException e) {
			log.warn("Ignore invalid cell hyperlink {}: {}", destination, e.getMessage());
		}
	}
}
