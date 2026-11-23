package com.jslib.md.xlsx;

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
	public void Given_WhenGeneratorExecute_Then() throws Exception {
		// GIVEN
		File documentDir = new File("src/test/resources/xlsx");

		// WHEN
		generator.execute(documentDir);

		// THEN
	}

	public void Given_When_Then() {
		// GIVEN

		// WHEN

		// THEN
	}
}
