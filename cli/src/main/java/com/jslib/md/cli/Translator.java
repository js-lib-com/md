package com.jslib.md.cli;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import com.jslib.api.json.Json;
import com.jslib.json.JsonImpl;
import com.jslib.util.Strings;

public class Translator {
	public static void main(String[] args) {
		String[] files = { "document.md", "revisions.md", "document.properties" };

		for (String file : files) {
			try {
				String text = translate(Files.readString(Path.of(file)), "English", "Romanian");
				Files.write(Path.of(i18n(file, "ro")), text.getBytes());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	static String i18n(String fileName, String language) {
		String[] parts = fileName.split("\\.");
		return Strings.concat(parts[0], '_', language, '.', parts[1]);
	}

	private static final String PARAGRAPHS_SEPARATOR = System.lineSeparator() + System.lineSeparator();
	
	static String translate(String text, String sourceLanguage, String targetLanguage) {
		String translation = "";
		String sourceParagraphs = "";

		for (String sourceParagraph : text.split(PARAGRAPHS_SEPARATOR)) {
			if (sourceParagraph.isEmpty()) {
				continue;
			}
			sourceParagraphs += sourceParagraph + PARAGRAPHS_SEPARATOR;
			// magic limit of 3000 characters is deduced empirically
			if (sourceParagraphs.length() >= 3000) {
				System.out.println("source paragraphs: " + sourceParagraphs);
				translation += openAiTranslate(sourceParagraphs, sourceLanguage, targetLanguage);
				sourceParagraphs = "";
			}
		}

		if (!sourceParagraphs.isEmpty()) {
			System.out.println("source paragraphs: " + sourceParagraphs);
			translation += openAiTranslate(sourceParagraphs, sourceLanguage, targetLanguage);
		}

		return translation;
	}

	private static final URI OPENAI_URI = URI.create("https://api.openai.com/v1/chat/completions");

	static String openAiTranslate(String text, String sourceLanguage, String targetLanguage) {
		String prompt = String.format("Translate the following %s markdown text to %s while preserving original formatting characters:\n\n%s", sourceLanguage, targetLanguage, text);
		OpenAiRequest openAiRequest = new OpenAiRequest(prompt);
		Json json = new JsonImpl();

		try {
			HttpClient client = HttpClient.newHttpClient();
			HttpRequest request = HttpRequest.newBuilder() //
					.uri(OPENAI_URI) //
					.header("Content-Type", "application/json") //
					.header("Authorization", "Bearer " + System.getProperty("OPENAI_KEY")) //
					.POST(HttpRequest.BodyPublishers.ofString(json.stringify(openAiRequest))) //
					.build();

			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() == 200) {
				OpenAiResponse openAiResponse = json.parse(response.body(), OpenAiResponse.class);
				return openAiResponse.choices[0].message.content;
			}

			OpenAiError openAiError = json.parse(response.body(), OpenAiError.class);
			return openAiError.error.message;

		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
			return "";
		}
	}

	@SuppressWarnings("unused")
	private static class OpenAiRequest {
		final Message[] messages;
		final int max_tokens;
		final int n;
		final float temperature;
		final String model;

		OpenAiRequest(String prompt) {
			this.messages = new Message[] { //
					new Message("system", "You are a helpful assistant that translates formatted text."), //
					new Message("user", prompt) //
			};
			this.max_tokens = 2400;
			this.n = 1;
			this.temperature = 0.5F;
			this.model = "gpt-3.5-turbo";
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
