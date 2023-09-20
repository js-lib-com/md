package com.jslib.md.docx.template;

import java.io.IOException;

/**
 * Stream transformer designed to be integrated in a stream pipeline for on the fly bytes processing. This transformer sits
 * between source and sink bytes streams; it appears as an output stream for the source stream and an input stream for the sink.
 *
 * Canonical use case is to read bytes from source stream and write them to transformer then read processed bytes from
 * transformer and write to sink stream. Note that transformer write should consume all given bytes. Also, because transformer
 * may produce more bytes than buffer capacity we need to read transformer in a loop till returns 0.
 * 
 * <pre>
 * while ((bytesRead = source.read(buffer)) != -1) {
 * 	transformer.write(buffer, 0, bytesRead);
 * 	while ((bytesRead = transformer.read(buffer)) > 0) {
 * 		sink.write(buffer, 0, bytesRead);
 * 	}
 * }
 * </pre>
 * 
 * @author Iulian Rotaru
 */
interface ITransformer {

	void write(byte[] buffer, int offset, int length) throws IOException;

	int read(byte[] buffer) throws IOException;

}