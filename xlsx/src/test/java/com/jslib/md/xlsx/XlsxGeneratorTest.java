package com.jslib.md.xlsx;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.io.File;
import java.nio.file.Files;

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
		// no project properties, so default file name is used
		File documentFile = new File(documentDir, "document.xlsx");
		Files.deleteIfExists(documentFile.toPath());

		// WHEN
		generator.execute(documentDir);

		// THEN
		assertThat(documentFile.isFile(), equalTo(true));
	}
}
