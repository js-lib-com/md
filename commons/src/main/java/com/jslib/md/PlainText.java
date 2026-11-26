package com.jslib.md;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Code;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Node;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.Text;

/**
 * Plain text content of a Markdown node, that is, the concatenation of all descendant text and inline code literals,
 * whatever inline formatting. Line breaks are rendered as spaces and leading and trailing white spaces are removed.
 */
public final class PlainText extends AbstractVisitor {
	public static String of(Node node) {
		PlainText visitor = new PlainText();
		node.accept(visitor);
		return visitor.builder.toString().trim();
	}

	private final StringBuilder builder = new StringBuilder();

	private PlainText() {
	}

	@Override
	public void visit(Text text) {
		builder.append(text.getLiteral());
	}

	@Override
	public void visit(Code code) {
		builder.append(code.getLiteral());
	}

	@Override
	public void visit(SoftLineBreak softLineBreak) {
		builder.append(' ');
	}

	@Override
	public void visit(HardLineBreak hardLineBreak) {
		builder.append(' ');
	}
}
