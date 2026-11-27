package com.jslib.md.xlsx;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.BiConsumer;

import org.commonmark.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jslib.md.CommandHandler;
import com.jslib.md.DocumentSources;
import com.jslib.md.Markdown;
import com.jslib.md.NaturalOrder;
import com.jslib.md.ProjectProperties;
import com.jslib.md.PropertiesFile;
import com.jslib.md.TableColumns;
import com.jslib.md.xlsx.xml.Cell;
import com.jslib.md.xlsx.xml.Row;
import com.jslib.md.xlsx.xml.Sheet;
import com.jslib.md.xlsx.xml.Workbook;

/**
 * Generate an Excel workbook from document directory, one sheet per markdown file with tables, in file name order.
 */
public class XlsxGenerator implements CommandHandler {
	private static final Logger log = LoggerFactory.getLogger(XlsxGenerator.class);

	/**
	 * Workbook properties set from document properties with the same name; document properties are shared with DOCX
	 * generator. Document date is handled separately since it is free text for DOCX templates.
	 */
	private static final Map<String, BiConsumer<Workbook, String>> WORKBOOK_PROPERTIES = new LinkedHashMap<>();
	static {
		WORKBOOK_PROPERTIES.put("document.author", Workbook::setAuthor);
		WORKBOOK_PROPERTIES.put("document.title", Workbook::setTitle);
		WORKBOOK_PROPERTIES.put("document.subject", Workbook::setSubject);
		WORKBOOK_PROPERTIES.put("document.keywords", Workbook::setKeywords);
		WORKBOOK_PROPERTIES.put("document.category", Workbook::setCategory);
		WORKBOOK_PROPERTIES.put("document.comments", Workbook::setDescription);
		WORKBOOK_PROPERTIES.put("document.status", Workbook::setStatus);
		WORKBOOK_PROPERTIES.put("document.version", Workbook::setVersion);
		WORKBOOK_PROPERTIES.put("document.revision", Workbook::setRevision);
		WORKBOOK_PROPERTIES.put("document.language", Workbook::setLanguage);
		WORKBOOK_PROPERTIES.put("document.company", Workbook::setCompany);
		WORKBOOK_PROPERTIES.put("document.manager", Workbook::setManager);
	}

	private final Parser parser = Markdown.parser();

	@Override
	public void execute(File documentDir) throws Exception {
		ProjectProperties projectProperties = ProjectProperties.load(documentDir);
		DocumentSources sources = new DocumentSources(documentDir, projectProperties);
		File sourceDir = sources.sourceDir();

		Workbook workbook = new Workbook();
		// document properties is optional; without author, workbook creator and last modified by is the application name
		setProperties(workbook, PropertiesFile.loadOptional(sources.documentProperties()));

		File[] markdownFiles = sourceDir.listFiles(file -> file.getName().endsWith(".md"));
		if (markdownFiles == null) {
			throw new IOException("Fail to list source directory " + sourceDir);
		}
		// sheets order follows file names natural order
		Arrays.sort(markdownFiles, NaturalOrder.FILES);

		for (File markdownFile : markdownFiles) {
			log.debug("Process markdown file {}", markdownFile);
			String fileName = markdownFile.getName();
			Sheet sheet = workbook.createSheet(sheetName(workbook, fileName.substring(0, fileName.length() - 3)));
			parser.parse(Files.readString(markdownFile.toPath(), UTF_8)).accept(new XlsxVisitor(sheet, projectProperties.tableAutoFit));

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
		try (OutputStream outputStream = new FileOutputStream(targetFile)) {
			workbook.write(outputStream);
		}
	}

	private static void setProperties(Workbook workbook, Properties documentProperties) {
		WORKBOOK_PROPERTIES.forEach((name, setter) -> {
			String value = PropertiesFile.value(documentProperties, name);
			try {
				setter.accept(workbook, value);
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException(String.format("Invalid document property %s=%s. %s", name, value, e.getMessage()), e);
			}
		});

		String date = PropertiesFile.value(documentProperties, "document.date");
		if (date != null) {
			Instant created = parseDate(date);
			if (created != null) {
				workbook.setCreated(created);
			} else {
				// document date is free text for DOCX templates so it does not fail XLSX generation
				log.warn("Ignore document.date={}, not an ISO date or date-time; workbook creation time is generation time.", date);
			}
		}
	}

	/**
	 * Parse ISO date, e.g. 2026-11-26, or date-time, e.g. 2026-11-26T10:15:30 or 2026-11-26T10:15:30+02:00. Date and
	 * date-time without offset are in system time zone; date is at start of day. Return null if not parsable.
	 */
	static Instant parseDate(String date) {
		ZoneId zone = ZoneId.systemDefault();
		try {
			return OffsetDateTime.parse(date).toInstant();
		} catch (DateTimeParseException e) {
			// try next format
		}
		try {
			return LocalDateTime.parse(date).atZone(zone).toInstant();
		} catch (DateTimeParseException e) {
			// try next format
		}
		try {
			return LocalDate.parse(date).atStartOfDay(zone).toInstant();
		} catch (DateTimeParseException e) {
			return null;
		}
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
			double characters = (double) projectProperties.xlsxTableWidth * widths[column] / TableColumns.FULL_WIDTH;
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
