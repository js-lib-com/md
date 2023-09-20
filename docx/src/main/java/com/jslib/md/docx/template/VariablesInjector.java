package com.jslib.md.docx.template;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.util.Properties;

class VariablesInjector implements ITransformer {
	private final ByteBuffer byteBuffer;
	private final CharBuffer charBuffer;
	private final CharsetDecoder decoder;

	private final Properties properties;
	private final StringBuilder variableBuilder;
	private final VariablesInjector.Variable variable;

	private VariablesInjector.ParserState parserState;

	public VariablesInjector(int bufferCapacity, Properties properties) {
		this.byteBuffer = ByteBuffer.allocate(bufferCapacity + 4);
		this.charBuffer = CharBuffer.allocate(bufferCapacity);
		this.charBuffer.flip();

		this.decoder = Charset.forName("UTF-8").newDecoder();
		this.decoder.onMalformedInput(CodingErrorAction.REPORT);
		this.decoder.onUnmappableCharacter(CodingErrorAction.REPORT);

		this.properties = properties;
		this.variableBuilder = new StringBuilder();
		this.variable = new Variable();

		this.parserState = ParserState.TEXT;
	}

	@Override
	public void write(byte[] buffer, int offset, int length) throws IOException {
		assert length <= byteBuffer.remaining();
		byteBuffer.put(buffer, offset, length);

		byteBuffer.flip();
		charBuffer.compact();
		CoderResult result = decoder.decode(byteBuffer, charBuffer, false);
		if (result.isError()) {
			if (!result.isUnderflow()) {
				result.throwException();
			}
		}

		// due to allocated buffers capacity char buffer overflow is impossible
		assert !result.isOverflow();

		if (result.isUnderflow()) {
			// byteBuffer.position(byteBuffer.position() - result.length());
			// somehow handle underflow; ensure byte buffer is rolled back !!!
		}

		byteBuffer.compact();
		assert byteBuffer.position() <= 4;
		charBuffer.flip();
	}

	@Override
	public int read(byte[] buffer) throws IOException {
		VariablesInjector.ReadBuffer readBuffer = new ReadBuffer(buffer);
		CHARS_LOOP: while (charBuffer.hasRemaining()) {
			Character c = charBuffer.charAt(0);

			switch (parserState) {
			case TEXT:
				if (c == '{') {
					parserState = ParserState.OPEN_BRACE;
					charBuffer.get();
					break;
				}
				if (!readBuffer.write(c)) {
					break CHARS_LOOP;
				}
				charBuffer.get();
				break;

			case OPEN_BRACE:
				if (c == '{') {
					parserState = ParserState.VARIABLE;
					charBuffer.get();
					break;
				}
				if (!readBuffer.write('{')) {
					break CHARS_LOOP;
				}
				charBuffer.get();
				if (!readBuffer.write(c)) {
					break CHARS_LOOP;
				}
				charBuffer.get();
				parserState = ParserState.TEXT;
				break;

			case VARIABLE:
				if (c == '}') {
					parserState = ParserState.CLOSE_BRACE;
					charBuffer.get();
					break;
				}
				variableBuilder.append(c);
				charBuffer.get();
				break;

			case CLOSE_BRACE:
				if (c != '}') {
					throw new IllegalStateException("Invalid variable definition. Missing second close brace.");
				}
				charBuffer.get();

				String variableName = variableBuilder.toString();
				variableBuilder.setLength(0);
				variable.setValue(properties.getOrDefault(variableName, variableName).toString());
				DocxTemplate.log("-- Replace variable value:", variableName, variable);

				parserState = ParserState.WRITE_VARIABLE;
				// fall through next case

			case WRITE_VARIABLE:
				while (variable.hasChars()) {
					if (!readBuffer.write(variable.getChar())) {
						break CHARS_LOOP;
					}
					variable.commitChanges();
				}
				parserState = ParserState.TEXT;
			}
		}

		return readBuffer.length();
	}

	private static class ReadBuffer {
		private final byte[] buffer;
		private int index;

		public ReadBuffer(byte[] buffer) {
			this.buffer = buffer;
		}

		public boolean write(char c) throws UnsupportedEncodingException {
			byte[] bytes = String.valueOf(c).getBytes("UTF-8");
			if (index + bytes.length > buffer.length) {
				return false;
			}
			for (byte b : bytes) {
				buffer[index++] = b;
			}
			return true;
		}

		public int length() {
			return index;
		}
	}

	private static class Variable {
		private String value;
		private int index;

		public boolean hasChars() {
			return index < value.length();
		}

		public void setValue(String value) {
			this.value = value;
			index = 0;
		}

		public char getChar() {
			if (index == value.length()) {
				throw new IndexOutOfBoundsException("Index variable overrun.");
			}
			return value.charAt(index);
		}

		public void commitChanges() {
			++index;
		}

		@Override
		public String toString() {
			return value;
		}
	}

	private static enum ParserState {
		TEXT, OPEN_BRACE, VARIABLE, CLOSE_BRACE, WRITE_VARIABLE
	}
}