package com.jslib.md.docx;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.poi.xwpf.usermodel.Borders;
import org.apache.poi.xwpf.usermodel.XWPFAbstractNum;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFNum;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.xmlbeans.XmlException;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.junit.Before;
import org.junit.Test;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBrType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STNumberFormat;

import com.jslib.md.docx.template.DocxTemplate;

public class DocxVisitorBlockTest {
	private XWPFDocument document;
	private int templateParagraphsCount;

	@Before
	public void beforeTest() throws IOException {
		document = new XWPFDocument(new DocxTemplate(new Properties()).getInputStream());
		templateParagraphsCount = document.getParagraphs().size();
	}

	@Test
	public void GivenTemplateNumbering_WhenCreateVisitor_ThenNumberingIdsAreUnique() throws IOException, XmlException {
		// when
		generate("- item");

		// then: identifiers are still unique after a save / reload round trip
		XWPFDocument reloaded = reload(document);
		List<BigInteger> abstractIds = reloaded.getNumbering().getAbstractNums().stream().map(a -> a.getCTAbstractNum().getAbstractNumId()).collect(Collectors.toList());
		List<BigInteger> numIds = reloaded.getNumbering().getNums().stream().map(n -> n.getCTNum().getNumId()).collect(Collectors.toList());
		assertThat(new HashSet<>(abstractIds), hasSize(abstractIds.size()));
		assertThat(new HashSet<>(numIds), hasSize(numIds.size()));
	}

	@Test
	public void GivenNestedBulletList_WhenVisit_ThenSetLevelsAndBulletFormat() throws IOException, XmlException {
		// when
		List<XWPFParagraph> paragraphs = generate("- one\n  - one.one\n    - one.one.one\n- two");

		// then
		assertThat(texts(paragraphs), contains("one", "one.one", "one.one.one", "two"));
		assertThat(levels(paragraphs), contains(0, 1, 2, 0));
		assertThat(numIds(paragraphs).size(), equalTo(1));
		assertThat(format(paragraphs.get(0)), equalTo(STNumberFormat.BULLET));
	}

	@Test
	public void GivenOrderedLists_WhenVisit_ThenUseDecimalFormatAndRestartEveryList() throws IOException, XmlException {
		// when
		List<XWPFParagraph> paragraphs = generate("1. first\n2. second\n\nParagraph.\n\n3. third\n4. fourth");

		// then
		List<XWPFParagraph> items = Arrays.asList(paragraphs.get(0), paragraphs.get(1), paragraphs.get(3), paragraphs.get(4));
		assertThat(texts(items), contains("first", "second", "third", "fourth"));
		assertThat(format(items.get(0)), equalTo(STNumberFormat.DECIMAL));
		assertThat(items.get(0).getNumID(), equalTo(items.get(1).getNumID()));
		assertThat(items.get(2).getNumID(), not(equalTo(items.get(0).getNumID())));
		assertThat(startOverride(items.get(0)), equalTo(BigInteger.ONE));
		assertThat(startOverride(items.get(2)), equalTo(BigInteger.valueOf(3)));
	}

	@Test
	public void GivenOrderedListNestedInBulletList_WhenVisit_ThenUseOrderedNumberingOnNestedLevel() throws IOException, XmlException {
		// when
		List<XWPFParagraph> paragraphs = generate("- bullet\n  1. ordered\n  2. ordered\n- bullet");

		// then
		assertThat(levels(paragraphs), contains(0, 1, 1, 0));
		assertThat(format(paragraphs.get(0)), equalTo(STNumberFormat.BULLET));
		assertThat(format(paragraphs.get(1)), equalTo(STNumberFormat.LOWER_LETTER));
		assertThat(paragraphs.get(3).getNumID(), equalTo(paragraphs.get(0).getNumID()));
	}

	@Test
	public void GivenFencedCodeAsFirstListItemBlock_WhenVisit_ThenRenderCode() throws IOException, XmlException {
		// when
		List<XWPFParagraph> paragraphs = generate("- ```\n  code line\n  ```\n- item");

		// then
		List<String> texts = texts(paragraphs);
		assertThat(texts.contains("code line"), equalTo(true));
		XWPFParagraph code = paragraphs.get(texts.indexOf("code line"));
		assertThat(code.getIndentationLeft(), equalTo(DocxNumbering.textIndent(0)));
	}

	@Test
	public void GivenFencedCodeAfterListItemText_WhenVisit_ThenRenderCodeIndented() throws IOException, XmlException {
		// when
		List<XWPFParagraph> paragraphs = generate("- step:\n\n  ```\n  code line\n  ```\n");

		// then
		assertThat(texts(paragraphs).subList(0, 2), contains("step:", "code line"));
		assertThat(paragraphs.get(1).getIndentationLeft(), equalTo(DocxNumbering.textIndent(0)));
	}

	@Test
	public void GivenEmptyListItem_WhenVisit_ThenFollowingParagraphIsNotListItem() throws IOException, XmlException {
		// when
		List<XWPFParagraph> paragraphs = generate("-\n\nParagraph.");

		// then
		XWPFParagraph last = paragraphs.get(paragraphs.size() - 1);
		assertThat(last.getText(), equalTo("Paragraph."));
		assertThat(last.getNumID(), equalTo(null));
	}

