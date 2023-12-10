package com.jslib.md.docx.util;

import java.util.ArrayList;
import java.util.List;

import com.jslib.util.Params;

public class Strings extends com.jslib.util.Strings {
	public static List<String> lines(String text) {
		Params.notNull(text, "Text");

		List<String> lines = new ArrayList<>();
		StringBuilder lineBuilder = new StringBuilder();

		for (int i = 0; i < text.length(); ++i) {
			char c = text.charAt(i);
			if (c == '\r') {
				continue;
			}
			if (c != '\n') {
				lineBuilder.append(c);
				continue;
			}
			lines.add(lineBuilder.toString());
			lineBuilder.setLength(0);
		}

		lines.add(lineBuilder.toString());
		return lines;
	}
}
