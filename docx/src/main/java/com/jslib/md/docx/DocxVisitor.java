package com.jslib.md.docx;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.ListItem;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import com.jslib.md.CustomVisitor;
import com.jslib.md.HtmlText;
import com.jslib.md.InlineHtml;
import com.jslib.md.InlineHtml.Format;
import com.jslib.md.ProjectProperties;
import com.jslib.md.docx.template.DocxTemplate;
import com.jslib.md.docx.xml.DocxDocument;
import com.jslib.md.docx.xml.Wml;

/**
 * Render Markdown document into WordprocessingML document body. Block nodes create paragraphs and tables appended to
 * document body; inline nodes create runs on current paragraph, with formatting from enclosing emphasis and inline HTML.
 */
public class DocxVisitor extends CustomVisitor {
	private static final Logger log = LoggerFactory.getLogger(DocxVisitor.class);

	/** Default image caption label, used if document properties have no figure.label. */
	public static final String DEFAULT_FIGURE_LABEL = "Figure";

	/** Code block font size, in half points. */
	private static final int CODE_FONT_SIZE = 16;
	private static final String CODE_FONT = "Consolas";
	private static final String CODE_COLOR = "365FA2";
	private static final String LINK_COLOR = "0000FF";

	private final DocxDocument document;
	private final ProjectProperties projectProperties;
	private final DocxNumbering numbering;
	private final DocxImages images;
	/** Width of the page used for content, i.e. page width minus left and right margins, in twips. */
	private final int contentPageWidth;

	// --------------------------------------------------------------------------------------------
	// rendering state

	/** Paragraph receiving inline content. */
	private Element currentParagraph;
	/**
	 * Run created ahead for next text node, e.g. hyperlink run; null if next text creates its own run. Reset on every
	 * block with inline content.
	 */
	private Element currentRun;
	/**
	 * Set by list item, which creates the numbered paragraph itself, so that its first Markdown paragraph does not
	 * create another one. Cleared by any block that creates a paragraph.
	 */
	private boolean ignoreParagraph;

	private int topHeadingsCount;
	/** Block quote nesting depth; all paragraphs inside a block quote use quote style. */
	private int blockQuoteDepth;
	/** Emphasis nesting depth; every inline run created while inside emphasis inherits its formatting. */
	private int italicDepth;
	private int boldDepth;
	/** Open inline HTML formats, reset on every block so that unbalanced tags do not leak. */
	private final InlineHtml.Formats htmlFormats = new InlineHtml.Formats();

	/** Numbering instance IDs of currently open lists, innermost on top. */
	private final Deque<Integer> lists = new ArrayDeque<>();

	private Element currentTable;
	private Element currentTableRow;

	private String figureLabel = DEFAULT_FIGURE_LABEL;
	private int figureIndex;
	/** Set by image so that next text node, that is, image description, is rendered as figure caption. */
	private boolean figureCaption;

	public DocxVisitor(DocxTemplate template, ProjectProperties projectProperties, File imagesDir) throws IOException {
		this(new DocxDocument(template.getInputStream()), projectProperties, imagesDir);
	}

	/** Images directory is optional; current directory by default. */
	DocxVisitor(DocxDocument document, ProjectProperties projectProperties, File... imagesDir) throws IOException {
		this.document = document;
		this.projectProperties = projectProperties;
		DocxStyles.install(document);
		this.numbering = new DocxNumbering(document);
		this.images = new DocxImages(document, imagesDir.length == 1 ? imagesDir[0] : new File("."), projectProperties.imageShadow);
		this.contentPageWidth = contentPageWidth(document);
	}

	private static int contentPageWidth(DocxDocument document) throws IOException {
		Element sectPr = Wml.child(document.body(), "sectPr");
		if (sectPr == null) {
			throw new IOException("Invalid DOCX template. Missing body section properties.");
		}
		Element pageSize = Wml.child(sectPr, "pgSz");
		if (pageSize == null) {
			throw new IOException("Invalid DOCX template. Missing page size section properties.");
		}
		Element pageMargins = Wml.child(sectPr, "pgMar");
		int margins = 0;
		if (pageMargins != null) {
			margins = Integer.parseInt(Wml.attr(pageMargins, "left")) + Integer.parseInt(Wml.attr(pageMargins, "right"));
		}
		return Integer.parseInt(Wml.attr(pageSize, "w")) - margins;
	}

