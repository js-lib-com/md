package com.jslib.md.docx;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.Borders;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.impl.xb.xmlschema.SpaceAttribute;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableBody;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableHead;
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
import org.openxmlformats.schemas.drawingml.x2006.main.CTEffectList;
import org.openxmlformats.schemas.drawingml.x2006.main.CTOuterShadowEffect;
import org.openxmlformats.schemas.drawingml.x2006.main.CTPositiveFixedPercentage;
import org.openxmlformats.schemas.drawingml.x2006.main.CTPresetColor;
import org.openxmlformats.schemas.drawingml.x2006.main.STPresetColorVal;
import org.openxmlformats.schemas.drawingml.x2006.main.STRectAlignment;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTDecimalNumber;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTHyperlink;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTNumPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTOnOff;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTString;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblLook;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTText;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jslib.md.CustomVisitor;
import com.jslib.md.InlineHtml;
import com.jslib.md.InlineHtml.Format;
import com.jslib.md.docx.template.DocxTemplate;
import com.jslib.md.docx.util.Color;
import com.jslib.md.docx.util.Dimensions;
import com.jslib.md.docx.util.Images;
import com.jslib.md.docx.util.Strings;

public class DocxVisitor extends CustomVisitor {
	private static final Logger log = LoggerFactory.getLogger(DocxVisitor.class);

	private final Images images;
	private final File imagesDir;
	private final XWPFDocument document;
	// the width of the page used for content display, i.e. page size minus left and right margins
	private final BigInteger contentPageWidth;

	private final ProjectProperties projectProperties;
	@SuppressWarnings("unused")
	private final DocxStyles styles;
	private final DocxNumbering numbering;

	private int topHeadingsCount;
	private XWPFParagraph currentParagraph;
	private XWPFRun currentRun;
	private boolean ignoreParagraph;
	private boolean blockQuoteDetected;

	public DocxVisitor(DocxTemplate template) throws IOException, XmlException {
		this(new XWPFDocument(template.getInputStream()), ProjectProperties.empty());
		log.trace("DocxVisitor(DocxTemplate template)");
	}

	public DocxVisitor(DocxTemplate template, File imagesDir) throws IOException, XmlException {
		this(new XWPFDocument(template.getInputStream()), ProjectProperties.empty(), imagesDir);
		log.trace("DocxVisitor(DocxTemplate template, File imagesDir)");
	}

	public DocxVisitor(DocxTemplate template, ProjectProperties projectProperties, File imagesDir) throws IOException, XmlException {
		this(new XWPFDocument(template.getInputStream()), projectProperties, imagesDir);
		log.trace("DocxVisitor(DocxTemplate template, ProjectProperties projectProperties, File imagesDir)");
	}

	DocxVisitor(XWPFDocument document, ProjectProperties projectProperties, File... imagesDir) throws IOException, XmlException {
		super();
		log.trace("DocxVisitor(XWPFDocument document, ProjectProperties projectProperties, File... imagesDir)");

		this.images = new Images();
		this.imagesDir = imagesDir.length == 1 ? imagesDir[0] : new File(".");
		this.document = document;

		this.projectProperties = projectProperties;

		log.debug("Create document styles");
		this.styles = new DocxStyles(document);

		log.debug("Create document numbering");
		this.numbering = new DocxNumbering(document);

		log.debug("Compute available page width");
		CTSectPr sectPr = document.getDocument().getBody().getSectPr();
		CTPageSz pageSize = sectPr.getPgSz();

		CTPageMar pageMargins = sectPr.getPgMar();
		BigInteger leftMargin = (BigInteger) pageMargins.getLeft();
		BigInteger rightMargin = (BigInteger) pageMargins.getRight();

		this.contentPageWidth = ((BigInteger) pageSize.getW()).subtract(leftMargin).subtract(rightMargin);

		this.topHeadingsCount = 0;
	}

	public XWPFDocument getDocument() {
		return document;
	}

