package com.jslib.md.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThrows;

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
	@SuppressWarnings("ResultOfMethodCallIgnored")
	public void afterTest() {
		dir.delete();
	}

	@Test
	public void GivenCommandOnly_WhenParse_ThenUseCurrentDirectory() throws UsageException {
		// GIVEN
		String command = "docx";

		// WHEN
		Arguments arguments = Arguments.parse(command);

		// THEN
		assertThat(arguments.command, equalTo(Command.DOCX));
		assertThat(arguments.documentDir, equalTo(new File(".")));
	}

	@Test
	public void GivenCommandDirectoryAndOptionsInAnyOrder_WhenParse_ThenRecognizeAll() throws UsageException {
		// GIVEN
		String[] optionCommandDirectory = { "-v", "xlsx", dir.getPath() };
		String[] commandDirectoryOption = { "translate", dir.getPath(), "--quiet" };

		// WHEN
		Arguments xlsxArguments = Arguments.parse(optionCommandDirectory);
		Arguments translateArguments = Arguments.parse(commandDirectoryOption);

		// THEN
		assertThat(xlsxArguments.command, equalTo(Command.XLSX));
		assertThat(xlsxArguments.documentDir, equalTo(dir));
		assertThat(xlsxArguments.verbose, equalTo(true));

		assertThat(translateArguments.command, equalTo(Command.TRANSLATE));
		assertThat(translateArguments.quiet, equalTo(true));
	}

	@Test
	public void GivenLegacyCommandOption_WhenParse_ThenAccept() throws UsageException {
		// GIVEN
		String[] docxOptionWithDirectory = { "--docx", dir.getPath() };
		String xlsxOption = "--xlsx";

		// WHEN
		Arguments docxArguments = Arguments.parse(docxOptionWithDirectory);
		Arguments xlsxArguments = Arguments.parse(xlsxOption);

		// THEN
		assertThat(docxArguments.command, equalTo(Command.DOCX));
		assertThat(xlsxArguments.command, equalTo(Command.XLSX));
	}

	@Test
	public void GivenHelpOrVersion_WhenParse_ThenSkipValidation() throws UsageException {
		// GIVEN
		String[] longHelp = { "--help" };
		String[] shortHelp = { "-h" };
		String[] commandShortHelp = { "docx", "-h" };
		String[] helpWithMissingDirectory = { "docx", "missing-directory", "--help" };
		String[] shortVersion = { "-V" };

		// WHEN
		Arguments longHelpArguments = Arguments.parse(longHelp);
		Arguments shortHelpArguments = Arguments.parse(shortHelp);
		Arguments commandShortHelpArguments = Arguments.parse(commandShortHelp);
		Arguments helpWithMissingDirectoryArguments = Arguments.parse(helpWithMissingDirectory);
		Arguments shortVersionArguments = Arguments.parse(shortVersion);

		// THEN
		assertThat(longHelpArguments.help, equalTo(true));
		assertThat(shortHelpArguments.command, nullValue());
		assertThat(commandShortHelpArguments.command, equalTo(Command.DOCX));
		assertThat(helpWithMissingDirectoryArguments.help, equalTo(true));
		assertThat(shortVersionArguments.version, equalTo(true));
	}

	@Test
	public void GivenInvalidArguments_WhenParse_ThenThrowWithMessage() {
		// GIVEN
		String[] noArguments = {};
		String[] optionOnly = { "-v" };
		String[] unknownCommand = { "pdf" };
		String[] unknownOption = { "docx", "--pdf" };
		String[] extraArgument = { "docx", ".", "extra" };
		String[] twoCommands = { "--docx", "--xlsx" };
		String[] verboseAndQuiet = { "docx", "-v", "-q" };
		String[] missingDirectory = { "docx", "missing-directory" };

		// WHEN
		String noArgumentsMessage = usageMessage(noArguments);
		String optionOnlyMessage = usageMessage(optionOnly);
		String unknownCommandMessage = usageMessage(unknownCommand);
		String unknownOptionMessage = usageMessage(unknownOption);
		String extraArgumentMessage = usageMessage(extraArgument);
		String twoCommandsMessage = usageMessage(twoCommands);
		String verboseAndQuietMessage = usageMessage(verboseAndQuiet);
		String missingDirectoryMessage = usageMessage(missingDirectory);

		// THEN
		assertThat(noArgumentsMessage, equalTo("missing command"));
		assertThat(optionOnlyMessage, equalTo("missing command"));
		assertThat(unknownCommandMessage, equalTo("unknown command 'pdf'"));
		assertThat(unknownOptionMessage, equalTo("unknown option '--pdf'"));
		assertThat(extraArgumentMessage, equalTo("unexpected argument 'extra'"));
		assertThat(twoCommandsMessage, equalTo("only one command is allowed"));
		assertThat(verboseAndQuietMessage, equalTo("options --verbose and --quiet cannot be used together"));
		assertThat(missingDirectoryMessage, equalTo("document directory not found 'missing-directory'"));
	}

	private static String usageMessage(String... args) {
		UsageException exception = assertThrows(UsageException.class, () -> Arguments.parse(args));
		return exception.getMessage();
	}
}
