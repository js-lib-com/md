package com.jslib.md.docx;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

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
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.jslib.md.CustomVisitor;
import com.jslib.md.InlineHtml;
import com.jslib.md.InlineHtml.Format;
import com.jslib.md.TableColumns;
import com.jslib.md.docx.template.DocxTemplate;
import com.jslib.md.docx.util.Color;
import com.jslib.md.docx.util.Dimensions;
import com.jslib.md.docx.util.Images;
import com.jslib.md.docx.util.Strings;
import com.jslib.md.docx.xml.DocxDocument;
import com.jslib.md.docx.xml.Wml;

public class DocxVisitor extends CustomVisitor {
    private static final Logger log = LoggerFactory.getLogger(DocxVisitor.class);

    private final Images images;
    private final File imagesDir;
    private final DocxDocument document;
    // the width of the page used for content display, i.e. page size minus left and right margins, in twips
    private final int contentPageWidth;

    private final ProjectProperties projectProperties;
    private final DocxNumbering numbering;

    private int topHeadingsCount;
    private Element currentParagraph;
    private Element currentRun;
    private boolean ignoreParagraph;
    // block quote nesting depth; all paragraphs inside a block quote use quote style
    private int blockQuoteDepth;

    public DocxVisitor(DocxTemplate template, ProjectProperties projectProperties, File imagesDir) throws IOException {
        this(new DocxDocument(template.getInputStream()), projectProperties, imagesDir);
        log.trace("DocxVisitor(DocxTemplate template, ProjectProperties projectProperties, File imagesDir)");
    }

    DocxVisitor(DocxDocument document, ProjectProperties projectProperties, File... imagesDir) throws IOException {
        super();
        log.trace("DocxVisitor(DocxDocument document, ProjectProperties projectProperties, File... imagesDir)");

        this.images = new Images();
        this.imagesDir = imagesDir.length == 1 ? imagesDir[0] : new File(".");
        this.document = document;

        this.projectProperties = projectProperties;

        log.debug("Create document styles");
        new DocxStyles(document);

        log.debug("Create document numbering");
        this.numbering = new DocxNumbering(document);

        log.debug("Compute available page width");
        Element sectPr = Wml.child(document.body(), "sectPr");
        if (sectPr == null) {
            throw new IOException("Invalid DOCX template. Missing body section properties.");
        }
        Element pageSize = Wml.child(sectPr, "pgSz");
        if (pageSize == null) {
            throw new IOException("Invalid DOCX template. Missing page size section properties.");
        }
        Element pageMargins = Wml.child(sectPr, "pgMar");
        int marginLeft = 0;
        int marginRight = 0;
        if (pageMargins != null) {
            marginLeft = Integer.parseInt(Wml.attr(pageMargins, "left"));
            marginRight = Integer.parseInt(Wml.attr(pageMargins, "right"));
        }
        this.contentPageWidth = Integer.parseInt(Wml.attr(pageSize, "w")) - marginLeft - marginRight;
        this.topHeadingsCount = 0;
    }

    public DocxDocument getDocument() {
        return document;
    }

    @Override
    public void visit(Heading heading) {
        log.trace("visit(Heading heading)");
        startInlineContent();

        if (heading.getLevel() == 1) {
            if (topHeadingsCount++ > 0) {
                log.debug("Add page break before heading 1");
                Wml.addBreak(Wml.add(createParagraph(), "r"), "page");
            }
        }

        log.debug("Create heading paragraph");
        currentParagraph = createParagraph();

        int headingLevel = heading.getLevel();
        log.debug("Set style: Heading: {}", headingLevel);
        setStyle(currentParagraph, DocxStyles.getHeadingStyleId(headingLevel));

        log.debug("Set heading numbering");
        // <w:pPr>
        // ..<w:numPr>
        // ....<w:ilvl w:val="3" />
        // ....<w:numId w:val="1" />
        // ..</w:numPr>
        // </w:pPr>

        setNumbering(currentParagraph, headingLevel - 1, numbering.getHeadingsNumId());

        super.visit(heading);
    }