	public DocxDocument getDocument() {
		return document;
	}

	/** Set image caption label, e.g. Figura for Romanian documents; caption is label, image index and image text. */
	public void setFigureLabel(String figureLabel) {
		this.figureLabel = figureLabel;
	}

	// --------------------------------------------------------------------------------------------
	// blocks

	/** Headings are numbered; every top level heading, except the first one, starts on a new page. */
	@Override
	public void visit(Heading heading) {
		startInlineContent();
		if (heading.getLevel() == 1 && topHeadingsCount++ > 0) {
			Wml.addBreak(Wml.add(createParagraph(), "r"), "page");
		}
		currentParagraph = createParagraph();
		Wml.setParagraphStyle(currentParagraph, DocxStyles.heading(heading.getLevel()));
		Wml.setNumbering(currentParagraph, heading.getLevel() - 1, numbering.getHeadingsNumId());
		visitChildren(heading);
	}

	@Override
	public void visit(Paragraph paragraph) {
		startInlineContent();
		if (!ignoreParagraph) {
			currentParagraph = createParagraph();
			if (blockQuoteDepth > 0) {
				Wml.setParagraphStyle(currentParagraph, DocxStyles.INTENSE_QUOTE);
			}
			// continuation paragraph of a loose list item is aligned with list item text
			indentToList(currentParagraph);
		}
		ignoreParagraph = false;
		visitChildren(paragraph);
	}

	@Override
	public void visit(BlockQuote blockQuote) {
		++blockQuoteDepth;
		visitChildren(blockQuote);
		--blockQuoteDepth;
	}

	@Override
	public void visit(FencedCodeBlock fencedCodeBlock) {
		codeBlock(fencedCodeBlock.getLiteral());
	}

	@Override
	public void visit(IndentedCodeBlock indentedCodeBlock) {
		codeBlock(indentedCodeBlock.getLiteral());
	}

	/**
	 * Render code literal, one paragraph per line, framed by a left border. Code block is rendered even if is the first
	 * block of a list item, case in which list item paragraph stays empty.
	 */
	private void codeBlock(String literal) {
		for (String line : literal.replace("\r", "").split("\n", -1)) {
			currentParagraph = createParagraph();
			Wml.setParagraphStyle(currentParagraph, DocxStyles.NO_SPACING);
			Wml.setParagraphBorder(currentParagraph, "left");
			indentToList(currentParagraph);

			Element run = Wml.add(currentParagraph, "r");
			Element rFonts = Wml.property(Wml.rPr(run), "rFonts");
			for (String attribute : new String[] { "ascii", "hAnsi", "cs", "eastAsia" }) {
				Wml.attr(rFonts, attribute, CODE_FONT);
			}
			Wml.property(Wml.rPr(run), "sz", CODE_FONT_SIZE);
			Wml.property(Wml.rPr(run), "color", CODE_COLOR);
			if (!line.isEmpty()) {
				Wml.setText(run, line);
			}
		}
		ignoreParagraph = false;
		// code literal ends with new line so last paragraph is empty; remove its border to not extend code block frame
		Wml.remove(Wml.pPr(currentParagraph), "pBdr");
	}

	@Override
	public void visit(ThematicBreak thematicBreak) {
		currentParagraph = createParagraph();
		Wml.setParagraphBorder(currentParagraph, "bottom");
		indentToList(currentParagraph);
		ignoreParagraph = false;
	}

	/**
	 * HTML is not interpreted, except page break style. Comments are ignored and other HTML blocks are rendered as plain
	 * text, one paragraph per HTML block element.
	 */
	@Override
	public void visit(HtmlBlock htmlBlock) {
		String html = htmlBlock.getLiteral();
		if (HtmlText.isPageBreak(html)) {
			currentParagraph = createParagraph();
			Wml.addBreak(Wml.add(currentParagraph, "r"), "page");
			ignoreParagraph = false;
			return;
		}

		List<String> lines = HtmlText.lines(html);
		if (lines.isEmpty()) {
			log.debug("Ignore HTML block without text: {}", html);
			return;
		}
		log.warn("HTML formatting is not supported; render HTML block as plain text: {}", html.strip());
		for (String line : lines) {
			currentParagraph = createParagraph();
			indentToList(currentParagraph);
			Wml.setText(createInlineRun(), line);
		}
		ignoreParagraph = false;
	}