	@Override
	public void visit(Heading heading) {
		log.trace("visit(Heading heading)");
		startInlineContent();

		if (heading.getLevel() == 1) {
			if (topHeadingsCount++ > 0) {
				log.debug("Add page break before heading 1");
				XWPFParagraph paragraph = document.createParagraph();
				XWPFRun run = paragraph.createRun();
				run.addBreak(org.apache.poi.xwpf.usermodel.BreakType.PAGE);
			}
		}

		log.debug("Create paragraph");
		currentParagraph = document.createParagraph();

		int headingLevel = heading.getLevel();
		log.debug("Set style: Heading: {}", headingLevel);
		currentParagraph.setStyle(DocxStyles.getHeadingStyleId(headingLevel));

		log.debug("Set heading numbering");
		// <w:pPr>
		// ..<w:numPr>
		// ....<w:ilvl w:val="3" />
		// ....<w:numId w:val="1" />
		// ..</w:numPr>
		// </w:pPr>

		CTPPr pPr = currentParagraph.getCTP().getPPr();
		if (pPr == null) {
			pPr = currentParagraph.getCTP().addNewPPr();
		}

		CTNumPr numPr = pPr.getNumPr();
		if (numPr == null) {
			numPr = pPr.addNewNumPr();
		}

		CTDecimalNumber ilvl = numPr.getIlvl();
		if (ilvl == null) {
			ilvl = numPr.addNewIlvl();
		}
		ilvl.setVal(BigInteger.valueOf(headingLevel - 1));

		CTDecimalNumber numId = numPr.getNumId();
		if (numId == null) {
			numId = numPr.addNewNumId();
		}
		numId.setVal(numbering.getHeadingsNumId());

		super.visit(heading);
	}

	@Override
	public void visit(Paragraph paragraph) {
		log.trace("visit(Paragraph paragraph)");
		startInlineContent();
		if (!ignoreParagraph) {
			log.debug("Create paragraph");
			currentParagraph = document.createParagraph();
			if (blockQuoteDetected) {
				blockQuoteDetected = false;
				log.debug("Set style: Quote");
				currentParagraph.setStyle(DocxStyles.getIntenseQuoteStyleId());
			}
			// continuation paragraph of a loose list item is aligned with list item text
			indentToList(currentParagraph);
		}
		ignoreParagraph = false;
		super.visit(paragraph);
	}

	@Override
	public void visit(BlockQuote blockQuote) {
		log.trace("visit(BlockQuote blockQuote)");
		blockQuoteDetected = true;
		super.visit(blockQuote);
	}

	@Override
	public void visit(Code code) {
		log.trace("visit(Code code)");
		log.debug("Code literal: {}", code.getLiteral());
		XWPFRun run = createInlineRun();
		run.setText(code.getLiteral());
		run.setStyle(DocxStyles.getQuoteCharStyleId());
		super.visit(code);
	}

	@Override
	public void visit(FencedCodeBlock fencedCodeBlock) {
		log.trace("visit(FencedCodeBlock fencedCodeBlock)");
		log.debug("fencedCodeBlock.getInfo(): {}", fencedCodeBlock.getInfo());
		codeBlock(fencedCodeBlock.getLiteral());
		super.visit(fencedCodeBlock);
	}

	@Override
	public void visit(IndentedCodeBlock indentedCodeBlock) {
		log.trace("visit(IndentedCodeBlock indentedCodeBlock)");
		codeBlock(indentedCodeBlock.getLiteral());
		super.visit(indentedCodeBlock);
	}

	/**
	 * Render code literal, one paragraph per line. Code block is rendered even if is the first block of a list item, case
	 * in which list item paragraph stays empty.
	 */
	private void codeBlock(String literal) {
		Strings.lines(literal).forEach(line -> {
			log.debug("Create paragraph");
			currentParagraph = document.createParagraph();
			currentParagraph.setStyle(DocxStyles.getNoSpacing());
			currentParagraph.setBorderLeft(Borders.SINGLE);
			indentToList(currentParagraph);

			log.debug("Create run");
			XWPFRun run = currentParagraph.createRun();
			run.setFontFamily("Consolas");
			run.setFontSize(8);
			run.setColor(Color.LIGHT_BLUE.name);

			CTText ctText = run.getCTR().addNewT();
			ctText.setSpace(SpaceAttribute.Space.PRESERVE);
			run.setText(line);
		});
		ignoreParagraph = false;
		// code literal ends with new line so last paragraph is empty; remove its border to not extend code block frame
		currentParagraph.setBorderLeft(Borders.NONE);
	}

