package com.jslib.md.cli;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.jslib.md.CommandHandler;

/**
 * Translate document sources from English to Romanian using an OpenAI compatible chat completions API, by default the
 * local llama.cpp server at {@value #DEFAULT_BASE_URL}. Settings are read from environment variables, with system
 * properties of the same name as fallback:
 * <ul>
 * <li><code>OPENAI_BASE_URL</code> - API base URL, e.g. <code>https://api.openai.com/v1</code>,
 * <li><code>OPENAI_API_KEY</code> - API key, with <code>OPENAI_KEY</code> as fallback; optional, since local server does
 * not require it,
 * <li><code>OPENAI_MODEL</code> - model name; if missing, server uses its loaded model,
 * <li><code>TRANSLATE_PARALLEL</code> - number of chunks translated concurrently; default {@value #DEFAULT_PARALLEL},
 * matching local server slots.
 * </ul>
 */
public class Translator implements CommandHandler {
	private static final Logger log = LoggerFactory.getLogger(Translator.class);

	/** Local llama.cpp server. */
	static final String DEFAULT_BASE_URL = "http://jarvis.local/v1";

	/** Local llama.cpp server processes this many requests in parallel; more requests are queued by server. */
	static final int DEFAULT_PARALLEL = 4;

	private static final String[] FILES = { "document.md", "revisions.md", "document.properties" };

	/**
	 * Translate document sources from document directory and write translated files beside, with <code>_ro</code>
	 * suffix. A target file is written only if all its chunks are translated; translation continues with next file on
	 * failure and execution fails at the end if any file was not translated.
	 */
	@Override
	public void execute(File documentDir) throws Exception {
		Translator translator = client != null ? this : new Translator(new OpenAiClient(HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build(), baseUrl(), apiKey(), model()), parallel());
		log.debug("Translate using {} with {} parallel requests", baseUrl(), translator.parallel);

		int translated = 0;
		int failed = 0;
		for (String file : FILES) {
			Path source = documentDir.toPath().resolve(file);
			if (!Files.exists(source)) {
				log.warn("Skip missing file {}", source);
				continue;
			}
			String format = file.endsWith(".md") ? "markdown" : "Java properties";
			Path target = i18n(source, "ro");
			try {
				String text = translator.translate(Files.readString(source, UTF_8), format, "English", "Romanian");
				Files.writeString(target, text, UTF_8);
				log.info("Translate {} to {}", source, target);
				++translated;
			} catch (IOException e) {
				log.error("Fail to translate {}: {}", source, e.getMessage());
				++failed;
			}
		}

		if (failed > 0) {
			throw new IOException(String.format("Fail to translate %d of %d files.", failed, failed + translated));
		}
		if (translated == 0) {
			throw new FileNotFoundException("No source files to translate in " + documentDir);
		}
	}

	/** Localized file path, e.g. <code>dir/document.md</code> to <code>dir/document_ro.md</code>. */
	static Path i18n(Path file, String language) {
		String fileName = file.getFileName().toString();
		int extensionPosition = fileName.lastIndexOf('.');
		String localizedName = extensionPosition > 0 //
				? fileName.substring(0, extensionPosition) + '_' + language + fileName.substring(extensionPosition)
				: fileName + '_' + language;
		return file.resolveSibling(localizedName);
	}

	static String baseUrl() {
		String baseUrl = setting("OPENAI_BASE_URL");
		return baseUrl != null ? baseUrl : DEFAULT_BASE_URL;
	}

	/** Optional API key, null if not configured. */
	static String apiKey() {
		return firstNonEmpty(setting("OPENAI_API_KEY"), setting("OPENAI_KEY"));
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
		return firstNonEmpty(System.getenv(name), System.getProperty(name));
	}

	private static String firstNonEmpty(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}

	// --------------------------------------------------------------------------------------------

	/** Chunk length, in characters, that keeps translation under completion tokens limit; deduced empirically. */
	static final int CHUNK_LENGTH = 3000;

	/**
	 * Blank lines, possibly with white spaces, between paragraphs, whatever line separator. Line breaks are atomic since
	 * otherwise backtracking matches CRLF as two line breaks, that is, as a blank line.
	 */
	private static final Pattern PARAGRAPHS_SEPARATOR = Pattern.compile("(?>\\R)(?:[ \\t]*(?>\\R))+");

	@FunctionalInterface
	interface Client {
		String complete(String prompt) throws IOException, InterruptedException;
	}

	/** Client is used concurrently and must be thread safe. */
	private final Client client;
	private final int parallel;

	public Translator() {
		this(null, DEFAULT_PARALLEL);
	}

	Translator(Client client, int parallel) {
		this.client = client;
		this.parallel = parallel;
	}

