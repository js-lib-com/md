package com.jslib.md.docx;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;

import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.junit.Before;
import org.junit.Test;

import com.jslib.md.docx.template.DocxTemplate;

public class DocxRevisionsTest {
	private XWPFDocument document;
	private XWPFTable table;
	private int templateRowsCount;

	@Before
	public void beforeTest() throws IOException {
		document = new XWPFDocument(new DocxTemplate(new Properties()).getInputStream());
		table = document.getTables().get(1);
		templateRowsCount = table.getNumberOfRows();
	}

	@Test
	public void GivenFormattedAndEmptyCells_WhenVisit_ThenCopyPlainTextPerColumn() {
		// given
		String markdown = "|Author|Date|Description|\n|-|-|-|\n" //
				+ "|**Iulian** Rotaru|2026-11-23|Fix `DocxRevisions` for [links](https://x.org) and *emphasis*.|\n" //
				+ "|Iulian Rotaru||Empty date cell.|\n";

		// when
		generate(markdown);

		// then
		assertThat(table.getNumberOfRows(), equalTo(templateRowsCount + 2));
		assertThat(cells(templateRowsCount), contains("Iulian Rotaru", "2026-11-23", "Fix DocxRevisions for links and emphasis."));
		assertThat(cells(templateRowsCount + 1), contains("Iulian Rotaru", "", "Empty date cell."));
	}

	@Test
	public void GivenTableWithHeaderOnly_WhenVisit_ThenAddNoRows() {
		// when
		generate("|Author|Date|Description|\n|-|-|-|\n");

		// then
		assertThat(table.getNumberOfRows(), equalTo(templateRowsCount));
	}

	private void generate(String markdown) {
		DocxRevisions revisions = new DocxRevisions(document);
		Parser.builder().extensions(List.of(TablesExtension.create())).build().parse(markdown).accept(revisions);
	}

	private List<String> cells(int rowIndex) {
		return table.getRow(rowIndex).getTableCells().stream().map(XWPFTableCell::getText).collect(Collectors.toList());
	}
}
