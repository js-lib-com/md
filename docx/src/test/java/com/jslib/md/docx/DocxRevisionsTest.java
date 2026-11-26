package com.jslib.md.docx;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.junit.Before;
import org.junit.Test;

import com.jslib.md.docx.xml.DocxDocument;

public class DocxRevisionsTest {
	private XWPFTable table;
	private int templateRowsCount;

	@Before
	public void beforeTest() throws IOException {
		templateRowsCount = TestDocuments.reload(TestDocuments.template()).getTables().get(1).getNumberOfRows();
	}

	@Test
	public void GivenFormattedAndEmptyCells_WhenVisit_ThenCopyPlainTextPerColumn() throws IOException {
		// GIVEN
		String markdown = "|Author|Date|Description|\n|-|-|-|\n" //
				+ "|**Iulian** Rotaru|2026-11-23|Fix `DocxRevisions` for [links](https://x.org) and *emphasis*.|\n" //
				+ "|Iulian Rotaru||Empty date cell.|\n";

		// WHEN
		generate(markdown);

		// THEN
		assertThat(table.getNumberOfRows(), equalTo(templateRowsCount + 2));
		assertThat(cells(templateRowsCount), contains("Iulian Rotaru", "2026-11-23", "Fix DocxRevisions for links and emphasis."));
		assertThat(cells(templateRowsCount + 1), contains("Iulian Rotaru", "", "Empty date cell."));
	}

	@Test
	public void GivenTableWithHeaderOnly_WhenVisit_ThenAddNoRows() throws IOException {
		// GIVEN
		String markdown = "|Author|Date|Description|\n|-|-|-|\n";

		// WHEN
		generate(markdown);

		// THEN
		assertThat(table.getNumberOfRows(), equalTo(templateRowsCount));
	}

	private void generate(String markdown) throws IOException {
		DocxDocument document = TestDocuments.template();
		TestDocuments.parse(markdown).accept(new DocxRevisions(document));
		table = TestDocuments.reload(document).getTables().get(1);
	}

	private List<String> cells(int rowIndex) {
		return table.getRow(rowIndex).getTableCells().stream().map(XWPFTableCell::getText).collect(Collectors.toList());
	}
}
