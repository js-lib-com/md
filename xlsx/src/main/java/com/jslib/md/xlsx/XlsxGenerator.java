package com.jslib.md.xlsx;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.WorkbookUtil;
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
	public void execute(File documentDir) throws Exception {
		log.trace("execute(File documentDir)");
		assert documentDir != null : "Document directory argument is null";

		File projectPropertiesFile = new File(documentDir, PROPJECT_PROPERTIES);
		Properties properties = new Properties();
		if (projectPropertiesFile.exists()) {
			// project properties is optional in which case defaults apply
			try (Reader reader = new InputStreamReader(new FileInputStream(projectPropertiesFile), StandardCharsets.UTF_8)) {
				properties.load(reader);
			}
		}
		ProjectProperties projectProperties = new ProjectProperties(properties);
		File sourceDir = projectProperties.sourceDir != null ? new File(documentDir, projectProperties.sourceDir) : documentDir;

		try (Workbook workbook = new XSSFWorkbook()) {
			File[] markdownFiles = sourceDir.listFiles(file -> file.getName().endsWith(".md"));
			if (markdownFiles == null) {
				throw new IOException("Fail to list source directory " + sourceDir);
			}
			// sheets order follows file names
			Arrays.sort(markdownFiles, Comparator.comparing(File::getName));

			for (File markdownFile : markdownFiles) {
				log.debug("Process markdown file {}", markdownFile);
				String fileName = markdownFile.getName();
				Sheet sheet = workbook.createSheet(sheetName(workbook, fileName.substring(0, fileName.length() - 3)));
				try (InputStreamReader reader = new InputStreamReader(new FileInputStream(markdownFile), StandardCharsets.UTF_8)) {
					XlsxVisitor visitor = new XlsxVisitor(sheet);
					parser.parseReader(reader).accept(visitor);
				}

				Row headerRow = sheet.getRow(0);
				if (headerRow == null) {
					log.warn("Skip markdown file without table {}", markdownFile);
					workbook.removeSheetAt(workbook.getSheetIndex(sheet));
					continue;
				}
				for (int columnIndex = 0; columnIndex < headerRow.getLastCellNum(); columnIndex++) {
					sheet.autoSizeColumn(columnIndex);
				}
			}
			if (workbook.getNumberOfSheets() == 0) {
				log.warn("No markdown tables found in {}", sourceDir);
			}

			try (FileOutputStream outputStream = new FileOutputStream(new File(documentDir, projectProperties.fileName + ".xlsx"))) {
				workbook.write(outputStream);
			}
		}
	}

	/**
	 * Valid sheet name derived from markdown file base name: invalid characters are replaced by underscore, length is limited to
	 * Excel maximum of 31 characters and a numeric suffix is added if the name is already used.
	 */
	static String sheetName(Workbook workbook, String baseName) {
		String safeName = WorkbookUtil.createSafeSheetName(baseName, '_');
		String name = safeName;
		// sheet names are case insensitive and so is workbook sheet lookup
		for (int index = 2; workbook.getSheet(name) != null; ++index) {
			String suffix = " (" + index + ")";
			name = safeName.substring(0, Math.min(safeName.length(), MAX_SHEET_NAME_LENGTH - suffix.length())) + suffix;
		}
		return name;
	}

	private static final int MAX_SHEET_NAME_LENGTH = 31;
}
