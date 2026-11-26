package com.jslib.md.cli;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

import com.jslib.md.cli.Arguments.Command;
import com.jslib.md.cli.Arguments.UsageException;

/**
 * Markdown tools command line entry point. See <code>help/mdt.txt</code> resource for usage.
 * <p>
 * Logging is configured before any command handler class is loaded, so this class does not use a logger.
 */
public class Main {
	static final String PROGRAM = "mdt";

	static final int EXIT_SUCCESS = 0;
	static final int EXIT_FAILURE = 1;
	static final int EXIT_USAGE = 2;

	private static final String LOG_LEVEL_PROPERTY = "org.slf4j.simpleLogger.defaultLogLevel";

	public static void main(String... args) {
		int exitCode = run(System.out, System.err, args);
		if (exitCode != EXIT_SUCCESS) {
			System.exit(exitCode);
		}
	}

	static int run(PrintStream out, PrintStream err, String... args) {
		Arguments arguments;
		try {
			arguments = Arguments.parse(args);
		} catch (UsageException e) {
			err.printf("%s: %s%n", PROGRAM, e.getMessage());
			err.printf("Try '%s --help' for more information.%n", PROGRAM);
			return EXIT_USAGE;
		}

		if (arguments.help) {
			out.print(help(arguments.command));
			return EXIT_SUCCESS;
		}
		if (arguments.version) {
			out.printf("%s %s%n", PROGRAM, version());
			return EXIT_SUCCESS;
		}

		if (arguments.verbose) {
			System.setProperty(LOG_LEVEL_PROPERTY, "debug");
		} else if (arguments.quiet) {
			System.setProperty(LOG_LEVEL_PROPERTY, "error");
		}

		try {
			arguments.command.handler().execute(arguments.documentDir);
			return EXIT_SUCCESS;
		} catch (Exception e) {
			err.printf("%s: error: %s%n", PROGRAM, message(e));
			if (arguments.verbose) {
				e.printStackTrace(err);
			}
			return EXIT_FAILURE;
		}
	}

	static String help(Command command) {
		String resource = "/help/" + (command != null ? command.name : PROGRAM) + ".txt";
		try (InputStream stream = Main.class.getResourceAsStream(resource)) {
			if (stream == null) {
				throw new IllegalStateException("Missing help resource " + resource);
			}
			return new String(stream.readAllBytes(), UTF_8).replace("\r\n", "\n");
		} catch (IOException e) {
			throw new IllegalStateException("Fail to read help resource " + resource, e);
		}
	}

	/** Version from jar manifest; development when running from classes directory. */
	static String version() {
		String version = Main.class.getPackage().getImplementationVersion();
		return version != null ? version : "development";
	}

	private static String message(Throwable throwable) {
		String message = throwable.getMessage();
		return message != null && !message.isBlank() ? message : throwable.getClass().getSimpleName();
	}
}