	@Override
	public void visit(ThematicBreak thematicBreak) {
		log.trace("visit(ThematicBreak thematicBreak)");
		log.debug("Create horizontal rule paragraph");
		currentParagraph = document.createParagraph();
		currentParagraph.setBorderBottom(Borders.SINGLE);
		indentToList(currentParagraph);
		ignoreParagraph = false;
	}

	// ------------------------------------------------------------------------

	private static final Pattern HTML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
	private static final Pattern HTML_PAGE_BREAK = Pattern.compile("(page-)?break-(before|after)\\s*:\\s*(always|page)", Pattern.CASE_INSENSITIVE);
	private static final Pattern HTML_BLOCK_TAG = Pattern.compile("</?(p|div|br|li|ul|ol|h[1-6]|tr|table|blockquote|pre|hr)\\b[^>]*>", Pattern.CASE_INSENSITIVE);
	private static final Pattern HTML_TAG = Pattern.compile("<[^>]*>");

	/**
	 * HTML is not interpreted, except page break style. Comments are ignored and other HTML blocks are rendered as plain
	 * text, one paragraph per HTML block element.
	 */
	@Override
	public void visit(HtmlBlock htmlBlock) {
		log.trace("visit(HtmlBlock htmlBlock)");
		String html = HTML_COMMENT.matcher(htmlBlock.getLiteral()).replaceAll("");

		if (HTML_PAGE_BREAK.matcher(html).find()) {
			log.debug("Add page break for HTML page break style");
			currentParagraph = document.createParagraph();
			currentParagraph.createRun().addBreak(org.apache.poi.xwpf.usermodel.BreakType.PAGE);
			ignoreParagraph = false;
			return;
		}

		List<String> lines = htmlText(html);
		if (lines.isEmpty()) {
			log.debug("Ignore HTML block without text: {}", htmlBlock.getLiteral());
			return;
		}
		log.warn("HTML formatting is not supported; render HTML block as plain text: {}", htmlBlock.getLiteral().strip());
		for (String line : lines) {
			currentParagraph = document.createParagraph();
			indentToList(currentParagraph);
			createInlineRun().setText(line);
		}
		ignoreParagraph = false;
	}

	static List<String> htmlText(String html) {
		String text = html.replaceAll("\\s+", " ");
		text = HTML_BLOCK_TAG.matcher(text).replaceAll("\n");
		text = HTML_TAG.matcher(text).replaceAll("");
		text = text.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'").replace("&amp;", "&");

		List<String> lines = new ArrayList<>();
		for (String line : text.split("\n")) {
			if (!line.isBlank()) {
				lines.add(line.strip());
			}
		}
		return lines;
	}

	// emphasis nesting depth; every inline run created while inside emphasis inherits its formatting
	private int italicDepth;
	private int boldDepth;

	@Override
	public void visit(Emphasis emphasis) {
		log.trace("visit(Emphasis emphasis)");
		++italicDepth;
		super.visit(emphasis);
		--italicDepth;
	}

	@Override
	public void visit(StrongEmphasis strongEmphasis) {
		log.trace("visit(StrongEmphasis strongEmphasis)");
		++boldDepth;
		super.visit(strongEmphasis);
		--boldDepth;
	}

	/** Create a run on current paragraph with formatting from enclosing emphasis. */
	private XWPFRun createInlineRun() {
		assert currentParagraph != null;
		log.debug("Create run");
		XWPFRun run = currentParagraph.createRun();
		applyEmphasis(run);
		return run;
	}

