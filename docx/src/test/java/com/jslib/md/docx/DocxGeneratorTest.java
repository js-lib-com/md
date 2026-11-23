package com.jslib.md.docx;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DocxGeneratorTest {
	private static final String[] LANGUAGES = { "ro", "de" };

	private File sourceDir;

	@Before
	public void beforeTest() throws IOException {
		sourceDir = Files.createTempDirectory("DocxGeneratorTest").toFile();
		// created in reverse order to not rely on file system listing order
		for (String name : new String[] { "03-usage_ro.md", "03-usage.md", "02-install_de.md", "02-install_ro.md", "02-install.md", "01-intro_ro.md", "01-intro.md", "revisions.md", "revisions_ro.md", "notes.txt" }) {
			new File(sourceDir, name).createNewFile();
		}
	}

	@After
	public void afterTest() {
		for (File file : sourceDir.listFiles()) {
			file.delete();
		}
		sourceDir.delete();
	}

	@Test
	public void GivenDefaultLanguage_WhenSourceFiles_ThenExcludeTranslationsAndSortByName() throws IOException {
		// when
		List<String> names = names(DocxGenerator.sourceFiles(sourceDir, null, LANGUAGES));

		// then
		assertThat(names, contains("01-intro.md", "02-install.md", "03-usage.md"));
	}

	@Test
	public void GivenLanguage_WhenSourceFiles_ThenIncludeOnlyLanguageFilesSortedByName() throws IOException {
		// when
		List<String> names = names(DocxGenerator.sourceFiles(sourceDir, "ro", LANGUAGES));

		// then
		assertThat(names, contains("01-intro_ro.md", "02-install_ro.md", "03-usage_ro.md"));
	}

	@Test(expected = IOException.class)
	public void GivenMissingDirectory_WhenSourceFiles_ThenThrow() throws IOException {
		DocxGenerator.sourceFiles(new File(sourceDir, "missing"), null, LANGUAGES);
	}

	private static List<String> names(File[] files) {
		return Arrays.stream(files).map(File::getName).collect(Collectors.toList());
	}
}
