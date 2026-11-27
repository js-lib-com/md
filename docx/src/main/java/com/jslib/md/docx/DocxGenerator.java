package com.jslib.md.docx;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Properties;

import org.commonmark.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jslib.md.CommandHandler;
import com.jslib.md.DocumentDefaults;
import com.jslib.md.DocumentSources;
import com.jslib.md.Markdown;
import com.jslib.md.ProjectProperties;
import com.jslib.md.docx.template.DocxTemplate;

/**
 * Generate Word documents from document directory, one for default language and one for every translation language
 * from project properties. See {@link DocumentSources} for document directory layout.
 */
public class DocxGenerator implements CommandHandler {
	private static final Logger log = LoggerFactory.getLogger(DocxGenerator.class);

	private final Parser parser = Markdown.parser();

	@Override
	public void execute(File documentDir) throws Exception {
		ProjectProperties projectProperties = ProjectProperties.load(documentDir);
		generate(documentDir, projectProperties, null);
		for (String language : projectProperties.languages) {
			generate(documentDir, projectProperties, language);
		}
	}

	private void generate(File documentDir, ProjectProperties projectProperties, String language) throws IOException {
		DocumentSources sources = new DocumentSources(documentDir, projectProperties, language);

		Properties documentProperties = DocumentDefaults.load(documentDir, sources);
		for (File unlisted : sources.unlistedSources()) {
			log.warn("Source file {} is not listed by source.files project property; it is not included in document.", unlisted);
		}
		String markdown = markdown(sources.markdownSources());

		DocxVisitor documentVisitor = new DocxVisitor(new DocxTemplate(documentProperties), projectProperties, documentDir);
		documentVisitor.setFigureLabel(documentProperties.getProperty("figure.label", DocxVisitor.DEFAULT_FIGURE_LABEL));
		File revisionsFile = sources.revisions();
		if (revisionsFile.isFile()) {
			parser.parse(Files.readString(revisionsFile.toPath(), UTF_8)).accept(new DocxRevisions(documentVisitor.getDocument()));
		}
		parser.parse(markdown).accept(documentVisitor);

		File targetFile = new File(documentDir, DocumentSources.localize(projectProperties.fileName + ".docx", language));
		log.info("Generate {}", targetFile);
		try (OutputStream outputStream = new FileOutputStream(targetFile)) {
			documentVisitor.getDocument().write(outputStream);
		}
	}

	/** Markdown source files content, separated by a blank line so that a file does not continue previous file block. */
	static String markdown(List<File> files) throws IOException {
		StringBuilder markdown = new StringBuilder();
		for (File file : files) {
			if (!file.isFile()) {
				throw new FileNotFoundException("Missing source file " + file);
			}
			markdown.append(Files.readString(file.toPath(), UTF_8)).append(System.lineSeparator()).append(System.lineSeparator());
		}
		return markdown.toString();
	}
}
