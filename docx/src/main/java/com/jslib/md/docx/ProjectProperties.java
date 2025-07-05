package com.jslib.md.docx;

import java.util.Properties;

public class ProjectProperties {
	public static final ProjectProperties empty() {
		return new ProjectProperties(new Properties());
	}

	public final String sourceDir;
	public final String fileName;
	public final boolean imageShadow;
	public final boolean linkEnabled;

	public ProjectProperties(Properties properties) {
		this.sourceDir = property(properties, "source.dir", null);
		this.fileName = property(properties, "file.name", "document");
		this.imageShadow = property(properties, "image.shadow", false);
		this.linkEnabled = property(properties, "link.enabled", false);
	}

	private static String property(Properties properties, String propertyName, String defaultValue) {
		String value = properties.getProperty(propertyName);
		return value != null ? value : defaultValue;
	}

	private static boolean property(Properties properties, String propertyName, boolean defaultValue) {
		String value = properties.getProperty(propertyName);
		return value != null ? Boolean.parseBoolean(value) : defaultValue;
	}
}
