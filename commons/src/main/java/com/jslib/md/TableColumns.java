package com.jslib.md;

import java.util.List;

/**
 * Table columns width computed from the quantity of text in every column. Column weight is the average text length of
 * its cells, header included, and column width is proportional to its weight, but not less than a minimal threshold.
 * Columns raised to the threshold take their space from the other columns, proportionally with their weights.
 * <p>
 * Widths are expressed in fiftieths of a percent, the unit used by WordprocessingML for percent widths; widths sum is
 * always {@link #FULL_WIDTH}, that is, 100%.
 */
public final class TableColumns {
	/** Full width, 100%, in fiftieths of a percent. */
	public static final int FULL_WIDTH = 5000;

	private TableColumns() {
	}

	/**
	 * Compute columns width for table text, given as rows of cell texts. All rows should have the same number of
	 * cells; missing cells are considered empty.
	 *
	 * @param rows table cell texts, header row included,
	 * @param minPercent minimal column width, in percent; if too large for columns count it is reduced to equal widths.
	 * @return columns width in fiftieths of a percent.
	 */
	public static int[] widths(List<List<String>> rows, int minPercent) {
		int columnsCount = rows.stream().mapToInt(List::size).max().orElse(0);
		double[] weights = new double[columnsCount];
		for (int column = 0; column < columnsCount; ++column) {
			int length = 0;
			for (List<String> row : rows) {
				length += column < row.size() ? row.get(column).strip().length() : 0;
			}
			// empty columns still need some space
			weights[column] = Math.max((double) length / rows.size(), 1);
		}
		return widths(weights, minPercent);
	}

	public static int[] widths(double[] weights, int minPercent) {
		int count = weights.length;
		if (count == 0) {
			return new int[0];
		}
		double min = Math.min(Math.max(minPercent, 0), 100.0 / count);

		// columns pinned to minimal width; repeat since pinning a column reduces space left for the others
		boolean[] pinned = new boolean[count];
		double[] percents = new double[count];
		boolean changed = true;
		while (changed) {
			changed = false;
			int pinnedCount = 0;
			double freeWeights = 0;
			for (int i = 0; i < count; ++i) {
				if (pinned[i]) {
					++pinnedCount;
				} else {
					freeWeights += weights[i];
				}
			}
			double freePercent = 100 - min * pinnedCount;
			for (int i = 0; i < count; ++i) {
				if (pinned[i]) {
					percents[i] = min;
					continue;
				}
				percents[i] = freePercent * weights[i] / freeWeights;
				if (percents[i] < min) {
					pinned[i] = true;
					changed = true;
				}
			}
		}

		// round to fiftieths of a percent and give rounding error to the widest column so that sum is exactly 100%
		int[] widths = new int[count];
		int sum = 0;
		int widest = 0;
		for (int i = 0; i < count; ++i) {
			widths[i] = (int) Math.round(percents[i] * FULL_WIDTH / 100);
			sum += widths[i];
			if (widths[i] > widths[widest]) {
				widest = i;
			}
		}
		widths[widest] += FULL_WIDTH - sum;
		return widths;
	}
}