    @Override
    public void visit(Paragraph paragraph) {
        log.trace("visit(Paragraph paragraph)");
        startInlineContent();
        if (!ignoreParagraph) {
            log.debug("Create paragraph");
            currentParagraph = createParagraph();
            if (blockQuoteDepth > 0) {
                log.debug("Set style: Quote");
                setStyle(currentParagraph, DocxStyles.getIntenseQuoteStyleId());
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
        ++blockQuoteDepth;
        super.visit(blockQuote);
        --blockQuoteDepth;
    }

    @Override
    public void visit(Code code) {
        log.trace("visit(Code code)");
        log.debug("Code literal: {}", code.getLiteral());
        Element run = createInlineRun();
        Wml.setText(run, code.getLiteral());
        Wml.property(rPr(run), "rStyle", DocxStyles.getQuoteCharStyleId());
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
            log.debug("Create code block paragraph");
            currentParagraph = createParagraph();
            setStyle(currentParagraph, DocxStyles.getNoSpacing());
            setBorder(currentParagraph, "left");
            indentToList(currentParagraph);

            log.debug("Create code block run");
            Element run = Wml.add(currentParagraph, "r");
            Element rFonts = Wml.property(rPr(run), "rFonts");
            for (String attribute : new String[]{"ascii", "hAnsi", "cs", "eastAsia"}) {
                Wml.attr(rFonts, attribute, "Consolas");
            }
            // font size is in half points
            Wml.property(rPr(run), "sz", 16);
            Wml.property(rPr(run), "color", Color.LIGHT_BLUE.name);
            if (!line.isEmpty()) {
                Wml.setText(run, line);
            }
        });
        ignoreParagraph = false;
        // code literal ends with new line so last paragraph is empty; remove its border to not extend code block frame
        Wml.remove(pPr(currentParagraph), "pBdr");
    }

    @Override
    public void visit(ThematicBreak thematicBreak) {
        log.trace("visit(ThematicBreak thematicBreak)");
        log.debug("Create horizontal rule paragraph");
        currentParagraph = createParagraph();
        setBorder(currentParagraph, "bottom");
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
            currentParagraph = createParagraph();
            Wml.addBreak(Wml.add(currentParagraph, "r"), "page");
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
            currentParagraph = createParagraph();
            indentToList(currentParagraph);
            Wml.setText(createInlineRun(), line);
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

    /**
     * Create a run on current paragraph with formatting from enclosing emphasis.
     */
    private Element createInlineRun() {
        assert currentParagraph != null;
        log.debug("Create inline run");
        Element run = Wml.add(currentParagraph, "r");
        applyEmphasis(run);
        return run;
    }

    private void applyEmphasis(Element run) {
        if (italicDepth > 0 || htmlFormats.isOpen(Format.ITALIC)) {
            Wml.property(rPr(run), "i");
        }
        if (boldDepth > 0 || htmlFormats.isOpen(Format.BOLD)) {
            Wml.property(rPr(run), "b");
        }
        if (htmlFormats.isOpen(Format.UNDERLINE)) {
            Wml.property(rPr(run), "u", "single");
        }
        if (htmlFormats.isOpen(Format.STRIKE)) {
            Wml.property(rPr(run), "strike");
        }
        if (htmlFormats.isOpen(Format.CODE)) {
            Wml.property(rPr(run), "rStyle", DocxStyles.getQuoteCharStyleId());
        }
        if (htmlFormats.isOpen(Format.SUPERSCRIPT)) {
            Wml.property(rPr(run), "vertAlign", "superscript");
        } else if (htmlFormats.isOpen(Format.SUBSCRIPT)) {
            Wml.property(rPr(run), "vertAlign", "subscript");
        }
        if (htmlFormats.isOpen(Format.HIGHLIGHT)) {
            Wml.property(rPr(run), "highlight", "yellow");
        }
    }

    // ------------------------------------------------------------------------
    // inline HTML

    /**
     * Open inline HTML formats, reset on every block so that unbalanced tags do not leak.
     */
    private final InlineHtml.Formats htmlFormats = new InlineHtml.Formats();

    /**
     * Reset inline state at the start of a block with inline content so that unbalanced inline HTML does not leak.
     */
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
        if (InlineHtml.isText(htmlInline.getLiteral())) {
            // not an HTML element, e.g. <special>, so it is rendered as text
            visit(new Text(htmlInline.getLiteral()));
            return;
        }
        InlineHtml.Tag tag = InlineHtml.parse(htmlInline.getLiteral());
        if (tag == null) {
            log.debug("Ignore inline HTML: {}", htmlInline.getLiteral());
            return;
        }
        if (htmlFormats.apply(tag)) {
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
            log.debug("Ignore unsupported inline HTML tag: {}", htmlInline.getLiteral());
        }
    }

    @Override
    public void visit(SoftLineBreak softLineBreak) {
        log.trace("visit(SoftLineBreak softLineBreak)");
        // Markdown source line break inside a paragraph is rendered as a space
        Wml.setText(createInlineRun(), " ");
    }

