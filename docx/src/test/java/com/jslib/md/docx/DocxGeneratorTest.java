package com.jslib.md.docx;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThrows;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DocxGeneratorTest {
    private static final String[] LANGUAGES = {"ro", "de"};

    private File sourceDir;

    @Before
    public void beforeTest() throws IOException {
        sourceDir = Files.createTempDirectory("DocxGeneratorTest").toFile();
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
    public void GivenDefaultLanguage_WhenSourceFiles_ThenExcludeTranslationsAndSortByName() throws IOException {
        // GIVEN
        String defaultLanguage = null;

        // WHEN
        File[] files = DocxGenerator.sourceFiles(sourceDir, defaultLanguage, LANGUAGES);

        // THEN
        List<String> names = names(files);
        assertThat(names, contains("01-intro.md", "02-install.md", "03-usage.md"));
    }

    @Test
    public void GivenLanguage_WhenSourceFiles_ThenIncludeOnlyLanguageFilesSortedByName() throws IOException {
        // GIVEN
        String language = "ro";

        // WHEN
        File[] files = DocxGenerator.sourceFiles(sourceDir, language, LANGUAGES);

        // THEN
        List<String> names = names(files);
        assertThat(names, contains("01-intro_ro.md", "02-install_ro.md", "03-usage_ro.md"));
    }

    @Test
    public void GivenMissingDirectory_WhenSourceFiles_ThenThrow() {
        // GIVEN
        File missingDirectory = new File(sourceDir, "missing");

        // WHEN
        IOException exception = assertThrows(IOException.class, () -> DocxGenerator.sourceFiles(missingDirectory, null, LANGUAGES));

        // THEN
        assertThat(exception.getMessage(), notNullValue());
    }

    private static List<String> names(File[] files) {
        return Arrays.stream(files).map(File::getName).collect(Collectors.toList());
    }
}
