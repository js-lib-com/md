package com.jslib.md;

import java.io.File;
import java.util.Comparator;

/**
 * Natural order of file names: digit runs are compared by numeric value, so that <code>2-install.md</code> comes before
 * <code>10-usage.md</code>; other characters are compared as in {@link String#compareTo(String)}. Names that are equal
 * by numeric value, e.g. <code>1-intro.md</code> and <code>01-intro.md</code>, are ordered by plain string comparison.
 */
public final class NaturalOrder implements Comparator<String> {
	public static final NaturalOrder INSTANCE = new NaturalOrder();

	/** Files comparator, by file name natural order. */
	public static final Comparator<File> FILES = Comparator.comparing(File::getName, INSTANCE);

	private NaturalOrder() {
	}

	@Override
	public int compare(String name1, String name2) {
		int index1 = 0;
		int index2 = 0;
		while (index1 < name1.length() && index2 < name2.length()) {
			char char1 = name1.charAt(index1);
			char char2 = name2.charAt(index2);
			if (isDigit(char1) && isDigit(char2)) {
				int end1 = digitsEnd(name1, index1);
				int end2 = digitsEnd(name2, index2);
				int result = compareNumbers(name1.substring(index1, end1), name2.substring(index2, end2));
				if (result != 0) {
					return result;
				}
				index1 = end1;
				index2 = end2;
				continue;
			}
			if (char1 != char2) {
				return Character.compare(char1, char2);
			}
			++index1;
			++index2;
		}
		int result = Integer.compare(name1.length() - index1, name2.length() - index2);
		return result != 0 ? result : name1.compareTo(name2);
	}

	/** Compare digit runs by numeric value, without parsing, so that any number of digits is supported. */
	private static int compareNumbers(String digits1, String digits2) {
		String number1 = stripLeadingZeros(digits1);
		String number2 = stripLeadingZeros(digits2);
		int result = Integer.compare(number1.length(), number2.length());
		return result != 0 ? result : number1.compareTo(number2);
	}

	private static String stripLeadingZeros(String digits) {
		int index = 0;
		while (index < digits.length() - 1 && digits.charAt(index) == '0') {
			++index;
		}
		return digits.substring(index);
	}

	private static int digitsEnd(String name, int index) {
		while (index < name.length() && isDigit(name.charAt(index))) {
			++index;
		}
		return index;
	}

	private static boolean isDigit(char c) {
		return c >= '0' && c <= '9';
	}
}
