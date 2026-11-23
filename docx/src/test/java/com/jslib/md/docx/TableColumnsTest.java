package com.jslib.md.docx;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class TableColumnsTest {
	@Test
	public void GivenWeightsAboveThreshold_WhenWidths_ThenProportional() {
		assertThat(TableColumns.widths(new double[] { 1, 3 }, 10), equalTo(new int[] { 1250, 3750 }));
	}

	@Test
	public void GivenNarrowColumn_WhenWidths_ThenRaiseToThresholdAndShrinkOthers() {
		assertThat(TableColumns.widths(new double[] { 1, 100 }, 10), equalTo(new int[] { 500, 4500 }));
	}

	@Test
	public void GivenColumnPushedUnderThresholdByPinnedColumn_WhenWidths_ThenPinItToo() {
		// 22% initially, but under 20% after first column is raised to 20%
		assertThat(TableColumns.widths(new double[] { 5, 22, 73 }, 20), equalTo(new int[] { 1000, 1000, 3000 }));
	}

	@Test
	public void GivenThresholdTooLargeForColumnsCount_WhenWidths_ThenEqualWidths() {
		int[] widths = TableColumns.widths(new double[] { 1, 50, 100 }, 50);
		assertThat(Arrays.stream(widths).sum(), equalTo(TableColumns.FULL_WIDTH));
		for (int width : widths) {
			assertThat(Math.abs(width - TableColumns.FULL_WIDTH / 3) <= 1, equalTo(true));
		}
	}

	@Test
	public void GivenRoundingError_WhenWidths_ThenSumIsFullWidth() {
		int[] widths = TableColumns.widths(new double[] { 1, 1, 1, 1, 1, 1, 1 }, 0);
		assertThat(Arrays.stream(widths).sum(), equalTo(TableColumns.FULL_WIDTH));
	}

	@Test
	public void GivenCellTexts_WhenWidths_ThenUseAverageTextLengthPerColumn() {
		// given: average lengths 2.5 and 15, that is, 14.29% and 85.71%
		List<List<String>> rows = Arrays.asList(Arrays.asList("Name", "Description"), Arrays.asList(" a ", "long long long text"));

		// when
		int[] widths = TableColumns.widths(rows, 10);

		// then
		assertThat(widths, equalTo(new int[] { 714, 4286 }));
	}

	@Test
	public void GivenEmptyColumns_WhenWidths_ThenEqualWidths() {
		assertThat(TableColumns.widths(Arrays.asList(Arrays.asList("", "")), 10), equalTo(new int[] { 2500, 2500 }));
	}
}
