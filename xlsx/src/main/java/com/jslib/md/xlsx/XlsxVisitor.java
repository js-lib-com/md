package com.jslib.md.xlsx;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Hyperlink;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.RichTextString;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableBody;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableHead;
import org.commonmark.ext.gfm.tables.TableRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jslib.md.CustomVisitor;
import com.jslib.md.InlineHtml.Format;

public class XlsxVisitor extends CustomVisitor {
	private static final Logger log = LoggerFactory.getLogger(XlsxVisitor.class);

	private final Sheet sheet;
	private final CellStyle xlsxHeaderStyle;
	private final CellStyle xlsxCellStyle;

	private Row xlsxRow;
	private int xlsxRowIndex;
	private int xlsxCellIndex;

	public XlsxVisitor(Sheet sheet) {
		super();
		log.trace("XlsxVisitor(Sheet sheet)");
		this.sheet = sheet;

		this.xlsxHeaderStyle = sheet.getWorkbook().createCellStyle();
		Font xlsxHeaderFont = sheet.getWorkbook().createFont();
		xlsxHeaderFont.setBold(false);
		xlsxHeaderFont.setColor(IndexedColors.WHITE.getIndex());
		xlsxHeaderStyle.setFont(xlsxHeaderFont);

		xlsxHeaderStyle.setFillForegroundColor(IndexedColors.TEAL.getIndex());
		xlsxHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		xlsxHeaderStyle.setBorderBottom(BorderStyle.THIN);
		xlsxHeaderStyle.setBorderTop(BorderStyle.THIN);
		xlsxHeaderStyle.setBorderLeft(BorderStyle.THIN);
		xlsxHeaderStyle.setBorderRight(BorderStyle.THIN);
		xlsxHeaderStyle.setAlignment(HorizontalAlignment.CENTER);

		this.xlsxCellStyle = sheet.getWorkbook().createCellStyle();
		xlsxCellStyle.setBorderBottom(BorderStyle.THIN);
		xlsxCellStyle.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
		xlsxCellStyle.setBorderTop(BorderStyle.THIN);
		xlsxCellStyle.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
		xlsxCellStyle.setBorderLeft(BorderStyle.THIN);
		xlsxCellStyle.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
		xlsxCellStyle.setBorderRight(BorderStyle.THIN);
		xlsxCellStyle.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
	}

	@Override
	public void visit(TableBlock table) {
		log.trace("visit table block");
		super.visit(table);
	}

	@Override
	public void visit(TableHead head) {
		log.trace("visit table head");
		if (xlsxRowIndex == 0) {
			super.visit(head);
		}
	}

	@Override
	public void visit(TableBody body) {
		log.trace("visit table body");
		super.visit(body);
	}

	@Override
	public void visit(TableRow row) {
		log.trace("visit table row");

		xlsxCellIndex = 0;
		xlsxRow = sheet.createRow(xlsxRowIndex++);

		super.visit(row);
	}

	@Override
	public void visit(TableCell cell) {
		log.trace("visit table cell");

		boolean header = xlsxRowIndex == 1;
		CellContent content = CellContent.of(cell);
		String text = content.text();
		log.trace("Cell text: {}", text);

		Cell xlsxCell = xlsxRow.createCell(xlsxCellIndex++);
		// empty markdown cell is left blank
		if (!text.isEmpty()) {
			if (content.isFormatted()) {
				xlsxCell.setCellValue(richText(content, header));
			} else {
				xlsxCell.setCellValue(text);
			}
		}

		// line breaks are visible only if cell text is wrapped
		boolean wrap = text.indexOf('\n') != -1;
		// Excel cannot highlight part of a cell text so the whole cell is filled; header keeps its own fill for readability
		boolean highlight = content.isHighlighted() && !header;
		xlsxCell.setCellStyle(style(header ? xlsxHeaderStyle : xlsxCellStyle, wrap, highlight));

		if (content.linkDestination() != null) {
			hyperlink(xlsxCell, content.linkDestination());
		}
	}

	private RichTextString richText(CellContent content, boolean header) {
		Workbook workbook = sheet.getWorkbook();
		RichTextString richText = workbook.getCreationHelper().createRichTextString(content.text());
		int position = 0;
		for (CellContent.Segment segment : content.segments()) {
			int end = position + segment.text.length();
			if (!segment.isPlain()) {
				// segments without explicit font use cell style font
				richText.applyFont(position, end, font(segment, header));
			}
			position = end;
		}
		return richText;
	}

	private final Map<String, Font> fonts = new HashMap<>();

	private Font font(CellContent.Segment segment, boolean header) {
		// highlight does not change the font so it is not part of the key
		String key = header + ":" + segment.link + ":" + segment.formats.stream().filter(format -> format != Format.HIGHLIGHT).collect(Collectors.toList());
		return fonts.computeIfAbsent(key, k -> {
			Font font = sheet.getWorkbook().createFont();
			if (header) {
				font.setColor(IndexedColors.WHITE.getIndex());
			}
			if (segment.formats.contains(Format.BOLD)) {
				font.setBold(true);
			}
			if (segment.formats.contains(Format.ITALIC)) {
				font.setItalic(true);
			}
			if (segment.formats.contains(Format.UNDERLINE) || segment.link) {
				font.setUnderline(Font.U_SINGLE);
			}
			if (segment.formats.contains(Format.STRIKE)) {
				font.setStrikeout(true);
			}
			if (segment.formats.contains(Format.CODE)) {
				font.setFontName("Consolas");
			}
			if (segment.formats.contains(Format.SUPERSCRIPT)) {
				font.setTypeOffset(Font.SS_SUPER);
			} else if (segment.formats.contains(Format.SUBSCRIPT)) {
				font.setTypeOffset(Font.SS_SUB);
			}
			if (segment.link && !header) {
				font.setColor(IndexedColors.BLUE.getIndex());
			}
			return font;
		});
	}

	private final Map<String, CellStyle> styleVariants = new HashMap<>();

	/** Base cell style variant with text wrapping and / or highlight fill; created once and reused. */
	private CellStyle style(CellStyle base, boolean wrap, boolean highlight) {
		if (!wrap && !highlight) {
			return base;
		}
		String key = base.getIndex() + ":" + wrap + ":" + highlight;
		return styleVariants.computeIfAbsent(key, k -> {
			CellStyle style = sheet.getWorkbook().createCellStyle();
			style.cloneStyleFrom(base);
			if (wrap) {
				style.setWrapText(true);
			}
			if (highlight) {
				style.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
				style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			}
			return style;
		});
	}

	/** Set cell hyperlink; Excel supports a single hyperlink per cell so only the first cell link is used. */
	private void hyperlink(Cell cell, String destination) {
		try {
			Hyperlink hyperlink = sheet.getWorkbook().getCreationHelper().createHyperlink(HyperlinkType.URL);
			hyperlink.setAddress(destination);
			cell.setHyperlink(hyperlink);
		} catch (IllegalArgumentException e) {
			log.warn("Ignore invalid cell hyperlink {}: {}", destination, e.getMessage());
		}
	}
}
