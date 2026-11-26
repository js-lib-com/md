package com.jslib.md.xlsx;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
import com.jslib.md.TableColumns;
import com.jslib.md.xlsx.xml.Cell;
import com.jslib.md.xlsx.xml.Row;
import com.jslib.md.xlsx.xml.Sheet;
import com.jslib.md.xlsx.xml.Workbook;

public class XlsxGenerator implements CommandHandler {
	private static final Logger log = LoggerFactory.getLogger(XlsxGenerator.class);

	private static final String PROJECT_PROPERTIES = "project.properties";
	/** Document properties, shared with DOCX generator; only document author, title, subject, keywords and category are used. */
	private static final String DOCUMENT_PROPERTIES = "document.properties";

	private final Parser parser;

	public XlsxGenerator() {
		log.trace("XlsxGenerator()");

		List<Extension> extensions = List.of(TablesExtension.create());
		this.parser = Parser.builder().extensions(extensions).build();
	}

	@Override
	public void execute(File documentDir) throws Exception {
		log.trace("execute(File documentDir)");

		// project properties is optional in which case defaults apply
		ProjectProperties projectProperties = new ProjectProperties(properties(new File(documentDir, PROJECT_PROPERTIES)));
		File sourceDir = projectProperties.sourceDir != null ? new File(documentDir, projectProperties.sourceDir) : documentDir;

		Workbook workbook = new Workbook();
		// document properties is optional; without author, workbook creator is the application name
		Properties documentProperties = properties(new File(documentDir, DOCUMENT_PROPERTIES));
		workbook.setAuthor(value(documentProperties, "document.author"));
		workbook.setTitle(value(documentProperties, "document.title"));
		workbook.setSubject(value(documentProperties, "document.subject"));
		workbook.setKeywords(value(documentProperties, "document.keywords"));
		workbook.setCategory(value(documentProperties, "document.category"));
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
				XlsxVisitor visitor = new XlsxVisitor(sheet, projectProperties.tableAutoFit);
				parser.parseReader(reader).accept(visitor);
			}

			Row headerRow = sheet.getRow(0);
			if (headerRow == null) {
				log.warn("Skip markdown file without table {}", markdownFile);
				workbook.removeSheet(sheet);
				continue;
			}
			int columnsCount = headerRow.getCells().size();
			if (projectProperties.tableAutoFit) {
				autoFit(sheet, columnsCount, projectProperties);
			} else {
				for (int columnIndex = 0; columnIndex < columnsCount; columnIndex++) {
					sheet.autoSizeColumn(columnIndex);
				}
			}
		}
		if (workbook.getSheets().isEmpty()) {
			log.warn("No markdown tables found in {}", sourceDir);
		}

		File targetFile = new File(documentDir, projectProperties.fileName + ".xlsx");
		log.info("Generate {}", targetFile);
		try (FileOutputStream outputStream = new FileOutputStream(targetFile)) {
			workbook.write(outputStream);
		}
	}

	/** Property value, stripped of surrounding white spaces, or null if property is missing or blank. */
	private static String value(Properties properties, String name) {
		String value = properties.getProperty(name, "").strip();
		return value.isEmpty() ? null : value;
	}

	/** Load UTF-8 properties file; return empty properties if file is missing. */
	private static Properties properties(File file) throws IOException {
		Properties properties = new Properties();
		if (file.exists()) {
			try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
				properties.load(reader);
			}
		}
		return properties;
	}

	/** Excel maximum column width, in characters. */
	private static final int MAX_COLUMN_WIDTH = 255;

	/**
	 * Share table width between columns proportionally with the quantity of text in every column, but not less than
	 * minimal column width. Uses the same algorithm as DOCX tables; cells text is wrapped by sheet visitor.
	 */
	static void autoFit(Sheet sheet, int columnsCount, ProjectProperties projectProperties) {
		List<List<String>> texts = new ArrayList<>();
		for (Row row : sheet.getRows()) {
			List<String> rowTexts = new ArrayList<>();
			for (int column = 0; column < columnsCount; ++column) {
				Cell cell = row.getCell(column);
				rowTexts.add(cell != null ? cell.getText() : "");
			}
			texts.add(rowTexts);
		}

		int[] widths = TableColumns.widths(texts, projectProperties.tableColumnMinWidth, projectProperties.tableColumnOutliers);
		for (int column = 0; column < columnsCount; ++column) {
			double characters = (double) projectProperties.tableWidth * widths[column] / TableColumns.FULL_WIDTH;
			// column width unit is 1/256 of a character width
			sheet.setColumnWidth(column, (int) Math.round(Math.min(characters, MAX_COLUMN_WIDTH) * 256));
		}
	}

	/**
	 * Valid sheet name derived from markdown file base name: invalid characters are replaced by underscore, length is limited to
	 * Excel maximum of 31 characters and a numeric suffix is added if the name is already used.
	 */
	static String sheetName(Workbook workbook, String baseName) {
		String safeName = safeSheetName(baseName);
		String name = safeName;
		// sheet names are case-insensitive and so is workbook sheet lookup
		for (int index = 2; workbook.getSheet(name) != null; ++index) {
			String suffix = " (" + index + ")";
			name = safeName.substring(0, Math.min(safeName.length(), MAX_SHEET_NAME_LENGTH - suffix.length())) + suffix;
		}
		return name;
	}

	private static final int MAX_SHEET_NAME_LENGTH = 31;

	/**
	 * Truncate name to Excel maximum sheet name length and replace characters not allowed in sheet names by underscore,
	 * including apostrophe on first or last position.
	 */
	static String safeSheetName(String name) {
		if (name.isEmpty()) {
			return "empty";
		}
		StringBuilder safeName = new StringBuilder(name.substring(0, Math.min(name.length(), MAX_SHEET_NAME_LENGTH)));
		for (int i = 0; i < safeName.length(); ++i) {
			char c = safeName.charAt(i);
			boolean edge = i == 0 || i == safeName.length() - 1;
			if ("/\\?*[]:".indexOf(c) != -1 || (c == '\'' && edge)) {
				safeName.setCharAt(i, '_');
			}
		}
		return safeName.toString();
	}
}
