package com.jslib.md.xlsx;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThrows;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.poi.ooxml.POIXMLProperties;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class XlsxGeneratorFilesTest {
	private static final String TABLE = "|Name|Value|\n|-|-|\n|a|1|\n";
	private static final String LONG_NAME = "a-markdown-file-name-longer-than-excel-limit";

	private File documentDir;

	@Before
	public void beforeTest() throws IOException {
		documentDir = Files.createTempDirectory("XlsxGeneratorFilesTest").toFile();
	}

	@After
	@SuppressWarnings("ResultOfMethodCallIgnored")
	public void afterTest() {
		for (File file : Objects.requireNonNull(documentDir.listFiles())) {
			file.delete();
		}
		documentDir.delete();
	}

	@Test
	public void GivenFormattedAndEmptyCells_WhenExecute_ThenWritePlainTextAndBlankCells() throws Exception {
		// GIVEN
		write("table.md", "|Name|Description|Note|\n|-|-|-|\n|**Bold** name|See [site](https://x.org) and `code`||\n");

		// WHEN
		Row row;
		try (Workbook workbook = execute()) {
			row = workbook.getSheet("table").getRow(1);
		}

		// THEN
		assertThat(row.getCell(0).getStringCellValue(), equalTo("Bold name"));
		assertThat(row.getCell(1).getStringCellValue(), equalTo("See site and code"));
		assertThat(row.getCell(2).getCellType(), equalTo(CellType.BLANK));
	}

	@Test
	public void GivenFileWithoutTable_WhenExecute_ThenSkipFile() throws Exception {
		// GIVEN
		write("notes.md", "# Notes\n\nNo table here.\n");
		write("table.md", TABLE);

		// WHEN
		Workbook workbook = execute();

		// THEN
		assertThat(sheetNames(workbook), contains("table"));
	}

	@Test
	public void GivenLongAndInvalidFileNames_WhenExecute_ThenCreateValidUniqueSheetNamesInFileOrder() throws Exception {
		// GIVEN
		write("c[1].md", TABLE);
		write(LONG_NAME + "-two.md", TABLE);
		write(LONG_NAME + "-one.md", TABLE);

		// WHEN
		Workbook workbook = execute();

		// THEN
		String truncated = LONG_NAME.substring(0, 31);
		assertThat(sheetNames(workbook), contains(truncated, truncated.substring(0, 27) + " (2)", "c_1_"));
	}

	@Test
	public void GivenNamesWithApostropheAndForbiddenChars_WhenSafeSheetName_ThenReplaceByUnderscore() {
		// GIVEN
		String[] names = { "'quoted'", "it's", "a/b\\c?d*e[f]g:h", "" };

		// WHEN
		List<String> safeNames = new ArrayList<>();
		for (String name : names) {
			safeNames.add(XlsxGenerator.safeSheetName(name));
		}

		// THEN
		// apostrophe is allowed only inside name
		assertThat(safeNames, contains("_quoted_", "it's", "a_b_c_d_e_f_g_h", "empty"));
	}

	@Test
	public void GivenDocumentAuthor_WhenExecute_ThenWriteItAsWorkbookCreatorAndLastModifiedBy() throws Exception {
		// GIVEN
		write("document.properties", "document.author=R&D <team> \"Iași\"\n");
		write("table.md", TABLE);

		// WHEN
		POIXMLProperties.CoreProperties properties = coreProperties(execute());

		// THEN
		assertThat(properties.getCreator(), equalTo("R&D <team> \"Iași\""));
		assertThat(properties.getLastModifiedByUser(), equalTo("R&D <team> \"Iași\""));
	}

	@Test
	public void GivenMissingOrBlankDocumentAuthor_WhenExecute_ThenUseApplicationName() throws Exception {
		// GIVEN
		write("table.md", TABLE);
		POIXMLProperties.CoreProperties missing = coreProperties(execute());
		write("document.properties", "document.author=  \n");

		// WHEN
		POIXMLProperties.CoreProperties blank = coreProperties(execute());

		// THEN
		assertThat(missing.getCreator(), equalTo("mdt"));
		assertThat(missing.getLastModifiedByUser(), equalTo("mdt"));
		assertThat(blank.getCreator(), equalTo("mdt"));
		assertThat(blank.getLastModifiedByUser(), equalTo("mdt"));
	}

	@Test
	public void GivenNotIntegerDocumentRevision_WhenExecute_ThenThrowAndWriteNoFile() throws Exception {
		// GIVEN
		// Excel does not open workbooks with these revision values
		String[] revisions = { "2b", "-1", "1.5" };
		write("table.md", TABLE);

		for (String revision : revisions) {
			write("document.properties", "document.revision=" + revision + "\n");

			// WHEN
			IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new XlsxGenerator().execute(documentDir));

			// THEN
			assertThat(exception.getMessage(), equalTo("Invalid document property document.revision=" + revision + ". Expected non-negative integer."));
			assertThat(new File(documentDir, "document.xlsx").exists(), equalTo(false));
		}
	}

	@Test
	public void GivenIsoDocumentDates_WhenParseDate_ThenReturnInstant() {
		// GIVEN
		ZoneId zone = ZoneId.systemDefault();

		// WHEN
		Instant date = XlsxGenerator.parseDate("2026-11-26");
		Instant localDateTime = XlsxGenerator.parseDate("2026-11-26T10:15:30");
		Instant offsetDateTime = XlsxGenerator.parseDate("2026-11-26T10:15:30+02:00");

		// THEN
		// date and date-time without offset are in system time zone
		assertThat(date, equalTo(LocalDate.of(2026, 11, 26).atStartOfDay(zone).toInstant()));
		assertThat(localDateTime, equalTo(LocalDateTime.of(2026, 11, 26, 10, 15, 30).atZone(zone).toInstant()));
		assertThat(offsetDateTime, equalTo(Instant.parse("2026-11-26T08:15:30Z")));
	}

	@Test
	public void GivenDocumentDate_WhenExecute_ThenWriteItAsCreationTimeAndKeepModificationTime() throws Exception {
		// GIVEN
		write("document.properties", "document.date=2025-03-14T09:26:53+02:00\n");
		write("table.md", TABLE);
		Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);

		// WHEN
		POIXMLProperties.CoreProperties properties = coreProperties(execute());

		// THEN
		assertThat(properties.getCreated().toInstant(), equalTo(Instant.parse("2025-03-14T07:26:53Z")));
		// modification time is generation time
		assertThat(properties.getModified().toInstant().isBefore(before), equalTo(false));
	}

	@Test
	public void GivenFreeTextOrMissingDocumentDate_WhenExecute_ThenUseGenerationTime() throws Exception {
		// GIVEN
		// free text date is valid for DOCX templates and does not fail generation
		write("table.md", TABLE);
		Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		POIXMLProperties.CoreProperties missing = coreProperties(execute());
		write("document.properties", "document.date=26 November 2026\n");

		// WHEN
		POIXMLProperties.CoreProperties freeText = coreProperties(execute());

		// THEN
		assertThat(missing.getCreated().toInstant().isBefore(before), equalTo(false));
		assertThat(freeText.getCreated().toInstant().isBefore(before), equalTo(false));
	}

	private static POIXMLProperties.CoreProperties coreProperties(Workbook workbook) {
		return ((XSSFWorkbook) workbook).getProperties().getCoreProperties();
	}

	private void write(String fileName, String content) throws IOException {
		Files.write(new File(documentDir, fileName).toPath(), content.getBytes(StandardCharsets.UTF_8));
	}

	private Workbook execute() throws Exception {
		new XlsxGenerator().execute(documentDir);
		try (FileInputStream stream = new FileInputStream(new File(documentDir, "document.xlsx"))) {
			return new XSSFWorkbook(stream);
		}
	}

	private static List<String> sheetNames(Workbook workbook) {
		List<String> names = new ArrayList<>();
		for (Sheet sheet : workbook) {
			names.add(sheet.getSheetName());
		}
		return names;
	}
}