    @Override
    public void visit(HardLineBreak hardLineBreak) {
        log.trace("visit(HardLineBreak hardLineBreak)");
        Wml.addBreak(createInlineRun(), null);
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
            textLiteral = figureLabel + " " + ++pictureIndex + " - " + textLiteral;
        }
        log.debug("Set run text: |{}|", textLiteral);
        Wml.setText(currentRun, textLiteral);
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

    /**
     * Create hyperlink run, used by next text node, styled as hyperlink if links are enabled by project properties.
     */
    private void startHyperlink(String destination) {
        log.debug("Create hyperlink run to: {}", destination);
        assert currentParagraph != null;
        Element hyperlink = Wml.add(currentParagraph, "hyperlink");
        hyperlink.setAttributeNS(Wml.R_NS, "r:id", document.addHyperlink(destination));
        currentRun = Wml.add(hyperlink, "r");

        if (!projectProperties.linkEnabled) {
            return;
        }

        // <w:hyperlink r:id="rId7">
        // ..<w:r>
        // ....<w:rPr>
        // ......<w:rStyle w:val="Hyperlink" />
        // ......<w:color w:val="0000FF" />
        // ....</w:rPr>
        // ....<w:t>see visit(Text)</w:t>
        // ..</w:r>
        // </w:hyperlink>

        log.debug("Set hyperlink style: {}", DocxStyles.getLinkStyleId());
        Wml.property(rPr(currentRun), "rStyle", DocxStyles.getLinkStyleId());
        Wml.property(rPr(currentRun), "color", "0000FF");
    }

    // ------------------------------------------------------------------------

    /** Default image caption label, used if document properties have no figure.label. */
    public static final String DEFAULT_FIGURE_LABEL = "Figure";

    private String figureLabel = DEFAULT_FIGURE_LABEL;
    private int pictureIndex;
    private boolean pictureDescription;

    /** Set image caption label, e.g. Figura for Romanian documents; caption is label, image index and image text. */
    public void setFigureLabel(String figureLabel) {
        this.figureLabel = figureLabel;
    }

    @Override
    public void visit(Image image) {
        log.trace("visit(Image image)");

        // image failure does not abort document generation; image is skipped but its caption is kept
        try {
            File imageFile = new File(imagesDir, image.getDestination());
            Dimensions dimensions = images.getDimension(imageFile);
            log.debug("Image dimensions: {}", dimensions);
            if (dimensions.getWidth() > 600) {
                dimensions.scaleWidth(600);
                log.debug("Resize image to {}", dimensions);
                imageFile = images.resize(imageFile, dimensions);
            }

            log.debug("Add run picture: {}", imageFile);
            String relId = document.addImage(Files.readAllBytes(imageFile.toPath()), getFileExtension(imageFile.getName()));
            Wml.add(currentParagraph, "r").appendChild(drawing(relId, new File(image.getDestination()).getName(), dimensions));
        } catch (IOException | IllegalArgumentException e) {
            log.error("Skip image {}: {}", image.getDestination(), e.getMessage());
        }

        // separated paragraph for image caption
        log.debug("Create image caption");
        currentParagraph = createParagraph();
        pictureDescription = true;

        super.visit(image);
    }

    /**
     * English Metric Units per pixel, at 96 DPI.
     */
    private static final int EMU_PER_PIXEL = 9525;

    private static final String SHADOW_EFFECT = """
            <a:effectLst>
              <a:outerShdw blurRad="50800" dist="38100" dir="2700000" algn="tl" rotWithShape="0">
                <a:prstClr val="black">
                  <a:alpha val="40000"/>
                </a:prstClr>
              </a:outerShdw>
            </a:effectLst>
            """;

