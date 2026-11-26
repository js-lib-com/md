package com.jslib.md.docx;

import java.util.Properties;

public class ProjectProperties {
	public static ProjectProperties empty() {
		return new ProjectProperties(new Properties());
	}

	public final String sourceDir;
	public final String[] languages;
	public final String fileName;
	public final boolean imageShadow;
	public final boolean linkEnabled;
	/** Compute table columns width from the quantity of text in every column. */
	public final boolean tableAutoFit;
	/** Minimal table column width, in percent, used when table auto-fit is enabled. */
	public final int tableColumnMinWidth;
	/** Percent of cells, from each end of sorted cell lengths, ignored when computing table column width. */
	public final int tableColumnOutliers;

	public ProjectProperties(Properties properties) {
		this.sourceDir = property(properties, "source.dir", null);
		this.languages = property(properties, new String[] {});
		this.fileName = property(properties, "file.name", "document");
		this.imageShadow = property(properties, "image.shadow");
		this.linkEnabled = property(properties, "link.enabled");
		this.tableAutoFit = property(properties, "table.auto.fit");
		this.tableColumnMinWidth = property(properties, "table.column.min.width", 10, 100);
		this.tableColumnOutliers = property(properties, "table.column.outliers", 20, 50);
	}

	private static String property(Properties properties, String propertyName, String defaultValue) {
		String value = properties.getProperty(propertyName);
		return value != null ? value : defaultValue;
	}

	private static boolean property(Properties properties, String propertyName) {
		String value = properties.getProperty(propertyName);
		return Boolean.parseBoolean(value);
	}

	private static int property(Properties properties, String propertyName, int defaultValue, int max) {
		String value = properties.getProperty(propertyName);
		if (value == null) {
			return defaultValue;
		}
		try {
			int number = Integer.parseInt(value.strip());
			if (number >= 0 && number <= max) {
				return number;
			}
		} catch (NumberFormatException e) {
			// fall through invalid property exception
		}
		throw new IllegalArgumentException(String.format("Invalid project property %s=%s. Expected integer from %d to %d.", propertyName, value, 0, max));
	}

	private static String[] property(Properties properties, String[] defaultValue) {
		String value = properties.getProperty("languages");
		return value != null ? value.split("\\s*,\\s*") : defaultValue;
	}
}
