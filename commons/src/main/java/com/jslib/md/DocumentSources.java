package com.jslib.md;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Source files of a document directory, for the default language or a translation language. This class is the single
 * place that knows the document directory layout:
 * <ul>
 * <li><code>document.properties</code> - document properties, e.g. title and author,
 * <li><code>revisions.md</code> - revisions table,
 * <li><code>document.md</code> - document source, if neither <code>source.dir</code> nor <code>source.files</code>
 * project properties are set,
 * <li><code>source.dir/*.md</code> - chapter sources, merged in natural file name order, if <code>source.dir</code> is set,
 * <li><code>source.files</code> - chapter sources, merged in listed order, relative to <code>source.dir</code> if set,
 * otherwise to document directory.
 * </ul>
 * Translations have a language suffix before extension, e.g. <code>document_ro.md</code>. For a translation language,
 * document properties and revisions fall back to default language files if not translated.
 */
public final class DocumentSources {
	public static final String DOCUMENT_PROPERTIES = "document.properties";
	public static final String REVISIONS = "revisions.md";
	public static final String DOCUMENT = "document.md";

	private final File documentDir;
	private final ProjectProperties projectProperties;
	/** Language suffix, null for default language. */
	private final String language;

	/** Sources for default language. */
	public DocumentSources(File documentDir, ProjectProperties projectProperties) {
		this(documentDir, projectProperties, null);
	}

	/** Sources for given translation language, or default language if language is null. */
	public DocumentSources(File documentDir, ProjectProperties projectProperties, String language) {
		this.documentDir = documentDir;
		this.projectProperties = projectProperties;
		this.language = language;
	}

	public String language() {
		return language;
	}

	/** Directory with markdown sources: <code>source.dir</code> if set, otherwise document directory. */
	public File sourceDir() {
		return projectProperties.sourceDir != null ? new File(documentDir, projectProperties.sourceDir) : documentDir;
	}

	/** Document properties file for language, falling back to default language file; may not exist. */
	public File documentProperties() {
		return withFallback(DOCUMENT_PROPERTIES);
	}

	/** Revisions file for language, falling back to default language file; may not exist. */
	public File revisions() {
		return withFallback(REVISIONS);
	}

	/**
	 * Markdown document sources for language: files from <code>source.files</code>, in listed order, if set; otherwise
	 * chapter files from <code>source.dir</code>, sorted by natural name order, if set; otherwise the single document file. Listed
	 * files and the single document file may not exist.
	 */
	public List<File> markdownSources() throws IOException {
		if (!projectProperties.sourceFiles.isEmpty()) {
			File sourceDir = sourceDir();
			return projectProperties.sourceFiles.stream().map(name -> localize(new File(sourceDir, name), language)).collect(Collectors.toList());
		}
		if (projectProperties.sourceDir == null) {
			return List.of(new File(documentDir, localize(DOCUMENT, language)));
		}
		return chapters(sourceDir(), language, projectProperties.languages);
	}

	/**
	 * Markdown files for language from source directory that are not listed by <code>source.files</code>, sorted by
	 * name, so that caller can warn about chapters left out; empty if <code>source.files</code> is not set.
	 */
	public List<File> unlistedSources() throws IOException {
		if (projectProperties.sourceFiles.isEmpty()) {
			return List.of();
		}
		List<Path> listed = markdownSources().stream().map(DocumentSources::normalize).collect(Collectors.toList());
		return chapters(sourceDir(), language, projectProperties.languages).stream() //
				.filter(file -> !listed.contains(normalize(file))) //
				.collect(Collectors.toList());
	}

	/**
	 * Default language files to translate: markdown sources, revisions and document properties; missing files are
	 * included so that caller can report them.
	 */
	public List<File> translationSources() throws IOException {
		if (language != null) {
			throw new IllegalStateException("Translation sources are in default language.");
		}
		List<File> files = new ArrayList<>(markdownSources());
		files.add(revisions());
		files.add(documentProperties());
		return files;
	}

	/**
	 * Markdown chapter files for requested language, sorted by natural name order; revisions files are excluded. For default language,
	 * i.e. null language argument, exclude files translated to any of the configured languages.
	 */
	static List<File> chapters(File sourceDir, String language, List<String> languages) throws IOException {
		File[] files = sourceDir.listFiles(file -> {
			String name = file.getName();
			if (!name.endsWith(".md") || name.startsWith("revisions")) {
				return false;
			}
			if (language != null) {
				return name.endsWith('_' + language + ".md");
			}
			return languages.stream().noneMatch(translation -> name.endsWith('_' + translation + ".md"));
		});
		if (files == null) {
			throw new IOException("Fail to list source directory " + sourceDir);
		}
		Arrays.sort(files, NaturalOrder.FILES);
		return List.of(files);
	}

	/**
	 * Localized file name, e.g. <code>document.md</code> to <code>document_ro.md</code>; name is unchanged for null
	 * language.
	 */
	public static String localize(String fileName, String language) {
		if (language == null) {
			return fileName;
		}
		int extensionPosition = fileName.lastIndexOf('.');
		return extensionPosition > 0 //
				? fileName.substring(0, extensionPosition) + '_' + language + fileName.substring(extensionPosition)
				: fileName + '_' + language;
	}

	/** Localized file, beside given file, e.g. <code>dir/document.md</code> to <code>dir/document_ro.md</code>. */
	public static File localize(File file, String language) {
		return new File(file.getParentFile(), localize(file.getName(), language));
	}

	private static Path normalize(File file) {
		return file.toPath().toAbsolutePath().normalize();
	}

	private File withFallback(String fileName) {
		File file = new File(documentDir, localize(fileName, language));
		return file.exists() ? file : new File(documentDir, fileName);
	}
}
