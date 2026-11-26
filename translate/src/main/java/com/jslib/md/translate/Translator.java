package com.jslib.md.translate;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

import com.jslib.md.CommandHandler;
import com.jslib.md.DocumentSources;
import com.jslib.md.ProjectProperties;

/**
 * Translate document sources from English to every language from <code>languages</code> project property, Romanian if
 * not set, using an OpenAI compatible chat completions API; see {@link TranslatorSettings} for service settings.
 * Translated files are written beside sources, with language suffix, e.g. <code>document_ro.md</code>.
 */
public class Translator implements CommandHandler {
	private static final Logger log = LoggerFactory.getLogger(Translator.class);

	/** Target language if project properties have no languages. */
	static final String DEFAULT_LANGUAGE = "ro";

	/** Attempts to get a translation with the same Markdown structure as the source chunk. */
	static final int STRUCTURE_ATTEMPTS = 3;

	private static final Pattern LINE_SEPARATOR = Pattern.compile("\\R");

	/** Completion client, null until execute if not provided by constructor. */
	private CompletionClient client;
	private int parallel;

	/** Translator with completion client configured from {@link TranslatorSettings} on execute. */
	public Translator() {
	}

	Translator(CompletionClient client, int parallel) {
		this.client = client;
		this.parallel = parallel;
	}

	/**
	 * Translate document sources from document directory. A target file is written only if all its chunks are
	 * translated; translation continues with next file on failure and execution fails at the end if any file was not
	 * translated.
	 */
	@Override
	public void execute(File documentDir) throws Exception {
		if (client == null) {
			OpenAiClient openAiClient = new OpenAiClient();
			client = openAiClient;
			parallel = TranslatorSettings.parallel();
			log.debug("Translate using {} with {} parallel requests", openAiClient.uri(), parallel);
		}

		ProjectProperties projectProperties = ProjectProperties.load(documentDir);
		List<String> languages = projectProperties.languages.isEmpty() ? List.of(DEFAULT_LANGUAGE) : projectProperties.languages;
		List<File> sources = new DocumentSources(documentDir, projectProperties).translationSources();

		int translated = 0;
		int failed = 0;
		for (String language : languages) {
			for (File source : sources) {
				if (!source.isFile()) {
					log.warn("Skip missing file {}", source);
					continue;
				}
				String format = source.getName().endsWith(".md") ? "markdown" : "Java properties";
				File target = DocumentSources.localize(source, language);
				try {
					String text = translate(Files.readString(source.toPath(), UTF_8), format, language);
					Files.writeString(target.toPath(), text, UTF_8);
					log.info("Translate {} to {}", source, target);
					++translated;
				} catch (IOException e) {
					log.error("Fail to translate {}: {}", source, e.getMessage());
					++failed;
				}
			}
		}

		if (failed > 0) {
			throw new IOException(String.format("Fail to translate %d of %d files.", failed, failed + translated));
		}
		if (translated == 0) {
			throw new FileNotFoundException("No source files to translate in " + documentDir);
		}
	}

	/**
	 * Translate text split into chunks of paragraphs, up to configured number of chunks concurrently. Translated chunks
	 * are joined in source order with a blank line, using source text line separator, and the result ends with a line
	 * separator. On first chunk failure pending chunks are canceled and the failure is rethrown.
	 *
	 * @param text source text, in English,
	 * @param format source format, used by prompt, e.g. markdown,
	 * @param language target language tag, e.g. ro.
	 */
	String translate(String text, String format, String language) throws IOException, InterruptedException {
		String lineSeparator = text.contains("\r\n") ? "\r\n" : "\n";
		String paragraphsSeparator = lineSeparator + lineSeparator;
		String languageName = languageName(language);

		List<String> chunks = Chunks.split(text, paragraphsSeparator, Chunks.CHUNK_LENGTH);
		ExecutorService executor = Executors.newFixedThreadPool(Math.max(1, Math.min(parallel, chunks.size())), daemonThreads());
		try {
			CompletionService<String> completion = new ExecutorCompletionService<>(executor);
			List<Future<String>> futures = new ArrayList<>();
			for (int i = 0; i < chunks.size(); ++i) {
				String chunk = chunks.get(i);
				String prompt = String.format("Translate this English %s text to %s:\n\n%s", format, languageName, chunk);
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

	/** English language name used by prompt, e.g. Romanian for ro; language tag itself if not known. */
	static String languageName(String language) {
		Locale locale = Locale.forLanguageTag(language);
		String name = locale.getDisplayLanguage(Locale.ENGLISH);
		// unknown languages display as their own code
		return name.isEmpty() || name.equalsIgnoreCase(locale.getLanguage()) ? language : name;
	}

	/**
	 * Translate chunk, repair known model artifacts and use source line separator. Translation is retried if structure
	 * differs from source and, if still different after all attempts, the last translation is used with a warning.
	 */
	private String translateChunk(int index, String chunk, String prompt, String lineSeparator) throws IOException, InterruptedException {
		String translation = null;
		for (int attempt = 1; attempt <= STRUCTURE_ATTEMPTS; ++attempt) {
			translation = StructureCheck.repair(chunk, client.complete(prompt).strip());
			String difference = StructureCheck.difference(chunk, translation);
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
		if (cause instanceof Error) {
			throw (Error) cause;
		}
		return new IOException(cause);
	}

	/** Daemon threads do not keep JVM alive if a canceled request ignores interruption. */
	private static ThreadFactory daemonThreads() {
		AtomicInteger index = new AtomicInteger();
		return runnable -> {
			Thread thread = new Thread(runnable, "translate-" + index.incrementAndGet());
			thread.setDaemon(true);
			return thread;
		};
	}
}
