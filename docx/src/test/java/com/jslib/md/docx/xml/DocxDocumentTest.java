package com.jslib.md.docx.xml;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackageRelationship;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.jslib.md.docx.template.DocxTemplate;

public class DocxDocumentTest {
	private static final byte[] IMAGE = { 1, 2, 3 };

	private DocxDocument document;

	@Before
	public void beforeTest() throws IOException {
		document = new DocxDocument(new DocxTemplate(new Properties()).getInputStream());
	}

	@Test
	public void GivenTemplateBody_WhenAppendBlock_ThenInsertBeforeSectionProperties() {
		// GIVEN
		Element paragraph = Wml.create(document.body(), "p");

		// WHEN
		document.appendBlock(paragraph);

		// THEN
		Node last = lastElement(document.body());
		assertThat(Wml.is(last, "sectPr"), equalTo(true));
		assertThat(lastElement(document.body()).getPreviousSibling(), equalTo(paragraph));
	}

	@Test
	public void GivenSameImageTwice_WhenAddImage_ThenEmbedOnce() throws IOException {
		// GIVEN
		String firstRelId = document.addImage(IMAGE, "PNG");

		// WHEN
		String secondRelId = document.addImage(IMAGE.clone(), "png");
		String otherRelId = document.addImage(new byte[] { 4 }, "jpg");

		// THEN
		assertThat(secondRelId, equalTo(firstRelId));
		assertThat(otherRelId, not(equalTo(firstRelId)));
		XWPFDocument reloaded = new XWPFDocument(new ByteArrayInputStream(document.toByteArray()));
		List<XWPFPictureData> pictures = reloaded.getAllPackagePictures();
		assertThat(pictures, hasSize(2));
		assertThat(reloaded.getPictureDataByID(firstRelId).getPackagePart().getContentType(), equalTo("image/png"));
		assertThat(reloaded.getPictureDataByID(otherRelId).getPackagePart().getContentType(), equalTo("image/jpeg"));
	}

	@Test
	public void GivenUnsupportedImageType_WhenAddImage_ThenThrow() {
		// GIVEN
		String extension = "svg";

		// WHEN
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> document.addImage(IMAGE, extension));

		// THEN
		assertThat(exception.getMessage().contains("svg"), equalTo(true));
	}

	@Test
	public void GivenUrl_WhenAddHyperlink_ThenWriteExternalRelationshipWithUniqueId() throws Exception {
		// GIVEN
		String url = "https://x.org/?a=1&b=2";

		// WHEN
		String relId = document.addHyperlink(url);

		// THEN
		XWPFDocument reloaded = new XWPFDocument(new ByteArrayInputStream(document.toByteArray()));
		PackagePart part = reloaded.getPackagePart();
		PackageRelationship relationship = part.getRelationship(relId);
		assertThat(relationship.getTargetURI().toString(), equalTo("https://x.org/?a=1&b=2"));
		assertThat(relationship.getTargetMode().name(), equalTo("EXTERNAL"));
		assertThat(part.getRelationships().size(), equalTo(13));
	}

	private static Node lastElement(Element parent) {
		Node node = parent.getLastChild();
		while (node != null && !(node instanceof Element)) {
			node = node.getPreviousSibling();
		}
		return node;
	}
}
