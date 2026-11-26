package com.jslib.md.xlsx;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.io.File;

import org.junit.Before;
import org.junit.Test;

public class XlsxGeneratorTest {
	private XlsxGenerator generator;

	@Before
	public void beforeTest() {
		generator = new XlsxGenerator();
	}

	@Test
	public void GivenMarkdownDirectory_WhenExecute_ThenWriteDocumentXlsx() throws Exception {
		// GIVEN
		File documentDir = new File("src/test/resources/xlsx");

		// WHEN
		generator.execute(documentDir);

		// THEN
		// no project properties, so default file name is used
		assertThat(new File(documentDir, "document.xlsx").isFile(), equalTo(true));
	}
}
