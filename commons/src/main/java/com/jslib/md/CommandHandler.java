package com.jslib.md;

import java.io.File;

@FunctionalInterface
public interface CommandHandler {

	void execute(File documentDir) throws Exception;

}
