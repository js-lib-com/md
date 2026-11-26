package com.jslib.md.translate;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.Test;

public class TranslatorTest {
	@Test
	public void GivenSourceDirAndLanguages_WhenExecute_ThenWriteTranslationPerLanguageBesideSources() throws Exception {
		// GIVEN
		Path documentDir = Files.createTempDirectory("TranslatorTest");
		Files.createDirectory(documentDir.resolve("chapters"));
		Files.writeString(documentDir.resolve("project.properties"), "source.dir=chapters\nlanguages=ro, de\n");
		Files.writeString(documentDir.resolve("chapters/01-intro.md"), "# Intro\n");
		Files.writeString(documentDir.resolve("chapters/01-intro_ro.md"), "# Old\n");
		Files.writeString(documentDir.resolve("document.properties"), "document.title=Manual\n");
		List<String> prompts = Collections.synchronizedList(new ArrayList<>());
		Translator translator = new Translator(prompt -> {
			prompts.add(prompt);
			return prompt.substring(prompt.indexOf("\n\n") + 2);
		}, 1);

		// WHEN
		translator.execute(documentDir.toFile());

		// THEN
		// existing translation is not used as source; missing revisions file is skipped
		assertThat(prompts, hasSize(4));
		assertThat(prompts.get(0), containsString("markdown text to Romanian"));
		assertThat(prompts.get(3), containsString("Java properties text to German"));
		for (String file : new String[] { "chapters/01-intro_ro.md", "chapters/01-intro_de.md", "document_ro.properties", "document_de.properties" }) {
			assertThat(file, Files.isRegularFile(documentDir.resolve(file)), equalTo(true));
		}
		assertThat(Files.readString(documentDir.resolve("chapters/01-intro_ro.md")), equalTo("# Intro\n"));
		try (Stream<Path> paths = Files.walk(documentDir)) {
			paths.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
		}
	}

	@Test
	public void GivenLanguageTags_WhenLanguageName_ThenReturnEnglishName() {
		assertThat(Translator.languageName("ro"), equalTo("Romanian"));
		assertThat(Translator.languageName("de"), equalTo("German"));
		assertThat(Translator.languageName("xx"), equalTo("xx"));
	}

	@Test
	public void GivenChunks_WhenTranslate_ThenJoinTranslationsWithBlankLineAndSourceLineSeparator() throws Exception {
		// GIVEN
		List<String> prompts = Collections.synchronizedList(new ArrayList<>());
		String paragraph = "x".repeat(Chunks.CHUNK_LENGTH);
		Translator translator = new Translator(prompt -> {
			prompts.add(prompt);
			return "  translated " + (prompt.endsWith("short") ? "short" : "long") + "\n";
		}, 4);

		// WHEN
		String translation = translator.translate(paragraph + "\r\n\r\nshort\r\n", "markdown", "ro");

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
		String translation = translator.translate("| A |\r\n| B |\r\n- C\r\n", "markdown", "ro");

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
		String translation = translator.translate("| A |\n| B |", "markdown", "ro");

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
		String translation = translator.translate("# Title", "markdown", "ro");

		// THEN
		assertThat(calls.get(), equalTo(Translator.STRUCTURE_ATTEMPTS));
		assertThat(translation, equalTo("attempt 3\n"));
	}

	@Test
	public void GivenChunksCompletingOutOfOrder_WhenTranslate_ThenKeepSourceOrderAndRunInParallel() throws Exception {
		// GIVEN
		// eight chunks where earlier chunks complete later
		StringBuilder text = new StringBuilder();
		for (int i = 0; i < 8; ++i) {
			text.append(i).append("x".repeat(Chunks.CHUNK_LENGTH)).append("\n\n");
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
		String translation = translator.translate(text.toString(), "markdown", "ro");

		// THEN
		assertThat(translation, equalTo("t0\n\nt1\n\nt2\n\nt3\n\nt4\n\nt5\n\nt6\n\nt7\n"));
		assertThat(maxRunning.get(), equalTo(3));
	}

	@Test
	public void GivenFailingChunk_WhenTranslate_ThenThrowChunkFailure() {
		// GIVEN
		String text = "a".repeat(Chunks.CHUNK_LENGTH) + "\n\n" + "b".repeat(Chunks.CHUNK_LENGTH);
		Translator translator = new Translator(prompt -> {
			if (prompt.endsWith("b")) {
				throw new IOException("Translation service not reachable");
			}
			return "ok";
		}, 4);

		// WHEN
		IOException exception = assertThrows(() -> {
			try {
				translator.translate(text, "markdown", "ro");
			} catch (InterruptedException e) {
				throw new AssertionError(e);
			}
		});

		// THEN
		assertThat(exception.getMessage(), equalTo("Translation service not reachable"));
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
