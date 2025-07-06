package com.jslib.md.docx;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.node.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jslib.md.CustomVisitor;

public class DocxRevisions extends CustomVisitor {
	private static final Logger log = LoggerFactory.getLogger(DocxRevisions.class);
	
	private final XWPFTable table;

	private boolean cellText;
	private int cellIndex;
	private String[] revisionArguments;

	public DocxRevisions(XWPFDocument document) {
		log.trace("DocxRevisions(XWPFDocument document)");
		this.table = document.getTables().get(1);
		this.cellText = false;
		this.revisionArguments = new String[3];
	}

	@Override
	public void visit(TableCell mdCell) {
		log.trace("visit(TableCell mdCell)");
		cellText = !mdCell.isHeader();
		super.visit(mdCell);
	}

	@Override
	public void visit(Text text) {
		log.trace("visit(Text text)");
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
}