    /**
     * Inline drawing template; format arguments are width and height in EMU, drawing ID, image name, image relationship
     * ID and optional shadow effect. Indentation white spaces are removed after parsing.
     */
    private static final String DRAWING = """
            <w:drawing xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
                xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
                xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"
                xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
              <wp:inline distT="0" distB="0" distL="0" distR="0">
                <wp:extent cx="%1$d" cy="%2$d"/>
                <wp:docPr id="%3$d" name="Picture %3$d" descr="%4$s"/>
                <wp:cNvGraphicFramePr>
                  <a:graphicFrameLocks noChangeAspect="1"/>
                </wp:cNvGraphicFramePr>
                <a:graphic>
                  <a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">
                    <pic:pic>
                      <pic:nvPicPr>
                        <pic:cNvPr id="0" name="%4$s"/>
                        <pic:cNvPicPr>
                          <a:picLocks noChangeAspect="1"/>
                        </pic:cNvPicPr>
                      </pic:nvPicPr>
                      <pic:blipFill>
                        <a:blip r:embed="%5$s"/>
                        <a:stretch>
                          <a:fillRect/>
                        </a:stretch>
                      </pic:blipFill>
                      <pic:spPr>
                        <a:xfrm>
                          <a:off x="0" y="0"/>
                          <a:ext cx="%1$d" cy="%2$d"/>
                        </a:xfrm>
                        <a:prstGeom prst="rect">
                          <a:avLst/>
                        </a:prstGeom>
                        %6$s
                      </pic:spPr>
                    </pic:pic>
                  </a:graphicData>
                </a:graphic>
              </wp:inline>
            </w:drawing>
            """;

    /**
     * Inline drawing referencing embedded image; image has shadow effect if enabled by project properties.
     */
    private Element drawing(String relId, String name, Dimensions dimensions) throws IOException {
        long width = (long) dimensions.getWidth() * EMU_PER_PIXEL;
        long height = (long) dimensions.getHeight() * EMU_PER_PIXEL;
        String effect = projectProperties.imageShadow ? SHADOW_EFFECT : "";
        String xml = String.format(DRAWING, width, height, document.nextDrawingId(), DocxTemplate.escapeXml(name), relId, effect);
        Document fragment = Wml.parse(new ByteArrayInputStream(xml.getBytes(UTF_8)));
        Wml.stripWhitespace(fragment.getDocumentElement());
        return (Element) document.body().getOwnerDocument().importNode(fragment.getDocumentElement(), true);
    }

    private static String getFileExtension(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        int lastDotIndex = path.lastIndexOf(".");
        if (lastDotIndex == -1 || lastDotIndex == path.length() - 1) {
            return "";
        }
        return path.substring(lastDotIndex + 1).toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------------

    // numbering instance IDs of currently open lists, innermost on top
    private final Deque<Integer> lists = new ArrayDeque<>();

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
        currentParagraph = createParagraph();
        setStyle(currentParagraph, DocxStyles.getListStyleId());
        ignoreParagraph = true;

        if (!lists.isEmpty()) {
            log.debug("Set list item numbering");
            // <w:pPr>
            // ..<w:numPr>
            // ....<w:ilvl w:val="1" />
            // ....<w:numId w:val="7" />
            // ..</w:numPr>
            // </w:pPr>
            Integer numId = lists.peek();
            if (numId != null) {
                setNumbering(currentParagraph, listLevel(lists.size() - 1), numId);
            }
        }
        super.visit(listItem);
        // empty list item does not consume pending list paragraph
        ignoreParagraph = false;
    }

    private static int listLevel(int depth) {
        return Math.min(depth, DocxNumbering.maxLevel());
    }

    /**
     * Align a block paragraph nested in a list with the text of the enclosing list item.
     */
    private void indentToList(Element paragraph) {
        if (!lists.isEmpty()) {
            Wml.attr(Wml.property(pPr(paragraph), "ind"), "left", DocxNumbering.textIndent(listLevel(lists.size() - 1)));
        }
    }

    // ------------------------------------------------------------------------

    private Element currentTable;
    private Element currentTableRow;

    @Override
    public void visit(TableBlock table) {
        log.trace("visit(TableBlock table)");

        log.debug("Create table");
        currentTable = document.appendBlock(Wml.create(document.body(), "tbl"));
        Element tblPr = Wml.property(currentTable, "tblPr");

        log.debug("Set table style ID {}", DocxStyles.getTableStyleId());
        Wml.property(tblPr, "tblStyle", DocxStyles.getTableStyleId());

        log.debug("Set table width to fill page");
        Element width = Wml.property(tblPr, "tblW");
        Wml.attr(width, "w", contentPageWidth);
        Wml.attr(width, "type", "dxa");

        log.debug("Set table look");
        Wml.property(tblPr, "tblLook", "04A0");

        super.visit(table);

        List<Element> rows = Wml.children(currentTable, "tr");
        assert !rows.isEmpty();
        int tableColumnsCount = Wml.children(rows.get(0), "tc").size();

        if (projectProperties.tableAutoFit) {
            autoFit(currentTable, tableColumnsCount);
        } else {
            // all but last column fit their content without wrapping; last column takes remaining page width
            for (Element row : rows) {
                List<Element> cells = Wml.children(row, "tc");
                for (int columnIndex = 0; columnIndex < Math.min(tableColumnsCount - 1, cells.size()); ++columnIndex) {
                    Wml.property(Wml.property(cells.get(columnIndex), "tcPr"), "noWrap");
                }
            }
        }

        log.debug("Add space after table");
        Element space = createParagraph();
        Element spacing = Wml.property(pPr(space), "spacing");
        Wml.attr(spacing, "before", 0);
        Wml.attr(spacing, "after", 0);
        Wml.property(rPr(Wml.add(space, "r")), "sz", 0);
    }

