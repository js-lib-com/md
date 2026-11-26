package com.jslib.md;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
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
		int[] widths = TableColumns.widths(rows, 10, 0);

		// then
		assertThat(widths, equalTo(new int[] { 714, 4286 }));
	}

	@Test
	public void GivenOutlierCell_WhenTrimmedMean_ThenIgnoreShortestAndLongest() {
		// 20% of 5 cells: one cell dropped from each end
		assertThat(TableColumns.trimmedMean(new int[] { 240, 8, 12, 9, 10 }, 20), closeTo(31.0 / 3, 1e-9));
	}

	@Test
	public void GivenTrimPercentTooSmallForCellsCount_WhenTrimmedMean_ThenUseAllCells() {
		// 10% of 5 cells, rounded down, drops nothing
		assertThat(TableColumns.trimmedMean(new int[] { 240, 8, 12, 9, 10 }, 10), closeTo(55.8, 1e-9));
		// 20% of 4 cells, rounded down, drops nothing
		assertThat(TableColumns.trimmedMean(new int[] { 1, 2, 3, 100 }, 20), closeTo(26.5, 1e-9));
	}

	@Test
	public void GivenZeroOutliersPercent_WhenTrimmedMean_ThenPlainMean() {
		assertThat(TableColumns.trimmedMean(new int[] { 240, 8, 12, 9, 10, 0, 0, 0, 0, 0 }, 0), closeTo(27.9, 1e-9));
	}

	@Test
	public void GivenMaximumOutliersPercent_WhenTrimmedMean_ThenMedian() {
		assertThat(TableColumns.trimmedMean(new int[] { 240, 8, 12, 9, 10 }, 50), closeTo(10, 1e-9));
		assertThat(TableColumns.trimmedMean(new int[] { 1, 2, 3, 100 }, 50), closeTo(2.5, 1e-9));
	}

	@Test
	public void GivenColumnWithOneLongCell_WhenWidths_ThenOutlierDoesNotWidenColumn() {
		// given: header and 4 rows; second column has a single long note
		List<List<String>> rows = Arrays.asList( //
				Arrays.asList("Description of the step", "Note"), //
				Arrays.asList("Open login form from menu", "optional"), //
				Arrays.asList("Enter user name and password", "required"), //
				Arrays.asList("Press the login button now", "-"), //
				Arrays.asList("Wait for the main window", "x".repeat(240)));

		// when
		int[] plain = TableColumns.widths(rows, 10, 0);
		int[] trimmed = TableColumns.widths(rows, 10, 20);

		// then: mean of note lengths is 52.2, more than description; trimmed mean is 6.7
		assertThat(plain[1] > plain[0], equalTo(true));
		assertThat(trimmed[1] < trimmed[0], equalTo(true));
	}

	@Test
	public void GivenEmptyColumns_WhenWidths_ThenEqualWidths() {
		assertThat(TableColumns.widths(List.of(Arrays.asList("", "")), 10, 20), equalTo(new int[] { 2500, 2500 }));
	}
}
