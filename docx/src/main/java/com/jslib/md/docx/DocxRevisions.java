package com.jslib.md.docx;

import java.util.ArrayList;
import java.util.List;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableHead;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.node.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jslib.md.CustomVisitor;
import com.jslib.md.PlainText;

/**
 * Copy revisions markdown table body rows into document template revisions table, that is the second table from
 * template. Cell content is copied as plain text, whatever inline formatting it has.
 */
public class DocxRevisions extends CustomVisitor {
	private static final Logger log = LoggerFactory.getLogger(DocxRevisions.class);

	private final XWPFTable table;

	public DocxRevisions(XWPFDocument document) {
		log.trace("DocxRevisions(XWPFDocument document)");
		this.table = document.getTables().get(1);
	}

	@Override
	public void visit(TableHead head) {
		log.trace("visit(TableHead head)");
		// header row is already present in template revisions table
	}

	@Override
	public void visit(TableRow mdRow) {
		log.trace("visit(TableRow mdRow)");

		List<String> cellTexts = new ArrayList<>();
		for (Node cell = mdRow.getFirstChild(); cell != null; cell = cell.getNext()) {
			if (cell instanceof TableCell) {
				cellTexts.add(PlainText.of(cell));
			}
		}

		XWPFTableRow row = table.createRow();
		int columnsCount = row.getTableCells().size();
		if (cellTexts.size() != columnsCount) {
			log.warn("Revisions row has {} cells but revisions table has {} columns: {}", cellTexts.size(), columnsCount, cellTexts);
		}
		for (int i = 0; i < Math.min(columnsCount, cellTexts.size()); ++i) {
			row.getCell(i).setText(cellTexts.get(i));
		}
	}
}