	/**
	 * Translate text split into chunks of paragraphs, up to configured number of chunks concurrently. Translated chunks
	 * are joined in source order with a blank line, using source text line separator, and the result ends with a line
	 * separator. On first chunk failure pending chunks are cancelled and the failure is rethrown.
	 */
	String translate(String text, String format, String sourceLanguage, String targetLanguage) throws IOException, InterruptedException {
		String lineSeparator = text.contains("\r\n") ? "\r\n" : "\n";
		String paragraphsSeparator = lineSeparator + lineSeparator;

		List<String> chunks = chunks(text, paragraphsSeparator, CHUNK_LENGTH);
		ExecutorService executor = Executors.newFixedThreadPool(Math.max(1, Math.min(parallel, chunks.size())), daemonThreads());
		try {
			CompletionService<String> completion = new ExecutorCompletionService<>(executor);
			List<Future<String>> futures = new ArrayList<>();
			for (int i = 0; i < chunks.size(); ++i) {
				String chunk = chunks.get(i);
				String prompt = String.format("Translate this %s %s text to %s:\n\n%s", sourceLanguage, format, targetLanguage, chunk);
				int index = i + 1;
				futures.add(completion.submit(() -> translateChunk(index, chunk, prompt, lineSeparator)));
			}

			// wait in completion order to fail fast on first failed chunk
			for (int i = 0; i < futures.size(); ++i) {
				try {
					completion.take().get();
				} catch (ExecutionException e) {
					throw rethrow(e.getCause());
				}
				log.debug("Translated {} of {} chunks", i + 1, futures.size());
			}

			List<String> translations = new ArrayList<>();
			for (Future<String> future : futures) {
				translations.add(future.get());
			}
			return String.join(paragraphsSeparator, translations) + lineSeparator;
		} catch (ExecutionException e) {
			// not reachable since all futures are already completed successfully
			throw new IllegalStateException(e);
		} finally {
			// cancel pending chunks on failure
			executor.shutdownNow();
		}
	}

	/** Attempts to get a translation with the same markdown structure as the source chunk. */
	static final int STRUCTURE_ATTEMPTS = 3;

	/**
	 * Translate chunk, repair known model artifacts and use source line separator. Local models occasionally damage
	 * markdown structure, e.g. drop a table row or prefix a line with garbage; translation is retried if structure differs
	 * from source and, if still different after all attempts, the last translation is used with a warning.
	 */
	private String translateChunk(int index, String chunk, String prompt, String lineSeparator) throws IOException, InterruptedException {
		String translation = null;
		for (int attempt = 1; attempt <= STRUCTURE_ATTEMPTS; ++attempt) {
			translation = repair(chunk, client.complete(prompt).strip());
			String difference = structureDifference(chunk, translation);
			if (difference == null) {
				break;
			}
			if (attempt < STRUCTURE_ATTEMPTS) {
				log.debug("Chunk {} translation differs from source: {}; retry {} of {}.", index, difference, attempt, STRUCTURE_ATTEMPTS - 1);
			} else {
				log.warn("Chunk {} translation differs from source: {}. Please review translation starting with: {}", index, difference, firstLine(translation));
			}
		}
		return LINE_SEPARATOR.matcher(translation).replaceAll(lineSeparator);
	}

	private static final Pattern LINE_SEPARATOR = Pattern.compile("\\R");

	/**
	 * Garbage model emits sometimes at line start, before a markdown marker: backslashes or a slash, e.g.
	 * <code>\\| cell |</code> or <code>/ - item</code>.
	 */
	private static final Pattern LINE_PREFIX_ARTIFACT = Pattern.compile("(?m)^([ \\t]*)(?:\\\\+ ?|/ )(?=[|#*-]|\\d+\\. )");

	/** Lines starting with backslash or slash in source, in which case line prefix artifacts are not repaired. */
	private static final Pattern SOURCE_LINE_PREFIX = Pattern.compile("(?m)^[ \\t]*[\\\\/]");

	/** Line with only backslashes or a slash, model emits sometimes between list items or table rows. */
	private static final Pattern LINE_ARTIFACT = Pattern.compile("(?m)^[ \\t]*(?:\\\\+|/)[ \\t]*(?:\\R|$)");

	static String repair(String source, String translation) {
		if (SOURCE_LINE_PREFIX.matcher(source).find()) {
			return translation;
		}
		String repaired = LINE_ARTIFACT.matcher(translation).replaceAll("");
		return LINE_PREFIX_ARTIFACT.matcher(repaired).replaceAll("$1");
	}