	// --------------------------------------------------------------------------------------------
	// lists

	@Override
	public void visit(BulletList bulletList) {
		lists.push(numbering.getBulletsNumId());
		visitChildren(bulletList);
		lists.pop();
	}

	@Override
	public void visit(OrderedList orderedList) {
		// every ordered list has its own numbering instance so that counting restarts from list start number
		lists.push(numbering.addOrderedNum(listLevel(lists.size()), orderedList.getStartNumber()));
		visitChildren(orderedList);
		lists.pop();
	}

	/** List item creates its numbered paragraph, used by its first Markdown paragraph. */
	@Override
	public void visit(ListItem listItem) {
		currentParagraph = createParagraph();
		Wml.setParagraphStyle(currentParagraph, DocxStyles.LIST_PARAGRAPH);
		Integer numId = lists.peek();
		if (numId != null) {
			Wml.setNumbering(currentParagraph, listLevel(lists.size() - 1), numId);
		}
		ignoreParagraph = true;
		visitChildren(listItem);
		// empty list item does not consume pending list paragraph
		ignoreParagraph = false;
	}

	private static int listLevel(int depth) {
		return Math.min(depth, DocxNumbering.maxLevel());
	}

	/** Align a block paragraph nested in a list with the text of the enclosing list item. */
	private void indentToList(Element paragraph) {
		if (!lists.isEmpty()) {
			Wml.setIndent(paragraph, DocxNumbering.textIndent(listLevel(lists.size() - 1)));
		}
	}

	// --------------------------------------------------------------------------------------------
	// tables

	@Override
	public void visit(TableBlock table) {
		currentTable = document.appendBlock(DocxTables.create(document.body(), contentPageWidth));
		visitChildren(table);
		DocxTables.setColumnsWidth(currentTable, contentPageWidth, projectProperties);

		// empty paragraph without spacing after table
		Element space = createParagraph();
		Element spacing = Wml.property(Wml.pPr(space), "spacing");
		Wml.attr(spacing, "before", 0);
		Wml.attr(spacing, "after", 0);
		Wml.property(Wml.rPr(Wml.add(space, "r")), "sz", 0);
	}

	@Override
	public void visit(TableRow row) {
		currentTableRow = Wml.add(currentTable, "tr");
		visitChildren(row);
	}

	@Override
	public void visit(TableCell cell) {
		startInlineContent();
		currentParagraph = Wml.add(Wml.add(currentTableRow, "tc"), "p");
		if (cell.getAlignment() != null) {
			Wml.property(Wml.pPr(currentParagraph), "jc", cell.getAlignment().name().toLowerCase(Locale.ROOT));
		}
		visitChildren(cell);
	}

	// --------------------------------------------------------------------------------------------
	// inline content

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
	public void visit(Code code) {
		Element run = createInlineRun();
		Wml.setText(run, code.getLiteral());
		Wml.property(Wml.rPr(run), "rStyle", DocxStyles.QUOTE_CHAR);
	}

	/**
	 * Inline HTML tags are mapped to run formatting: bold, italic, underline, strike through, code, superscript,
	 * subscript and highlight. Also supported are line break and anchor with href, rendered as hyperlink. Comments and
	 * other tags are ignored, while text between tags is rendered as usual.
	 */
	@Override
	public void visit(HtmlInline htmlInline) {
		String literal = htmlInline.getLiteral();
		if (InlineHtml.isText(literal)) {
			// not an HTML element, e.g. <special>, so it is rendered as text
			visit(new Text(literal));
			return;
		}
		InlineHtml.Tag tag = InlineHtml.parse(literal);
		if (tag == null || htmlFormats.apply(tag)) {
			return;
		}
		if (tag.isLineBreak()) {
			Wml.addBreak(createInlineRun(), null);
		} else if (tag.isAnchor()) {
			if (tag.closing) {
				// anchor without text does not leave pending hyperlink run for following text
				currentRun = null;
			} else if (tag.href() != null) {
				startHyperlink(tag.href());
			}
		} else {
			log.debug("Ignore unsupported inline HTML tag: {}", literal);
		}
	}

	/** Markdown source line break inside a paragraph is rendered as a space. */
	@Override
	public void visit(SoftLineBreak softLineBreak) {
		Wml.setText(createInlineRun(), " ");
	}

