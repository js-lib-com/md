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
		// GIVEN
		double[] weights = new double[] { 1, 3 };

		// WHEN
		int[] widths = TableColumns.widths(weights, 10);

		// THEN
		assertThat(widths, equalTo(new int[] { 1250, 3750 }));
	}

	@Test
	public void GivenNarrowColumn_WhenWidths_ThenRaiseToThresholdAndShrinkOthers() {
		// GIVEN
		double[] weights = new double[] { 1, 100 };

		// WHEN
		int[] widths = TableColumns.widths(weights, 10);

		// THEN
		assertThat(widths, equalTo(new int[] { 500, 4500 }));
	}

	@Test
	public void GivenColumnPushedUnderThresholdByPinnedColumn_WhenWidths_ThenPinItToo() {
		// GIVEN
		// 22% initially, but under 20% after first column is raised to 20%
		double[] weights = new double[] { 5, 22, 73 };

		// WHEN
		int[] widths = TableColumns.widths(weights, 20);

		// THEN
		assertThat(widths, equalTo(new int[] { 1000, 1000, 3000 }));
	}

	@Test
	public void GivenThresholdTooLargeForColumnsCount_WhenWidths_ThenEqualWidths() {
		// GIVEN
		double[] weights = new double[] { 1, 50, 100 };

		// WHEN
		int[] widths = TableColumns.widths(weights, 50);

		// THEN
		assertThat(Arrays.stream(widths).sum(), equalTo(TableColumns.FULL_WIDTH));
		for (int width : widths) {
			assertThat(Math.abs(width - TableColumns.FULL_WIDTH / 3) <= 1, equalTo(true));
		}
	}

	@Test
	public void GivenRoundingError_WhenWidths_ThenSumIsFullWidth() {
		// GIVEN
		double[] weights = new double[] { 1, 1, 1, 1, 1, 1, 1 };

		// WHEN
		int[] widths = TableColumns.widths(weights, 0);

		// THEN
		assertThat(Arrays.stream(widths).sum(), equalTo(TableColumns.FULL_WIDTH));
	}

	@Test
	public void GivenCellTexts_WhenWidths_ThenUseAverageTextLengthPerColumn() {
		// GIVEN
		// average lengths 2.5 and 15, that is, 14.29% and 85.71%
		List<List<String>> rows = Arrays.asList(Arrays.asList("Name", "Description"), Arrays.asList(" a ", "long long long text"));

		// WHEN
		int[] widths = TableColumns.widths(rows, 10, 0);

		// THEN
		assertThat(widths, equalTo(new int[] { 714, 4286 }));
	}

	@Test
	public void GivenOutlierCell_WhenTrimmedMean_ThenIgnoreShortestAndLongest() {
		// GIVEN
		// 20% of 5 cells: one cell dropped from each end
		int[] lengths = new int[] { 240, 8, 12, 9, 10 };

		// WHEN
		double mean = TableColumns.trimmedMean(lengths, 20);

		// THEN
		assertThat(mean, closeTo(31.0 / 3, 1e-9));
	}

	@Test
	public void GivenTrimPercentTooSmallForCellsCount_WhenTrimmedMean_ThenUseAllCells() {
		// GIVEN
		// 10% of 5 cells, rounded down, drops nothing
		int[] fiveLengths = new int[] { 240, 8, 12, 9, 10 };
		// 20% of 4 cells, rounded down, drops nothing
		int[] fourLengths = new int[] { 1, 2, 3, 100 };

		// WHEN
		double fiveMean = TableColumns.trimmedMean(fiveLengths, 10);
		double fourMean = TableColumns.trimmedMean(fourLengths, 20);

		// THEN
		assertThat(fiveMean, closeTo(55.8, 1e-9));
		assertThat(fourMean, closeTo(26.5, 1e-9));
	}

	@Test
	public void GivenZeroOutliersPercent_WhenTrimmedMean_ThenPlainMean() {
		// GIVEN
		int[] lengths = new int[] { 240, 8, 12, 9, 10, 0, 0, 0, 0, 0 };

		// WHEN
		double mean = TableColumns.trimmedMean(lengths, 0);

		// THEN
		assertThat(mean, closeTo(27.9, 1e-9));
	}

	@Test
	public void GivenMaximumOutliersPercent_WhenTrimmedMean_ThenMedian() {
		// GIVEN
		int[] oddLengths = new int[] { 240, 8, 12, 9, 10 };
		int[] evenLengths = new int[] { 1, 2, 3, 100 };

		// WHEN
		double oddMean = TableColumns.trimmedMean(oddLengths, 50);
		double evenMean = TableColumns.trimmedMean(evenLengths, 50);

		// THEN
		assertThat(oddMean, closeTo(10, 1e-9));
		assertThat(evenMean, closeTo(2.5, 1e-9));
	}

	@Test
	public void GivenColumnWithOneLongCell_WhenWidths_ThenOutlierDoesNotWidenColumn() {
		// GIVEN
		// header and 4 rows; second column has a single long note
		List<List<String>> rows = Arrays.asList( //
				Arrays.asList("Description of the step", "Note"), //
				Arrays.asList("Open login form from menu", "optional"), //
				Arrays.asList("Enter user name and password", "required"), //
				Arrays.asList("Press the login button now", "-"), //
				Arrays.asList("Wait for the main window", "x".repeat(240)));

		// WHEN
		int[] plain = TableColumns.widths(rows, 10, 0);
		int[] trimmed = TableColumns.widths(rows, 10, 20);

		// THEN
		// mean of note lengths is 52.2, more than description; trimmed mean is 6.7
		assertThat(plain[1] > plain[0], equalTo(true));
		assertThat(trimmed[1] < trimmed[0], equalTo(true));
	}

	@Test
	public void GivenEmptyColumns_WhenWidths_ThenEqualWidths() {
		// GIVEN
		List<List<String>> rows = List.of(Arrays.asList("", ""));

		// WHEN
		int[] widths = TableColumns.widths(rows, 10, 20);

		// THEN
		assertThat(widths, equalTo(new int[] { 2500, 2500 }));
	}
}
