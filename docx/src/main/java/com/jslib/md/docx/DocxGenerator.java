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

		String targetFileName = projectProperties.fileName + ".docx";
		
		File documentPropertiesFile = new File(documentDir, "document.properties");
		if (!documentPropertiesFile.exists()) {
			System.out.printf("Missing document properties file %s .\r\n", documentPropertiesFile);
			return;
		}
		File revisionsFile = new File(documentDir, "revisions.md");
		if (!revisionsFile.exists()) {
			System.out.printf("Missing document revisions file %s .\r\n", revisionsFile);
			return;
		}

		if (language != null) {
			targetFileName = String.format("%s_%s.docx", projectProperties.fileName, language);

			File languageDocumentPropertiesFile = new File(documentDir, String.format("document_%s.properties", language));
			if(languageDocumentPropertiesFile.exists()) {
				documentPropertiesFile = languageDocumentPropertiesFile;
			}
			File languageRevisionsFile = new File(documentDir, String.format("revisions_%s.md", language));
			if(languageRevisionsFile.exists()) {
				revisionsFile = languageRevisionsFile;
			}
		}

		File sourceFile;
		if (projectProperties.sourceDir != null) {
			File sourceDir = new File(documentDir, projectProperties.sourceDir);
			sourceFile = File.createTempFile("DocxGenerator", null);
			sourceFile.deleteOnExit();
			for (File file : sourceFiles(sourceDir, language)) {
				String line;
				try (BufferedReader reader = reader(file); BufferedWriter appender = appender(sourceFile)) {
					while ((line = reader.readLine()) != null) {
						appender.write(line);
						appender.newLine();
					}
					appender.newLine();
				}
			}
		} else {
			String sourceFileName = "document.md";
			if (language != null) {
				sourceFileName = String.format("document_%s.md", language);
			}
			sourceFile = new File(documentDir, sourceFileName);
		}
		if (!sourceFile.exists()) {
			System.out.printf("Missing source file %s .\r\n", sourceFile);
			return;
		}

		int extensionPosition = sourceFile.getName().lastIndexOf('.');
		if (extensionPosition == -1) {
			System.out.printf("Missing extension on file %s .\r\n", sourceFile);
			return;
		}

		Properties documentProperties = new Properties();
		try (Reader reader = new FileReader(documentPropertiesFile)) {
			documentProperties.load(reader);
		}

		List<Extension> extensions = Arrays.asList(TablesExtension.create());
		Parser parser = Parser.builder().extensions(extensions).build();

		DocxTemplate template = new DocxTemplate(documentProperties);
		DocxVisitor documentVisitor = new DocxVisitor(template, projectProperties, documentDir);

		DocxRevisions revisions = new DocxRevisions(documentVisitor.getDocument());
		try (FileReader reader = new FileReader(revisionsFile)) {
			parser.parseReader(reader).accept(revisions);
		}

		try (InputStreamReader reader = new InputStreamReader(new FileInputStream(sourceFile), "UTF-8")) {
			parser.parseReader(reader).accept(documentVisitor);
		}

		try (FileOutputStream outputStream = new FileOutputStream(new File(documentDir, targetFileName))) {
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