	private void applyEmphasis(XWPFRun run) {
		if (italicDepth > 0 || htmlFormats.isOpen(Format.ITALIC)) {
			run.setItalic(true);
		}
		if (boldDepth > 0 || htmlFormats.isOpen(Format.BOLD)) {
			run.setBold(true);
		}
		if (htmlFormats.isOpen(Format.UNDERLINE)) {
			run.setUnderline(UnderlinePatterns.SINGLE);
		}
		if (htmlFormats.isOpen(Format.STRIKE)) {
			run.setStrikeThrough(true);
		}
		if (htmlFormats.isOpen(Format.CODE)) {
			run.setStyle(DocxStyles.getQuoteCharStyleId());
		}
		if (htmlFormats.isOpen(Format.SUPERSCRIPT)) {
			run.setVerticalAlignment("superscript");
		} else if (htmlFormats.isOpen(Format.SUBSCRIPT)) {
			run.setVerticalAlignment("subscript");
		}
		if (htmlFormats.isOpen(Format.HIGHLIGHT)) {
			run.setTextHighlightColor("yellow");
		}
	}

	// ------------------------------------------------------------------------
	// inline HTML

	/** Open inline HTML formats, reset on every block so that unbalanced tags do not leak. */
	private final InlineHtml.Formats htmlFormats = new InlineHtml.Formats();

	/** Reset inline state at the start of a block with inline content so that unbalanced inline HTML does not leak. */
	private void startInlineContent() {
		htmlFormats.reset();
		currentRun = null;
	}

	/**
	 * Inline HTML tags are mapped to run formatting: bold, italic, underline, strike through, code, superscript,
	 * subscript and highlight. Also supported are line break and anchor with href, rendered as hyperlink. Comments and
	 * other tags are ignored, while text between tags is rendered as usual.
	 */
	@Override
	public void visit(HtmlInline htmlInline) {
		log.trace("visit(HtmlInline htmlInline)");
		InlineHtml.Tag tag = InlineHtml.parse(htmlInline.getLiteral());
		if (tag == null) {
			log.debug("Ignore inline HTML: {}", htmlInline.getLiteral());
			return;
		}
		if (htmlFormats.apply(tag)) {
			return;
		}

		if (tag.isLineBreak()) {
			createInlineRun().addBreak();
		} else if (tag.isAnchor()) {
			if (tag.closing) {
				// anchor without text does not leave pending hyperlink run for following text
				currentRun = null;
			} else if (tag.href() != null) {
				startHyperlink(tag.href());
			}
		} else {
			log.debug("Ignore unsupported inline HTML tag: {}", htmlInline.getLiteral());
		}
	}

	@Override
	public void visit(SoftLineBreak softLineBreak) {
		log.trace("visit(SoftLineBreak softLineBreak)");
		// markdown source line break inside a paragraph is rendered as a space
		createInlineRun().setText(" ");
	}

	@Override
	public void visit(HardLineBreak hardLineBreak) {
		log.trace("visit(HardLineBreak hardLineBreak)");
		createInlineRun().addBreak();
	}

	@Override
	public void visit(Text text) {
		log.trace("visit(Text text)");
		if (currentRun == null) {
			currentRun = createInlineRun();
		} else {
			applyEmphasis(currentRun);
		}
		String textLiteral = text.getLiteral();
		if (pictureDescription) {
			pictureDescription = false;
			StringBuilder textBuilder = new StringBuilder();
			textBuilder.append("Figure ");
			textBuilder.append(++pictureIndex);
			textBuilder.append(" - ");
			textBuilder.append(textLiteral);
			textLiteral = textBuilder.toString();
		}
		log.debug("Set run text: |{}|", textLiteral);
		currentRun.setText(textLiteral);
		currentRun = null;
	}

	// ------------------------------------------------------------------------

	@Override
	public void visit(Link link) {
		log.trace("visit(Link link)");
		startHyperlink(link.getDestination());
		// w:t
		super.visit(link);
	}

