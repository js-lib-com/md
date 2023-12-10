package com.jslib.md.docx;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

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

	public void generateHeading1() throws IOException, XmlException {
		String markdown = "# Generate Heading One\n";
		generate(markdown);
	}

	public void generateHeadings() throws IOException, XmlException {
		String markdown = "# Heading One\n# Heading Two\n# Heading Three\n";
		generate(markdown);
	}

	public void generateHeadingLevels() throws IOException, XmlException {
		String markdown = "# Heading One\n## Heading One Level Two\n## Heading One Level Three\n### Heading 3\n### Heading 3\n# Heading Two\n# Heading Three\n";
		generate(markdown);
	}

	public void generateParagraph() throws IOException, XmlException {
		String markdown = "# Generate Heading One\nThis is a paragraph\n\nThis is another paragraph with _italic_ and __bold__ parts\n.";
		generate(markdown);
	}

	public void generateImage() throws IOException, XmlException {
		String markdown = "![Image Description](image.png 'sample image')\n\n![User Interface](user-interface.word.png 'sample image')\n\n";
		generate(markdown);
	}

	public void generateLink() throws IOException, XmlException {
		String markdown = "Paragraph with link to [Wikipedia](https://www.wikipedia.org/) site.\n\n";
		generate(markdown);
	}

	public void generateList() throws IOException, XmlException {
		String markdown = "List:\n- Item #1,\n- Item #2.";
		generate(markdown);
	}
	
	@Test
	public void generateTable() throws IOException, XmlException {
		String markdown = "# Generate Table\n|head 1|head 2|head 3|\n|:-|:-:|-:|\n|cell to _11_|cell to __12__|cell 13|\n|cell 21|cell 22|cell 23|\n|cell 31|cell 32|private static final void generate(String markdown)|\n";
		generate(markdown);
	}

	private static final void generate(String markdown) throws IOException, XmlException {
		List<Extension> extensions = Arrays.asList(TablesExtension.create());
		Parser parser = Parser.builder().extensions(extensions).build();

		try (FileInputStream inputStream = new FileInputStream(TEMPLATE_FILE)) {
			XWPFDocument document = new XWPFDocument(inputStream);

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

			DocxVisitor visitor = new DocxVisitor(document, new Properties());
			parser.parse(markdown).accept(visitor);

			try (FileOutputStream outputStream = new FileOutputStream(DOCUMENT_FILE)) {
				document.write(outputStream);
			}
		}
	}
}
