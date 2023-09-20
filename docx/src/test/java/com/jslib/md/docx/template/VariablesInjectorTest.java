package com.jslib.md.docx.template;

import java.io.IOException;
import java.util.Properties;

import org.junit.Before;
import org.junit.Test;

import com.jslib.md.docx.template.VariablesInjector;

public class VariablesInjectorTest {
	private VariablesInjector injector;

	@Before
	public void beforeTest() {
		injector = new VariablesInjector(4, new Properties());
	}
	
	@Test
	public void writeUndeflow() throws IOException {
		//byte[] utf8Bytes = { (byte)0xE2, (byte)0x82, (byte)0xAC };
		// EUR€
		byte[] buffer = { 69, 85, 82, -30, -126, -84 };
		
		injector.write(buffer, 0, 5);
		
	}
}
