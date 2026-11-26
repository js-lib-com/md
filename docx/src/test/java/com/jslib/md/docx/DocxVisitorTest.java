package com.jslib.md.docx;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.xmlbeans.XmlException;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.junit.Test;

public class DocxVisitorTest {
	private static final String TEMPLATE_FILE = "work/template.docx";
	private static final String DOCUMENT_FILE = "work/document.docx";

	@Test
	public void GivenMarkdownTable_WhenVisit_ThenWriteDocument() throws IOException, XmlException {
		// GIVEN
		String markdown = "# Generate Table\n|head 1|head 2|head 3|\n|:-|:-:|-:|\n|cell to _11_|cell to __12__|cell 13|\n|cell 21|cell 22|cell 23|\n|cell 31|cell 32|private static final void generate(String markdown)|\n";
		List<Extension> extensions = List.of(TablesExtension.create());
		Parser parser = Parser.builder().extensions(extensions).build();

		XWPFDocument document;
		try (FileInputStream inputStream = new FileInputStream(TEMPLATE_FILE)) {
			document = new XWPFDocument(inputStream);
		}

		if (document.getTables().size() > 1) {
			XWPFTable revisionTable = document.getTables().get(1);
			assert revisionTable != null;

			XWPFTableRow row = revisionTable.createRow();
			row.getCell(0).setText("Iulian Rotaru");
			row.getCell(1).setText("2023-05-08");
			row.getCell(2).setText("Create user manual document for Call Radar application.");

			row.getCell(0).setWidth("144");
			row.getCell(1).setWidth("144");

			row = revisionTable.createRow();
			row.getCell(0).setText("Iulian Rotaru Master");
			row.getCell(1).setText("2023-05-09");
			row.getCell(2).setText("Create user manual document for Call Radar application.");
		}

		// WHEN
		DocxVisitor visitor = new DocxVisitor(document, ProjectProperties.empty());
		parser.parse(markdown).accept(visitor);

		try (FileOutputStream outputStream = new FileOutputStream(DOCUMENT_FILE)) {
			document.write(outputStream);
		}

		// THEN
		assertThat(new File(DOCUMENT_FILE).exists(), equalTo(true));
	}
}
