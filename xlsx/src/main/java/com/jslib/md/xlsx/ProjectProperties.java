package com.jslib.md.xlsx;

import java.util.Properties;

public class ProjectProperties {
	public static final ProjectProperties empty() {
		return new ProjectProperties(new Properties());
	}

	public final String sourceDir;
	public final String fileName;

	public ProjectProperties(Properties properties) {
		this.sourceDir = property(properties, "source.dir", null);
		this.fileName = property(properties, "file.name", "document");
	}

	private static String property(Properties properties, String propertyName, String defaultValue) {
		String value = properties.getProperty(propertyName);
		return value != null ? value : defaultValue;
	}
}
