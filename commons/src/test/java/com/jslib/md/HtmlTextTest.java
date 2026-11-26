package com.jslib.md;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;

import org.junit.Test;

public class HtmlTextTest {
	@Test
	public void GivenHtmlBlocks_WhenLines_ThenOneLinePerBlockWithoutTagsAndWithDecodedEntities() {
		assertThat(HtmlText.lines("<div>one <b>bold</b></div>\n<p>a &amp; b&nbsp;&lt;c&gt;</p>"), contains("one bold", "a & b <c>"));
		assertThat(HtmlText.lines("<!-- comment --><div> </div>"), empty());
	}

	@Test
	public void GivenPageBreakStyle_WhenIsPageBreak_ThenIgnoreCommentedStyle() {
		assertThat(HtmlText.isPageBreak("<div style=\"page-break-after: always\"></div>"), equalTo(true));
		assertThat(HtmlText.isPageBreak("<div style=\"break-before:page\"></div>"), equalTo(true));
		assertThat(HtmlText.isPageBreak("<!-- <div style=\"page-break-after: always\"></div> -->"), equalTo(false));
	}
}
