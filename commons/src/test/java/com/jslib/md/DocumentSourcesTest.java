package com.jslib.md;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThrows;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DocumentSourcesTest {
	private static final List<String> LANGUAGES = List.of("ro", "de");

	private File sourceDir;

	@Before
	public void beforeTest() throws IOException {
		sourceDir = Files.createTempDirectory("DocumentSourcesTest").toFile();
		// created in reverse order to not rely on file system listing order
		for (String name : new String[]{"03-usage_ro.md", "03-usage.md", "02-install_de.md", "02-install_ro.md", "02-install.md", "01-intro_ro.md", "01-intro.md", "revisions.md", "revisions_ro.md", "notes.txt"}) {
			File file = new File(sourceDir, name);
			if (!file.createNewFile()) {
				throw new IOException("Could not create file " + name);
			}
		}
	}

	@After
	@SuppressWarnings("ResultOfMethodCallIgnored")
	public void afterTest() {
		for (File file : Objects.requireNonNull(sourceDir.listFiles())) {
			file.delete();
		}
		sourceDir.delete();
	}

	@Test
	public void GivenDefaultLanguage_WhenChapters_ThenExcludeTranslationsAndSortByName() throws IOException {
		// GIVEN
		String defaultLanguage = null;

		// WHEN
		List<File> files = DocumentSources.chapters(sourceDir, defaultLanguage, LANGUAGES);

		// THEN
		List<String> names = names(files);
		assertThat(names, contains("01-intro.md", "02-install.md", "03-usage.md"));
	}

	@Test
	public void GivenLanguage_WhenChapters_ThenIncludeOnlyLanguageFilesSortedByName() throws IOException {
		// GIVEN
		String language = "ro";

		// WHEN
		List<File> files = DocumentSources.chapters(sourceDir, language, LANGUAGES);

		// THEN
		List<String> names = names(files);
		assertThat(names, contains("01-intro_ro.md", "02-install_ro.md", "03-usage_ro.md"));
	}

	@Test
	public void GivenMissingDirectory_WhenChapters_ThenThrow() {
		// GIVEN
		File missingDirectory = new File(sourceDir, "missing");

		// WHEN
		IOException exception = assertThrows(IOException.class, () -> DocumentSources.chapters(missingDirectory, null, LANGUAGES));

		// THEN
		assertThat(exception.getMessage(), notNullValue());
	}

	@Test
	public void GivenFileNamesAndLanguage_WhenLocalize_ThenInsertLanguageBeforeExtension() {
		assertThat(DocumentSources.localize("document.md", "ro"), equalTo("document_ro.md"));
		assertThat(DocumentSources.localize("document.properties", "ro"), equalTo("document_ro.properties"));
		assertThat(DocumentSources.localize("README", "ro"), equalTo("README_ro"));
		assertThat(DocumentSources.localize("document.md", null), equalTo("document.md"));
		assertThat(DocumentSources.localize(new File("work.v1/revisions.md"), "ro"), equalTo(new File("work.v1/revisions_ro.md")));
	}

	@Test
	public void GivenTranslatedRevisionsOnly_WhenResolveLanguageSources_ThenFallBackToDefaultLanguageFiles() throws IOException {
		// GIVEN
		DocumentSources sources = new DocumentSources(sourceDir, ProjectProperties.empty(), "ro");

		// WHEN
		File revisions = sources.revisions();
		File documentProperties = sources.documentProperties();
		List<File> markdownSources = sources.markdownSources();

		// THEN
		assertThat(revisions.getName(), equalTo("revisions_ro.md"));
		assertThat(documentProperties.getName(), equalTo("document.properties"));
		assertThat(names(markdownSources), contains("document_ro.md"));
	}

	@Test
	public void GivenSourceDir_WhenTranslationSources_ThenListChaptersRevisionsAndDocumentProperties() throws IOException {
		// GIVEN
		Properties properties = new Properties();
		properties.setProperty("source.dir", ".");
		properties.setProperty("languages", "ro, de");
		DocumentSources sources = new DocumentSources(sourceDir, new ProjectProperties(properties));

		// WHEN
		List<File> files = sources.translationSources();

		// THEN
		assertThat(names(files), contains("01-intro.md", "02-install.md", "03-usage.md", "revisions.md", "document.properties"));
	}

	private static List<String> names(List<File> files) {
		return files.stream().map(File::getName).collect(Collectors.toList());
	}
}
