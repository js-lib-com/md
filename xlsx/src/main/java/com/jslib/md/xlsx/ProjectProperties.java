package com.jslib.md.xlsx;

import java.util.Properties;

public class ProjectProperties {

	public final String sourceDir;
	public final String fileName;
	/** Compute table columns width from the quantity of text in every column and wrap cells text. */
	public final boolean tableAutoFit;
	/** Minimal table column width, in percent, used when table auto-fit is enabled. */
	public final int tableColumnMinWidth;
	/** Percent of cells, from each end of sorted cell lengths, ignored when computing table column width. */
	public final int tableColumnOutliers;
	/** Total table width, in characters, shared by columns when table auto-fit is enabled. */
	public final int tableWidth;

	public ProjectProperties(Properties properties) {
		this.sourceDir = property(properties, "source.dir", null);
		this.fileName = property(properties, "file.name", "document");
		this.tableAutoFit = Boolean.parseBoolean(property(properties, "table.auto.fit", "false"));
		this.tableColumnMinWidth = property(properties, "table.column.min.width", 10, 0, 100);
		this.tableColumnOutliers = property(properties, "table.column.outliers", 20, 0, 50);
		this.tableWidth = property(properties, "xlsx.table.width", 100, 10, 2000);
	}

	private static String property(Properties properties, String propertyName, String defaultValue) {
		String value = properties.getProperty(propertyName);
		return value != null ? value : defaultValue;
	}

	private static int property(Properties properties, String propertyName, int defaultValue, int min, int max) {
		String value = properties.getProperty(propertyName);
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
		throw new IllegalArgumentException(String.format("Invalid project property %s=%s. Expected integer from %d to %d.", propertyName, value, min, max));
	}
}
