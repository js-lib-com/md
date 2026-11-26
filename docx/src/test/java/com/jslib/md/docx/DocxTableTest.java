package com.jslib.md.docx;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.xmlbeans.XmlException;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.junit.Before;
import org.junit.Test;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblGridCol;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblLayoutType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;

import com.jslib.md.docx.template.DocxTemplate;

public class DocxTableTest {
	// average text lengths 2.5 and 15, that is, 714 and 4286 fiftieths of percent
	private static final String TABLE = "|Name|Description|\n|-|-|\n|a|long long long text|\n";

	private Properties properties;

	@Before
	public void beforeTest() {
		properties = new Properties();
	}

	@Test
	public void GivenAutoFit_WhenVisitTable_ThenSetPercentWidthsAndFixedLayout() throws Exception {
		// GIVEN
		properties.setProperty("table.auto.fit", "true");
		String markdown = TABLE;

		// WHEN
		XWPFTable table = table(markdown);

		// THEN
		assertThat(table.getCTTbl().getTblPr().getTblLayout().getType(), equalTo(STTblLayoutType.FIXED));
		for (XWPFTableRow row : table.getRows()) {
			assertThat(cellWidths(row), contains("pct:714", "pct:4286"));
			assertThat(row.getCell(0).getCTTc().getTcPr().isSetNoWrap(), equalTo(false));
		}

		BigInteger contentWidth = contentWidth(table.getBody().getXWPFDocument());
		List<BigInteger> grid = gridWidths(table);
		assertThat(grid.size(), equalTo(2));
		assertThat(grid.get(0), equalTo(contentWidth.multiply(BigInteger.valueOf(714)).divide(BigInteger.valueOf(5000))));
		assertThat(grid.get(1), equalTo(contentWidth.multiply(BigInteger.valueOf(4286)).divide(BigInteger.valueOf(5000))));
	}

	@Test
	public void GivenAutoFitWithMinWidth_WhenVisitTable_ThenRaiseNarrowColumn() throws Exception {
		// GIVEN
		properties.setProperty("table.auto.fit", "true");
		properties.setProperty("table.column.min.width", "25");
		String markdown = TABLE;

		// WHEN
		XWPFTable table = table(markdown);

		// THEN
		assertThat(cellWidths(table.getRow(1)), contains("pct:1250", "pct:3750"));
	}

	// step column lengths 9, 10, 10, 10, 10: trimmed mean 10, plain mean 9.8; note column lengths 4, 8, 8, 1, 240: trimmed mean 6.67, plain mean 52.2
	private static final String TABLE_WITH_OUTLIER = "|Step text|Note|\n|-|-|\n|aaaaaaaaaa|optional|\n|bbbbbbbbbb|required|\n|cccccccccc|-|\n|dddddddddd|" + "x".repeat(240) + "|\n";

	@Test
	public void GivenAutoFitWithDefaultOutliers_WhenVisitTable_ThenIgnoreOutlierCell() throws Exception {
		// GIVEN
		properties.setProperty("table.auto.fit", "true");
		String markdown = TABLE_WITH_OUTLIER;

		// WHEN
		XWPFTable table = table(markdown);

		// THEN
		// weights 10 and 6.67, that is, 60% and 40%
		assertThat(cellWidths(table.getRow(1)), contains("pct:3000", "pct:2000"));
	}

	@Test
	public void GivenAutoFitWithZeroOutliers_WhenVisitTable_ThenUseAllCells() throws Exception {
		// GIVEN
		properties.setProperty("table.auto.fit", "true");
		properties.setProperty("table.column.outliers", "0");
		String markdown = TABLE_WITH_OUTLIER;

		// WHEN
		XWPFTable table = table(markdown);

		// THEN
		// weights 9.8 and 52.2, that is, 15.81% and 84.19%
		assertThat(cellWidths(table.getRow(1)), contains("pct:790", "pct:4210"));
	}

	@Test
	public void GivenInvalidOutliersPercent_WhenCreateProjectProperties_ThenThrow() {
		// GIVEN
		properties.setProperty("table.column.outliers", "60");

		// WHEN
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new ProjectProperties(properties));

		// THEN
		assertThat(exception.getMessage(), notNullValue());
	}

	@Test
	public void GivenAutoFitDisabled_WhenVisitTable_ThenKeepNoWrapColumns() throws Exception {
		// GIVEN
		String markdown = TABLE;

		// WHEN
		XWPFTable table = table(markdown);

		// THEN
		assertThat(table.getCTTbl().getTblPr().getTblLayout(), nullValue());
		assertThat(table.getRow(1).getCell(0).getCTTc().getTcPr().isSetNoWrap(), equalTo(true));
		assertThat(table.getRow(1).getCell(0).getCTTc().getTcPr().isSetTcW(), equalTo(false));
	}

	@Test
	public void GivenInvalidMinWidth_WhenCreateProjectProperties_ThenThrow() {
		// GIVEN
		properties.setProperty("table.column.min.width", "150");

		// WHEN
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new ProjectProperties(properties));

		// THEN
		assertThat(exception.getMessage(), notNullValue());
	}

	private XWPFTable table(String markdown) throws IOException, XmlException {
		XWPFDocument document = new XWPFDocument(new DocxTemplate(new Properties()).getInputStream());
		int tablesCount = document.getTables().size();
		DocxVisitor visitor = new DocxVisitor(document, new ProjectProperties(properties));
		Parser.builder().extensions(List.of(TablesExtension.create())).build().parse(markdown).accept(visitor);

		// save and reload to check widths survive serialization
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		document.write(buffer);
		XWPFDocument reloaded = new XWPFDocument(new ByteArrayInputStream(buffer.toByteArray()));
		assertThat(reloaded.getTables().size(), equalTo(tablesCount + 1));
		return reloaded.getTables().get(tablesCount);
	}

	private static List<String> cellWidths(XWPFTableRow row) {
		List<String> widths = new ArrayList<>();
		for (XWPFTableCell cell : row.getTableCells()) {
			CTTcPr tcPr = cell.getCTTc().getTcPr();
			STTblWidth.Enum type = tcPr.getTcW().getType();
			widths.add((type == STTblWidth.PCT ? "pct" : type.toString()) + ":" + tcPr.getTcW().getW());
		}
		return widths;
	}

	private static List<BigInteger> gridWidths(XWPFTable table) {
		List<BigInteger> widths = new ArrayList<>();
		for (CTTblGridCol col : table.getCTTbl().getTblGrid().getGridColList()) {
			widths.add(new BigInteger(col.getW().toString()));
		}
		return widths;
	}

	private static BigInteger contentWidth(XWPFDocument document) {
		CTSectPr sectPr = document.getDocument().getBody().getSectPr();
		BigInteger page = new BigInteger(sectPr.getPgSz().getW().toString());
		return page.subtract(new BigInteger(sectPr.getPgMar().getLeft().toString())).subtract(new BigInteger(sectPr.getPgMar().getRight().toString()));
	}
}
