package com.jslib.md.docx;

import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableBody;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableHead;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.CustomBlock;
import org.commonmark.node.CustomNode;

public abstract class CustomVisitor extends AbstractVisitor {
	@Override
	public void visit(CustomBlock customBlock) {
		switch (customBlock.getClass().getSimpleName()) {
		case "TableBlock":
			visit((TableBlock) customBlock);
			break;

		default:
			super.visit(customBlock);
		}
	}

	@Override
	public void visit(CustomNode customNode) {
		switch (customNode.getClass().getSimpleName()) {
		case "TableHead":
			visit((TableHead) customNode);
			break;

		case "TableBody":
			visit((TableBody) customNode);
			break;

		case "TableRow":
			visit((TableRow) customNode);
			break;

		case "TableCell":
			visit((TableCell) customNode);
			break;

		default:
			super.visit(customNode);
		}
	}

	public void visit(TableBlock table) {
        visitChildren(table);
	}

	public void visit(TableHead head) {
        visitChildren(head);
	}

	public void visit(TableBody body) {
        visitChildren(body);
	}

	public void visit(TableRow row) {
        visitChildren(row);
	}

	public void visit(TableCell cell) {
        visitChildren(cell);
	}
}
