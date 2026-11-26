package com.jslib.md.translate;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import org.junit.Test;

public class StructureCheckTest {
	@Test
	public void GivenSourceWithBackslashLine_WhenRepair_ThenKeepTranslation() {
		// GIVEN
		String escapedSource = "\\| escaped";
		String escapedTranslation = "\\| escaped ro";
		String plainSource = "text";
		String escapedMarkersTranslation = "  \\\\ - item\n\\# heading\n1. x\n\\ 2. y";
		String strayLinesTranslation = "- a\n\\\n- b\n / \n- c\n\\";

		// WHEN
		String keptTranslation = StructureCheck.repair(escapedSource, escapedTranslation);
		String repairedMarkers = StructureCheck.repair(plainSource, escapedMarkersTranslation);
		String repairedLines = StructureCheck.repair(plainSource, strayLinesTranslation);

		// THEN
		assertThat(keptTranslation, equalTo("\\| escaped ro"));
		assertThat(repairedMarkers, equalTo("  - item\n# heading\n1. x\n2. y"));
		assertThat(repairedLines, equalTo("- a\n- b\n- c\n"));
	}

	@Test
	public void GivenSameAndDifferentStructure_WhenStructureDifference_ThenDescribeDifference() {
		// GIVEN
		String source = "# T\n\n| a |\n|---|\n\n- x\n  - y\n1. z";
		String sameStructure = "# t\n\n| a |\n|---|\n\n- x\n  - y\n1. z";
		String differentStructure = "# t\n\n| a |\n\n- x\n- y 1. z";
		String listSource = "- a\n- b";
		String extraLine = "- a\nLOS\n- b";

		// WHEN
		String sameDifference = StructureCheck.difference(source, sameStructure);
		String differentDifference = StructureCheck.difference(source, differentStructure);
		String extraLineDifference = StructureCheck.difference(listSource, extraLine);

		// THEN
		assertThat(sameDifference, equalTo(null));
		assertThat(differentDifference, equalTo("4 lines instead of 6, 1 table rows instead of 2, 2 list items instead of 3"));
		assertThat(extraLineDifference, equalTo("3 lines instead of 2"));
	}
}
