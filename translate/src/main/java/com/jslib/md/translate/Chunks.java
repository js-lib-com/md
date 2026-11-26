package com.jslib.md.translate;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Split text into chunks of whole paragraphs, small enough to be translated by a single completion request. */
final class Chunks {
	/** Chunk length, in characters, that keeps translation under completion tokens limit; deduced empirically. */
	static final int CHUNK_LENGTH = 3000;

	/**
	 * Blank lines, possibly with white spaces, between paragraphs, whatever line separator. Line breaks are atomic since
	 * otherwise backtracking matches CRLF as two line breaks, that is, as a blank line.
	 */
	private static final Pattern PARAGRAPHS_SEPARATOR = Pattern.compile("(?>\\R)(?:[ \\t]*(?>\\R))+");

	private Chunks() {
	}

	/** Group paragraphs into chunks of at least given length, except the last one. Paragraphs are never split. */
	static List<String> split(String text, String paragraphsSeparator, int chunkLength) {
		List<String> chunks = new ArrayList<>();
		StringBuilder chunk = new StringBuilder();
		for (String paragraph : PARAGRAPHS_SEPARATOR.split(text)) {
			if (paragraph.isBlank()) {
				continue;
			}
			if (chunk.length() > 0) {
				chunk.append(paragraphsSeparator);
			}
			chunk.append(paragraph.strip());
			if (chunk.length() >= chunkLength) {
				chunks.add(chunk.toString());
				chunk.setLength(0);
			}
		}
		if (chunk.length() > 0) {
			chunks.add(chunk.toString());
		}
		return chunks;
	}
}
