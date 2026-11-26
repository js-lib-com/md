package com.jslib.md.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import com.google.gson.Gson;

public class TranslatorTest {
	private final Gson json = new Gson();

	@Test
	public void GivenRelativeAndDottedPaths_WhenI18n_ThenInsertLanguageBeforeExtension() {
		assertThat(Translator.i18n(Path.of("./document.md")), equalTo(Path.of("./document_ro.md")));
		assertThat(Translator.i18n(Path.of("work.v1/revisions.md")), equalTo(Path.of("work.v1/revisions_ro.md")));
		assertThat(Translator.i18n(Path.of("document.properties")), equalTo(Path.of("document_ro.properties")));
	}

	@Test
	public void GivenLfAndCrlfParagraphs_WhenChunks_ThenSplitOnBlankLines() {
		assertThat(Translator.chunks("one\ntwo\n\nthree\n", "\n\n", 3), contains("one\ntwo", "three"));
		assertThat(Translator.chunks("one\r\n\r\n\r\ntwo\r\n \r\nthree", "\r\n\r\n", 3), contains("one", "two", "three"));
	}

	@Test
	public void GivenCrlfTableRows_WhenChunks_ThenKeepRowsInSameParagraph() {
		assertThat(Translator.chunks("| a |\r\n|---|\r\n| b |\r\n\r\npara", "\r\n\r\n", 3000), contains("| a |\r\n|---|\r\n| b |\r\n\r\npara"));
		assertThat(Translator.chunks("| a |\r\n| b |\r\n\r\npara", "\r\n\r\n", 3), contains("| a |\r\n| b |", "para"));
	}

	@Test
	public void GivenShortParagraphs_WhenChunks_ThenGroupUpToChunkLength() {
		assertThat(Translator.chunks("aa\n\nbb\n\ncc\n\ndd", "\n\n", 6), contains("aa\n\nbb", "cc\n\ndd"));
		assertThat(Translator.chunks("aa\n\nbb", "\n\n", 3000), hasSize(1));
	}

	@Test
	public void GivenChunks_WhenTranslate_ThenJoinTranslationsWithBlankLineAndSourceLineSeparator() throws Exception {
		// given
		List<String> prompts = Collections.synchronizedList(new ArrayList<>());
		String paragraph = "x".repeat(Translator.CHUNK_LENGTH);
		Translator translator = new Translator(prompt -> {
			prompts.add(prompt);
			return "  translated " + (prompt.endsWith("short") ? "short" : "long") + "\n";
		}, 4);

		// when
		String translation = translator.translate(paragraph + "\r\n\r\nshort\r\n", "markdown");

		// then
		assertThat(prompts, hasSize(2));
		assertThat(prompts.get(0), containsString("Translate this English markdown text to Romanian"));
		assertThat(translation, equalTo("translated long\r\n\r\ntranslated short\r\n"));
	}

	@Test
	public void GivenTranslationWithLfAndArtifacts_WhenTranslate_ThenRepairAndUseSourceLineSeparator() throws Exception {
		// given
		Translator translator = new Translator(prompt -> "| a |\n\\\\| b |\n/ - c\n", 1);

		// when
		String translation = translator.translate("| A |\r\n| B |\r\n- C\r\n", "markdown");

		// then
		assertThat(translation, equalTo("| a |\r\n| b |\r\n- c\r\n"));
	}

	@Test
	public void GivenTranslationMissingTableRow_WhenTranslate_ThenRetry() throws Exception {
		// given: first translation drops a row
		AtomicInteger calls = new AtomicInteger();
		Translator translator = new Translator(prompt -> calls.incrementAndGet() == 1 ? "| a |" : "| a |\n| b |", 1);

		// when
		String translation = translator.translate("| A |\n| B |", "markdown");

		// then
		assertThat(calls.get(), equalTo(2));
		assertThat(translation, equalTo("| a |\n| b |\n"));
	}

	@Test
	public void GivenTranslationAlwaysDifferent_WhenTranslate_ThenUseLastAttempt() throws Exception {
		// given
		AtomicInteger calls = new AtomicInteger();
		Translator translator = new Translator(prompt -> "attempt " + calls.incrementAndGet(), 1);

		// when
		String translation = translator.translate("# Title", "markdown");

		// then
		assertThat(calls.get(), equalTo(Translator.STRUCTURE_ATTEMPTS));
		assertThat(translation, equalTo("attempt 3\n"));
	}

	@Test
	public void GivenSourceWithBackslashLine_WhenRepair_ThenKeepTranslation() {
		assertThat(Translator.repair("\\| escaped", "\\| escaped ro"), equalTo("\\| escaped ro"));
		assertThat(Translator.repair("text", "  \\\\ - item\n\\# heading\n1. x\n\\ 2. y"), equalTo("  - item\n# heading\n1. x\n2. y"));
		assertThat(Translator.repair("text", "- a\n\\\n- b\n / \n- c\n\\"), equalTo("- a\n- b\n- c\n"));
	}

