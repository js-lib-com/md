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
 * XML parts have variables injected, with values XML escaped, while all other parts are copied unchanged. If a variable
 * has no property, its name is used as value.
 */
public class DocxTemplate {
	private static final Logger log = LoggerFactory.getLogger(DocxTemplate.class);

	private static final String TEMPLATE_RESOURCE = "/template.docx";
	private static final Pattern VARIABLE = Pattern.compile("\\{\\{([^{}]+)}}");

	private final byte[] document;

	public DocxTemplate(Properties properties) throws IOException {
		this(DocxTemplate.class.getResourceAsStream(TEMPLATE_RESOURCE), properties);
		log.trace("DocxTemplate(Properties properties)");
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
			log.warn("Missing values for template variables, replaced by variable names: {}", String.join(", ", missingVariables));
		}
	}

	public InputStream getInputStream() {
		log.trace("getInputStream()");
		return new ByteArrayInputStream(document);
	}

	static String inject(String xml, Properties properties) {
		return inject(xml, properties, new TreeSet<>());
	}

	/** Replace variables in XML text; names of variables without value are collected in missing variables set. */
	static String inject(String xml, Properties properties, Set<String> missingVariables) {
		Matcher matcher = VARIABLE.matcher(xml);
		StringBuilder builder = new StringBuilder();
		while (matcher.find()) {
			String name = matcher.group(1);
			String value = properties.getProperty(name);
			if (value == null) {
				missingVariables.add(name);
				value = name;
			}
			log.debug("Replace variable value: {}: {}", name, value);
			matcher.appendReplacement(builder, Matcher.quoteReplacement(Wml.escapeXml(value)));
		}
		matcher.appendTail(builder);
		return builder.toString();
	}

	private static boolean isXml(String entryName) {
		return entryName.endsWith(".xml") || entryName.endsWith(".rels");
	}
}