	/** Translation keeps source lines, so a different count means inserted garbage or merged lines. */
	private static final Pattern NON_BLANK_LINE = Pattern.compile("(?m)^[ \\t]*\\S");
	private static final Pattern HEADING = Pattern.compile("(?m)^#+ ");
	private static final Pattern TABLE_ROW = Pattern.compile("(?m)^[ \\t]*\\|");
	private static final Pattern LIST_ITEM = Pattern.compile("(?m)^[ \\t]*(?:[-*+]|\\d+\\.) ");

	/** Describe markdown structure difference between source and translation, or null if same structure. */
	static String structureDifference(String source, String translation) {
		List<String> differences = new ArrayList<>();
		compare(differences, "lines", NON_BLANK_LINE, source, translation);
		compare(differences, "headings", HEADING, source, translation);
		compare(differences, "table rows", TABLE_ROW, source, translation);
		compare(differences, "list items", LIST_ITEM, source, translation);
		return differences.isEmpty() ? null : String.join(", ", differences);
	}

	private static void compare(List<String> differences, String name, Pattern pattern, String source, String translation) {
		long sourceCount = pattern.matcher(source).results().count();
		long translationCount = pattern.matcher(translation).results().count();
		if (sourceCount != translationCount) {
			differences.add(String.format("%d %s instead of %d", translationCount, name, sourceCount));
		}
	}

	private static String firstLine(String text) {
		String line = text.lines().findFirst().orElse("");
		return line.length() > 80 ? line.substring(0, 80) + "..." : line;
	}

	private static IOException rethrow(Throwable cause) throws InterruptedException {
		if (cause instanceof IOException) {
			return (IOException) cause;
		}
		if (cause instanceof InterruptedException) {
			throw (InterruptedException) cause;
		}
		if (cause instanceof RuntimeException) {
			throw (RuntimeException) cause;
		}
		if (cause instanceof java.lang.Error) {
			throw (java.lang.Error) cause;
		}
		return new IOException(cause);
	}

	/** Daemon threads do not keep JVM alive if a cancelled request ignores interruption. */
	private static ThreadFactory daemonThreads() {
		AtomicInteger index = new AtomicInteger();
		return runnable -> {
			Thread thread = new Thread(runnable, "translate-" + index.incrementAndGet());
			thread.setDaemon(true);
			return thread;
		};
	}

	/** Group paragraphs into chunks of at least given length, except the last one. Paragraphs are never split. */
	static List<String> chunks(String text, String paragraphsSeparator, int chunkLength) {
		List<String> chunks = new ArrayList<>();
		StringBuilder chunk = new StringBuilder();
		for (String paragraph : PARAGRAPHS_SEPARATOR.split(text)) {
			if (paragraph.isBlank()) {
				continue;
			}
			if (chunk.length() > 0) {
				chunk.append(paragraphsSeparator);
			}
			chunk.append(paragraph.strip());
			if (chunk.length() >= chunkLength) {
				chunks.add(chunk.toString());
				chunk.setLength(0);
			}
		}
		if (chunk.length() > 0) {
			chunks.add(chunk.toString());
		}
		return chunks;
	}

	// --------------------------------------------------------------------------------------------

	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

	/** Generous since a local server may be slow or busy serving other requests. */
	private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(10);

	static class OpenAiClient implements Client {
		private static final String OPENAI_HOST = "api.openai.com";
		private static final int MAX_ATTEMPTS = 3;

		private final HttpClient httpClient;
		private final URI uri;
		private final String apiKey;
		private final String model;
		/** Add llama.cpp specific request fields on servers other than OpenAI, which rejects unknown fields. */
		private final boolean llamaCpp;
		private final Gson json;

		/** API key and model are optional; model is omitted from request if null. */
		OpenAiClient(HttpClient httpClient, String baseUrl, String apiKey, String model) {
			this.httpClient = httpClient;
			this.uri = completionsUri(baseUrl);
			this.apiKey = apiKey;
			this.model = model;
			this.llamaCpp = !OPENAI_HOST.equalsIgnoreCase(uri.getHost());
			this.json = new Gson();
		}

		/** Retry on server errors since local model output occasionally fails server side parsing. */
		@Override
		public String complete(String prompt) throws IOException, InterruptedException {
			HttpRequest.Builder request = HttpRequest.newBuilder() //
					.uri(uri) //
					.timeout(REQUEST_TIMEOUT) //
					.header("Content-Type", "application/json") //
					.POST(HttpRequest.BodyPublishers.ofString(requestBody(json, model, llamaCpp, prompt), UTF_8));
			if (apiKey != null) {
				request.header("Authorization", "Bearer " + apiKey);
			}

			for (int attempt = 1;; ++attempt) {
				HttpResponse<String> response;
				try {
					response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString(UTF_8));
				} catch (ConnectException e) {
					throw new IOException("Translation service not reachable at " + uri, e);
				} catch (HttpTimeoutException e) {
					throw new IOException("Translation service timeout at " + uri, e);
				}
				if (response.statusCode() >= 500 && attempt < MAX_ATTEMPTS) {
					log.warn("Translation request failed with HTTP status {}; retry {} of {}.", response.statusCode(), attempt, MAX_ATTEMPTS - 1);
					continue;
				}
				return content(json, response.statusCode(), response.body());
			}
		}

