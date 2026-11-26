package com.jslib.md.docx;

import java.util.ArrayList;
import java.util.List;

import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableHead;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.node.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import com.jslib.md.CustomVisitor;
import com.jslib.md.PlainText;
import com.jslib.md.docx.xml.DocxDocument;
import com.jslib.md.docx.xml.Wml;

/**
 * Copy revisions Markdown table body rows into document template revisions table, that is the second table from
 * template. Cell content is copied as plain text, whatever inline formatting it has.
 */
public class DocxRevisions extends CustomVisitor {
	private static final Logger log = LoggerFactory.getLogger(DocxRevisions.class);

	private final Element table;
	private final int columnsCount;

	public DocxRevisions(DocxDocument document) {
		log.trace("DocxRevisions(DocxDocument document)");
		List<Element> tables = Wml.children(document.body(), "tbl");
		if (tables.size() < 2) {
			throw new IllegalStateException("Invalid DOCX template. Missing revisions table.");
		}
		this.table = tables.get(1);
		// new rows have the same cells count as the first table row
		List<Element> rows = Wml.children(table, "tr");
		this.columnsCount = rows.isEmpty() ? 0 : Wml.children(rows.get(0), "tc").size();
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

		if (cellTexts.size() != columnsCount) {
			log.warn("Revisions row has {} cells but revisions table has {} columns: {}", cellTexts.size(), columnsCount, cellTexts);
		}
		Element row = Wml.add(table, "tr");
		for (int i = 0; i < columnsCount; ++i) {
			Element paragraph = Wml.add(Wml.add(row, "tc"), "p");
			if (i < cellTexts.size() && !cellTexts.get(i).isEmpty()) {
				Wml.setText(Wml.add(paragraph, "r"), cellTexts.get(i));
			}
		}
	}
}
