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
		// GIVEN
		Path relativePath = Path.of("./document.md");
		Path dottedDirectoryPath = Path.of("work.v1/revisions.md");
		Path propertiesPath = Path.of("document.properties");

		// WHEN
		Path relativeI18n = Translator.i18n(relativePath);
		Path dottedDirectoryI18n = Translator.i18n(dottedDirectoryPath);
		Path propertiesI18n = Translator.i18n(propertiesPath);

		// THEN
		assertThat(relativeI18n, equalTo(Path.of("./document_ro.md")));
		assertThat(dottedDirectoryI18n, equalTo(Path.of("work.v1/revisions_ro.md")));
		assertThat(propertiesI18n, equalTo(Path.of("document_ro.properties")));
	}

	@Test
	public void GivenLfAndCrlfParagraphs_WhenChunks_ThenSplitOnBlankLines() {
		// GIVEN
		String lfText = "one\ntwo\n\nthree\n";
		String crlfText = "one\r\n\r\n\r\ntwo\r\n \r\nthree";

		// WHEN
		List<String> lfChunks = Translator.chunks(lfText, "\n\n", 3);
		List<String> crlfChunks = Translator.chunks(crlfText, "\r\n\r\n", 3);

		// THEN
		assertThat(lfChunks, contains("one\ntwo", "three"));
		assertThat(crlfChunks, contains("one", "two", "three"));
	}

	@Test
	public void GivenCrlfTableRows_WhenChunks_ThenKeepRowsInSameParagraph() {
		// GIVEN
		String tableWithHeaderSeparator = "| a |\r\n|---|\r\n| b |\r\n\r\npara";
		String tableWithoutHeaderSeparator = "| a |\r\n| b |\r\n\r\npara";

		// WHEN
		List<String> longChunks = Translator.chunks(tableWithHeaderSeparator, "\r\n\r\n", 3000);
		List<String> shortChunks = Translator.chunks(tableWithoutHeaderSeparator, "\r\n\r\n", 3);

		// THEN
		assertThat(longChunks, contains("| a |\r\n|---|\r\n| b |\r\n\r\npara"));
		assertThat(shortChunks, contains("| a |\r\n| b |", "para"));
	}

	@Test
	public void GivenShortParagraphs_WhenChunks_ThenGroupUpToChunkLength() {
		// GIVEN
		String fourParagraphs = "aa\n\nbb\n\ncc\n\ndd";
		String twoParagraphs = "aa\n\nbb";

		// WHEN
		List<String> smallChunks = Translator.chunks(fourParagraphs, "\n\n", 6);
		List<String> largeChunks = Translator.chunks(twoParagraphs, "\n\n", 3000);

		// THEN
		assertThat(smallChunks, contains("aa\n\nbb", "cc\n\ndd"));
		assertThat(largeChunks, hasSize(1));
	}

	@Test
	public void GivenChunks_WhenTranslate_ThenJoinTranslationsWithBlankLineAndSourceLineSeparator() throws Exception {
		// GIVEN
		List<String> prompts = Collections.synchronizedList(new ArrayList<>());
		String paragraph = "x".repeat(Translator.CHUNK_LENGTH);
		Translator translator = new Translator(prompt -> {
			prompts.add(prompt);
			return "  translated " + (prompt.endsWith("short") ? "short" : "long") + "\n";
		}, 4);

		// WHEN
		String translation = translator.translate(paragraph + "\r\n\r\nshort\r\n", "markdown");

		// THEN
		assertThat(prompts, hasSize(2));
		assertThat(prompts.get(0), containsString("Translate this English markdown text to Romanian"));
		assertThat(translation, equalTo("translated long\r\n\r\ntranslated short\r\n"));
	}

	@Test
	public void GivenTranslationWithLfAndArtifacts_WhenTranslate_ThenRepairAndUseSourceLineSeparator() throws Exception {
		// GIVEN
		Translator translator = new Translator(prompt -> "| a |\n\\\\| b |\n/ - c\n", 1);

		// WHEN
		String translation = translator.translate("| A |\r\n| B |\r\n- C\r\n", "markdown");

		// THEN
		assertThat(translation, equalTo("| a |\r\n| b |\r\n- c\r\n"));
	}

	@Test
	public void GivenTranslationMissingTableRow_WhenTranslate_ThenRetry() throws Exception {
		// GIVEN
		// first translation drops a row
		AtomicInteger calls = new AtomicInteger();
		Translator translator = new Translator(prompt -> calls.incrementAndGet() == 1 ? "| a |" : "| a |\n| b |", 1);

		// WHEN
		String translation = translator.translate("| A |\n| B |", "markdown");

		// THEN
		assertThat(calls.get(), equalTo(2));
		assertThat(translation, equalTo("| a |\n| b |\n"));
	}

	@Test
	public void GivenTranslationAlwaysDifferent_WhenTranslate_ThenUseLastAttempt() throws Exception {
		// GIVEN
		AtomicInteger calls = new AtomicInteger();
		Translator translator = new Translator(prompt -> "attempt " + calls.incrementAndGet(), 1);

		// WHEN
		String translation = translator.translate("# Title", "markdown");

		// THEN
		assertThat(calls.get(), equalTo(Translator.STRUCTURE_ATTEMPTS));
		assertThat(translation, equalTo("attempt 3\n"));
	}

	@Test
	public void GivenSourceWithBackslashLine_WhenRepair_ThenKeepTranslation() {
		// GIVEN
		String escapedSource = "\\| escaped";
		String escapedTranslation = "\\| escaped ro";
		String plainSource = "text";
		String escapedMarkersTranslation = "  \\\\ - item\n\\# heading\n1. x\n\\ 2. y";
		String strayLinesTranslation = "- a\n\\\n- b\n / \n- c\n\\";

		// WHEN
		String keptTranslation = Translator.repair(escapedSource, escapedTranslation);
		String repairedMarkers = Translator.repair(plainSource, escapedMarkersTranslation);
		String repairedLines = Translator.repair(plainSource, strayLinesTranslation);

		// THEN
		assertThat(keptTranslation, equalTo("\\| escaped ro"));
		assertThat(repairedMarkers, equalTo("  - item\n# heading\n1. x\n2. y"));
		assertThat(repairedLines, equalTo("- a\n- b\n- c\n"));
	}

	@Test
	public void GivenSameAndDifferentStructure_WhenStructureDifference_ThenDescribeDifference() {
		// GIVEN
		String source = "# T\n\n| a |\n|---|\n\n- x\n  - y\n1. z";
		String sameStructure = "# t\n\n| a |\n|---|\n\n- x\n  - y\n1. z";
		String differentStructure = "# t\n\n| a |\n\n- x\n- y 1. z";
		String listSource = "- a\n- b";
		String extraLine = "- a\nLOS\n- b";

		// WHEN
		String sameDifference = Translator.structureDifference(source, sameStructure);
		String differentDifference = Translator.structureDifference(source, differentStructure);
		String extraLineDifference = Translator.structureDifference(listSource, extraLine);

		// THEN
		assertThat(sameDifference, equalTo(null));
		assertThat(differentDifference, equalTo("4 lines instead of 6, 1 table rows instead of 2, 2 list items instead of 3"));
		assertThat(extraLineDifference, equalTo("3 lines instead of 2"));
	}

	@Test
	public void GivenChunksCompletingOutOfOrder_WhenTranslate_ThenKeepSourceOrderAndRunInParallel() throws Exception {
		// GIVEN
		// eight chunks where earlier chunks complete later
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

		// WHEN
		String translation = translator.translate(text.toString(), "markdown");

		// THEN
		assertThat(translation, equalTo("t0\n\nt1\n\nt2\n\nt3\n\nt4\n\nt5\n\nt6\n\nt7\n"));
		assertThat(maxRunning.get(), equalTo(3));
	}

	@Test
	public void GivenFailingChunk_WhenTranslate_ThenThrowChunkFailure() {
		// GIVEN
		String text = "a".repeat(Translator.CHUNK_LENGTH) + "\n\n" + "b".repeat(Translator.CHUNK_LENGTH);
		Translator translator = new Translator(prompt -> {
			if (prompt.endsWith("b")) {
				throw new IOException("Translation service not reachable");
			}
			return "ok";
		}, 4);

		// WHEN
		IOException exception = assertThrows(() -> {
			try {
				translator.translate(text, "markdown");
			} catch (InterruptedException e) {
				throw new AssertionError(e);
			}
		});

		// THEN
		assertThat(exception.getMessage(), equalTo("Translation service not reachable"));
	}

	@Test
	public void GivenCurrentApiResponse_WhenContent_ThenReturnMessageContent() throws IOException {
		// GIVEN
		// response with fields not modeled by response classes
		String body = "{\"id\":\"chatcmpl-1\",\"object\":\"chat.completion\",\"created\":1763900000,\"model\":\"gpt-4o-mini-2024-07-18\"," //
				+ "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"Bună ziua\",\"refusal\":null,\"annotations\":[]},\"logprobs\":null,\"finish_reason\":\"stop\"}]," //
				+ "\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":2,\"total_tokens\":12,\"prompt_tokens_details\":{\"cached_tokens\":0,\"audio_tokens\":0}}," //
				+ "\"service_tier\":\"default\",\"system_fingerprint\":\"fp_1\"}";

		// WHEN
		String content = Translator.OpenAiClient.content(json, 200, body);

		// THEN
		assertThat(content, equalTo("Bună ziua"));
	}

	@Test
	public void GivenErrorResponse_WhenContent_ThenThrowWithApiMessage() {
		// GIVEN
		String body = "{\"error\":{\"message\":\"Incorrect API key provided.\",\"type\":\"invalid_request_error\",\"param\":null,\"code\":\"invalid_api_key\"}}";

		// WHEN
		IOException exception = assertThrows(() -> Translator.OpenAiClient.content(json, 401, body));

		// THEN
		assertThat(exception.getMessage(), equalTo("Translation request failed with HTTP status 401: Incorrect API key provided."));
	}

	@Test
	public void GivenNonJsonErrorResponse_WhenContent_ThenThrowWithBody() {
		// GIVEN
		String body = "<html>Bad Gateway</html>";

		// WHEN
		IOException exception = assertThrows(() -> Translator.OpenAiClient.content(json, 502, body));

		// THEN
		assertThat(exception.getMessage(), equalTo("Translation request failed with HTTP status 502: <html>Bad Gateway</html>"));
	}

	@Test
	public void GivenPrompt_WhenRequestBody_ThenSerializeChatCompletionRequest() {
		// GIVEN
		String model = "gpt-test";
		String prompt = "Translate \"ăâ\"\nnow";

		// WHEN
		String body = Translator.OpenAiClient.requestBody(json, model, false, prompt);

		// THEN
		assertThat(body, equalTo("{\"messages\":[" //
				+ "{\"role\":\"system\",\"content\":\"" + Translator.SYSTEM_PROMPT + "\"}," //
				+ "{\"role\":\"user\",\"content\":\"Translate \\\"ăâ\\\"\\nnow\"}]," //
				+ "\"max_tokens\":2400,\"n\":1,\"temperature\":0.2,\"model\":\"gpt-test\"}"));
	}

	@Test
	public void GivenNoModel_WhenRequestBody_ThenOmitModel() {
		// GIVEN
		String model = null;
		String prompt = "text";

		// WHEN
		String body = Translator.OpenAiClient.requestBody(json, model, false, prompt);

		// THEN
		assertThat(body, not(containsString("model")));
	}

	@Test
	public void GivenLlamaCpp_WhenRequestBody_ThenDisableThinking() {
		// GIVEN
		boolean llamaCpp = true;
		String prompt = "text";

		// WHEN
		String body = Translator.OpenAiClient.requestBody(json, null, llamaCpp, prompt);

		// THEN
		assertThat(body, containsString("\"chat_template_kwargs\":{\"enable_thinking\":false}"));
	}

	@Test
	public void GivenBaseUrlWithAndWithoutTrailingSlash_WhenCompletionsUri_ThenAppendEndpoint() {
		// GIVEN
		String baseUrlWithoutSlash = "http://jarvis.local/v1";
		String baseUrlWithSlash = "https://api.openai.com/v1/";

		// WHEN
		URI uriWithoutSlash = Translator.OpenAiClient.completionsUri(baseUrlWithoutSlash);
		URI uriWithSlash = Translator.OpenAiClient.completionsUri(baseUrlWithSlash);

		// THEN
		assertThat(uriWithoutSlash, equalTo(URI.create("http://jarvis.local/v1/chat/completions")));
		assertThat(uriWithSlash, equalTo(URI.create("https://api.openai.com/v1/chat/completions")));
	}

	@Test
	public void GivenMalformedSuccessResponse_WhenContent_ThenThrow() {
		// GIVEN
		String body = "{\"choices\":[{";

		// WHEN
		IOException exception = assertThrows(() -> Translator.OpenAiClient.content(json, 200, body));

		// THEN
		assertThat(exception.getMessage(), containsString("Invalid translation response"));
	}

	@Test
	public void GivenTruncatedResponse_WhenContent_ThenThrow() {
		// GIVEN
		String body = "{\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"partial\"},\"finish_reason\":\"length\"}]}";

		// WHEN
		IOException exception = assertThrows(() -> Translator.OpenAiClient.content(json, 200, body));

		// THEN
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