	@Test
	public void GivenSameAndDifferentStructure_WhenStructureDifference_ThenDescribeDifference() {
		String source = "# T\n\n| a |\n|---|\n\n- x\n  - y\n1. z";
		assertThat(Translator.structureDifference(source, "# t\n\n| a |\n|---|\n\n- x\n  - y\n1. z"), equalTo(null));
		assertThat(Translator.structureDifference(source, "# t\n\n| a |\n\n- x\n- y 1. z"), equalTo("4 lines instead of 6, 1 table rows instead of 2, 2 list items instead of 3"));
		assertThat(Translator.structureDifference("- a\n- b", "- a\nLOS\n- b"), equalTo("3 lines instead of 2"));
	}

	@Test
	public void GivenChunksCompletingOutOfOrder_WhenTranslate_ThenKeepSourceOrderAndRunInParallel() throws Exception {
		// given: eight chunks where earlier chunks complete later
		StringBuilder text = new StringBuilder();
		for (int i = 0; i < 8; ++i) {
			text.append(i).append("x".repeat(Translator.CHUNK_LENGTH)).append("\n\n");
		}
		AtomicInteger running = new AtomicInteger();
		AtomicInteger maxRunning = new AtomicInteger();
		Translator translator = new Translator(prompt -> {
			maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
			int index = prompt.charAt(prompt.indexOf("\n\n") + 2) - '0';
			Thread.sleep(10L * (8 - index));
			running.decrementAndGet();
			return "t" + index;
		}, 3);

		// when
		String translation = translator.translate(text.toString(), "markdown");

		// then
		assertThat(translation, equalTo("t0\n\nt1\n\nt2\n\nt3\n\nt4\n\nt5\n\nt6\n\nt7\n"));
		assertThat(maxRunning.get(), equalTo(3));
	}

	@Test
	public void GivenFailingChunk_WhenTranslate_ThenThrowChunkFailure() {
		// given
		String text = "a".repeat(Translator.CHUNK_LENGTH) + "\n\n" + "b".repeat(Translator.CHUNK_LENGTH);
		Translator translator = new Translator(prompt -> {
			if (prompt.endsWith("b")) {
				throw new IOException("Translation service not reachable");
			}
			return "ok";
		}, 4);

		// when
		IOException exception = assertThrows(() -> {
			try {
				translator.translate(text, "markdown");
			} catch (InterruptedException e) {
				throw new AssertionError(e);
			}
		});

		// then
		assertThat(exception.getMessage(), equalTo("Translation service not reachable"));
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
		assertThat(exception.getMessage(), equalTo("Translation request failed with HTTP status 401: Incorrect API key provided."));
	}

	@Test
	public void GivenNonJsonErrorResponse_WhenContent_ThenThrowWithBody() {
		IOException exception = assertThrows(() -> Translator.OpenAiClient.content(json, 502, "<html>Bad Gateway</html>"));
		assertThat(exception.getMessage(), equalTo("Translation request failed with HTTP status 502: <html>Bad Gateway</html>"));
	}

	@Test
	public void GivenPrompt_WhenRequestBody_ThenSerializeChatCompletionRequest() {
		// when
		String body = Translator.OpenAiClient.requestBody(json, "gpt-test", false, "Translate \"ăâ\"\nnow");

		// then
		assertThat(body, equalTo("{\"messages\":[" //
				+ "{\"role\":\"system\",\"content\":\"" + Translator.SYSTEM_PROMPT + "\"}," //
				+ "{\"role\":\"user\",\"content\":\"Translate \\\"ăâ\\\"\\nnow\"}]," //
				+ "\"max_tokens\":2400,\"n\":1,\"temperature\":0.2,\"model\":\"gpt-test\"}"));
	}

	@Test
	public void GivenNoModel_WhenRequestBody_ThenOmitModel() {
		String body = Translator.OpenAiClient.requestBody(json, null, false, "text");
		assertThat(body, not(containsString("model")));
	}

	@Test
	public void GivenLlamaCpp_WhenRequestBody_ThenDisableThinking() {
		String body = Translator.OpenAiClient.requestBody(json, null, true, "text");
		assertThat(body, containsString("\"chat_template_kwargs\":{\"enable_thinking\":false}"));
	}

	@Test
	public void GivenBaseUrlWithAndWithoutTrailingSlash_WhenCompletionsUri_ThenAppendEndpoint() {
		assertThat(Translator.OpenAiClient.completionsUri("http://jarvis.local/v1"), equalTo(URI.create("http://jarvis.local/v1/chat/completions")));
		assertThat(Translator.OpenAiClient.completionsUri("https://api.openai.com/v1/"), equalTo(URI.create("https://api.openai.com/v1/chat/completions")));
	}

	@Test
	public void GivenMalformedSuccessResponse_WhenContent_ThenThrow() {
		IOException exception = assertThrows(() -> Translator.OpenAiClient.content(json, 200, "{\"choices\":[{"));
		assertThat(exception.getMessage(), containsString("Invalid translation response"));
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
