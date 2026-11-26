package com.jslib.md.translate;

/**
 * Translation service settings, read from environment variables, with system properties of the same name as fallback:
 * <ul>
 * <li><code>OPENAI_BASE_URL</code> - API base URL, default {@value #DEFAULT_BASE_URL}, the llama.cpp server default,
 * <li><code>OPENAI_API_KEY</code> - API key, with <code>OPENAI_KEY</code> as fallback; optional, since a local server
 * does not require it,
 * <li><code>OPENAI_MODEL</code> - model name; if missing, server uses its loaded model,
 * <li><code>TRANSLATE_PARALLEL</code> - number of chunks translated concurrently; default {@value #DEFAULT_PARALLEL},
 * matching local server slots.
 * </ul>
 */
final class TranslatorSettings {
	/** Local llama.cpp server on its default port. */
	static final String DEFAULT_BASE_URL = "http://localhost:8080/v1";

	/** Local llama.cpp server processes this many requests in parallel; more requests are queued by server. */
	static final int DEFAULT_PARALLEL = 4;

	private TranslatorSettings() {
	}

	static String baseUrl() {
		String baseUrl = setting("OPENAI_BASE_URL");
		return baseUrl != null ? baseUrl : DEFAULT_BASE_URL;
	}

	/** Optional API key, null if not configured. */
	static String apiKey() {
		return firstNonBlank(setting("OPENAI_API_KEY"), setting("OPENAI_KEY"));
	}

	/** Optional model, null if not configured. */
	static String model() {
		return setting("OPENAI_MODEL");
	}

	static int parallel() {
		String parallel = setting("TRANSLATE_PARALLEL");
		if (parallel == null) {
			return DEFAULT_PARALLEL;
		}
		try {
			int value = Integer.parseInt(parallel.strip());
			if (value > 0) {
				return value;
			}
		} catch (NumberFormatException e) {
			// fall through to invalid value error
		}
		throw new IllegalArgumentException("Invalid TRANSLATE_PARALLEL value: " + parallel + ". Please use a positive integer.");
	}

	private static String setting(String name) {
		return firstNonBlank(System.getenv(name), System.getProperty(name));
	}

	private static String firstNonBlank(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}
}
