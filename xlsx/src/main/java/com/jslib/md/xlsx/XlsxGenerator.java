package com.jslib.md.xlsx;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jslib.md.CommandHandler;

public class XlsxGenerator implements CommandHandler {
	private static final Logger log = LoggerFactory.getLogger(XlsxGenerator.class);

	private static final String PROPJECT_PROPERTIES = "project.properties";

	private final Parser parser;

	public XlsxGenerator() {
		log.trace("XlsxGenerator()");

		List<Extension> extensions = Arrays.asList(TablesExtension.create());
		this.parser = Parser.builder().extensions(extensions).build();
	}

	@Override
	public void execute(File documentDir, String language) throws Exception {
		log.trace("execute(File documentDir, String language)");
		assert documentDir != null : "Document directory argument is null";
		assert language == null : "Language is not used in current implementation";

		File projectPropertiesFile = new File(documentDir, PROPJECT_PROPERTIES);
		Properties properties = new Properties();
		if (projectPropertiesFile.exists()) {
			// project properties is optional in which case defaults apply
			try (Reader reader = new FileReader(projectPropertiesFile)) {
				properties.load(reader);
			}
		}
		ProjectProperties projectProperties = new ProjectProperties(properties);
		File sourceDir = projectProperties.sourceDir != null ? new File(documentDir, projectProperties.sourceDir) : documentDir;

		try (Workbook workbook = new XSSFWorkbook()) {
			File[] markdownFiles = sourceDir.listFiles(file -> file.getName().endsWith(".md"));
			assert markdownFiles != null : "Error listing markdown directory";
			for (File markdownFile : markdownFiles) {
				Sheet sheet = workbook.createSheet(markdownFile.getName().substring(0, markdownFile.getName().length() - 3));
				log.debug("Process markdown file {}", markdownFile);
				try (InputStreamReader reader = new InputStreamReader(new FileInputStream(markdownFile), "UTF-8")) {
					XlsxVisitor visitor = new XlsxVisitor(sheet);
					parser.parseReader(reader).accept(visitor);
				}
				for (int columnIndex = 0; columnIndex < sheet.getRow(0).getLastCellNum(); columnIndex++) {
					sheet.autoSizeColumn(columnIndex);
				}
			}

			try (FileOutputStream outputStream = new FileOutputStream(new File(documentDir, projectProperties.fileName + ".xlsx"))) {
				workbook.write(outputStream);
			}
		}
	}
}
