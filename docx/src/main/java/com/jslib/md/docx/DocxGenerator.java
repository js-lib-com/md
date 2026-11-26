package com.jslib.md.docx;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jslib.md.CommandHandler;
import com.jslib.md.docx.template.DocxTemplate;

public class DocxGenerator implements CommandHandler {
	private static final Logger log = LoggerFactory.getLogger(DocxGenerator.class);

	private static final String PROJECT_PROPERTIES = "project.properties";

	private ProjectProperties projectProperties;

	@Override
	public void execute(File documentDir) throws Exception {
		log.trace("execute(File documentDir)");

		File projectPropertiesFile = new File(documentDir, PROJECT_PROPERTIES);
		Properties properties = new Properties();
		if (projectPropertiesFile.exists()) {
			// project properties is optional in which case defaults apply
			try (Reader reader = reader(projectPropertiesFile)) {
				properties.load(reader);
			}
		}
		projectProperties = new ProjectProperties(properties);

		execute(documentDir, null);
		for (String language : projectProperties.languages) {
			execute(documentDir, language);
		}
	}

	private void execute(File documentDir, String language) throws Exception {
		log.trace("execute(File documentDir, String language)");

		String targetFileName = projectProperties.fileName + ".docx";

		File documentPropertiesFile = new File(documentDir, "document.properties");
		if (!documentPropertiesFile.exists()) {
			throw new FileNotFoundException("Missing document properties file " + documentPropertiesFile);
		}
		File revisionsFile = new File(documentDir, "revisions.md");
		if (!revisionsFile.exists()) {
			throw new FileNotFoundException("Missing document revisions file " + revisionsFile);
		}

		if (language != null) {
			targetFileName = String.format("%s_%s.docx", projectProperties.fileName, language);

			File languageDocumentPropertiesFile = new File(documentDir, String.format("document_%s.properties", language));
			if (languageDocumentPropertiesFile.exists()) {
				documentPropertiesFile = languageDocumentPropertiesFile;
			}
			File languageRevisionsFile = new File(documentDir, String.format("revisions_%s.md", language));
			if (languageRevisionsFile.exists()) {
				revisionsFile = languageRevisionsFile;
			}
		}

		File sourceFile;
		if (projectProperties.sourceDir != null) {
			File sourceDir = new File(documentDir, projectProperties.sourceDir);
			sourceFile = File.createTempFile("DocxGenerator", null);
			sourceFile.deleteOnExit();
			for (File file : sourceFiles(sourceDir, language, projectProperties.languages)) {
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
			throw new FileNotFoundException("Missing source file " + sourceFile);
		}

		int extensionPosition = sourceFile.getName().lastIndexOf('.');
		if (extensionPosition == -1) {
			throw new FileNotFoundException("Missing extension on file " + sourceFile);
		}

		Properties documentProperties = new Properties();
		try (Reader reader = reader(documentPropertiesFile)) {
			documentProperties.load(reader);
		}

		List<Extension> extensions = List.of(TablesExtension.create());
		Parser parser = Parser.builder().extensions(extensions).build();

		DocxTemplate template = new DocxTemplate(documentProperties);
		DocxVisitor documentVisitor = new DocxVisitor(template, projectProperties, documentDir);
		documentVisitor.setFigureLabel(documentProperties.getProperty("figure.label", DocxVisitor.DEFAULT_FIGURE_LABEL));

		DocxRevisions revisions = new DocxRevisions(documentVisitor.getDocument());
		try (Reader reader = reader(revisionsFile)) {
			parser.parseReader(reader).accept(revisions);
		}

		try (InputStreamReader reader = new InputStreamReader(new FileInputStream(sourceFile), StandardCharsets.UTF_8)) {
			parser.parseReader(reader).accept(documentVisitor);
		}

		File targetFile = new File(documentDir, targetFileName);
		log.info("Generate {}", targetFile);
		try (FileOutputStream outputStream = new FileOutputStream(targetFile)) {
			documentVisitor.getDocument().write(outputStream);
		}
	}

	/**
	 * Markdown source files for requested language, sorted by name. For default language, i.e. null language argument,
	 * exclude files translated to any of the configured languages.
	 */
	static File[] sourceFiles(File sourceDir, String language, String[] languages) throws IOException {
		File[] files = sourceDir.listFiles(file -> {
			String name = file.getName();
			if (!name.endsWith(".md")) {
				return false;
			}
			if (name.startsWith("revisions")) {
				return false;
			}
			if (language != null) {
				return name.endsWith('_' + language + ".md");
			}
			for (String translation : languages) {
				if (name.endsWith('_' + translation + ".md")) {
					return false;
				}
			}
			return true;
		});
		if (files == null) {
			throw new IOException("Fail to list source directory " + sourceDir);
		}
		Arrays.sort(files, Comparator.comparing(File::getName));
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
