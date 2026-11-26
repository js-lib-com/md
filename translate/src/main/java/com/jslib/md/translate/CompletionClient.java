package com.jslib.md.translate;

import java.io.IOException;

/** Chat completion service; used concurrently, so implementations must be thread safe. */
@FunctionalInterface
public interface CompletionClient {
	/** Complete prompt and return model answer. */
	String complete(String prompt) throws IOException, InterruptedException;
}
