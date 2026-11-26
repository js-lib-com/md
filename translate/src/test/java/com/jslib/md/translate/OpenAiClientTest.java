package com.jslib.md.translate;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

import java.io.IOException;
import java.net.URI;

import org.junit.Test;

import com.google.gson.Gson;

public class OpenAiClientTest {
	private final Gson json = new Gson();

	@Test
	public void GivenCurrentApiResponse_WhenContent_ThenReturnMessageContent() throws IOException {
		// GIVEN
		// response with fields not modeled by response classes
		String body = "{\"id\":\"chatcmpl-1\",\"object\":\"chat.completion\",\"created\":1763900000,\"model\":\"gpt-4o-mini-2024-07-18\"," //
				+ "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"Bună ziua\",\"refusal\":null,\"annotations\":[]},\"logprobs\":null,\"finish_reason\":\"stop\"}]," //
				+ "\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":2,\"total_tokens\":12,\"prompt_tokens_details\":{\"cached_tokens\":0,\"audio_tokens\":0}}," //
				+ "\"service_tier\":\"default\",\"system_fingerprint\":\"fp_1\"}";

		// WHEN
		String content = OpenAiClient.content(json, 200, body);

		// THEN
		assertThat(content, equalTo("Bună ziua"));
	}

	@Test
	public void GivenErrorResponse_WhenContent_ThenThrowWithApiMessage() {
		// GIVEN
		String body = "{\"error\":{\"message\":\"Incorrect API key provided.\",\"type\":\"invalid_request_error\",\"param\":null,\"code\":\"invalid_api_key\"}}";

		// WHEN
		IOException exception = assertThrows(() -> OpenAiClient.content(json, 401, body));

		// THEN
		assertThat(exception.getMessage(), equalTo("Translation request failed with HTTP status 401: Incorrect API key provided."));
	}

	@Test
	public void GivenNonJsonErrorResponse_WhenContent_ThenThrowWithBody() {
		// GIVEN
		String body = "<html>Bad Gateway</html>";

		// WHEN
		IOException exception = assertThrows(() -> OpenAiClient.content(json, 502, body));

		// THEN
		assertThat(exception.getMessage(), equalTo("Translation request failed with HTTP status 502: <html>Bad Gateway</html>"));
	}

	@Test
	public void GivenPrompt_WhenRequestBody_ThenSerializeChatCompletionRequest() {
		// GIVEN
		String model = "gpt-test";
		String prompt = "Translate \"ăâ\"\nnow";

		// WHEN
		String body = OpenAiClient.requestBody(json, model, false, prompt);

		// THEN
		assertThat(body, equalTo("{\"messages\":[" //
				+ "{\"role\":\"system\",\"content\":\"" + OpenAiClient.SYSTEM_PROMPT + "\"}," //
				+ "{\"role\":\"user\",\"content\":\"Translate \\\"ăâ\\\"\\nnow\"}]," //
				+ "\"max_tokens\":2400,\"n\":1,\"temperature\":0.2,\"model\":\"gpt-test\"}"));
	}

	@Test
	public void GivenNoModel_WhenRequestBody_ThenOmitModel() {
		// GIVEN
		String model = null;
		String prompt = "text";

		// WHEN
		String body = OpenAiClient.requestBody(json, model, false, prompt);

		// THEN
		assertThat(body, not(containsString("model")));
	}

	@Test
	public void GivenLlamaCpp_WhenRequestBody_ThenDisableThinking() {
		// GIVEN
		boolean llamaCpp = true;
		String prompt = "text";

		// WHEN
		String body = OpenAiClient.requestBody(json, null, llamaCpp, prompt);

		// THEN
		assertThat(body, containsString("\"chat_template_kwargs\":{\"enable_thinking\":false}"));
	}

	@Test
	public void GivenBaseUrlWithAndWithoutTrailingSlash_WhenCompletionsUri_ThenAppendEndpoint() {
		// GIVEN
		String baseUrlWithoutSlash = "http://localhost:8080/v1";
		String baseUrlWithSlash = "https://api.openai.com/v1/";

		// WHEN
		URI uriWithoutSlash = OpenAiClient.completionsUri(baseUrlWithoutSlash);
		URI uriWithSlash = OpenAiClient.completionsUri(baseUrlWithSlash);

		// THEN
		assertThat(uriWithoutSlash, equalTo(URI.create("http://localhost:8080/v1/chat/completions")));
		assertThat(uriWithSlash, equalTo(URI.create("https://api.openai.com/v1/chat/completions")));
	}

	@Test
	public void GivenMalformedSuccessResponse_WhenContent_ThenThrow() {
		// GIVEN
		String body = "{\"choices\":[{";

		// WHEN
		IOException exception = assertThrows(() -> OpenAiClient.content(json, 200, body));

		// THEN
		assertThat(exception.getMessage(), containsString("Invalid translation response"));
	}

	@Test
	public void GivenTruncatedResponse_WhenContent_ThenThrow() {
		// GIVEN
		String body = "{\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"partial\"},\"finish_reason\":\"length\"}]}";

		// WHEN
		IOException exception = assertThrows(() -> OpenAiClient.content(json, 200, body));

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
