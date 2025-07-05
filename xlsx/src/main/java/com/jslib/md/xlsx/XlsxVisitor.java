package com.jslib.md.xlsx;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableBody;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableHead;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jslib.md.CustomVisitor;

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

		String text = null;
		Node node = cell.getFirstChild();
		if (node instanceof Text) {
			text = ((Text)node).getLiteral();
		}
		else if (node instanceof Link) {
			text = ((Link)node).getDestination();
		}
		log.trace("Cell text: {}", text);

		Cell xlsxCell = xlsxRow.createCell(xlsxCellIndex++);
		xlsxCell.setCellValue(text);
		xlsxCell.setCellStyle(xlsxRowIndex == 1 ? xlsxHeaderStyle : xlsxCellStyle);

		super.visit(cell);
	}
}
