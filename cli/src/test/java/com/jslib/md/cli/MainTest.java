package com.jslib.md.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.Objects;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.jslib.md.cli.Arguments.Command;

public class MainTest {
	private ByteArrayOutputStream out;
	private ByteArrayOutputStream err;
	private File dir;

	@Before
	public void beforeTest() throws IOException {
		out = new ByteArrayOutputStream();
		err = new ByteArrayOutputStream();
		dir = Files.createTempDirectory("MainTest").toFile();
	}

	@After
	@SuppressWarnings("ResultOfMethodCallIgnored")
	public void afterTest() {
		for (File file : Objects.requireNonNull(dir.listFiles())) {
			file.delete();
		}
		dir.delete();
	}

	@Test
	public void GivenHelp_WhenRun_ThenPrintUsageAndSucceed() {
		// GIVEN
		String[] args = { "--help" };

		// WHEN
		int exitCode = run(args);

		// THEN
		assertThat(exitCode, equalTo(Main.EXIT_SUCCESS));
		assertThat(out(), startsWith("Usage: mdt <command> [options] [document-dir]"));
	}

	@Test
	public void GivenCommandHelp_WhenRun_ThenPrintCommandUsage() {
		// GIVEN
		Command[] commands = Command.values();
		int[] exitCodes = new int[commands.length];
		String[] outputs = new String[commands.length];

		// WHEN
		for (int i = 0; i < commands.length; ++i) {
			out.reset();
			exitCodes[i] = run(commands[i].name, "--help");
			outputs[i] = out();
		}

		// THEN
		for (int i = 0; i < commands.length; ++i) {
			assertThat(exitCodes[i], equalTo(Main.EXIT_SUCCESS));
			assertThat(outputs[i], startsWith("Usage: mdt " + commands[i].name + " [options] [document-dir]"));
		}
	}

	@Test
	public void GivenVersion_WhenRun_ThenPrintVersion() {
		// GIVEN
		String[] args = { "--version" };

		// WHEN
		int exitCode = run(args);

		// THEN
		assertThat(exitCode, equalTo(Main.EXIT_SUCCESS));
		assertThat(out(), startsWith("mdt "));
	}

	@Test
	public void GivenInvalidArguments_WhenRun_ThenPrintErrorAndExitWithUsageCode() {
		// GIVEN
		String[] args = { "pdf" };

		// WHEN
		int exitCode = run(args);

		// THEN
		assertThat(exitCode, equalTo(Main.EXIT_USAGE));
		assertThat(err(), equalTo("mdt: unknown command 'pdf'\nTry 'mdt --help' for more information.\n"));
	}

	@Test
	public void GivenNoMarkdownFiles_WhenRunDocx_ThenPrintErrorAndExitWithFailureCode() {
		// GIVEN
		String[] args = { "docx", dir.getPath() };

		// WHEN
		int exitCode = run(args);

		// THEN
		assertThat(exitCode, equalTo(Main.EXIT_FAILURE));
		assertThat(err(), startsWith("mdt: error: Missing source file "));
	}

	@Test
	public void GivenMarkdownTable_WhenRunXlsx_ThenGenerateWorkbook() throws IOException {
		// GIVEN
		Files.writeString(new File(dir, "table.md").toPath(), "|a|b|\n|-|-|\n|1|2|\n");
		String[] args = { "xlsx", dir.getPath(), "-q" };

		// WHEN
		int exitCode = run(args);

		// THEN
		assertThat(exitCode, equalTo(Main.EXIT_SUCCESS));
		assertThat(new File(dir, "document.xlsx").isFile(), equalTo(true));
	}

	@Test
	public void GivenNoSources_WhenRunTranslate_ThenFail() {
		// GIVEN
		String[] args = { "translate", dir.getPath() };

		// WHEN
		int exitCode = run(args);

		// THEN
		// fails on missing API key or, if a key is configured, on missing source files
		assertThat(exitCode, equalTo(Main.EXIT_FAILURE));
		assertThat(err(), containsString("mdt: error: "));
	}

	private int run(String... args) {
		return Main.run(new PrintStream(out, true, UTF_8), new PrintStream(err, true, UTF_8), args);
	}

	private String out() {
		return out.toString(UTF_8).replace(System.lineSeparator(), "\n");
	}

	private String err() {
		return err.toString(UTF_8).replace(System.lineSeparator(), "\n");
	}
}
