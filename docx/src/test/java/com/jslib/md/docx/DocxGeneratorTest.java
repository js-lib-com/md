package com.jslib.md.docx;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThrows;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DocxGeneratorTest {
	private File documentDir;

	@Before
	public void beforeTest() throws IOException {
		documentDir = Files.createTempDirectory("DocxGeneratorTest").toFile();
	}

	@After
	@SuppressWarnings("ResultOfMethodCallIgnored")
	public void afterTest() throws IOException {
		try (Stream<Path> paths = Files.walk(documentDir.toPath())) {
			paths.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
		}
	}

	@Test
	public void GivenSourceDirAndLanguage_WhenExecute_ThenGenerateDocumentPerLanguageFromChapters() throws Exception {
		// GIVEN
		write("project.properties", "file.name=manual\nsource.dir=chapters\nlanguages=ro\n");
		write("document.properties", "document.title=Manual\n");
		write("revisions.md", "|Author|Date|Description|\n|-|-|-|\n|A|2026-11-26|Create.|\n");
		write("chapters/02-usage.md", "# Usage");
		write("chapters/01-intro.md", "# Intro");
		write("chapters/01-intro_ro.md", "# Introducere");

		// WHEN
		new DocxGenerator().execute(documentDir);

		// THEN
		String text = text("manual.docx");
		assertThat(text.indexOf("Intro") < text.indexOf("Usage"), equalTo(true));
		assertThat(text, not(containsString("Introducere")));
		String translation = text("manual_ro.docx");
		assertThat(translation, containsString("Introducere"));
		assertThat(translation, not(containsString("Usage")));
	}

	@Test
	public void GivenOnlyMarkdownFiles_WhenExecute_ThenMergeFilesInNaturalOrderWithInferredProperties() throws Exception {
		// GIVEN
		write("10-usage.md", "# Usage");
		write("2-intro.md", "# Intro");

		// WHEN
		new DocxGenerator().execute(documentDir);

		// THEN
		String text = text("document.docx");
		assertThat(text.indexOf("Intro") < text.indexOf("Usage"), equalTo(true));
		assertThat(text, not(containsString("document.title")));
		assertThat(text, not(containsString("company.name")));
	}

	@Test
	public void GivenNoMarkdownFiles_WhenExecute_ThenThrowMissingDocument() {
		// WHEN
		FileNotFoundException exception = assertThrows(FileNotFoundException.class, () -> new DocxGenerator().execute(documentDir));

		// THEN
		assertThat(exception.getMessage(), containsString("document.md"));
	}

	@SuppressWarnings("ResultOfMethodCallIgnored")
	private void write(String name, String content) throws IOException {
		File file = new File(documentDir, name);
		file.getParentFile().mkdirs();
		Files.writeString(file.toPath(), content);
	}

	private String text(String name) throws IOException {
		try (FileInputStream stream = new FileInputStream(new File(documentDir, name))) {
			XWPFDocument document = new XWPFDocument(stream);
			return document.getParagraphs().stream().map(XWPFParagraph::getText).collect(Collectors.joining("\n"));
		}
	}
}
