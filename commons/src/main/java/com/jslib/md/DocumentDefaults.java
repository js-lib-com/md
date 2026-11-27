package com.jslib.md;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import org.commonmark.ext.gfm.tables.TableBody;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.CustomNode;
import org.commonmark.node.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Document properties with defaults for properties that are not set; document properties and revisions files are both
 * optional. Defaults are inferred from document context and every inferred value is logged:
 * <ul>
 * <li><code>document.title</code> - document directory name, e.g. <code>user-guide</code> to <code>User Guide</code>,
 * <li><code>document.author</code> - author of the newest revision, that is, the last revisions table row, otherwise
 * the operating system username,
 * <li><code>document.date</code> - date of the newest revision, otherwise today, as ISO date.
 * </ul>
 * Revisions table columns are author, date and description, in this order.
 */
public final class DocumentDefaults {
	private static final Logger log = LoggerFactory.getLogger(DocumentDefaults.class);

	public static final String TITLE = "document.title";
	public static final String AUTHOR = "document.author";
	public static final String DATE = "document.date";

	private static final String DEFAULT_TITLE = "Document";

	private DocumentDefaults() {
	}

	/** Load document properties for sources language, if any, and add defaults for missing title, author and date. */
	public static Properties load(File documentDir, DocumentSources sources) throws IOException {
		Properties properties = PropertiesFile.loadOptional(sources.documentProperties());
		File revisionsFile = sources.revisions();
		List<List<String>> revisions = revisionsFile.isFile() ? revisions(Files.readString(revisionsFile.toPath(), UTF_8)) : List.of();
		return apply(properties, documentDir, revisions, System.getProperty("user.name"), LocalDate.now());
	}

	/** Copy of document properties with defaults for missing title, author and date. */
	static Properties apply(Properties documentProperties, File documentDir, List<List<String>> revisions, String userName, LocalDate today) throws IOException {
		Properties properties = new Properties();
		properties.putAll(documentProperties);
		List<String> newestRevision = revisions.isEmpty() ? List.of() : revisions.get(revisions.size() - 1);

		if (PropertiesFile.value(properties, TITLE) == null) {
			setDefault(properties, TITLE, title(documentDir.getCanonicalFile().getName()), "document directory name");
		}
		if (PropertiesFile.value(properties, AUTHOR) == null) {
			String author = cell(newestRevision, 0);
			if (author != null) {
				setDefault(properties, AUTHOR, author, "newest revision");
			} else if (userName != null && !userName.isBlank()) {
				setDefault(properties, AUTHOR, userName, "operating system user name");
			}
		}
		if (PropertiesFile.value(properties, DATE) == null) {
			String date = cell(newestRevision, 1);
			if (date != null) {
				setDefault(properties, DATE, date, "newest revision");
			} else {
				setDefault(properties, DATE, today.toString(), "today");
			}
		}
		return properties;
	}

	/**
	 * Title from directory name: words separated by hyphens, underscores or spaces, with first letter capitalized, e.g.
	 * <code>user-guide</code> to <code>User Guide</code>. Return a generic title if directory name has no words.
	 */
	static String title(String directoryName) {
		List<String> words = new ArrayList<>();
		for (String word : directoryName.strip().split("[-_\\s]+")) {
			if (!word.isEmpty()) {
				words.add(word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1));
			}
		}
		return words.isEmpty() ? DEFAULT_TITLE : String.join(" ", words);
	}

	/** Revisions table body rows, as cell plain texts; header row is excluded. */
	static List<List<String>> revisions(String markdown) {
		List<List<String>> rows = new ArrayList<>();
		Markdown.parser().parse(markdown).accept(new AbstractVisitor() {
			@Override
			public void visit(CustomNode customNode) {
				if (customNode instanceof TableRow && customNode.getParent() instanceof TableBody) {
					List<String> cells = new ArrayList<>();
					for (Node cell = customNode.getFirstChild(); cell != null; cell = cell.getNext()) {
						if (cell instanceof TableCell) {
							cells.add(PlainText.of(cell));
						}
					}
					rows.add(cells);
					return;
				}
				visitChildren(customNode);
			}
		});
		return rows;
	}

	private static String cell(List<String> row, int index) {
		if (index >= row.size()) {
			return null;
		}
		String value = row.get(index).strip();
		return value.isEmpty() ? null : value;
	}

	private static void setDefault(Properties properties, String name, String value, String source) {
		log.info("Document property {}={} inferred from {}.", name, value, source);
		properties.setProperty(name, value);
	}
}
