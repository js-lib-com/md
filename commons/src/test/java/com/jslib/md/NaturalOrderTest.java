package com.jslib.md;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class NaturalOrderTest {
	@Test
	public void GivenNumericPrefixes_WhenSort_ThenNumericOrder() {
		// GIVEN
		List<String> names = new ArrayList<>(List.of("10-usage.md", "2-install.md", "1-intro.md", "100-appendix.md"));

		// WHEN
		names.sort(NaturalOrder.INSTANCE);

		// THEN
		assertThat(names, contains("1-intro.md", "2-install.md", "10-usage.md", "100-appendix.md"));
	}

	@Test
	public void GivenNumbersInsideName_WhenSort_ThenNumericOrder() {
		// GIVEN
		List<String> names = new ArrayList<>(List.of("chapter12.md", "chapter2.md", "chapter1b.md", "chapter1a.md", "appendix.md"));

		// WHEN
		names.sort(NaturalOrder.INSTANCE);

		// THEN
		assertThat(names, contains("appendix.md", "chapter1a.md", "chapter1b.md", "chapter2.md", "chapter12.md"));
	}

	@Test
	public void GivenLeadingZeros_WhenCompare_ThenEqualValueOrderedByPlainString() {
		assertThat(NaturalOrder.INSTANCE.compare("02-install.md", "10-usage.md"), lessThan(0));
		assertThat(NaturalOrder.INSTANCE.compare("01-intro.md", "1-intro.md"), lessThan(0));
		assertThat(NaturalOrder.INSTANCE.compare("1-intro.md", "01-intro.md"), greaterThan(0));
		assertThat(NaturalOrder.INSTANCE.compare("01-intro.md", "01-intro.md"), equalTo(0));
	}

	@Test
	public void GivenNumbersLongerThanLong_WhenCompare_ThenNumericOrder() {
		assertThat(NaturalOrder.INSTANCE.compare("99999999999999999999.md", "100000000000000000000.md"), lessThan(0));
	}

	@Test
	public void GivenPrefix_WhenCompare_ThenShorterFirst() {
		assertThat(NaturalOrder.INSTANCE.compare("intro", "intro.md"), lessThan(0));
		assertThat(NaturalOrder.INSTANCE.compare("intro2", "intro"), greaterThan(0));
	}
}
