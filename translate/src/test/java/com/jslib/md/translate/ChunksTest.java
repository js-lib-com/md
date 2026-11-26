package com.jslib.md.translate;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;

import java.util.List;

import org.junit.Test;

public class ChunksTest {
	@Test
	public void GivenLfAndCrlfParagraphs_WhenChunks_ThenSplitOnBlankLines() {
		// GIVEN
		String lfText = "one\ntwo\n\nthree\n";
		String crlfText = "one\r\n\r\n\r\ntwo\r\n \r\nthree";

		// WHEN
		List<String> lfChunks = Chunks.split(lfText, "\n\n", 3);
		List<String> crlfChunks = Chunks.split(crlfText, "\r\n\r\n", 3);

		// THEN
		assertThat(lfChunks, contains("one\ntwo", "three"));
		assertThat(crlfChunks, contains("one", "two", "three"));
	}

	@Test
	public void GivenCrlfTableRows_WhenChunks_ThenKeepRowsInSameParagraph() {
		// GIVEN
		String tableWithHeaderSeparator = "| a |\r\n|---|\r\n| b |\r\n\r\npara";
		String tableWithoutHeaderSeparator = "| a |\r\n| b |\r\n\r\npara";

		// WHEN
		List<String> longChunks = Chunks.split(tableWithHeaderSeparator, "\r\n\r\n", 3000);
		List<String> shortChunks = Chunks.split(tableWithoutHeaderSeparator, "\r\n\r\n", 3);

		// THEN
		assertThat(longChunks, contains("| a |\r\n|---|\r\n| b |\r\n\r\npara"));
		assertThat(shortChunks, contains("| a |\r\n| b |", "para"));
	}

	@Test
	public void GivenShortParagraphs_WhenChunks_ThenGroupUpToChunkLength() {
		// GIVEN
		String fourParagraphs = "aa\n\nbb\n\ncc\n\ndd";
		String twoParagraphs = "aa\n\nbb";

		// WHEN
		List<String> smallChunks = Chunks.split(fourParagraphs, "\n\n", 6);
		List<String> largeChunks = Chunks.split(twoParagraphs, "\n\n", 3000);

		// THEN
		assertThat(smallChunks, contains("aa\n\nbb", "cc\n\ndd"));
		assertThat(largeChunks, hasSize(1));
	}
}
