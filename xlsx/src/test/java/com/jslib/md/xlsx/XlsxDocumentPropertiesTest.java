package com.jslib.md.xlsx;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.apache.poi.ooxml.POIXMLProperties;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Document properties copied as they are to workbook properties: value is written, XML special characters included,
 * while missing or blank value writes no property.
 */
@RunWith(Parameterized.class)
public class XlsxDocumentPropertiesTest {
	private static final String TABLE = "|Name|Value|\n|-|-|\n|a|1|\n";

	@Parameters(name = "{0}")
	public static List<Object[]> parameters() {
		return List.of( //
				property("document.title", "Tables & <Fields> \"Iași\"", properties -> properties.getCoreProperties().getTitle()), //
				property("document.subject", "Work orders & <tasks> \"Iași\"", properties -> properties.getCoreProperties().getSubject()), //
				property("document.keywords", "fields, R&D, <tasks>, Iași", properties -> properties.getCoreProperties().getKeywords()), //
				property("document.category", "R&D <reference> \"Iași\"", properties -> properties.getCoreProperties().getCategory()), //
				property("document.comments", "Generated for R&D <team> \"Iași\"", properties -> properties.getCoreProperties().getDescription()), //
				property("document.status", "Draft & <review> \"Iași\"", properties -> properties.getCoreProperties().getContentStatus()), //
				property("document.version", "1.2 <beta> & \"Iași\"", properties -> properties.getCoreProperties().getUnderlyingProperties().getVersionProperty().orElse(null)), //
				property("document.revision", "7", properties -> properties.getCoreProperties().getRevision()), //
				property("document.language", "ro-RO", properties -> properties.getCoreProperties().getUnderlyingProperties().getLanguageProperty().orElse(null)), //
				property("document.company", "R&D <Grid> \"Iași\"", properties -> properties.getExtendedProperties().getCompany()), //
				property("document.manager", "R&D <lead> \"Iași\"", properties -> properties.getExtendedProperties().getManager()));
	}

	private static Object[] property(String name, String value, Function<POIXMLProperties, String> getter) {
		return new Object[] { name, value, getter };
	}

	private final String name;
	private final String value;
	private final Function<POIXMLProperties, String> getter;

	private File documentDir;

	public XlsxDocumentPropertiesTest(String name, String value, Function<POIXMLProperties, String> getter) {
		this.name = name;
		this.value = value;
		this.getter = getter;
	}

	@Before
	public void beforeTest() throws IOException {
		documentDir = Files.createTempDirectory("XlsxDocumentPropertiesTest").toFile();
		write("table.md", TABLE);
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
	public void GivenDocumentProperty_WhenExecute_ThenWriteWorkbookProperty() throws Exception {
		// GIVEN
		write("document.properties", name + "=" + value + "\n");

		// WHEN
		String workbookValue = getter.apply(execute());

		// THEN
		assertThat(workbookValue, equalTo(value));
	}

	@Test
	public void GivenMissingOrBlankDocumentProperty_WhenExecute_ThenWriteNoWorkbookProperty() throws Exception {
		// GIVEN
		String missingValue = getter.apply(execute());
		write("document.properties", name + "=  \n");

		// WHEN
		String blankValue = getter.apply(execute());

		// THEN
		assertThat(missingValue, nullValue());
		assertThat(blankValue, nullValue());
	}

	private void write(String fileName, String content) throws IOException {
		Files.writeString(new File(documentDir, fileName).toPath(), content, UTF_8);
	}

	private POIXMLProperties execute() throws Exception {
		new XlsxGenerator().execute(documentDir);
		try (FileInputStream stream = new FileInputStream(new File(documentDir, "document.xlsx"))) {
			return new XSSFWorkbook(stream).getProperties();
		}
	}
}
