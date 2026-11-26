package com.jslib.md.docx.xml;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.sameInstance;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class WmlTest {
	private Element paragraph;

	@Before
	public void beforeTest() throws IOException {
		String xml = "<w:p xmlns:w=\"" + Wml.W_NS + "\"><w:r><w:t>text</w:t></w:r></w:p>";
		paragraph = Wml.parse(new ByteArrayInputStream(xml.getBytes(UTF_8))).getDocumentElement();
	}

	@Test
	public void GivenParagraphWithRun_WhenAddProperties_ThenKeepSchemaOrder() {
		// GIVEN
		Element pPr = Wml.property(paragraph, "pPr");

		// WHEN
		Wml.property(pPr, "jc", "center");
		Wml.property(pPr, "ind");
		Wml.property(pPr, "pStyle", "Quote");
		Wml.property(pPr, "numPr");

		// THEN
		assertThat(names(paragraph), contains("pPr", "r"));
		assertThat(names(pPr), contains("pStyle", "numPr", "ind", "jc"));
	}

	@Test
	public void GivenRunProperties_WhenAddInReverseOrder_ThenKeepSchemaOrder() {
		// GIVEN
		Element rPr = Wml.property(Wml.child(paragraph, "r"), "rPr");

		// WHEN
		Wml.property(rPr, "vertAlign", "superscript");
		Wml.property(rPr, "highlight", "yellow");
		Wml.property(rPr, "sz", 16);
		Wml.property(rPr, "color", "0000FF");
		Wml.property(rPr, "i");
		Wml.property(rPr, "b");
		Wml.property(rPr, "rStyle", "Hyperlink");

		// THEN
		assertThat(names(rPr), contains("rStyle", "b", "i", "color", "sz", "highlight", "vertAlign"));
	}

	@Test
	public void GivenExistingProperty_WhenProperty_ThenReturnSameElementAndUpdateValue() {
		// GIVEN
		Element pPr = Wml.property(paragraph, "pPr");
		Element style = Wml.property(pPr, "pStyle", "Quote");

		// WHEN
		Element updated = Wml.property(pPr, "pStyle", "Heading1");

		// THEN
		assertThat(updated, sameInstance(style));
		assertThat(Wml.attr(updated, "val"), equalTo("Heading1"));
		assertThat(names(pPr), contains("pStyle"));
	}

	@Test
	public void GivenTextWithSurroundingSpaces_WhenSetText_ThenPreserveSpaces() {
		// GIVEN
		Element run = Wml.add(paragraph, "r");

		// WHEN
		Wml.setText(run, " spaced ");
		Wml.setText(run, "plain");

		// THEN
		List<Element> texts = Wml.children(run, "t");
		assertThat(texts.get(0).getAttribute("xml:space"), equalTo("preserve"));
		assertThat(texts.get(1).hasAttribute("xml:space"), equalTo(false));
		assertThat(Wml.text(paragraph), equalTo("text spaced plain"));
	}

	private static List<String> names(Element parent) {
		List<String> names = new ArrayList<>();
		for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
			names.add(node.getLocalName());
		}
		return names;
	}
}
