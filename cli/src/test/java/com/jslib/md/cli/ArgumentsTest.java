package com.jslib.md.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.jslib.md.cli.Arguments.Command;
import com.jslib.md.cli.Arguments.UsageException;

public class ArgumentsTest {
	private File dir;

	@Before
	public void beforeTest() throws IOException {
		dir = Files.createTempDirectory("ArgumentsTest").toFile();
	}

	@After
	public void afterTest() {
		dir.delete();
	}

	@Test
	public void GivenCommandOnly_WhenParse_ThenUseCurrentDirectory() throws UsageException {
		Arguments arguments = Arguments.parse("docx");
		assertThat(arguments.command, equalTo(Command.DOCX));
		assertThat(arguments.documentDir, equalTo(new File(".")));
	}

	@Test
	public void GivenCommandDirectoryAndOptionsInAnyOrder_WhenParse_ThenRecognizeAll() throws UsageException {
		Arguments arguments = Arguments.parse("-v", "xlsx", dir.getPath());
		assertThat(arguments.command, equalTo(Command.XLSX));
		assertThat(arguments.documentDir, equalTo(dir));
		assertThat(arguments.verbose, equalTo(true));

		arguments = Arguments.parse("translate", dir.getPath(), "--quiet");
		assertThat(arguments.command, equalTo(Command.TRANSLATE));
		assertThat(arguments.quiet, equalTo(true));
	}

	@Test
	public void GivenLegacyCommandOption_WhenParse_ThenAccept() throws UsageException {
		assertThat(Arguments.parse("--docx", dir.getPath()).command, equalTo(Command.DOCX));
		assertThat(Arguments.parse("--xlsx").command, equalTo(Command.XLSX));
	}

	@Test
	public void GivenHelpOrVersion_WhenParse_ThenSkipValidation() throws UsageException {
		assertThat(Arguments.parse("--help").help, equalTo(true));
		assertThat(Arguments.parse("-h").command, nullValue());
		assertThat(Arguments.parse("docx", "-h").command, equalTo(Command.DOCX));
		assertThat(Arguments.parse("docx", "missing-directory", "--help").help, equalTo(true));
		assertThat(Arguments.parse("-V").version, equalTo(true));
	}

	@Test
	public void GivenInvalidArguments_WhenParse_ThenThrowWithMessage() {
		assertUsage("missing command");
		assertUsage("missing command", "-v");
		assertUsage("unknown command 'pdf'", "pdf");
		assertUsage("unknown option '--pdf'", "docx", "--pdf");
		assertUsage("unexpected argument 'extra'", "docx", ".", "extra");
		assertUsage("only one command is allowed", "--docx", "--xlsx");
		assertUsage("options --verbose and --quiet cannot be used together", "docx", "-v", "-q");
		assertUsage("document directory not found 'missing-directory'", "docx", "missing-directory");
	}

	private static void assertUsage(String message, String... args) {
		try {
			Arguments.parse(args);
		} catch (UsageException e) {
			assertThat(e.getMessage(), equalTo(message));
			return;
		}
		throw new AssertionError("Expected usage exception: " + message);
	}
}
