package com.jslib.md;

import java.util.List;

import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;

/** CommonMark parser configuration shared by all document generators. */
public final class Markdown {
	private Markdown() {
	}

	/** Markdown parser with GitHub flavored tables support. Parser is thread safe and can be reused. */
	public static Parser parser() {
		return Parser.builder().extensions(List.of(TablesExtension.create())).build();
	}
}