	/** Create hyperlink run, used by next text node, styled as hyperlink if links are enabled by project properties. */
	private void startHyperlink(String destination) {
		log.debug("Create hyperlink run to: {}", destination);
		assert currentParagraph != null;
		currentRun = currentParagraph.createHyperlinkRun(destination);

		if (!projectProperties.linkEnabled) {
			return;
		}

		// <w:hyperlink r:id="rId7">
		// ..<w:r>
		// ....<w:rPr>
		// ......<w:color w:val="0000FF" />
		// ......<w:rStyle w:val="Hyperlink" />
		// ....</w:rPr>
		// ....<w:t>see visit(Text)</w:t>
		// ..</w:r>
		// </w:hyperlink>

		log.debug("Set hyperlink style: {}", DocxStyles.getLinkStyleId());
		// w:hyperlink
		CTHyperlink ctHyperlink = ((XWPFHyperlinkRun) currentRun).getCTHyperlink();
		assert ctHyperlink.getRArray().length == 1;
		// w:r
		CTR ctr = ctHyperlink.getRArray(0);
		// w:rPr
		CTRPr ctrPr = ctr.getRPr();
		if (ctrPr == null) {
			ctrPr = ctr.addNewRPr();
		}

		// w:rStyle
		CTString rStyle = CTString.Factory.newInstance();
		rStyle.setVal(DocxStyles.getLinkStyleId());
		ctrPr.setRStyleArray(new CTString[] { rStyle });

		// crazy: without next statement above w:rStyle is not written in document xml w:color
		ctrPr.addNewColor().setVal("0000FF");
	}

	// ------------------------------------------------------------------------

	private int pictureIndex;
	private boolean pictureDescription;

	@Override
	public void visit(Image image) {
		log.trace("visit(Image image)");

		log.debug("Create run");
		XWPFRun run = currentParagraph.createRun();

		File imageFile = new File(imagesDir, image.getDestination());
		Dimensions dimensions = images.getDimension(imageFile);
		log.debug("Image dimensions: {}", dimensions);
		if (dimensions.getWidth() > 600) {
			dimensions.scaleWidth(600);
			log.debug("Resize image to {}", dimensions);
			imageFile = images.resize(imageFile, dimensions);
		}

		try (InputStream inputStream = new FileInputStream(imageFile)) {
			int width = Units.pixelToEMU(dimensions.getWidth());
			int height = Units.pixelToEMU(dimensions.getHeight());
			log.debug("Add run picture: {}", imageFile);
			XWPFPicture picture = run.addPicture(inputStream, pictureType(imageFile.getName()), imageFile.getName(), width, height);

			if (projectProperties.imageShadow) {
				log.debug("Add shadow effect");
				shadowEffect(picture);
			}
		} catch (Exception e) {
			log.error("Fail to process image: {}: {}", e.getClass(), e.getMessage(), e);
		}

		// separated paragraph for image caption
		log.debug("Create image caption");
		currentParagraph = document.createParagraph();
		pictureDescription = true;

		super.visit(image);
	}

	private static int pictureType(String path) {
		switch (getFileExtension(path)) {
		case "emf":
			return XWPFDocument.PICTURE_TYPE_EMF;
		case "wmf":
			return XWPFDocument.PICTURE_TYPE_WMF;
		case "pict":
			return XWPFDocument.PICTURE_TYPE_PICT;
		case "jpeg":
		case "jpg":
			return XWPFDocument.PICTURE_TYPE_JPEG;
		case "png":
			return XWPFDocument.PICTURE_TYPE_PNG;
		case "dib":
			return XWPFDocument.PICTURE_TYPE_DIB;
		case "gif":
			return XWPFDocument.PICTURE_TYPE_GIF;
		case "tiff":
			return XWPFDocument.PICTURE_TYPE_TIFF;
		case "eps":
			return XWPFDocument.PICTURE_TYPE_EPS;
		case "bmp":
			return XWPFDocument.PICTURE_TYPE_BMP;
		case "wpg":
			return XWPFDocument.PICTURE_TYPE_WPG;
		default:
			System.err.println("Unsupported picture:" + path + ". Expected emf|wmf|pict|jpeg|png|dib|gif|tiff|eps|bmp|wpg");
			return 0;
		}
	}

