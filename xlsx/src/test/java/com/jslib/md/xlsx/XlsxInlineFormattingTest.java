package com.jslib.md.xlsx;

import static org.hamcrest.MatcherAssert.assertThat;
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

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class XlsxInlineFormattingTest {
	private File documentDir;
	private XSSFWorkbook workbook;

	@Before
	public void beforeTest() throws IOException {
		documentDir = Files.createTempDirectory("XlsxInlineFormattingTest").toFile();
	}

	@After
	@SuppressWarnings("ResultOfMethodCallIgnored")
	public void afterTest() throws IOException {
		if (workbook != null) {
			workbook.close();
		}
		for (File file : Objects.requireNonNull(documentDir.listFiles())) {
			file.delete();
		}
		documentDir.delete();
	}

	@Test
	public void GivenInlineHtmlFormattingTags_WhenExecute_ThenApplyFontRuns() throws Exception {
		// GIVEN
		String content = "a <b>bold</b> <i>it</i> <u>u</u> <s>s</s> <code>c</code> x<sup>2</sup> H<sub>2</sub>O";

		// WHEN
		XSSFCell cell = cell(content);

		// THEN
		assertThat(cell.getStringCellValue(), equalTo("a bold it u s c x2 H2O"));
		assertThat(runs(cell), equalTo("[a ]|[bold]b|[ ]|[it]i|[ ]|[u]u|[ ]|[s]s|[ ]|[c]c|[ x]|[2]^|[ H]|[2]_|[O]"));
	}

	@Test
	public void GivenMarkdownEmphasisAndInlineCode_WhenExecute_ThenApplyFontRuns() throws Exception {
		// GIVEN
		String content = "**bold <em>both</em>** and `code`";

		// WHEN
		XSSFCell cell = cell(content);

		// THEN
		assertThat(runs(cell), equalTo("[bold ]b|[both]bi|[ and ]|[code]c"));
	}

	@Test
	public void GivenLineBreaks_WhenExecute_ThenWriteNewLinesAndWrapText() throws Exception {
		// GIVEN
		String content = "line<br>next<br/>end";

		// WHEN
		XSSFCell cell = cell(content);

		// THEN
		assertThat(cell.getStringCellValue(), equalTo("line\nnext\nend"));
		assertThat(cell.getCellStyle().getWrapText(), equalTo(true));
	}

	@Test
	public void GivenPlainText_WhenExecute_ThenDoNotWrapNorApplyFontRuns() throws Exception {
		// GIVEN
		// table auto-fit wraps all cells, so it is disabled to check that plain text alone does not wrap
		Files.writeString(new File(documentDir, "project.properties").toPath(), "table.auto.fit=false\n");
		String content = "plain <span>text</span>";

		// WHEN
		XSSFCell cell = cell(content);

		// THEN
		assertThat(cell.getStringCellValue(), equalTo("plain text"));
		assertThat(cell.getRichStringCellValue().numFormattingRuns(), equalTo(0));
		assertThat(cell.getCellStyle().getWrapText(), equalTo(false));
	}

	@Test
	public void GivenTextThatLooksLikeTag_WhenExecute_ThenKeepItAsText() throws Exception {
		// GIVEN
		String content = "Ünïcödé & <special> \"quotes\" <b>bold</b> <span>span</span> <!-- note -->";

		// WHEN
		XSSFCell cell = cell(content);

		// THEN
		// unknown element is text while HTML tags and comments are not
		assertThat(cell.getStringCellValue(), equalTo("Ünïcödé & <special> \"quotes\" bold span"));
		assertThat(runs(cell), equalTo("[Ünïcödé & <special> \"quotes\" ]|[bold]b|[ span]"));
	}

	@Test
	public void GivenAnchorAndMarkdownLinks_WhenExecute_ThenSetFirstLinkAsCellHyperlink() throws Exception {
		// GIVEN
		String content = "see <a href=\"https://x.org/?a=1&amp;b=2\">site</a> or [other](https://y.org)";

		// WHEN
		XSSFCell cell = cell(content);

		// THEN
		assertThat(cell.getStringCellValue(), equalTo("see site or other"));
		assertThat(cell.getHyperlink().getAddress(), equalTo("https://x.org/?a=1&b=2"));
		assertThat(runs(cell), equalTo("[see ]|[site]l|[ or ]|[other]l"));
	}

	@Test
	public void GivenUnbalancedTags_WhenExecute_ThenDoNotLeakToNextCell() throws Exception {
		// GIVEN
		writeTable("|<b>open|plain|\n|-|-|\n|</i>stray|<b>bold</b>|\n");

		// WHEN
		XSSFSheet sheet = sheet();

		// THEN
		assertThat(runs(sheet.getRow(0).getCell(1)), equalTo("[plain]"));
		assertThat(runs(sheet.getRow(1).getCell(0)), equalTo("[stray]"));
		assertThat(sheet.getRow(1).getCell(0).getHyperlink(), nullValue());
	}

	@Test
	public void GivenFormattedHeaderCell_WhenExecute_ThenKeepHeaderFontColor() throws Exception {
		// GIVEN
		writeTable("|<b>Name</b>|Value|\n|-|-|\n|a|1|\n");

		// WHEN
		XSSFCell cell = sheet().getRow(0).getCell(0);

		// THEN
		XSSFFont font = cell.getRichStringCellValue().getFontOfFormattingRun(0);
		assertThat(font.getBold(), equalTo(true));
		assertThat(font.getColor(), equalTo(IndexedColors.WHITE.getIndex()));
	}

	@Test
	public void GivenPartlyHighlightedText_WhenExecute_ThenFillCellAndKeepTextPlain() throws Exception {
		// GIVEN
		String content = "note: <mark>important</mark> text";

		// WHEN
		XSSFCell cell = cell(content);

		// THEN
		assertThat(cell.getStringCellValue(), equalTo("note: important text"));
		assertThat(runs(cell), equalTo("[note: important text]"));
		assertThat(cell.getCellStyle().getFillPattern(), equalTo(FillPatternType.SOLID_FOREGROUND));
		assertThat(cell.getCellStyle().getFillForegroundColor(), equalTo(IndexedColors.YELLOW.getIndex()));
		assertThat(cell.getCellStyle().getBorderBottom(), equalTo(BorderStyle.THIN));
	}

	@Test
	public void GivenHighlightedBoldTextWithLineBreak_WhenExecute_ThenFillWrapAndKeepBold() throws Exception {
		// GIVEN
		String content = "<mark><b>bold</b><br>line</mark>";

		// WHEN
		XSSFCell cell = cell(content);

		// THEN
		assertThat(runs(cell), equalTo("[bold]b|[\nline]"));
		assertThat(cell.getCellStyle().getFillForegroundColor(), equalTo(IndexedColors.YELLOW.getIndex()));
		assertThat(cell.getCellStyle().getWrapText(), equalTo(true));
	}

	@Test
	public void GivenHighlightedHeaderCell_WhenExecute_ThenKeepHeaderFill() throws Exception {
		// GIVEN
		writeTable("|<mark>Name</mark>|Value|\n|-|-|\n|a|1|\n");

		// WHEN
		XSSFCell cell = sheet().getRow(0).getCell(0);

		// THEN
		assertThat(cell.getCellStyle().getFillForegroundColor(), equalTo(IndexedColors.TEAL.getIndex()));
	}

	@Test
	public void GivenNotHighlightedCell_WhenExecute_ThenDoNotFill() throws Exception {
		// GIVEN
		String content = "plain <b>bold</b>";

		// WHEN
		XSSFCell cell = cell(content);

		// THEN
		assertThat(cell.getCellStyle().getFillPattern(), equalTo(FillPatternType.NO_FILL));
	}

	@Test
	public void GivenEmptyCell_WhenExecute_ThenLeaveBlank() throws Exception {
		// GIVEN
		writeTable("|Name|Value|\n|-|-|\n|a| <!-- note --> |\n");

		// WHEN
		XSSFCell cell = sheet().getRow(1).getCell(1);

		// THEN
		assertThat(cell.getCellType(), equalTo(CellType.BLANK));
	}

	// --------------------------------------------------------------------------------------------

	/** Body cell from a single column table with given content. */
	private XSSFCell cell(String content) throws Exception {
		writeTable("|Header|\n|-|\n|" + content + "|\n");
		return sheet().getRow(1).getCell(0);
	}

	private void writeTable(String markdown) throws IOException {
		Files.write(new File(documentDir, "table.md").toPath(), markdown.getBytes(StandardCharsets.UTF_8));
	}

	private XSSFSheet sheet() throws Exception {
		new XlsxGenerator().execute(documentDir);
		try (FileInputStream stream = new FileInputStream(new File(documentDir, "document.xlsx"))) {
			workbook = new XSSFWorkbook(stream);
		}
		return workbook.getSheet("table");
	}

	/** Describe rich text runs as [text]flags, where flags are b, i, u, s, c for code, ^, _ and l for link. */
	private static String runs(XSSFCell cell) {
		XSSFRichTextString text = cell.getRichStringCellValue();
		String value = text.getString();
		if (text.numFormattingRuns() == 0) {
			return "[" + value + "]";
		}
		List<String> runs = new ArrayList<>();
		for (int i = 0; i < text.numFormattingRuns(); ++i) {
			int start = text.getIndexOfFormattingRun(i);
			String run = "[" + value.substring(start, start + text.getLengthOfFormattingRun(i)) + "]";
			XSSFFont font = text.getFontOfFormattingRun(i);
			if (font != null) {
				run += font.getBold() ? "b" : "";
				run += font.getItalic() ? "i" : "";
				boolean link = font.getColor() == IndexedColors.BLUE.getIndex();
				run += font.getUnderline() == Font.U_SINGLE && !link ? "u" : "";
				run += font.getStrikeout() ? "s" : "";
				run += "Consolas".equals(font.getFontName()) ? "c" : "";
				run += font.getTypeOffset() == Font.SS_SUPER ? "^" : "";
				run += font.getTypeOffset() == Font.SS_SUB ? "_" : "";
				run += link ? "l" : "";
			}
			runs.add(run);
		}
		return String.join("|", runs);
	}
}
