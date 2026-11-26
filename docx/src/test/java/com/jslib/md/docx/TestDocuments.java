package com.jslib.md.docx;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Properties;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.commonmark.node.Node;

import com.jslib.md.Markdown;
import com.jslib.md.ProjectProperties;
import com.jslib.md.docx.template.DocxTemplate;
import com.jslib.md.docx.xml.DocxDocument;

/**
 * Generated documents are serialized and read back with Apache POI, used only by tests, so that assertions check the
 * DOCX package as seen by an independent reader.
 */
final class TestDocuments {
	private TestDocuments() {
	}

	static DocxDocument template() throws IOException {
		return new DocxDocument(new DocxTemplate(new Properties()).getInputStream());
	}

	static XWPFDocument reload(DocxDocument document) throws IOException {
		return new XWPFDocument(new ByteArrayInputStream(document.toByteArray()));
	}

	static Node parse(String markdown) {
		return Markdown.parser().parse(markdown);
	}

	/** Generate markdown into a fresh template document and read it back. */
	static XWPFDocument generate(String markdown, ProjectProperties projectProperties) throws IOException {
		DocxDocument document = template();
		parse(markdown).accept(new DocxVisitor(document, projectProperties));
		return reload(document);
	}
}
