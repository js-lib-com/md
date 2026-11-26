package com.jslib.md.translate;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Markdown structure checks and repairs for model translations. Local models occasionally damage Markdown structure,
 * e.g. drop a table row or prefix a line with garbage.
 */
final class StructureCheck {
	/**
	 * Garbage model emits sometimes at line start, before a Markdown marker: backslashes or a slash, e.g.
	 * <code>\\| cell |</code> or <code>/ - item</code>.
	 */
	private static final Pattern LINE_PREFIX_ARTIFACT = Pattern.compile("(?m)^([ \\t]*)(?:\\\\+ ?|/ )(?=[|#*-]|\\d+\\. )");

	/** Lines starting with backslash or slash in source, in which case line prefix artifacts are not repaired. */
	private static final Pattern SOURCE_LINE_PREFIX = Pattern.compile("(?m)^[ \\t]*[\\\\/]");

	/** Line with only backslashes or a slash, model emits sometimes between list items or table rows. */
	private static final Pattern LINE_ARTIFACT = Pattern.compile("(?m)^[ \\t]*(?:\\\\+|/)[ \\t]*(?:\\R|$)");

	/** Translation keeps source lines, so a different count means inserted garbage or merged lines. */
	private static final Pattern NON_BLANK_LINE = Pattern.compile("(?m)^[ \\t]*\\S");
	private static final Pattern HEADING = Pattern.compile("(?m)^#+ ");
	private static final Pattern TABLE_ROW = Pattern.compile("(?m)^[ \\t]*\\|");
	private static final Pattern LIST_ITEM = Pattern.compile("(?m)^[ \\t]*(?:[-*+]|\\d+\\.) ");

	private StructureCheck() {
	}

	/** Remove known model artifacts from translation, unless source has lines that look like artifacts. */
	static String repair(String source, String translation) {
		if (SOURCE_LINE_PREFIX.matcher(source).find()) {
			return translation;
		}
		String repaired = LINE_ARTIFACT.matcher(translation).replaceAll("");
		return LINE_PREFIX_ARTIFACT.matcher(repaired).replaceAll("$1");
	}

	/** Describe Markdown structure difference between source and translation, or null if same structure. */
	static String difference(String source, String translation) {
		List<String> differences = new ArrayList<>();
		compare(differences, "lines", NON_BLANK_LINE, source, translation);
		compare(differences, "headings", HEADING, source, translation);
		compare(differences, "table rows", TABLE_ROW, source, translation);
		compare(differences, "list items", LIST_ITEM, source, translation);
		return differences.isEmpty() ? null : String.join(", ", differences);
	}

	private static void compare(List<String> differences, String name, Pattern pattern, String source, String translation) {
		long sourceCount = pattern.matcher(source).results().count();
		long translationCount = pattern.matcher(translation).results().count();
		if (sourceCount != translationCount) {
			differences.add(String.format("%d %s instead of %d", translationCount, name, sourceCount));
		}
	}
}
