package com.jslib.md.xlsx;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
	public void GivenDocumentAuthor_WhenExecute_ThenWriteItAsWorkbookCreator() throws Exception {
		// GIVEN
		write("document.properties", "document.author=R&D <team> \"Iași\"\n");
		write("table.md", TABLE);

		// WHEN
		String creator = creator(execute());

		// THEN
		assertThat(creator, equalTo("R&D <team> \"Iași\""));
	}

	@Test
	public void GivenMissingOrBlankDocumentAuthor_WhenExecute_ThenUseApplicationName() throws Exception {
		// GIVEN
		write("table.md", TABLE);
		String missingCreator = creator(execute());
		write("document.properties", "document.author=  \n");

		// WHEN
		String blankCreator = creator(execute());

		// THEN
		assertThat(missingCreator, equalTo("mdt"));
		assertThat(blankCreator, equalTo("mdt"));
	}

	@Test
	public void GivenDocumentTitle_WhenExecute_ThenWriteItAsWorkbookTitle() throws Exception {
		// GIVEN
		write("document.properties", "document.title=Tables & <Fields> \"Iași\"\n");
		write("table.md", TABLE);

		// WHEN
		String title = coreProperties(execute()).getTitle();

		// THEN
		assertThat(title, equalTo("Tables & <Fields> \"Iași\""));
	}

	@Test
	public void GivenMissingOrBlankDocumentTitle_WhenExecute_ThenWriteNoTitle() throws Exception {
		// GIVEN
		write("table.md", TABLE);
		String missingTitle = coreProperties(execute()).getTitle();
		write("document.properties", "document.title=  \n");

		// WHEN
		String blankTitle = coreProperties(execute()).getTitle();

		// THEN
		assertThat(missingTitle, nullValue());
		assertThat(blankTitle, nullValue());
	}

	@Test
	public void GivenDocumentSubject_WhenExecute_ThenWriteItAsWorkbookSubject() throws Exception {
		// GIVEN
		write("document.properties", "document.subject=Work orders & <tasks> \"Iași\"\n");
		write("table.md", TABLE);

		// WHEN
		String subject = coreProperties(execute()).getSubject();

		// THEN
		assertThat(subject, equalTo("Work orders & <tasks> \"Iași\""));
	}

	@Test
	public void GivenMissingOrBlankDocumentSubject_WhenExecute_ThenWriteNoSubject() throws Exception {
		// GIVEN
		write("table.md", TABLE);
		String missingSubject = coreProperties(execute()).getSubject();
		write("document.properties", "document.subject=  \n");

		// WHEN
		String blankSubject = coreProperties(execute()).getSubject();

		// THEN
		assertThat(missingSubject, nullValue());
		assertThat(blankSubject, nullValue());
	}

	@Test
	public void GivenDocumentKeywords_WhenExecute_ThenWriteThemAsWorkbookKeywords() throws Exception {
		// GIVEN
		write("document.properties", "document.keywords=fields, R&D, <tasks>, Iași\n");
		write("table.md", TABLE);

		// WHEN
		String keywords = coreProperties(execute()).getKeywords();

		// THEN
		assertThat(keywords, equalTo("fields, R&D, <tasks>, Iași"));
	}

	@Test
	public void GivenMissingOrBlankDocumentKeywords_WhenExecute_ThenWriteNoKeywords() throws Exception {
		// GIVEN
		write("table.md", TABLE);
		String missingKeywords = coreProperties(execute()).getKeywords();
		write("document.properties", "document.keywords=  \n");

		// WHEN
		String blankKeywords = coreProperties(execute()).getKeywords();

		// THEN
		assertThat(missingKeywords, nullValue());
		assertThat(blankKeywords, nullValue());
	}

	@Test
	public void GivenDocumentCategory_WhenExecute_ThenWriteItAsWorkbookCategory() throws Exception {
		// GIVEN
		write("document.properties", "document.category=R&D <reference> \"Iași\"\n");
		write("table.md", TABLE);

		// WHEN
		String category = coreProperties(execute()).getCategory();

		// THEN
		assertThat(category, equalTo("R&D <reference> \"Iași\""));
	}

	@Test
	public void GivenMissingOrBlankDocumentCategory_WhenExecute_ThenWriteNoCategory() throws Exception {
		// GIVEN
		write("table.md", TABLE);
		String missingCategory = coreProperties(execute()).getCategory();
		write("document.properties", "document.category=  \n");

		// WHEN
		String blankCategory = coreProperties(execute()).getCategory();

		// THEN
		assertThat(missingCategory, nullValue());
		assertThat(blankCategory, nullValue());
	}

	@Test
	public void GivenDocumentComments_WhenExecute_ThenWriteThemAsWorkbookDescription() throws Exception {
		// GIVEN
		write("document.properties", "document.comments=Generated for R&D <team> \"Iași\"\n");
		write("table.md", TABLE);

		// WHEN
		String description = coreProperties(execute()).getDescription();

		// THEN
		assertThat(description, equalTo("Generated for R&D <team> \"Iași\""));
	}

	@Test
	public void GivenMissingOrBlankDocumentComments_WhenExecute_ThenWriteNoDescription() throws Exception {
		// GIVEN
		write("table.md", TABLE);
		String missingDescription = coreProperties(execute()).getDescription();
		write("document.properties", "document.comments=  \n");

		// WHEN
		String blankDescription = coreProperties(execute()).getDescription();

		// THEN
		assertThat(missingDescription, nullValue());
		assertThat(blankDescription, nullValue());
	}

	@Test
	public void GivenDocumentCompany_WhenExecute_ThenWriteItAsWorkbookCompany() throws Exception {
		// GIVEN
		write("document.properties", "document.company=R&D <Grid> \"Iași\"\n");
		write("table.md", TABLE);

		// WHEN
		String company = extendedProperties(execute()).getCompany();

		// THEN
		assertThat(company, equalTo("R&D <Grid> \"Iași\""));
	}

	@Test
	public void GivenMissingOrBlankDocumentCompany_WhenExecute_ThenWriteNoCompany() throws Exception {
		// GIVEN
		write("table.md", TABLE);
		String missingCompany = extendedProperties(execute()).getCompany();
		write("document.properties", "document.company=  \n");

		// WHEN
		String blankCompany = extendedProperties(execute()).getCompany();

		// THEN
		assertThat(missingCompany, nullValue());
		assertThat(blankCompany, nullValue());
	}

	@Test
	public void GivenDocumentManager_WhenExecute_ThenWriteItAsWorkbookManager() throws Exception {
		// GIVEN
		write("document.properties", "document.manager=R&D <lead> \"Iași\"\n");
		write("table.md", TABLE);

		// WHEN
		String manager = extendedProperties(execute()).getManager();

		// THEN
		assertThat(manager, equalTo("R&D <lead> \"Iași\""));
	}

	@Test
	public void GivenMissingOrBlankDocumentManager_WhenExecute_ThenWriteNoManager() throws Exception {
		// GIVEN
		write("table.md", TABLE);
		String missingManager = extendedProperties(execute()).getManager();
		write("document.properties", "document.manager=  \n");

		// WHEN
		String blankManager = extendedProperties(execute()).getManager();

		// THEN
		assertThat(missingManager, nullValue());
		assertThat(blankManager, nullValue());
	}

	@Test
	public void GivenDocumentStatus_WhenExecute_ThenWriteItAsWorkbookContentStatus() throws Exception {
		// GIVEN
		write("document.properties", "document.status=Draft & <review> \"Iași\"\n");
		write("table.md", TABLE);

		// WHEN
		String status = coreProperties(execute()).getContentStatus();

		// THEN
		assertThat(status, equalTo("Draft & <review> \"Iași\""));
	}

	@Test
	public void GivenMissingOrBlankDocumentStatus_WhenExecute_ThenWriteNoContentStatus() throws Exception {
		// GIVEN
		write("table.md", TABLE);
		String missingStatus = coreProperties(execute()).getContentStatus();
		write("document.properties", "document.status=  \n");

		// WHEN
		String blankStatus = coreProperties(execute()).getContentStatus();

		// THEN
		assertThat(missingStatus, nullValue());
		assertThat(blankStatus, nullValue());
	}

	@Test
	public void GivenDocumentVersion_WhenExecute_ThenWriteItAsWorkbookVersion() throws Exception {
		// GIVEN
		write("document.properties", "document.version=1.2 <beta> & \"Iași\"\n");
		write("table.md", TABLE);

		// WHEN
		Optional<String> version = coreProperties(execute()).getUnderlyingProperties().getVersionProperty();

		// THEN
		assertThat(version, equalTo(Optional.of("1.2 <beta> & \"Iași\"")));
	}

	@Test
	public void GivenMissingOrBlankDocumentVersion_WhenExecute_ThenWriteNoVersion() throws Exception {
		// GIVEN
		write("table.md", TABLE);
		Optional<String> missingVersion = coreProperties(execute()).getUnderlyingProperties().getVersionProperty();
		write("document.properties", "document.version=  \n");

		// WHEN
		Optional<String> blankVersion = coreProperties(execute()).getUnderlyingProperties().getVersionProperty();

		// THEN
		assertThat(missingVersion, equalTo(Optional.empty()));
		assertThat(blankVersion, equalTo(Optional.empty()));
	}

	private static POIXMLProperties.ExtendedProperties extendedProperties(Workbook workbook) {
		return ((XSSFWorkbook) workbook).getProperties().getExtendedProperties();
	}

	private static POIXMLProperties.CoreProperties coreProperties(Workbook workbook) {
		return ((XSSFWorkbook) workbook).getProperties().getCoreProperties();
	}

	private static String creator(Workbook workbook) {
		return ((XSSFWorkbook) workbook).getProperties().getCoreProperties().getCreator();
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
