package com.jslib.md.cli;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;

import com.jslib.md.docx.DocxRevisions;
import com.jslib.md.docx.DocxVisitor;
import com.jslib.md.docx.template.DocxTemplate;

public class Main {

	public static void main(String... args) throws Exception {
		if (args.length > 2) {
			System.out.println("md2docx [document-dir] [language]");
			return;
		}

		File documentDir = new File(args.length >= 1 ? args[0] : ".");
		String documentFileName = "document.md";
		String documentPropertiesFileName = "document.properties";
		String revisionsFileName = "revisions.md";
		
		String language = args.length == 2? args[1]: null;
		if (language != null) {
			documentFileName = String.format("document_%s.md", language);
			documentPropertiesFileName = String.format("document_%s.properties", language);
			revisionsFileName = String.format("revisions_%s.md", language);
		}

		File markdownFile = new File(documentDir, documentFileName);
		if(!markdownFile.exists()) {
			System.out.printf("Missing markdown file %s .\r\n", markdownFile);
			return;
		}

		int extensionPosition = markdownFile.getName().lastIndexOf('.');
		if (extensionPosition == -1) {
			System.out.printf("Missing extension on file %s .\r\n", markdownFile);
			return;
		}

		File propertiesFile = new File(documentDir, documentPropertiesFileName);
		if (!propertiesFile.exists()) {
			System.out.printf("Missing document properties file %s .\r\n", propertiesFile);
			return;
		}

		File revisionsFile = new File(documentDir, revisionsFileName);
		if (!revisionsFile.exists()) {
			System.out.printf("Missing document revisions file %s .\r\n", revisionsFile);
			return;
		}

		List<Extension> extensions = Arrays.asList(TablesExtension.create());
		Parser parser = Parser.builder().extensions(extensions).build();

		Properties properties = new Properties();
		try (Reader reader = new FileReader(propertiesFile)) {
			properties.load(reader);
		}

		DocxTemplate template = new DocxTemplate(properties);
		DocxVisitor documentVisitor = new DocxVisitor(template, documentDir);

		DocxRevisions revisions = new DocxRevisions(documentVisitor.getDocument());
		try (FileReader reader = new FileReader(revisionsFile)) {
			parser.parseReader(reader).accept(revisions);
		}

		try (InputStreamReader reader = new InputStreamReader(new FileInputStream(markdownFile), "UTF-8")) {
			parser.parseReader(reader).accept(documentVisitor);
		}

		String markdownBasename = markdownFile.getName().substring(0, extensionPosition);
		try (FileOutputStream outputStream = new FileOutputStream(new File(documentDir, markdownBasename + ".docx"))) {
			documentVisitor.getDocument().write(outputStream);
		}
	}
}
