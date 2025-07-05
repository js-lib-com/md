package com.jslib.md;

public enum Command {
	DOCX("docx"), XLSX("xlsx"), UNKNOWN("unknown");

	public final String value;

	private Command(String value) {
		this.value = value;
	}

	public static Command of(String option) {
		assert option != null && !option.isEmpty() : "Option argument is null or empty";
		assert option.startsWith("--") : "Option argument is not valid";
		switch (option.substring(2)) {
		case "docx":
			return DOCX;
		case "xlsx":
			return XLSX;
		}
		return UNKNOWN;
	}
}