	private static void shadowEffect(XWPFPicture picture) {
		// <a:effectLst>
		// ..<a:outerShdw blurRad="50800" dist="38100" dir="2700000" algn="tl" rotWithShape="false">
		// ....<a:prstClr val="black">
		// ......<a:alpha val="40000" />
		// ....</a:prstClr>
		// ..</a:outerShdw>
		// </a:effectLst>

		// a:outerShdw
		CTOuterShadowEffect outerShadow = CTOuterShadowEffect.Factory.newInstance();
		outerShadow.setBlurRad(50800);
		outerShadow.setDist(38100);
		outerShadow.setDir(2700000);
		outerShadow.setAlgn(STRectAlignment.TL);
		outerShadow.setRotWithShape(false);

		// a:alpha
		CTPositiveFixedPercentage transparency = CTPositiveFixedPercentage.Factory.newInstance();
		transparency.setVal(new BigInteger("40000"));

		// a:prstClr
		CTPresetColor presetColor = CTPresetColor.Factory.newInstance();
		presetColor.setVal(STPresetColorVal.BLACK);
		presetColor.setAlphaArray(new CTPositiveFixedPercentage[] { transparency });
		outerShadow.setPrstClr(presetColor);

		// a:effectLst
		CTEffectList effectList = CTEffectList.Factory.newInstance();
		effectList.setOuterShdw(outerShadow);

		picture.getCTPicture().getSpPr().setEffectLst(effectList);
	}

	private static String getFileExtension(String path) {
		if (path == null || path.isEmpty()) {
			return "";
		}
		int lastDotIndex = path.lastIndexOf(".");
		if (lastDotIndex == -1 || lastDotIndex == path.length() - 1) {
			return "";
		}
		return path.substring(lastDotIndex + 1);
	}

	// ------------------------------------------------------------------------

	@Override
	public void visit(BulletList bulletList) {
		log.trace("visit(BulletList bulletList)");
		lists.push(numbering.getBulletsNumId());
		super.visit(bulletList);
		lists.pop();
	}

	@Override
	public void visit(OrderedList orderedList) {
		log.trace("visit(OrderedList orderedList)");
		// every ordered list has its own numbering instance so that counting restarts from list start number
		lists.push(numbering.addOrderedNum(listLevel(lists.size()), orderedList.getStartNumber()));
		super.visit(orderedList);
		lists.pop();
	}

	@Override
	public void visit(ListItem listItem) {
		log.trace("visit(ListItem listItem)");
		assert !lists.isEmpty();

		log.debug("Create list item");
		currentParagraph = document.createParagraph();
		currentParagraph.setStyle(DocxStyles.getListStyleId());
		ignoreParagraph = true;

		log.debug("Set list item numbering");
		// <w:pPr>
		// ..<w:numPr>
		// ....<w:ilvl w:val="1" />
		// ....<w:numId w:val="7" />
		// ..</w:numPr>
		// </w:pPr>

		CTPPr pPr = currentParagraph.getCTP().getPPr();
		if (pPr == null) {
			pPr = currentParagraph.getCTP().addNewPPr();
		}

		CTNumPr numPr = pPr.getNumPr();
		if (numPr == null) {
			numPr = pPr.addNewNumPr();
		}

		CTDecimalNumber ilvl = numPr.getIlvl();
		if (ilvl == null) {
			ilvl = numPr.addNewIlvl();
		}
		ilvl.setVal(BigInteger.valueOf(listLevel(lists.size() - 1)));

		CTDecimalNumber numId = numPr.getNumId();
		if (numId == null) {
			numId = numPr.addNewNumId();
		}
		numId.setVal(lists.peek());

		super.visit(listItem);
		// empty list item does not consume pending list paragraph
		ignoreParagraph = false;
	}

	// numbering instance IDs of currently open lists, innermost on top
	private final Deque<BigInteger> lists = new ArrayDeque<>();

	private static int listLevel(int depth) {
		return Math.min(depth, DocxNumbering.maxLevel());
	}

	/** Align a block paragraph nested in a list with the text of the enclosing list item. */
	private void indentToList(XWPFParagraph paragraph) {
		if (!lists.isEmpty()) {
			paragraph.setIndentationLeft(DocxNumbering.textIndent(listLevel(lists.size() - 1)));
		}
	}

	// ------------------------------------------------------------------------

	private XWPFTable currentTable;
	private XWPFTableRow currentTableRow;
	private boolean tableRowIndexInitialized;
	private int tableRowIndex;
	private int tableColumnIndex;

