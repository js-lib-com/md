package com.jslib.md.docx;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.Test;
import org.openxmlformats.schemas.officeDocument.x2006.sharedTypes.STVerticalAlignRun;

import com.jslib.md.ProjectProperties;

public class DocxVisitorInlineTest {
	@Test
	public void GivenWrappedParagraph_WhenVisit_ThenJoinLinesWithSpace() throws Exception {
		// GIVEN
		String markdown = "First line\nsecond line\nthird line.";

		// WHEN
		XWPFParagraph paragraph = lastParagraph(markdown);

		// THEN
		assertThat(paragraph.getText(), equalTo("First line second line third line."));
	}

	@Test
	public void GivenHardLineBreak_WhenVisit_ThenAddBreak() throws Exception {
		// GIVEN
		String markdown = "First line  \nsecond line";

		// WHEN
		XWPFParagraph paragraph = lastParagraph(markdown);

		// THEN
		assertThat(paragraph.getText(), equalTo("First line\nsecond line"));
	}

	@Test
	public void GivenEmphasisWithInlineCode_WhenVisit_ThenKeepItalicAfterCode() throws Exception {
		// GIVEN
		String markdown = "plain *italic `code` still italic* plain";

		// WHEN
		XWPFParagraph paragraph = lastParagraph(markdown);

		// THEN
		assertThat(format(paragraph), equalTo("[plain ]|[italic ]i|[code]ic|[ still italic]i|[ plain]"));
	}

	@Test
	public void GivenNestedEmphasis_WhenVisit_ThenCombineBoldAndItalic() throws Exception {
		// GIVEN
		String markdown = "**bold *both* bold**\nnext";

		// WHEN
		XWPFParagraph paragraph = lastParagraph(markdown);

		// THEN
		assertThat(format(paragraph), equalTo("[bold ]b|[both]ib|[ bold]b|[ ]|[next]"));
	}

	@Test
	public void GivenInlineHtmlFormattingTags_WhenVisit_ThenApplyRunFormatting() throws Exception {
		// GIVEN
		String markdown = "a <b>bold</b> <i>it</i> <u>u</u> <s>s</s> <code>c</code> x<sup>2</sup> H<sub>2</sub>O <mark>m</mark>";

		// WHEN
		XWPFParagraph paragraph = lastParagraph(markdown);

		// THEN
		assertThat(format(paragraph), equalTo("[a ]|[bold]b|[ ]|[it]i|[ ]|[u]u|[ ]|[s]s|[ ]|[c]c|[ x]|[2]^|[ H]|[2]_|[O ]|[m]h"));
	}

	@Test
	public void GivenInlineHtmlNestedInEmphasis_WhenVisit_ThenCombineFormatting() throws Exception {
		// GIVEN
		String markdown = "**bold <EM>both</EM>** <strong><em>html</em></strong>";

		// WHEN
		XWPFParagraph paragraph = lastParagraph(markdown);

		// THEN
		assertThat(format(paragraph), equalTo("[bold ]b|[both]ib|[ ]|[html]ib"));
	}

	@Test
	public void GivenUnbalancedInlineHtml_WhenVisit_ThenDoNotLeakToNextBlock() throws Exception {
		// GIVEN
		String markdown = "Text with <b>unclosed bold.\n\n</i>stray close and plain";

		// WHEN
		XWPFParagraph paragraph = lastParagraph(markdown);

		// THEN
		assertThat(format(paragraph), equalTo("[stray close and plain]"));
	}

	@Test
	public void GivenInlineHtmlLineBreaks_WhenVisit_ThenAddBreaks() throws Exception {
		// GIVEN
		String markdown = "line<br>next<BR />end";

		// WHEN
		XWPFParagraph paragraph = lastParagraph(markdown);

		// THEN
		assertThat(paragraph.getText(), equalTo("line\nnext\nend"));
	}

	@Test
	public void GivenInlineHtmlAnchor_WhenVisit_ThenCreateHyperlink() throws Exception {
		// GIVEN
		String markdown = "see <a href=\"https://x.org/?a=1&amp;b=2\" title=\"x\">site</a> now";

		// WHEN
		XWPFParagraph paragraph = lastParagraph(markdown);

		// THEN
		assertThat(paragraph.getText(), equalTo("see site now"));
		XWPFHyperlinkRun link = (XWPFHyperlinkRun) paragraph.getRuns().get(1);
		assertThat(link.text(), equalTo("site"));
		assertThat(link.getHyperlink(paragraph.getDocument()).getURL(), equalTo("https://x.org/?a=1&b=2"));
		assertThat(paragraph.getRuns().get(2) instanceof XWPFHyperlinkRun, equalTo(false));
	}

	@Test
	public void GivenInlineHtmlCommentAndUnsupportedTags_WhenVisit_ThenKeepTextOnly() throws Exception {
		// GIVEN
		String markdown = "a <!-- note --> <span class=\"x\">b</span> <img src=\"x.png\"/> c";

		// WHEN
		XWPFParagraph paragraph = lastParagraph(markdown);

		// THEN
		assertThat(paragraph.getText(), equalTo("a  b  c"));
		assertThat(format(paragraph).replaceAll("[^]|]+]", ""), equalTo("||||"));
	}

	@Test
	public void GivenTextThatLooksLikeTag_WhenVisit_ThenKeepItAsText() throws Exception {
		// GIVEN
		String markdown = "a <special> and **<field/>** <span>b</span> <!-- note -->";

		// WHEN
		XWPFParagraph paragraph = lastParagraph(markdown);

		// THEN
		// unknown elements are text, keeping enclosing emphasis, while HTML tags and comments are not
		assertThat(paragraph.getText(), equalTo("a <special> and <field/> b "));
		assertThat(format(paragraph), equalTo("[a ]|[<special>]|[ and ]|[<field/>]b|[ ]|[b]|[ ]"));
	}

	private static String format(XWPFParagraph paragraph) {
		return paragraph.getRuns().stream().map(DocxVisitorInlineTest::format).collect(Collectors.joining("|"));
	}

	private static String format(XWPFRun run) {
        return "[" + run.text() + "]" + (run.isItalic() ? "i" : "") + (run.isBold() ? "b" : "") +
                (run.getUnderline() != UnderlinePatterns.NONE ? "u" : "") + (run.isStrikeThrough() ? "s" : "") +
                (DocxStyles.QUOTE_CHAR.equals(run.getStyle()) ? "c" : "") +
                (run.getVerticalAlignment() == STVerticalAlignRun.SUPERSCRIPT ? "^" : "") +
                (run.getVerticalAlignment() == STVerticalAlignRun.SUBSCRIPT ? "_" : "") +
                (run.isHighlighted() ? "h" : "");
	}

	private static XWPFParagraph lastParagraph(String markdown) throws IOException {
		XWPFDocument document = TestDocuments.generate(markdown, ProjectProperties.empty());
		List<XWPFParagraph> paragraphs = document.getParagraphs();
		return paragraphs.get(paragraphs.size() - 1);
	}
}