	@Override
	public void visit(HardLineBreak hardLineBreak) {
		Wml.addBreak(createInlineRun(), null);
	}

	@Override
	public void visit(Text text) {
		if (currentRun == null) {
			currentRun = createInlineRun();
		} else {
			applyFormats(currentRun);
		}
		String literal = text.getLiteral();
		if (figureCaption) {
			figureCaption = false;
			literal = figureLabel + " " + ++figureIndex + " - " + literal;
		}
		Wml.setText(currentRun, literal);
		currentRun = null;
	}

	@Override
	public void visit(Link link) {
		startHyperlink(link.getDestination());
		visitChildren(link);
	}

	/**
	 * Embed image on its own run and create a separated paragraph for image caption, from image description. Image
	 * failure does not abort document generation; image is skipped but its caption is kept.
	 */
	@Override
	public void visit(Image image) {
		try {
			Element drawing = images.drawing(image.getDestination());
			Wml.add(currentParagraph, "r").appendChild(drawing);
		} catch (IOException e) {
			log.error("Skip image {}: {}", image.getDestination(), e.getMessage());
		}
		currentParagraph = createParagraph();
		figureCaption = true;
		visitChildren(image);
	}

	/** Reset inline state at the start of a block with inline content so that unbalanced inline HTML does not leak. */
	private void startInlineContent() {
		htmlFormats.reset();
		currentRun = null;
	}

	/** Create a run on current paragraph with formatting from enclosing emphasis and inline HTML. */
	private Element createInlineRun() {
		Element run = Wml.add(currentParagraph, "r");
		applyFormats(run);
		return run;
	}

	private void applyFormats(Element run) {
		if (italicDepth > 0 || htmlFormats.isOpen(Format.ITALIC)) {
			Wml.property(Wml.rPr(run), "i");
		}
		if (boldDepth > 0 || htmlFormats.isOpen(Format.BOLD)) {
			Wml.property(Wml.rPr(run), "b");
		}
		if (htmlFormats.isOpen(Format.UNDERLINE)) {
			Wml.property(Wml.rPr(run), "u", "single");
		}
		if (htmlFormats.isOpen(Format.STRIKE)) {
			Wml.property(Wml.rPr(run), "strike");
		}
		if (htmlFormats.isOpen(Format.CODE)) {
			Wml.property(Wml.rPr(run), "rStyle", DocxStyles.QUOTE_CHAR);
		}
		if (htmlFormats.isOpen(Format.SUPERSCRIPT)) {
			Wml.property(Wml.rPr(run), "vertAlign", "superscript");
		} else if (htmlFormats.isOpen(Format.SUBSCRIPT)) {
			Wml.property(Wml.rPr(run), "vertAlign", "subscript");
		}
		if (htmlFormats.isOpen(Format.HIGHLIGHT)) {
			Wml.property(Wml.rPr(run), "highlight", "yellow");
		}
	}

	/**
	 * Create hyperlink run, used by next text node, styled as hyperlink if links are enabled by project properties.
	 *
	 * <pre>
	 * &lt;w:hyperlink r:id="rId7"&gt;
	 * ..&lt;w:r&gt;
	 * ....&lt;w:rPr&gt;
	 * ......&lt;w:rStyle w:val="Hyperlink" /&gt;
	 * ......&lt;w:color w:val="0000FF" /&gt;
	 * ....&lt;/w:rPr&gt;
	 * ....&lt;w:t&gt;see visit(Text)&lt;/w:t&gt;
	 * ..&lt;/w:r&gt;
	 * &lt;/w:hyperlink&gt;
	 * </pre>
	 */
	private void startHyperlink(String destination) {
		Element hyperlink = Wml.add(currentParagraph, "hyperlink");
		hyperlink.setAttributeNS(Wml.R_NS, "r:id", document.addHyperlink(destination));
		currentRun = Wml.add(hyperlink, "r");
		if (projectProperties.linkEnabled) {
			Wml.property(Wml.rPr(currentRun), "rStyle", DocxStyles.HYPERLINK);
			Wml.property(Wml.rPr(currentRun), "color", LINK_COLOR);
		}
	}

	/** Create paragraph and append it to document body. */
	private Element createParagraph() {
		return document.appendBlock(Wml.create(document.body(), "p"));
	}
}
