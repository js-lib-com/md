package com.jslib.md;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.util.Properties;

/** UTF-8 properties files, as used by document and project properties. */
public final class PropertiesFile {
	private PropertiesFile() {
	}

	/** Load properties file; throw {@link FileNotFoundException} if file is missing. */
	public static Properties load(File file) throws IOException {
		if (!file.isFile()) {
			throw new FileNotFoundException("Missing properties file " + file);
		}
		Properties properties = new Properties();
		try (Reader reader = Files.newBufferedReader(file.toPath(), UTF_8)) {
			properties.load(reader);
		}
		return properties;
	}

	/** Load properties file; return empty properties if file is missing. */
	public static Properties loadOptional(File file) throws IOException {
		return file.isFile() ? load(file) : new Properties();
	}

	/** Property value, stripped of surrounding white spaces, or null if property is missing or blank. */
	public static String value(Properties properties, String name) {
		String value = properties.getProperty(name, "").strip();
		return value.isEmpty() ? null : value;
	}
}