	@Override
	public void visit(TableBlock table) {
		log.trace("visit(TableBlock table)");

		log.debug("Create table");
		currentTable = document.createTable();

		log.debug("Set table style ID {}", DocxStyles.getTableStyleId());
		currentTable.setStyleID(DocxStyles.getTableStyleId());

		CTTblPr tblPr = currentTable.getCTTbl().getTblPr();
		log.debug("Unset table borders");
		tblPr.unsetTblBorders();

		log.debug("Set table width to fill page");
		CTTblWidth width = CTTblWidth.Factory.newInstance();
		width.setW(contentPageWidth);
		width.setType(STTblWidth.DXA);
		tblPr.setTblW(width);

		log.debug("Set table look");
		CTTblLook tblLook = tblPr.isSetTblLook() ? tblPr.getTblLook() : tblPr.addNewTblLook();
		try {
			tblLook.setVal(Hex.decodeHex("04A0".toCharArray()));
		} catch (DecoderException e) {
			e.printStackTrace();
		}

		super.visit(table);

		int tableRowsCount = tableRowIndex;
		assert tableRowsCount > 0;
		int tableColumnsCount = currentTable.getRow(0).getTableCells().size();

		CTOnOff noWrapFlag = CTOnOff.Factory.newInstance();
		noWrapFlag.setVal("1");

		for (XWPFTableRow row : currentTable.getRows()) {
			for (int columnIndex = 0; columnIndex < tableColumnsCount - 1; ++columnIndex) {
				XWPFTableCell cell = row.getTableCells().get(columnIndex);
				CTTcPr properties = cell.getCTTc().getTcPr();
				if (properties == null) {
					properties = cell.getCTTc().addNewTcPr();
				}
				properties.setNoWrap(noWrapFlag);
			}
		}

		log.debug("Add space after table");
		XWPFParagraph space = document.createParagraph();
		space.setSpacingAfter(0);
		space.setSpacingBefore(0);
		space.createRun().setFontSize(0);
	}

	@Override
	public void visit(TableHead head) {
		log.trace("visit(TableHead head)");
		tableRowIndexInitialized = true;
		tableRowIndex = 0;
		super.visit(head);
	}

	@Override
	public void visit(TableBody body) {
		log.trace("visit(TableBody body)");
		if (!tableRowIndexInitialized) {
			tableRowIndex = 0;
		}
		tableRowIndexInitialized = false;
		super.visit(body);
	}

	@Override
	public void visit(TableRow row) {
		log.trace("visit(TableRow row)");

		assert currentTable != null;
		if (tableRowIndex < currentTable.getNumberOfRows()) {
			log.debug("Retrieve row: {} ", tableRowIndex);
			currentTableRow = currentTable.getRow(tableRowIndex);
		} else {
			log.debug("Create row: {} ", tableRowIndex);
			currentTableRow = currentTable.createRow();
		}

		++tableRowIndex;
		tableColumnIndex = 0;
		super.visit(row);
	}

	@Override
	public void visit(TableCell mdCell) {
		log.trace("visit(TableCell mdCell)");
		startInlineContent();

		XWPFTableCell docxCell = null;
		assert currentTableRow != null;
		if (tableColumnIndex < currentTableRow.getTableCells().size()) {
			log.debug("Retrieve cell: {}", tableColumnIndex);
			docxCell = currentTableRow.getCell(tableColumnIndex);
		} else {
			log.debug("Create cell: {}", tableColumnIndex);
			docxCell = currentTableRow.createCell();
		}
		++tableColumnIndex;

		log.debug("Create paragraph");
		List<XWPFParagraph> paragraphs = docxCell.getParagraphs();
		if (paragraphs.isEmpty()) {
			currentParagraph = docxCell.addParagraph();
		} else {
			currentParagraph = paragraphs.get(0);
		}

		ParagraphAlignment alignment = null;
		if (mdCell.getAlignment() != null) {
			switch (mdCell.getAlignment()) {
			case LEFT:
				alignment = ParagraphAlignment.LEFT;
				break;
			case CENTER:
				alignment = ParagraphAlignment.CENTER;
				break;
			case RIGHT:
				alignment = ParagraphAlignment.RIGHT;
				break;
			}
		}
		if (alignment != null) {
			log.debug("Set paragraph alignment: {}", alignment);
			currentParagraph.setAlignment(alignment);
		}

		super.visit(mdCell);
	}
}
