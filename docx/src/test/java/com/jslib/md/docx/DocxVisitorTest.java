package com.jslib.md.docx;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.Test;

import com.jslib.md.ProjectProperties;
import com.jslib.md.docx.xml.DocxDocument;

public class DocxVisitorTest {
	/** Template other than the one bundled with generator, to check generation does not depend on template content. */
	private static final String TEMPLATE_RESOURCE = "/custom-template.docx";

	@Test
	public void GivenMarkdownTable_WhenVisit_ThenWriteDocument() throws IOException {
		// GIVEN
		String revisions = "|Author|Date|Description|\n|-|-|-|\n" //
				+ "|Iulian Rotaru|2023-05-08|Create user manual document for Call Radar application.|\n" //
				+ "|Iulian Rotaru Master|2023-05-09|Create user manual document for Call Radar application.|\n";
		String markdown = "# Generate Table\n|head 1|head 2|head 3|\n|:-|:-:|-:|\n|cell to _11_|cell to __12__|cell 13|\n|cell 21|cell 22|cell 23|\n|cell 31|cell 32|private static final void generate(String markdown)|\n";

		DocxDocument document;
		try (InputStream inputStream = DocxVisitorTest.class.getResourceAsStream(TEMPLATE_RESOURCE)) {
			document = new DocxDocument(inputStream);
		}

		// WHEN
		TestDocuments.parse(revisions).accept(new DocxRevisions(document));
		TestDocuments.parse(markdown).accept(new DocxVisitor(document, ProjectProperties.empty()));

		File documentFile = File.createTempFile("DocxVisitorTest", ".docx");
		documentFile.deleteOnExit();
		try (FileOutputStream outputStream = new FileOutputStream(documentFile)) {
			document.write(outputStream);
		}

		// THEN
		try (FileInputStream inputStream = new FileInputStream(documentFile)) {
			XWPFDocument written = new XWPFDocument(inputStream);
			int tablesCount = written.getTables().size();
			assertThat(written.getTables().get(tablesCount - 1).getRow(3).getCell(2).getText(), equalTo("private static final void generate(String markdown)"));
		}
	}
}
