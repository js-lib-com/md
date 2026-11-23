package com.jslib.md.docx;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.xmlbeans.XmlException;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.junit.Test;

import com.jslib.md.docx.template.DocxTemplate;

public class DocxVisitorInlineTest {
	@Test
	public void GivenWrappedParagraph_WhenVisit_ThenJoinLinesWithSpace() throws Exception {
		// when
		XWPFParagraph paragraph = lastParagraph("First line\nsecond line\nthird line.");

		// then
		assertThat(paragraph.getText(), equalTo("First line second line third line."));
	}

	@Test
	public void GivenHardLineBreak_WhenVisit_ThenAddBreak() throws Exception {
		// when
		XWPFParagraph paragraph = lastParagraph("First line  \nsecond line");

		// then
		assertThat(paragraph.getText(), equalTo("First line\nsecond line"));
	}

	@Test
	public void GivenEmphasisWithInlineCode_WhenVisit_ThenKeepItalicAfterCode() throws Exception {
		// when
		XWPFParagraph paragraph = lastParagraph("plain *italic `code` still italic* plain");

		// then
		assertThat(format(paragraph), equalTo("[plain ]|[italic ]i|[code]i|[ still italic]i|[ plain]"));
	}

	@Test
	public void GivenNestedEmphasis_WhenVisit_ThenCombineBoldAndItalic() throws Exception {
		// when
		XWPFParagraph paragraph = lastParagraph("**bold *both* bold**\nnext");

		// then
		assertThat(format(paragraph), equalTo("[bold ]b|[both]ib|[ bold]b|[ ]|[next]"));
	}

	private static String format(XWPFParagraph paragraph) {
		return paragraph.getRuns().stream().map(DocxVisitorInlineTest::format).collect(Collectors.joining("|"));
	}

	private static String format(XWPFRun run) {
		return "[" + run.text() + "]" + (run.isItalic() ? "i" : "") + (run.isBold() ? "b" : "");
	}

	private static XWPFParagraph lastParagraph(String markdown) throws IOException, XmlException {
		XWPFDocument document = new XWPFDocument(new DocxTemplate(new Properties()).getInputStream());
		DocxVisitor visitor = new DocxVisitor(document, ProjectProperties.empty());
		Parser.builder().extensions(Arrays.asList(TablesExtension.create())).build().parse(markdown).accept(visitor);
		List<XWPFParagraph> paragraphs = document.getParagraphs();
		return paragraphs.get(paragraphs.size() - 1);
	}
}
