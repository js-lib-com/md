package com.jslib.md.cli;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

/**
 * Translate document sources from English to Romanian using OpenAI chat completions API. API key is read from
 * <code>OPENAI_API_KEY</code> environment variable, with <code>OPENAI_KEY</code> environment variable and system property
 * as fallback. Model can be changed with <code>OPENAI_MODEL</code> environment variable or system property.
 */
public class Translator {
	public static void main(String[] args) {
		String[] files = { "document.md", "revisions.md", "document.properties" };
		Path workingDir = Path.of(args.length > 0 ? args[0] : ".");

		Translator translator;
		try {
			translator = new Translator(new OpenAiClient(HttpClient.newHttpClient(), apiKey(), model()));
		} catch (IllegalStateException e) {
			System.err.println(e.getMessage());
			return;
		}

		for (String file : files) {
			Path source = workingDir.resolve(file);
			if (!Files.exists(source)) {
				System.out.printf("Skip missing file %s%n", source);
				continue;
			}
			String format = file.endsWith(".md") ? "markdown" : "Java properties";
			Path target = i18n(source, "ro");
			try {
				String text = translator.translate(Files.readString(source, UTF_8), format, "English", "Romanian");
				// target file is written only if all source chunks were successfully translated
				Files.writeString(target, text, UTF_8);
				System.out.printf("Translate %s to %s%n", source, target);
			} catch (IOException e) {
				System.err.printf("Fail to translate %s: %s%n", source, e.getMessage());
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				System.err.println("Translation interrupted.");
				return;
			}
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

	static String apiKey() {
		String apiKey = firstNonEmpty(System.getenv("OPENAI_API_KEY"), System.getenv("OPENAI_KEY"), System.getProperty("OPENAI_KEY"));
		if (apiKey == null) {
			throw new IllegalStateException("Missing OpenAI API key. Please set OPENAI_API_KEY environment variable.");
		}
		return apiKey;
	}

	static String model() {
		String model = firstNonEmpty(System.getenv("OPENAI_MODEL"), System.getProperty("OPENAI_MODEL"));
		return model != null ? model : OpenAiClient.DEFAULT_MODEL;
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

	/** Blank lines, possibly with white spaces, between paragraphs, whatever line separator. */
	private static final Pattern PARAGRAPHS_SEPARATOR = Pattern.compile("\\R(?:[ \\t]*\\R)+");

	@FunctionalInterface
	interface Client {
		String complete(String prompt) throws IOException, InterruptedException;
	}

	private final Client client;

	Translator(Client client) {
		this.client = client;
	}

	/**
	 * Translate text split into chunks of paragraphs. Translated chunks are joined with a blank line, using source text
	 * line separator, and the result ends with a line separator.
	 */
	String translate(String text, String format, String sourceLanguage, String targetLanguage) throws IOException, InterruptedException {
		String lineSeparator = text.contains("\r\n") ? "\r\n" : "\n";
		String paragraphsSeparator = lineSeparator + lineSeparator;

		List<String> translations = new ArrayList<>();
		for (String chunk : chunks(text, paragraphsSeparator, CHUNK_LENGTH)) {
			String prompt = String.format("Translate the following %s %s text to %s while preserving original formatting characters:\n\n%s", sourceLanguage, format, targetLanguage, chunk);
			translations.add(client.complete(prompt).strip());
		}
		return String.join(paragraphsSeparator, translations) + lineSeparator;
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

	static class OpenAiClient implements Client {
		static final String DEFAULT_MODEL = "gpt-4o-mini";
		private static final URI OPENAI_URI = URI.create("https://api.openai.com/v1/chat/completions");

		private final HttpClient httpClient;
		private final String apiKey;
		private final String model;
		private final Gson json;

		OpenAiClient(HttpClient httpClient, String apiKey, String model) {
			this.httpClient = httpClient;
			this.apiKey = apiKey;
			this.model = model;
			this.json = new Gson();
		}

		@Override
		public String complete(String prompt) throws IOException, InterruptedException {
			HttpRequest request = HttpRequest.newBuilder() //
					.uri(OPENAI_URI) //
					.header("Content-Type", "application/json") //
					.header("Authorization", "Bearer " + apiKey) //
					.POST(HttpRequest.BodyPublishers.ofString(requestBody(json, model, prompt), UTF_8)) //
					.build();

			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(UTF_8));
			return content(json, response.statusCode(), response.body());
		}

		static String requestBody(Gson json, String model, String prompt) {
			return json.toJson(new OpenAiRequest(model, prompt));
		}

		/** Extract completion content from response or throw exception if request failed or response is truncated. */
		static String content(Gson json, int statusCode, String body) throws IOException {
			if (statusCode != 200) {
				throw new IOException(String.format("OpenAI request failed with HTTP status %d: %s", statusCode, errorMessage(json, body)));
			}

			OpenAiResponse response;
			try {
				response = json.fromJson(body, OpenAiResponse.class);
			} catch (JsonParseException e) {
				throw new IOException("Invalid OpenAI response: " + e.getMessage(), e);
			}
			if (response == null || response.choices == null || response.choices.length == 0 || response.choices[0].message == null) {
				throw new IOException("OpenAI response has no completion: " + body);
			}
			Choice choice = response.choices[0];
			if ("length".equals(choice.finish_reason)) {
				throw new IOException("OpenAI response is truncated by tokens limit. Please reduce chunk length.");
			}
			if (choice.message.content == null) {
				throw new IOException("OpenAI response has no content: " + body);
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

	@SuppressWarnings("unused")
	private static class OpenAiRequest {
		final Message[] messages;
		final int max_tokens;
		final int n;
		final float temperature;
		final String model;

		OpenAiRequest(String model, String prompt) {
			this.messages = new Message[] { //
					new Message("system", "You are a helpful assistant that translates formatted text. For Java properties file please preserve original keys."), //
					new Message("user", prompt) //
			};
			this.max_tokens = 2400;
			this.n = 1;
			this.temperature = 0.5F;
			this.model = model;
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
