package com.jslib.md;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

/**
 * Project properties, loaded from optional <code>project.properties</code> file from document directory. Properties are
 * shared by all commands; a command ignores properties it does not use. Missing properties have default values while
 * invalid values are rejected with {@link IllegalArgumentException}.
 */
public final class ProjectProperties {
	public static final String FILE_NAME = "project.properties";

	/** Load project properties from document directory; defaults apply if project properties file is missing. */
	public static ProjectProperties load(File documentDir) throws IOException {
		return new ProjectProperties(PropertiesFile.loadOptional(new File(documentDir, FILE_NAME)));
	}

	public static ProjectProperties empty() {
		return new ProjectProperties(new Properties());
	}

	/** Directory with markdown sources, relative to document directory, or null if not set. */
	public final String sourceDir;
	/** Translation languages, e.g. ro; empty if document is not translated. Used by docx and translate. */
	public final List<String> languages;
	/** Generated file name, without extension. */
	public final String fileName;
	/** Add shadow to images. Used by docx. */
	public final boolean imageShadow;
	/** Style links as hyperlinks. Used by docx. */
	public final boolean linkEnabled;
	/** Compute table columns width from the quantity of text in every column. */
	public final boolean tableAutoFit;
	/** Minimal table column width, in percent, used when table auto-fit is enabled. */
	public final int tableColumnMinWidth;
	/** Percent of cells, from each end of sorted cell lengths, ignored when computing table column width. */
	public final int tableColumnOutliers;
	/** Total table width, in characters, shared by columns when table auto-fit is enabled. Used by xlsx. */
	public final int xlsxTableWidth;

	public ProjectProperties(Properties properties) {
		this.sourceDir = properties.getProperty("source.dir");
		this.languages = languages(properties.getProperty("languages"));
		this.fileName = properties.getProperty("file.name", "document");
		this.imageShadow = Boolean.parseBoolean(properties.getProperty("image.shadow"));
		this.linkEnabled = Boolean.parseBoolean(properties.getProperty("link.enabled"));
		this.tableAutoFit = Boolean.parseBoolean(properties.getProperty("table.auto.fit"));
		this.tableColumnMinWidth = integer(properties, "table.column.min.width", 10, 0, 100);
		this.tableColumnOutliers = integer(properties, "table.column.outliers", 20, 0, 50);
		this.xlsxTableWidth = integer(properties, "xlsx.table.width", 100, 10, 2000);
	}

	private static List<String> languages(String value) {
		if (value == null || value.isBlank()) {
			return List.of();
		}
		return List.of(value.strip().split("\\s*,\\s*"));
	}

	private static int integer(Properties properties, String name, int defaultValue, int min, int max) {
		String value = properties.getProperty(name);
		if (value == null) {
			return defaultValue;
		}
		try {
			int number = Integer.parseInt(value.strip());
			if (number >= min && number <= max) {
				return number;
			}
		} catch (NumberFormatException e) {
			// fall through invalid property exception
		}
		throw new IllegalArgumentException(String.format("Invalid project property %s=%s. Expected integer from %d to %d.", name, value, min, max));
	}
}
