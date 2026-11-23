package com.jslib.md.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.google.gson.Gson;

public class TranslatorTest {
	private final Gson json = new Gson();

	@Test
	public void GivenRelativeAndDottedPaths_WhenI18n_ThenInsertLanguageBeforeExtension() {
		assertThat(Translator.i18n(Path.of("./document.md"), "ro"), equalTo(Path.of("./document_ro.md")));
		assertThat(Translator.i18n(Path.of("work.v1/revisions.md"), "ro"), equalTo(Path.of("work.v1/revisions_ro.md")));
		assertThat(Translator.i18n(Path.of("document.properties"), "ro"), equalTo(Path.of("document_ro.properties")));
	}

	@Test
	public void GivenLfAndCrlfParagraphs_WhenChunks_ThenSplitOnBlankLines() {
		assertThat(Translator.chunks("one\ntwo\n\nthree\n", "\n\n", 3), contains("one\ntwo", "three"));
		assertThat(Translator.chunks("one\r\n\r\n\r\ntwo\r\n \r\nthree", "\r\n\r\n", 3), contains("one", "two", "three"));
	}

	@Test
	public void GivenShortParagraphs_WhenChunks_ThenGroupUpToChunkLength() {
		assertThat(Translator.chunks("aa\n\nbb\n\ncc\n\ndd", "\n\n", 6), contains("aa\n\nbb", "cc\n\ndd"));
		assertThat(Translator.chunks("aa\n\nbb", "\n\n", 3000), hasSize(1));
	}

	@Test
	public void GivenChunks_WhenTranslate_ThenJoinTranslationsWithBlankLineAndSourceLineSeparator() throws Exception {
		// given
		List<String> prompts = new ArrayList<>();
		String paragraph = "x".repeat(Translator.CHUNK_LENGTH);
		Translator translator = new Translator(prompt -> {
			prompts.add(prompt);
			return "  translated " + prompts.size() + "\n";
		});

		// when
		String translation = translator.translate(paragraph + "\r\n\r\nshort\r\n", "markdown", "English", "Romanian");

		// then
		assertThat(prompts, hasSize(2));
		assertThat(prompts.get(0), containsString("Translate the following English markdown text to Romanian"));
		assertThat(translation, equalTo("translated 1\r\n\r\ntranslated 2\r\n"));
	}

	@Test
	public void GivenCurrentApiResponse_WhenContent_ThenReturnMessageContent() throws IOException {
		// given: response with fields not modeled by response classes
		String body = "{\"id\":\"chatcmpl-1\",\"object\":\"chat.completion\",\"created\":1763900000,\"model\":\"gpt-4o-mini-2024-07-18\"," //
				+ "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"Bună ziua\",\"refusal\":null,\"annotations\":[]},\"logprobs\":null,\"finish_reason\":\"stop\"}]," //
				+ "\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":2,\"total_tokens\":12,\"prompt_tokens_details\":{\"cached_tokens\":0,\"audio_tokens\":0}}," //
				+ "\"service_tier\":\"default\",\"system_fingerprint\":\"fp_1\"}";

		// when
		String content = Translator.OpenAiClient.content(json, 200, body);

		// then
		assertThat(content, equalTo("Bună ziua"));
	}

	@Test
	public void GivenErrorResponse_WhenContent_ThenThrowWithApiMessage() {
		String body = "{\"error\":{\"message\":\"Incorrect API key provided.\",\"type\":\"invalid_request_error\",\"param\":null,\"code\":\"invalid_api_key\"}}";
		IOException exception = assertThrows(() -> Translator.OpenAiClient.content(json, 401, body));
		assertThat(exception.getMessage(), equalTo("OpenAI request failed with HTTP status 401: Incorrect API key provided."));
	}

	@Test
	public void GivenNonJsonErrorResponse_WhenContent_ThenThrowWithBody() {
		IOException exception = assertThrows(() -> Translator.OpenAiClient.content(json, 502, "<html>Bad Gateway</html>"));
		assertThat(exception.getMessage(), equalTo("OpenAI request failed with HTTP status 502: <html>Bad Gateway</html>"));
	}

	@Test
	public void GivenPrompt_WhenRequestBody_ThenSerializeChatCompletionRequest() {
		// when
		String body = Translator.OpenAiClient.requestBody(json, "gpt-test", "Translate \"ăâ\"\nnow");

		// then
		assertThat(body, equalTo("{\"messages\":[" //
				+ "{\"role\":\"system\",\"content\":\"You are a helpful assistant that translates formatted text. For Java properties file please preserve original keys.\"}," //
				+ "{\"role\":\"user\",\"content\":\"Translate \\\"ăâ\\\"\\nnow\"}]," //
				+ "\"max_tokens\":2400,\"n\":1,\"temperature\":0.5,\"model\":\"gpt-test\"}"));
	}

	@Test
	public void GivenMalformedSuccessResponse_WhenContent_ThenThrow() {
		IOException exception = assertThrows(() -> Translator.OpenAiClient.content(json, 200, "{\"choices\":[{"));
		assertThat(exception.getMessage(), containsString("Invalid OpenAI response"));
	}

	@Test
	public void GivenTruncatedResponse_WhenContent_ThenThrow() {
		String body = "{\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"partial\"},\"finish_reason\":\"length\"}]}";
		IOException exception = assertThrows(() -> Translator.OpenAiClient.content(json, 200, body));
		assertThat(exception.getMessage(), containsString("truncated"));
	}

	private interface Action {
		void run() throws IOException;
	}

	private static IOException assertThrows(Action action) {
		try {
			action.run();
		} catch (IOException e) {
			return e;
		}
		throw new AssertionError("Expected IOException");
	}
}
