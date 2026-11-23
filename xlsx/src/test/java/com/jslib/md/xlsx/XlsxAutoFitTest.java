package com.jslib.md.xlsx;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class XlsxAutoFitTest {
	// average text lengths 2.5 and 15, that is, 14.28% and 85.72% of table width
	private static final String TABLE = "|Name|Description|\n|-|-|\n|a|long long long text|\n";

	private File documentDir;
	private Workbook workbook;

	@Before
	public void beforeTest() throws IOException {
		documentDir = Files.createTempDirectory("XlsxAutoFitTest").toFile();
	}

	@After
	public void afterTest() throws IOException {
		if (workbook != null) {
			workbook.close();
		}
		for (File file : documentDir.listFiles()) {
			file.delete();
		}
		documentDir.delete();
	}

	@Test
	public void GivenAutoFit_WhenExecute_ThenShareDefaultTableWidthByTextQuantity() throws Exception {
		// when
		Sheet sheet = generate("table.auto.fit=true\n", TABLE);

		// then: default table width of 100 characters, in 1/256 of character
		assertThat(sheet.getColumnWidth(0), equalTo(3656));
		assertThat(sheet.getColumnWidth(1), equalTo(21944));
	}

	@Test
	public void GivenAutoFitWithMinWidthAndTableWidth_WhenExecute_ThenApplyBoth() throws Exception {
		// when
		Sheet sheet = generate("table.auto.fit=true\ntable.column.min.width=25\nxlsx.table.width=120\n", TABLE);

		// then: 25% and 75% of 120 characters
		assertThat(sheet.getColumnWidth(0), equalTo(30 * 256));
		assertThat(sheet.getColumnWidth(1), equalTo(90 * 256));
	}

	@Test
	public void GivenAutoFitAndSeveralTables_WhenExecute_ThenMeasureAllRows() throws Exception {
		// when: second table header is skipped and its body rows are appended to sheet
		Sheet sheet = generate("table.auto.fit=true\ntable.column.min.width=0\n", TABLE + "\n|Name|Description|\n|-|-|\n|longer name value|x|\n");

		// then: average lengths (4 + 1 + 17) / 3 = 7.33 and (11 + 19 + 1) / 3 = 10.33, that is, 41.50% and 58.50% after rounding to fiftieths of percent
		assertThat(sheet.getColumnWidth(0), equalTo(10624));
		assertThat(sheet.getColumnWidth(1), equalTo(14976));
	}

	@Test
	public void GivenAutoFitWithOutlierCell_WhenExecute_ThenIgnoreItByDefault() throws Exception {
		// given: note column lengths 4, 8, 8, 1, 240; step column lengths 9, 10, 10, 10, 10
		String table = "|Step text|Note|\n|-|-|\n|aaaaaaaaaa|optional|\n|bbbbbbbbbb|required|\n|cccccccccc|-|\n|dddddddddd|" + "x".repeat(240) + "|\n";

		// when
		Sheet trimmed = generate("table.auto.fit=true\n", table);
		int[] trimmedWidths = { trimmed.getColumnWidth(0), trimmed.getColumnWidth(1) };
		workbook.close();
		Sheet plain = generate("table.auto.fit=true\ntable.column.outliers=0\n", table);

		// then: trimmed means 10 and 6.67, that is, 60 and 40 characters; plain means 9.8 and 52.2, that is, 15.8 and 84.2
		assertThat(trimmedWidths[0], equalTo(60 * 256));
		assertThat(trimmedWidths[1], equalTo(40 * 256));
		assertThat(plain.getColumnWidth(0), equalTo(4045));
		assertThat(plain.getColumnWidth(1), equalTo(21555));
	}

	@Test
	public void GivenAutoFit_WhenExecute_ThenWrapAllCellsAlignedTop() throws Exception {
		// when
		Sheet sheet = generate("table.auto.fit=true\n", TABLE);

		// then
		for (int row = 0; row < 2; ++row) {
			for (int column = 0; column < 2; ++column) {
				assertThat(sheet.getRow(row).getCell(column).getCellStyle().getWrapText(), equalTo(true));
				assertThat(sheet.getRow(row).getCell(column).getCellStyle().getVerticalAlignment(), equalTo(VerticalAlignment.TOP));
			}
		}
	}

	@Test
	public void GivenAutoFitDisabled_WhenExecute_ThenDoNotWrap() throws Exception {
		// when
		Sheet sheet = generate("", TABLE);

		// then
		assertThat(sheet.getRow(1).getCell(1).getCellStyle().getWrapText(), equalTo(false));
	}

	@Test(expected = IllegalArgumentException.class)
	public void GivenInvalidTableWidth_WhenExecute_ThenThrow() throws Exception {
		generate("table.auto.fit=true\nxlsx.table.width=wide\n", TABLE);
	}

	private Sheet generate(String projectProperties, String markdown) throws Exception {
		Files.write(new File(documentDir, "project.properties").toPath(), projectProperties.getBytes(StandardCharsets.UTF_8));
		Files.write(new File(documentDir, "table.md").toPath(), markdown.getBytes(StandardCharsets.UTF_8));
		new XlsxGenerator().execute(documentDir);
		try (FileInputStream stream = new FileInputStream(new File(documentDir, "document.xlsx"))) {
			workbook = new XSSFWorkbook(stream);
		}
		return workbook.getSheet("table");
	}
}
