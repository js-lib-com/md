package com.jslib.md;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;
import java.util.Properties;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DocumentDefaultsTest {
	private static final LocalDate TODAY = LocalDate.of(2026, 11, 27);

	private File documentDir;

	@Before
	public void beforeTest() throws IOException {
		documentDir = new File(Files.createTempDirectory("DocumentDefaultsTest").toFile(), "user-guide_v2");
		if (!documentDir.mkdir()) {
			throw new IOException("Could not create document directory");
		}
	}

	@After
	@SuppressWarnings("ResultOfMethodCallIgnored")
	public void afterTest() {
		File parent = documentDir.getParentFile();
		documentDir.delete();
		parent.delete();
	}

	@Test
	public void GivenNoPropertiesAndNoRevisions_WhenApply_ThenInferFromDirectoryUserAndToday() throws IOException {
		// WHEN
		Properties properties = DocumentDefaults.apply(new Properties(), documentDir, List.of(), "jdoe", TODAY);

		// THEN
		assertThat(properties.getProperty("document.title"), equalTo("User Guide V2"));
		assertThat(properties.getProperty("document.author"), equalTo("jdoe"));
		assertThat(properties.getProperty("document.date"), equalTo("2026-11-27"));
		assertThat(properties.getProperty("document.subtitle"), nullValue());
	}

	@Test
	public void GivenRevisions_WhenApply_ThenAuthorAndDateFromNewestRevision() throws IOException {
		// GIVEN
		List<List<String>> revisions = DocumentDefaults.revisions("|Author|Date|Description|\n|-|-|-|\n|Ann|2026-10-01|Create.|\n|**Bob**|2026-11-02|Update.|\n");

		// WHEN
		Properties properties = DocumentDefaults.apply(new Properties(), documentDir, revisions, "jdoe", TODAY);

		// THEN
		assertThat(properties.getProperty("document.author"), equalTo("Bob"));
		assertThat(properties.getProperty("document.date"), equalTo("2026-11-02"));
	}

	@Test
	public void GivenDocumentProperties_WhenApply_ThenKeepThemAndDoNotChangeSource() throws IOException {
		// GIVEN
		Properties source = new Properties();
		source.setProperty("document.title", "Manual");
		source.setProperty("document.author", "Ann");
		source.setProperty("document.date", "November 2026");
		source.setProperty("company.name", "ACME");

		// WHEN
		Properties properties = DocumentDefaults.apply(source, documentDir, List.of(List.of("Bob", "2026-11-02", "Update.")), "jdoe", TODAY);

		// THEN
		assertThat(properties, equalTo(source));
		assertThat(source.size(), equalTo(4));
	}

	@Test
	public void GivenBlankPropertiesAndBlankRevisionCells_WhenApply_ThenFallBack() throws IOException {
		// GIVEN
		Properties source = new Properties();
		source.setProperty("document.title", "  ");

		// WHEN
		Properties properties = DocumentDefaults.apply(source, documentDir, List.of(List.of(" ", "")), "jdoe", TODAY);

		// THEN
		assertThat(properties.getProperty("document.title"), equalTo("User Guide V2"));
		assertThat(properties.getProperty("document.author"), equalTo("jdoe"));
		assertThat(properties.getProperty("document.date"), equalTo("2026-11-27"));
	}

	@Test
	public void GivenNoUserName_WhenApply_ThenNoAuthor() throws IOException {
		// WHEN
		Properties properties = DocumentDefaults.apply(new Properties(), documentDir, List.of(), null, TODAY);

		// THEN
		assertThat(properties.getProperty("document.author"), nullValue());
	}

	@Test
	public void GivenDirectoryNames_WhenTitle_ThenCapitalizeWords() {
		assertThat(DocumentDefaults.title("user-guide"), equalTo("User Guide"));
		assertThat(DocumentDefaults.title("api_reference  manual"), equalTo("Api Reference Manual"));
		assertThat(DocumentDefaults.title("iOS"), equalTo("IOS"));
		assertThat(DocumentDefaults.title("ghid-utilizare-ștampilă"), equalTo("Ghid Utilizare Ștampilă"));
		assertThat(DocumentDefaults.title("--"), equalTo("Document"));
		assertThat(DocumentDefaults.title(""), equalTo("Document"));
	}

	@Test
	public void GivenMarkdownWithoutTable_WhenRevisions_ThenEmpty() {
		assertThat(DocumentDefaults.revisions("# Revisions\n\nNone yet.\n"), empty());
	}

	@Test
	@SuppressWarnings("unchecked")
	public void GivenRevisionsTable_WhenRevisions_ThenBodyRowsOnly() {
		List<List<String>> rows = DocumentDefaults.revisions("|Author|Date|Description|\n|-|-|-|\n|Ann|2026-10-01|`v1` created|\n");
		assertThat(rows, contains(List.of("Ann", "2026-10-01", "v1 created")));
	}
}
