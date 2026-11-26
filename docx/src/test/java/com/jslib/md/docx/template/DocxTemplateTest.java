package com.jslib.md.docx.template;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.Before;
import org.junit.Test;

public class DocxTemplateTest {
	private Properties properties;

	@Before
	public void beforeTest() {
		properties = new Properties();
	}

	@Test
	public void GivenDefinedVariable_WhenInject_ThenReplaceWithValue() {
		// GIVEN
		properties.setProperty("document.title", "User Manual");
		String source = "<w:t>{{document.title}}</w:t>";

		// WHEN
		String xml = DocxTemplate.inject(source, properties);

		// THEN
		assertThat(xml, equalTo("<w:t>User Manual</w:t>"));
	}

	@Test
	public void GivenMissingVariable_WhenInject_ThenReplaceWithName() {
		// GIVEN
		String source = "<w:t>{{document.title}}</w:t>";

		// WHEN
		String xml = DocxTemplate.inject(source, properties);

		// THEN
		assertThat(xml, equalTo("<w:t>document.title</w:t>"));
	}

	@Test
	public void GivenSingleBraces_WhenInject_ThenKeepUnchanged() {
		// GIVEN
		String source = "<ds:datastoreItem ds:itemID=\"{55AF091B-3C7A-41E3-B477-F2FDAA23CFDA}\"/> {x} } {";

		// WHEN
		String xml = DocxTemplate.inject(source, properties);

		// THEN
		assertThat(xml, equalTo(source));
	}

	@Test
	public void GivenXmlSpecialCharsInValue_WhenInject_ThenEscape() {
		// GIVEN
		properties.setProperty("document.author", "R&D <team> \"$1\"");
		String source = "<w:t>{{document.author}}</w:t>";

		// WHEN
		String xml = DocxTemplate.inject(source, properties);

		// THEN
		assertThat(xml, equalTo("<w:t>R&amp;D &lt;team&gt; &quot;$1&quot;</w:t>"));
	}

	@Test
	public void GivenNonAsciiValue_WhenInject_ThenPreserve() {
		// GIVEN
		properties.setProperty("document.title", "Manual de utilizare ăâîșț €");
		String source = "{{document.title}}";

		// WHEN
		String xml = DocxTemplate.inject(source, properties);

		// THEN
		assertThat(xml, equalTo("Manual de utilizare ăâîșț €"));
	}

	@Test
	public void GivenTemplateResource_WhenCreate_ThenInjectVariablesAndPreserveGuids() throws IOException {
		// GIVEN
		properties.setProperty("document.title", "Manual ăâîșț");

		// WHEN
		DocxTemplate template = new DocxTemplate(properties);

		// THEN
		Map<String, String> entries = entries(template.getInputStream());
		assertThat(entries.get("word/document.xml"), containsString("Manual ăâîșț"));
		assertThat(entries.get("word/document.xml"), not(containsString("{{document.title}}")));
		assertThat(entries.get("customXml/itemProps1.xml"), containsString("{55AF091B-3C7A-41E3-B477-F2FDAA23CFDA}"));
	}

	@Test
	public void GivenAllDocumentProperties_WhenCreate_ThenFillDocumentMetadataAndLeaveNoPlaceholders() throws IOException {
		// GIVEN
		for (String name : new String[] { "title", "subtitle", "author", "date", "abstract", "version" }) {
			properties.setProperty("document." + name, "value-" + name);
		}
		properties.setProperty("company.name", "value-company-name");
		properties.setProperty("company.logotype", "value-company-logotype");

		// WHEN
		DocxTemplate template = new DocxTemplate(properties);

		// THEN
		Map<String, String> entries = entries(template.getInputStream());
		assertThat(entries.get("docProps/core.xml"), containsString("<dc:title>value-title</dc:title>"));
		assertThat(entries.get("docProps/core.xml"), containsString("<dc:subject>value-subtitle</dc:subject>"));
		assertThat(entries.get("docProps/core.xml"), containsString("<dc:creator>value-author</dc:creator>"));
		assertThat(entries.get("docProps/app.xml"), containsString("<vt:lpstr>value-title</vt:lpstr>"));
		assertThat(entries.get("customXml/item1.xml"), containsString("<PublishDate>value-date</PublishDate>"));
		assertThat(entries.get("customXml/item1.xml"), containsString("<Abstract>value-abstract</Abstract>"));
		for (Map.Entry<String, String> entry : entries.entrySet()) {
			assertThat(entry.getKey(), entry.getValue(), not(containsString("{{")));
		}
	}

	private static Map<String, String> entries(InputStream stream) throws IOException {
		Map<String, String> entries = new HashMap<>();
		try (ZipInputStream zip = new ZipInputStream(stream)) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				entries.put(entry.getName(), new String(zip.readAllBytes(), UTF_8));
			}
		}
		return entries;
	}
}
