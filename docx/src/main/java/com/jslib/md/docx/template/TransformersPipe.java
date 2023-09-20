package com.jslib.md.docx.template;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TransformersPipe implements ITransformer {
	private final List<ITransformer> transformers;

	public TransformersPipe(ITransformer... transformers) {
		this.transformers = new ArrayList<>();
		for (ITransformer transformer : transformers) {
			this.transformers.add(transformer);
		}
	}

	public void add(ITransformer transformer) {
		transformers.add(transformer);
	}

	@Override
	public void write(byte[] buffer, int offset, int length) throws IOException {

		transformers.get(0).write(buffer, offset, length);

		int bytesRead = 0;
		for (int index = 0; index < transformers.size() - 1; ++index) {
			while ((bytesRead = transformers.get(index).read(buffer)) > 0) {
				transformers.get(index + 1).write(buffer, 0, bytesRead);
			}
		}

		while ((bytesRead = transformers.get(0).read(buffer)) != -1) {
			transformers.get(1).write(buffer, 0, bytesRead);
			while ((bytesRead = transformers.get(1).read(buffer)) > 0) {
				transformers.get(2).write(buffer, 0, bytesRead);
			}
		}
	}

	@SuppressWarnings("unused")
	private void procesingPipe(byte[] buffer, int index) throws IOException {
		int bytesRead = 0;
		while ((bytesRead = transformers.get(index).read(buffer)) > 0) {
			transformers.get(index + 1).write(buffer, 0, bytesRead);
		}
	}

	@Override
	public int read(byte[] buffer) throws IOException {
		return transformers.get(transformers.size() - 1).read(buffer);
	}
}
