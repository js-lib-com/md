package com.jslib.md.docx;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;

import com.jslib.md.CommandHandler;
import com.jslib.md.docx.template.DocxTemplate;

public class DocxGenerator implements CommandHandler {
	private static final String PROPJECT_PROPERTIES = "project.properties";

	@Override
	public void execute(File documentDir, String language) throws Exception {
		File projectPropertiesFile = new File(documentDir, PROPJECT_PROPERTIES);
		Properties properties = new Properties();
		if (projectPropertiesFile.exists()) {
			// project properties is optional in which case defaults apply
			try (Reader reader = new FileReader(projectPropertiesFile)) {
				properties.load(reader);
			}
		}
		ProjectProperties projectProperties = new ProjectProperties(properties);
		File sourceDir = projectProperties.sourceDir != null ? new File(documentDir, projectProperties.sourceDir) : null;

		String documentFileName = "document.md";
		String documentPropertiesFileName = "document.properties";
		String revisionsFileName = "revisions.md";

		if (language != null) {
			documentFileName = String.format("document_%s.md", language);
			documentPropertiesFileName = String.format("document_%s.properties", language);
			revisionsFileName = String.format("revisions_%s.md", language);
		}

		File markdownFile;
		if (sourceDir != null) {
			markdownFile = File.createTempFile("DocxGenerator", null);
			markdownFile.deleteOnExit();
			for (File file : sourceFiles(sourceDir, language)) {
				String line;
				try (BufferedReader reader = reader(file); BufferedWriter appender = appender(markdownFile)) {
					while ((line = reader.readLine()) != null) {
						appender.write(line);
						appender.newLine();
					}
					appender.newLine();
				}
			}
		} else {
			markdownFile = new File(documentDir, documentFileName);
		}
		if (!markdownFile.exists()) {
			System.out.printf("Missing markdown file %s .\r\n", markdownFile);
			return;
		}

		int extensionPosition = markdownFile.getName().lastIndexOf('.');
		if (extensionPosition == -1) {
			System.out.printf("Missing extension on file %s .\r\n", markdownFile);
			return;
		}

		File documentPropertiesFile = new File(documentDir, documentPropertiesFileName);
		if (!documentPropertiesFile.exists()) {
			System.out.printf("Missing document properties file %s .\r\n", documentPropertiesFile);
			return;
		}
		Properties documentProperties = new Properties();
		try (Reader reader = new FileReader(documentPropertiesFile)) {
			documentProperties.load(reader);
		}

		File revisionsFile = new File(documentDir, revisionsFileName);
		if (!revisionsFile.exists()) {
			System.out.printf("Missing document revisions file %s .\r\n", revisionsFile);
			return;
		}

		List<Extension> extensions = Arrays.asList(TablesExtension.create());
		Parser parser = Parser.builder().extensions(extensions).build();

		DocxTemplate template = new DocxTemplate(documentProperties);
		DocxVisitor documentVisitor = new DocxVisitor(template, projectProperties, documentDir);

		DocxRevisions revisions = new DocxRevisions(documentVisitor.getDocument());
		try (FileReader reader = new FileReader(revisionsFile)) {
			parser.parseReader(reader).accept(revisions);
		}

		try (InputStreamReader reader = new InputStreamReader(new FileInputStream(markdownFile), "UTF-8")) {
			parser.parseReader(reader).accept(documentVisitor);
		}

		try (FileOutputStream outputStream = new FileOutputStream(new File(documentDir, projectProperties.fileName + ".docx"))) {
			documentVisitor.getDocument().write(outputStream);
		}
	}

	private File[] sourceFiles(File sourceDir, String language) {
		File[] files = sourceDir.listFiles(file -> {
			if (!file.getName().endsWith(".md")) {
				return false;
			}
			if (file.getName().startsWith("revisions")) {
				return false;
			}
			if (language == null) {
				return true;
			}
			return file.getName().endsWith('_' + language + ".md");
		});
		assert files != null : "Fail to list source directory";
		return files;
	}

	private static BufferedReader reader(File file) throws FileNotFoundException {
		FileInputStream stream = new FileInputStream(file);
		InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
		return new BufferedReader(reader);
	}

	private static BufferedWriter appender(File markdownFile) throws FileNotFoundException {
		FileOutputStream stream = new FileOutputStream(markdownFile, true);
		OutputStreamWriter writer = new OutputStreamWriter(stream, StandardCharsets.UTF_8);
		return new BufferedWriter(writer);
	}
}