		/** Chat completions endpoint from API base URL, with or without trailing slash. */
		static URI completionsUri(String baseUrl) {
			String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
			return URI.create(base + "/chat/completions");
		}

		static String requestBody(Gson json, String model, boolean llamaCpp, String prompt) {
			return json.toJson(new OpenAiRequest(model, llamaCpp, prompt));
		}

		/** Extract completion content from response or throw exception if request failed or response is truncated. */
		static String content(Gson json, int statusCode, String body) throws IOException {
			if (statusCode != 200) {
				throw new IOException(String.format("Translation request failed with HTTP status %d: %s", statusCode, errorMessage(json, body)));
			}

			OpenAiResponse response;
			try {
				response = json.fromJson(body, OpenAiResponse.class);
			} catch (JsonParseException e) {
				throw new IOException("Invalid translation response: " + e.getMessage(), e);
			}
			if (response == null || response.choices == null || response.choices.length == 0 || response.choices[0].message == null) {
				throw new IOException("Translation response has no completion: " + body);
			}
			Choice choice = response.choices[0];
			if ("length".equals(choice.finish_reason)) {
				throw new IOException("Translation response is truncated by tokens limit. Please reduce chunk length.");
			}
			if (choice.message.content == null) {
				throw new IOException("Translation response has no content: " + body);
			}
			return choice.message.content;
		}

		private static String errorMessage(Gson json, String body) {
			try {
				OpenAiError error = json.fromJson(body, OpenAiError.class);
				if (error != null && error.error != null && error.error.message != null) {
					return error.error.message;
				}
			} catch (RuntimeException e) {
				// response body is not a JSON error object, e.g. HTML from a gateway
			}
			return body.length() > 200 ? body.substring(0, 200) + "..." : body;
		}
	}

	/** Strict about markdown structure since local models tend to alter it. */
	static final String SYSTEM_PROMPT = "You are a professional translator. Reply with the translation only, without any comment. " //
			+ "Keep the markdown structure exactly as in the source: same lines and blank lines, headings, list markers, table rows and pipes, bold, italic, links and code. " //
			+ "Do not add characters that are not in the source. For Java properties keep keys unchanged and translate only values.";

	@SuppressWarnings("unused")
	private static class OpenAiRequest {
		final Message[] messages;
		final int max_tokens;
		final int n;
		final float temperature;
		final String model;
		/**
		 * Chat template arguments for llama.cpp server, which runs thinking models with thinking enabled by default. With
		 * thinking enabled Gemma 4 often answers with an empty thinking channel the server fails to parse, responding with
		 * HTTP status 500 "Failed to parse input". With thinking disabled this is rare and handled by retry.
		 */
		final TemplateArguments chat_template_kwargs;

		OpenAiRequest(String model, boolean llamaCpp, String prompt) {
			this.messages = new Message[] { //
					new Message("system", SYSTEM_PROMPT), //
					new Message("user", prompt) //
			};
			this.max_tokens = 2400;
			this.n = 1;
			this.temperature = 0.2F;
			this.model = model;
			this.chat_template_kwargs = llamaCpp ? new TemplateArguments(false) : null;
		}
	}

	@SuppressWarnings("unused")
	private static class TemplateArguments {
		final boolean enable_thinking;

		TemplateArguments(boolean enableThinking) {
			this.enable_thinking = enableThinking;
		}
	}

	@SuppressWarnings("unused")
	private static class Message {
		String role;
		String content;

		Message() {
		}

		Message(String role, String content) {
			this.role = role;
			this.content = content;
		}
	}

	@SuppressWarnings("unused")
	private static class OpenAiResponse {
		String id;
		String object;
		long created;
		String model;
		Choice[] choices;
		Usage usage;
	}

	@SuppressWarnings("unused")
	private static class Choice {
		int index;
		Message message;
		String finish_reason;
	}

	@SuppressWarnings("unused")
	private static class Usage {
		int prompt_tokens;
		int completion_tokens;
		int total_tokens;
	}

	private static class OpenAiError {
		Error error;
	}

	@SuppressWarnings("unused")
	private static class Error {
		String message;
		String type;
		String param;
		String code;
	}
}
