package com.jslib.md;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import org.junit.Test;

public class InlineHtmlTest {
	@Test
	public void GivenUnknownElementTags_WhenIsText_ThenTrue() {
		// GIVEN
		String[] literals = { "<special>", "</special>", "<Special attr=\"x\">", "<field/>" };

		// WHEN
		boolean[] texts = new boolean[literals.length];
		for (int i = 0; i < literals.length; ++i) {
			texts[i] = InlineHtml.isText(literals[i]);
		}

		// THEN
		assertThat(texts, equalTo(new boolean[] { true, true, true, true }));
	}

	@Test
	public void GivenHtmlTagsAndComments_WhenIsText_ThenFalse() {
		// GIVEN
		String[] literals = { "<b>", "</STRONG>", "<span class=\"x\">", "<br/>", "<a href=\"https://x.org\">", "<!-- note -->", "<?php echo 1; ?>", "<![CDATA[x]]>" };

		// WHEN
		boolean[] texts = new boolean[literals.length];
		for (int i = 0; i < literals.length; ++i) {
			texts[i] = InlineHtml.isText(literals[i]);
		}

		// THEN
		assertThat(texts, equalTo(new boolean[literals.length]));
	}
}
