package com.jslib.md.docx.template;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;

import org.junit.Test;

public class CharsetDecoderApiTest {
	@Test
	public void GivenUnderflow_WhenDecode_ThenByteBufferPositionAtLastCharStart() {
		// given
		byte[] buffer = { 69, 85, 82, -30, -126, -84 }; 
		// EUR€: E, U and R one byte code and € of three bytes

		CharsetDecoder decoder = Charset.forName("UTF-8").newDecoder();
		decoder.onMalformedInput(CodingErrorAction.REPORT);
		decoder.onUnmappableCharacter(CodingErrorAction.REPORT);

		ByteBuffer byteBuffer = ByteBuffer.allocate(10);
		CharBuffer charBuffer = CharBuffer.allocate(10);
		charBuffer.flip();

		// last character (€) is incomplete; only one byte from three 
		byteBuffer.put(buffer, 0, 4);
		// prepare byte buffer for read
		byteBuffer.flip();
		// prepare character buffer for write
		charBuffer.compact();

		// when
		CoderResult result = decoder.decode(byteBuffer, charBuffer, false);

		// then
		assertThat(result.isUnderflow(), equalTo(true));
		assertThat(byteBuffer.position(), equalTo(3));
		// byte buffer position is at last character start
		assertThat(byteBuffer.get(3), equalTo((byte) -30));
		// above byte buffer get() does not move position
		assertThat(byteBuffer.position(), equalTo(3));

		// after compact byte buffer has only the byte not processed from char €
		// it is ready to accumulate the rest of the two bytes to complete the code point
		byteBuffer.compact();
		assertThat(byteBuffer.position(), equalTo(1));
		assertThat(byteBuffer.limit(), equalTo(10));
		assertThat(byteBuffer.get(0), equalTo((byte) -30));
		
		// character buffer contains only those 3 complete characters
		assertThat(charBuffer.position(), equalTo(3));
		charBuffer.flip();
		assertThat(charBuffer.position(), equalTo(0));
		assertThat(charBuffer.limit(), equalTo(3));
		assertThat(charBuffer.toString(), equalTo("EUR"));
	}
}
