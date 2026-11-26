package com.jslib.md.xlsx.xml;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.poi.ooxml.POIXMLProperties;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Before;
import org.junit.Test;

public class WorkbookTest {
	private Workbook workbook;
	private Sheet sheet;

	@Before
	public void beforeTest() {
		workbook = new Workbook();
		sheet = workbook.createSheet("Sheet");
	}

	@Test
	public void GivenColumnAndRowIndexes_WhenReference_ThenUseExcelLetters() {
		// GIVEN
		int[][] indexes = { { 0, 0 }, { 25, 1 }, { 26, 2 }, { 701, 3 }, { 702, 4 } };

		// WHEN
		String[] references = new String[indexes.length];
		for (int i = 0; i < indexes.length; ++i) {
			references[i] = Workbook.reference(indexes[i][0], indexes[i][1]);
		}

		// THEN
		assertThat(references, equalTo(new String[] { "A1", "Z2", "AA3", "ZZ4", "AAA5" }));
	}

	@Test
	public void GivenControlCharacters_WhenWrite_ThenEscapeAndReadBack() throws IOException {
		// GIVEN
		sheet.createRow().createCell().setValue("a\u0001b\tc");

		// WHEN
		XSSFSheet read = reload().getSheet("Sheet");

		// THEN
		assertThat(Workbook.escapeControlChars("a\u0001b"), equalTo("a_x0001_b"));
		assertThat(read.getRow(0).getCell(0).getStringCellValue(), equalTo("a\u0001b\tc"));
	}

	@Test
	public void GivenRepeatedText_WhenWrite_ThenShareString() throws IOException {
		// GIVEN
		Row row = sheet.createRow();
		row.createCell().setValue("same");
		row.createCell().setValue("same");
		row.createCell().setValue(List.of(new TextRun("same", Font.DEFAULT.bold())));

		// WHEN
		String sharedStrings = part("xl/sharedStrings.xml");

		// THEN
		// plain and rich text with the same characters are different shared strings
		assertThat(sharedStrings.contains("uniqueCount=\"2\""), equalTo(true));
	}

	@Test
	public void GivenInvalidUri_WhenSetHyperlink_ThenThrow() {
		// GIVEN
		Cell cell = sheet.createRow().createCell();

		// WHEN
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> cell.setHyperlink("http://a b"));

		// THEN
		assertThat(exception.getMessage().startsWith("Address of hyperlink must be a valid URI"), equalTo(true));
	}

	@Test
	public void GivenExistingSheetNameWithOtherCase_WhenCreateSheet_ThenThrow() {
		// GIVEN
		String name = "SHEET";

		// WHEN
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> workbook.createSheet(name));

		// THEN
		assertThat(exception.getMessage().contains(name), equalTo(true));
	}

	@Test
	public void GivenLongerAndMultilineTexts_WhenAutoSizeColumn_ThenFitWidestLine() throws IOException {
		// GIVEN
		Row row = sheet.createRow();
		row.createCell().setValue("short");
		row.createCell().setValue("a much longer text");
		row.createCell().setValue("short\na much longer text");
		sheet.createRow().createCell();

		// WHEN
		for (int column = 0; column < 3; ++column) {
			sheet.autoSizeColumn(column);
		}
		sheet.autoSizeColumn(3);

		// THEN
		assertThat(sheet.getColumnWidth(1), greaterThan(sheet.getColumnWidth(0)));
		assertThat(sheet.getColumnWidth(2), equalTo(sheet.getColumnWidth(1)));
		// empty column is not sized
		assertThat(sheet.getColumnWidth(3), equalTo(-1));
		assertThat(reload().getSheet("Sheet").getColumnWidth(1), equalTo(sheet.getColumnWidth(1)));
	}

	@Test
	public void GivenCreationAndModificationTime_WhenWrite_ThenWriteDocumentProperties() throws IOException {
		// GIVEN
		workbook.setCreated(Instant.parse("2026-11-26T10:15:30.500Z"));
		workbook.setModified(Instant.parse("2026-11-27T08:00:00Z"));

		// WHEN
		POIXMLProperties properties = reload().getProperties();

		// THEN
		// time is stored with seconds precision
		assertThat(properties.getCoreProperties().getCreator(), equalTo("mdt"));
		assertThat(properties.getCoreProperties().getCreated().toInstant(), equalTo(Instant.parse("2026-11-26T10:15:30Z")));
		assertThat(properties.getCoreProperties().getModified().toInstant(), equalTo(Instant.parse("2026-11-27T08:00:00Z")));
		assertThat(properties.getExtendedProperties().getApplication(), equalTo("mdt"));
	}

	private XSSFWorkbook reload() throws IOException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		workbook.write(buffer);
		return new XSSFWorkbook(new ByteArrayInputStream(buffer.toByteArray()));
	}

	private String part(String name) throws IOException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		workbook.write(buffer);
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(buffer.toByteArray()))) {
			for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
				if (entry.getName().equals(name)) {
					return new String(zip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
				}
			}
		}
		throw new IOException("Missing part " + name);
	}
}
