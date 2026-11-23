package com.jslib.md.cli;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import com.jslib.md.Command;
import com.jslib.md.CommandHandler;
import com.jslib.md.docx.DocxGenerator;
import com.jslib.md.xlsx.XlsxGenerator;

public class Main {
	private static final String HELP = "md --docx | --xlsx [document-dir]";

	private static final Map<Command, CommandHandler> OPERATORS = new HashMap<>();
	static {
		OPERATORS.put(Command.DOCX, new DocxGenerator());
		OPERATORS.put(Command.XLSX, new XlsxGenerator());
	}

	public static void main(String... args) throws Exception {
		if (args.length < 1 || args.length > 2) {
			System.err.println(HELP);
			return;
		}
		Command command = Command.of(args[0]);
		if (command == Command.UNKNOWN) {
			System.err.println(HELP);
			return;
		}
		File documentDir = new File(args.length >= 2 ? args[1] : ".");
		OPERATORS.get(command).execute(documentDir);
	}
}
