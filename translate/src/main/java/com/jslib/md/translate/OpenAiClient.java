package com.jslib.md.translate;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

/** Client for OpenAI compatible chat completions API, e.g. OpenAI or a local llama.cpp server. Thread safe. */
final class OpenAiClient implements CompletionClient {
	private static final Logger log = LoggerFactory.getLogger(OpenAiClient.class);

	private static final String OPENAI_HOST = "api.openai.com";
	private static final int MAX_ATTEMPTS = 3;
	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
	/** Generous since a local server may be slow or busy serving other requests. */
	private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(10);

	/** Strict about Markdown structure since local models tend to alter it. */
	static final String SYSTEM_PROMPT = "You are a professional translator. Reply with the translation only, without any comment. " //
			+ "Keep the markdown structure exactly as in the source: same lines and blank lines, headings, list markers, table rows and pipes, bold, italic, links and code. " //
			+ "Do not add characters that are not in the source. For Java properties keep keys unchanged and translate only values.";

	private final HttpClient httpClient;
	private final URI uri;
	private final String apiKey;
	private final String model;
	/** Add llama.cpp specific request fields on servers other than OpenAI, which rejects unknown fields. */
	private final boolean llamaCpp;
	private final Gson json = new Gson();

	/** Client configured from {@link TranslatorSettings}. */
	OpenAiClient() {
		this(HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build(), TranslatorSettings.baseUrl(), TranslatorSettings.apiKey(), TranslatorSettings.model());
	}

	/** API key and model are optional; model is omitted from request if null. */
	OpenAiClient(HttpClient httpClient, String baseUrl, String apiKey, String model) {
		this.httpClient = httpClient;
		this.uri = completionsUri(baseUrl);
		this.apiKey = apiKey;
		this.model = model;
		this.llamaCpp = !OPENAI_HOST.equalsIgnoreCase(uri.getHost());
	}

	URI uri() {
		return uri;
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
				throw new IOException("Translation service not reachable at " + uri + ". Please check OPENAI_BASE_URL.", e);
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
		return json.toJson(new Request(model, llamaCpp, prompt));
	}

	/** Extract completion content from response or throw exception if request failed or response is truncated. */
	static String content(Gson json, int statusCode, String body) throws IOException {
		if (statusCode != 200) {
			throw new IOException(String.format("Translation request failed with HTTP status %d: %s", statusCode, errorMessage(json, body)));
		}

		Response response;
		try {
			response = json.fromJson(body, Response.class);
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
			ErrorResponse error = json.fromJson(body, ErrorResponse.class);
			if (error != null && error.error != null && error.error.message != null) {
				return error.error.message;
			}
		} catch (RuntimeException e) {
			// response body is not a JSON error object, e.g. HTML from a gateway
		}
		return body.length() > 200 ? body.substring(0, 200) + "..." : body;
	}

	// --------------------------------------------------------------------------------------------
	// JSON messages; field names follow API, unused fields document the protocol

	@SuppressWarnings("unused")
	private static class Request {
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

		Request(String model, boolean llamaCpp, String prompt) {
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
	private static class Response {
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

	private static class ErrorResponse {
		ErrorDetail error;
	}

	@SuppressWarnings("unused")
	private static class ErrorDetail {
		String message;
		String type;
		String param;
		String code;
	}
}