	@Test
	public void GivenIndentedCodeBlock_WhenVisit_ThenRenderLikeFencedCode() throws IOException, XmlException {
		// when
		List<XWPFParagraph> indented = generate("Text:\n\n    int x = 1;\n      return x;\n");
		beforeTest();
		List<XWPFParagraph> fenced = generate("Text:\n\n```\nint x = 1;\n  return x;\n```\n");

		// then
		assertThat(texts(indented), equalTo(texts(fenced)));
		assertThat(texts(indented).subList(1, 3), contains("int x = 1;", "  return x;"));
		assertThat(indented.get(1).getStyle(), equalTo("NoSpacing"));
		assertThat(indented.get(1).getRuns().get(0).getFontFamily(), equalTo("Consolas"));
		assertThat(indented.get(1).getBorderLeft(), equalTo(Borders.SINGLE));
	}

	@Test
	public void GivenThematicBreak_WhenVisit_ThenAddParagraphWithBottomBorder() throws IOException, XmlException {
		// when
		List<XWPFParagraph> paragraphs = generate("Before.\n\n***\n\nAfter.");

		// then
		assertThat(texts(paragraphs), contains("Before.", "", "After."));
		assertThat(paragraphs.get(1).getBorderBottom(), equalTo(Borders.SINGLE));
		assertThat(paragraphs.get(2).getBorderBottom(), equalTo(Borders.NONE));
	}

	@Test
	public void GivenHtmlComment_WhenVisit_ThenIgnore() throws IOException, XmlException {
		// when
		List<XWPFParagraph> paragraphs = generate("Before.\n\n<!-- author note\nspanning lines -->\n\nAfter.");

		// then
		assertThat(texts(paragraphs), contains("Before.", "After."));
	}

	@Test
	public void GivenHtmlPageBreak_WhenVisit_ThenAddPageBreak() throws IOException, XmlException {
		// when
		List<XWPFParagraph> paragraphs = generate("Before.\n\n<div style=\"page-break-after: always\"></div>\n\nAfter.");

		// then
		assertThat(paragraphs, hasSize(3));
		CTBr br = paragraphs.get(1).getRuns().get(0).getCTR().getBrArray(0);
		assertThat(br.getType(), equalTo(STBrType.PAGE));
	}

	@Test
	public void GivenHtmlBlock_WhenVisit_ThenRenderPlainTextPerBlockElement() throws IOException, XmlException {
		// when
		List<XWPFParagraph> paragraphs = generate("<div align=\"center\">\n<p>First <b>bold</b>\nline &amp; more</p>\n<p>Second&nbsp;line</p>\n</div>\n");

		// then
		assertThat(texts(paragraphs), contains("First bold line & more", "Second line"));
	}

	@Test
	public void GivenHeadingLevelsFiveAndSix_WhenVisit_ThenUseHeadingStylesAndNumbering() throws IOException, XmlException {
		// when
		List<XWPFParagraph> paragraphs = generate("# H1\n## H2\n### H3\n#### H4\n##### H5\n###### H6");

		// then
		assertThat(paragraphs.stream().map(XWPFParagraph::getStyle).collect(Collectors.toList()), contains("Heading1", "Heading2", "Heading3", "Heading4", "Heading5", "Heading6"));
		assertThat(levels(paragraphs), contains(0, 1, 2, 3, 4, 5));
		assertThat(lvlText(paragraphs.get(5)), equalTo("%1.%2.%3.%4.%5.%6"));
	}

	// --------------------------------------------------------------------------------------------

	private List<XWPFParagraph> generate(String markdown) throws IOException, XmlException {
		DocxVisitor visitor = new DocxVisitor(document, ProjectProperties.empty());
		Parser.builder().extensions(List.of(TablesExtension.create())).build().parse(markdown).accept(visitor);
		List<XWPFParagraph> paragraphs = document.getParagraphs();
		return new ArrayList<>(paragraphs.subList(templateParagraphsCount, paragraphs.size()));
	}

	private static XWPFDocument reload(XWPFDocument document) throws IOException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		document.write(buffer);
		return new XWPFDocument(new ByteArrayInputStream(buffer.toByteArray()));
	}

	private static List<String> texts(List<XWPFParagraph> paragraphs) {
		return paragraphs.stream().map(XWPFParagraph::getText).collect(Collectors.toList());
	}

	private static List<Integer> levels(List<XWPFParagraph> paragraphs) {
		return paragraphs.stream().map(p -> p.getNumIlvl().intValue()).collect(Collectors.toList());
	}

	private static Set<BigInteger> numIds(List<XWPFParagraph> paragraphs) {
		return paragraphs.stream().map(XWPFParagraph::getNumID).collect(Collectors.toSet());
	}

	private XWPFAbstractNum abstractNum(XWPFParagraph paragraph) {
		BigInteger abstractNumId = document.getNumbering().getAbstractNumID(paragraph.getNumID());
		return document.getNumbering().getAbstractNum(abstractNumId);
	}

	private STNumberFormat.Enum format(XWPFParagraph paragraph) {
		return abstractNum(paragraph).getCTAbstractNum().getLvlArray(paragraph.getNumIlvl().intValue()).getNumFmt().getVal();
	}

	private String lvlText(XWPFParagraph paragraph) {
		return abstractNum(paragraph).getCTAbstractNum().getLvlArray(paragraph.getNumIlvl().intValue()).getLvlText().getVal();
	}

	private BigInteger startOverride(XWPFParagraph paragraph) {
		XWPFNum num = document.getNumbering().getNum(paragraph.getNumID());
		return num.getCTNum().getLvlOverrideArray(0).getStartOverride().getVal();
	}
}
