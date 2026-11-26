package com.jslib.md.cli;

import java.io.File;
import java.util.function.Supplier;

import com.jslib.md.CommandHandler;
import com.jslib.md.docx.DocxGenerator;
import com.jslib.md.translate.Translator;
import com.jslib.md.xlsx.XlsxGenerator;

/**
 * Command line arguments: <code>mdt &lt;command&gt; [options] [document-dir]</code>. Options may appear anywhere.
 * Legacy <code>--docx</code> and <code>--xlsx</code> command options are still accepted.
 */
final class Arguments {
	/** Command name, used on command line and as help resource name, and handler factory. */
	enum Command {
		// lambdas, not constructor references, so that handler classes are not resolved before logging is configured
		DOCX("docx", () -> new DocxGenerator()), XLSX("xlsx", () -> new XlsxGenerator()), TRANSLATE("translate", () -> new Translator());

		final String name;
		private final Supplier<CommandHandler> handlerFactory;

		Command(String name, Supplier<CommandHandler> handlerFactory) {
			this.name = name;
			this.handlerFactory = handlerFactory;
		}

		/** Create command handler; handler classes are loaded only when command is executed. */
		CommandHandler handler() {
			return handlerFactory.get();
		}

		static Command of(String name) {
			for (Command command : values()) {
				if (command.name.equals(name)) {
					return command;
				}
			}
			return null;
		}
	}

	/**
	 * Invalid command line arguments.
	 */
	public static final class UsageException extends Exception {
		private static final long serialVersionUID = 1L;

		UsageException(String message) {
			super(message);
		}
	}

	/**
	 * Command to execute; null if only help or version is requested.
	 */
	final Command command;
	/**
	 * Document directory, current directory by default.
	 */
	final File documentDir;
	final boolean help;
	final boolean version;
	final boolean verbose;
	final boolean quiet;

	private Arguments(Command command, File documentDir, boolean help, boolean version, boolean verbose, boolean quiet) {
		this.command = command;
		this.documentDir = documentDir;
		this.help = help;
		this.version = version;
		this.verbose = verbose;
		this.quiet = quiet;
	}

	static Arguments parse(String... args) throws UsageException {
		Command command = null;
		String documentDir = null;
		boolean help = false;
		boolean version = false;
		boolean verbose = false;
		boolean quiet = false;

		for (String arg : args) {
			switch (arg) {
				case "-h":
				case "--help":
					help = true;
					continue;

				case "-V":
				case "--version":
					version = true;
					continue;

				case "-v":
				case "--verbose":
					verbose = true;
					continue;

				case "-q":
				case "--quiet":
					quiet = true;
					continue;

				case "--docx":
				case "--xlsx":
					command = command(command, Command.of(arg.substring(2)));
					continue;

				default:
					break;
			}

			if (arg.startsWith("-") && arg.length() > 1) {
				throw new UsageException("unknown option '" + arg + "'");
			}
			if (command == null) {
				Command named = Command.of(arg);
				if (named == null) {
					throw new UsageException("unknown command '" + arg + "'");
				}
				command = named;
			} else if (documentDir == null) {
				documentDir = arg;
			} else {
				throw new UsageException("unexpected argument '" + arg + "'");
			}
		}

		if (verbose && quiet) {
			throw new UsageException("options --verbose and --quiet cannot be used together");
		}
		if (help || version) {
			return new Arguments(command, null, help, version, verbose, quiet);
		}
		if (command == null) {
			throw new UsageException("missing command");
		}

		File directory = new File(documentDir != null ? documentDir : ".");
		if (!directory.isDirectory()) {
			throw new UsageException("document directory not found '" + directory + "'");
		}
		return new Arguments(command, directory, false, false, verbose, quiet);
	}

	private static Command command(Command current, Command command) throws UsageException {
		if (current != null && current != command) {
			throw new UsageException("only one command is allowed");
		}
		return command;
	}
}
