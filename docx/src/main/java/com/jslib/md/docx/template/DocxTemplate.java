package com.jslib.md.docx.template;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jslib.md.docx.xml.Wml;

/**
 * DOCX template with {{variable}} placeholders replaced by document properties. Template archive is processed in memory:
 * XML parts have variables injected, with values XML escaped, while all other parts are copied unchanged. A variable
 * without value, i.e. missing or blank property, is replaced by empty text. Moreover, a WordprocessingML text element
 * with a variable without value is emptied, together with its static text, so that a label like
 * <code>Version {{document.version}}</code> is not displayed without value.
 */
public class DocxTemplate {
	private static final Logger log = LoggerFactory.getLogger(DocxTemplate.class);

	private static final String TEMPLATE_RESOURCE = "/template.docx";
	private static final Pattern VARIABLE = Pattern.compile("\\{\\{([^{}]+)}}");
	/** WordprocessingML text element, with start tag, text content and end tag groups. */
	private static final Pattern TEXT_ELEMENT = Pattern.compile("(<w:t(?:\\s[^>]*)?>)([^<]*)(</w:t>)");

	private final byte[] document;

	public DocxTemplate(Properties properties) throws IOException {
		this(DocxTemplate.class.getResourceAsStream(TEMPLATE_RESOURCE), properties);
	}

	DocxTemplate(InputStream templateStream, Properties properties) throws IOException {
		if (templateStream == null) {
			throw new IOException("Missing template resource " + TEMPLATE_RESOURCE);
		}

		Set<String> missingVariables = new TreeSet<>();
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		try (ZipInputStream zipInput = new ZipInputStream(templateStream); ZipOutputStream zipOutput = new ZipOutputStream(buffer)) {
			ZipEntry entry;
			while ((entry = zipInput.getNextEntry()) != null) {
				log.debug("Process template entry: {}", entry.getName());
				byte[] bytes = zipInput.readAllBytes();
				if (isXml(entry.getName())) {
					bytes = inject(new String(bytes, UTF_8), properties, missingVariables).getBytes(UTF_8);
				}
				zipOutput.putNextEntry(new ZipEntry(entry.getName()));
				zipOutput.write(bytes);
				zipOutput.closeEntry();
			}
		}
		this.document = buffer.toByteArray();

		if (!missingVariables.isEmpty()) {
			log.warn("Missing values for template variables, left empty: {}", String.join(", ", missingVariables));
		}
	}

	public InputStream getInputStream() {
		return new ByteArrayInputStream(document);
	}

	static String inject(String xml, Properties properties) {
		return inject(xml, properties, new TreeSet<>());
	}

	/**
	 * Replace variables in XML text; variables without value are replaced by empty text and their names are collected in
	 * missing variables set. Text elements with a variable without value are emptied, static text included.
	 */
	static String inject(String xml, Properties properties, Set<String> missingVariables) {
		return injectVariables(emptyTextsWithMissingVariables(xml, properties, missingVariables), properties, missingVariables);
	}

	/** Empty text elements with at least one variable without value; static text of emptied elements is removed too. */
	private static String emptyTextsWithMissingVariables(String xml, Properties properties, Set<String> missingVariables) {
		Matcher matcher = TEXT_ELEMENT.matcher(xml);
		StringBuilder builder = new StringBuilder();
		while (matcher.find()) {
			Set<String> missing = new TreeSet<>();
			Matcher variables = VARIABLE.matcher(matcher.group(2));
			while (variables.find()) {
				if (value(properties, variables.group(1)) == null) {
					missing.add(variables.group(1));
				}
			}
			String element = matcher.group();
			if (!missing.isEmpty()) {
				log.debug("Empty text element with missing variables {}: {}", missing, element);
				missingVariables.addAll(missing);
				element = matcher.group(1) + matcher.group(3);
			}
			matcher.appendReplacement(builder, Matcher.quoteReplacement(element));
		}
		matcher.appendTail(builder);
		return builder.toString();
	}

	private static String injectVariables(String xml, Properties properties, Set<String> missingVariables) {
		Matcher matcher = VARIABLE.matcher(xml);
		StringBuilder builder = new StringBuilder();
		while (matcher.find()) {
			String name = matcher.group(1);
			String value = value(properties, name);
			if (value == null) {
				missingVariables.add(name);
				value = "";
			}
			log.debug("Replace variable value: {}: {}", name, value);
			matcher.appendReplacement(builder, Matcher.quoteReplacement(Wml.escapeXml(value)));
		}
		matcher.appendTail(builder);
		return builder.toString();
	}

	/** Property value or null if property is missing or blank. */
	private static String value(Properties properties, String name) {
		String value = properties.getProperty(name);
		return value == null || value.isBlank() ? null : value;
	}

	private static boolean isXml(String entryName) {
		return entryName.endsWith(".xml") || entryName.endsWith(".rels");
	}
}