    /**
     * Set columns width, in percent, proportional with the quantity of text in every column but not less than minimal
     * column width from project properties. Table layout is fixed so that word processors use computed widths.
     */
    private void autoFit(Element table, int columnsCount) {
        List<List<String>> texts = new ArrayList<>();
        for (Element row : Wml.children(table, "tr")) {
            List<String> rowTexts = new ArrayList<>();
            for (Element cell : Wml.children(row, "tc")) {
                rowTexts.add(Wml.text(cell));
            }
            texts.add(rowTexts);
        }
        int[] widths = TableColumns.widths(texts, projectProperties.tableColumnMinWidth, projectProperties.tableColumnOutliers);
        log.debug("Set table columns width, in fiftieths of percent: {}", Arrays.toString(widths));

        Element tblPr = Wml.property(table, "tblPr");
        Wml.attr(Wml.property(tblPr, "tblLayout"), "type", "fixed");

        // fixed layout uses table grid, in twips, computed from page content width
        Element grid = Wml.property(table, "tblGrid");
        while (grid.getFirstChild() != null) {
            grid.removeChild(grid.getFirstChild());
        }
        for (int width : widths) {
            Wml.attr(Wml.add(grid, "gridCol"), "w", (long) contentPageWidth * width / TableColumns.FULL_WIDTH);
        }

        for (Element row : Wml.children(table, "tr")) {
            List<Element> cells = Wml.children(row, "tc");
            for (int column = 0; column < Math.min(columnsCount, cells.size()); ++column) {
                Element tcW = Wml.property(Wml.property(cells.get(column), "tcPr"), "tcW");
                Wml.attr(tcW, "w", widths[column]);
                Wml.attr(tcW, "type", "pct");
            }
        }
    }

    @Override
    public void visit(TableRow row) {
        log.trace("visit(TableRow row)");
        assert currentTable != null;
        log.debug("Create row");
        currentTableRow = Wml.add(currentTable, "tr");
        super.visit(row);
    }

    @Override
    public void visit(TableCell mdCell) {
        log.trace("visit(TableCell mdCell)");
        startInlineContent();

        assert currentTableRow != null;
        log.debug("Create cell and its paragraph");
        currentParagraph = Wml.add(Wml.add(currentTableRow, "tc"), "p");

        if (mdCell.getAlignment() != null) {
            String alignment = mdCell.getAlignment().name().toLowerCase(Locale.ROOT);
            log.debug("Set paragraph alignment: {}", alignment);
            Wml.property(pPr(currentParagraph), "jc", alignment);
        }

        super.visit(mdCell);
    }

    // ------------------------------------------------------------------------
    // WordprocessingML helpers

    /**
     * Create paragraph and append it to document body.
     */
    private Element createParagraph() {
        return document.appendBlock(Wml.create(document.body(), "p"));
    }

    private static Element pPr(Element paragraph) {
        return Wml.property(paragraph, "pPr");
    }

    private static Element rPr(Element run) {
        return Wml.property(run, "rPr");
    }

    private static void setStyle(Element paragraph, String styleId) {
        Wml.property(pPr(paragraph), "pStyle", styleId);
    }

    private static void setBorder(Element paragraph, String side) {
        Wml.property(Wml.property(pPr(paragraph), "pBdr"), side, "single");
    }

    /**
     * Set paragraph numbering.
     *
     * <pre>
     * &lt;w:pPr&gt;
     * ..&lt;w:numPr&gt;
     * ....&lt;w:ilvl w:val="1" /&gt;
     * ....&lt;w:numId w:val="7" /&gt;
     * ..&lt;/w:numPr&gt;
     * &lt;/w:pPr&gt;
     * </pre>
     */
    private static void setNumbering(Element paragraph, int level, int numId) {
        Element numPr = Wml.property(pPr(paragraph), "numPr");
        Wml.property(numPr, "ilvl", level);
        Wml.property(numPr, "numId", numId);
    }
}
