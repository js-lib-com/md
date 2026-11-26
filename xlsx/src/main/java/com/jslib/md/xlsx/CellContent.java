package com.jslib.md.xlsx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Code;
import org.commonmark.node.Emphasis;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;

import com.jslib.md.InlineHtml;
import com.jslib.md.InlineHtml.Format;

/**
 * Content of a Markdown table cell as text segments with formatting from Markdown emphasis, inline code, links and
 * inline HTML. Leading and trailing white spaces are removed. Line breaks, both Markdown hard breaks and HTML
 * <code>br</code>, are rendered as new line, while source soft breaks as space.
 */
final class CellContent extends AbstractVisitor {
	static CellContent of(Node cell) {
		CellContent content = new CellContent();
		cell.accept(content);
		content.trim();
		return content;
	}

	static final class Segment {
		final String text;
		final Set<Format> formats;
		final boolean link;

		Segment(String text, Set<Format> formats, boolean link) {
			this.text = text;
			this.formats = Collections.unmodifiableSet(formats);
			this.link = link;
		}

		/** Segment needs no font of its own; highlight is not a font format but is applied on cell. */
		boolean isPlain() {
			return link || !formats.stream().allMatch(format -> format == Format.HIGHLIGHT);
		}

		boolean isHighlighted() {
			return formats.contains(Format.HIGHLIGHT);
		}
	}

	private final List<Segment> segments = new ArrayList<>();
	private final InlineHtml.Formats htmlFormats = new InlineHtml.Formats();

	private int italicDepth;
	private int boldDepth;
	private int linkDepth;
	private String linkDestination;

	private CellContent() {
	}

	List<Segment> segments() {
		return segments;
	}

	String text() {
		StringBuilder text = new StringBuilder();
		segments.forEach(segment -> text.append(segment.text));
		return text.toString();
	}

	/** Destination of the first link from cell, or null if cell has no link. */
	String linkDestination() {
		return linkDestination;
	}

	boolean isFormatted() {
		return segments.stream().anyMatch(Segment::isPlain);
	}

	/** True if any part of cell text is highlighted. */
	boolean isHighlighted() {
		return segments.stream().anyMatch(Segment::isHighlighted);
	}

	@Override
	public void visit(Text text) {
		append(text.getLiteral(), null);
	}

	@Override
	public void visit(Code code) {
		append(code.getLiteral(), Format.CODE);
	}

	@Override
	public void visit(SoftLineBreak softLineBreak) {
		append(" ", null);
	}

	@Override
	public void visit(HardLineBreak hardLineBreak) {
		append("\n", null);
	}

	@Override
	public void visit(Emphasis emphasis) {
		++italicDepth;
		visitChildren(emphasis);
		--italicDepth;
	}

	@Override
	public void visit(StrongEmphasis strongEmphasis) {
		++boldDepth;
		visitChildren(strongEmphasis);
		--boldDepth;
	}

	@Override
	public void visit(Link link) {
		startLink(link.getDestination());
		visitChildren(link);
		--linkDepth;
	}

	@Override
	public void visit(HtmlInline htmlInline) {
		if (InlineHtml.isText(htmlInline.getLiteral())) {
			// not an HTML element, e.g. <special>, so it is cell text
			append(htmlInline.getLiteral(), null);
			return;
		}
		InlineHtml.Tag tag = InlineHtml.parse(htmlInline.getLiteral());
		if (tag == null || htmlFormats.apply(tag)) {
			return;
		}
		if (tag.isLineBreak()) {
			append("\n", null);
		} else if (tag.isAnchor()) {
			if (!tag.closing) {
				startLink(tag.href());
			} else if (linkDepth > 0) {
				--linkDepth;
			}
		}
	}

	private void startLink(String destination) {
		++linkDepth;
		if (linkDestination == null && destination != null && !destination.isBlank()) {
			linkDestination = destination;
		}
	}

	private void append(String text, Format extraFormat) {
		if (text.isEmpty()) {
			return;
		}
		Set<Format> formats = EnumSet.noneOf(Format.class);
		for (Format format : Format.values()) {
			if (htmlFormats.isOpen(format)) {
				formats.add(format);
			}
		}
		if (italicDepth > 0) {
			formats.add(Format.ITALIC);
		}
		if (boldDepth > 0) {
			formats.add(Format.BOLD);
		}
		if (extraFormat != null) {
			formats.add(extraFormat);
		}
		boolean link = linkDepth > 0;

		if (!segments.isEmpty()) {
			Segment last = segments.get(segments.size() - 1);
			if (last.formats.equals(formats) && last.link == link) {
				segments.set(segments.size() - 1, new Segment(last.text + text, formats, link));
				return;
			}
		}
		segments.add(new Segment(text, formats, link));
	}

	private void trim() {
		while (!segments.isEmpty() && segments.get(0).text.isBlank()) {
			segments.remove(0);
		}
		while (!segments.isEmpty() && segments.get(segments.size() - 1).text.isBlank()) {
			segments.remove(segments.size() - 1);
		}
		if (segments.isEmpty()) {
			return;
		}
		Segment first = segments.get(0);
		segments.set(0, new Segment(first.text.stripLeading(), first.formats, first.link));
		int lastIndex = segments.size() - 1;
		Segment last = segments.get(lastIndex);
		segments.set(lastIndex, new Segment(last.text.stripTrailing(), last.formats, last.link));
	}
}
