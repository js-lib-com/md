package com.jslib.md.docx;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.node.Text;

public class DocxRevisions extends CustomVisitor {
	private final XWPFTable table;

	private boolean cellText;
	private int cellIndex;
	private String[] revisionArguments;

	public DocxRevisions(XWPFDocument document) {
		this.table = document.getTables().get(1);
		this.cellText = false;
		this.revisionArguments = new String[3];
	}

	@Override
	public void visit(TableCell mdCell) {
		log("visit table cell");
		cellText = !mdCell.isHeader();
		super.visit(mdCell);
	}

	@Override
	public void visit(Text text) {
		log("visit text");
		if (!cellText) {
			super.visit(text);
			return;
		}
		revisionArguments[cellIndex] = text.getLiteral();
		cellIndex++;
		if (cellIndex == 3) {
			cellIndex = 0;
			XWPFTableRow row = table.createRow();
			row.getCell(0).setText(revisionArguments[0]);
			row.getCell(1).setText(revisionArguments[1]);
			row.getCell(2).setText(revisionArguments[2]);
		}
	}

	// ------------------------------------------------------------------------

	private static final void log(Object... objects) {
		for (Object object : objects) {
			System.out.print(object instanceof String ? (String) object : object.toString());
			System.out.print(' ');
		}
		System.out.println();
	}
}
