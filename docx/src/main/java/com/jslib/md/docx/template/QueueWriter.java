package com.jslib.md.docx.template;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Bytes queue writer using output stream interface. Bytes queue is created externally and injected via constructor. Because
 * queue is blocking stream write operation waits with timeout till queue space is available.
 * 
 * @author Iulian Rotaru
 */
class QueueWriter extends OutputStream {
	/** Timeout value in milliseconds for write operation. */
	private static final int WRITE_TIMEOUT = 4000;

	private final BlockingQueue<Byte> queue;

	public QueueWriter(BlockingQueue<Byte> queue) {
		super();
		this.queue = queue;
	}

	/**
	 * Write a byte to the blocking queue.
	 * 
	 * @param value byte value represented by the least significant octet from integer value.
	 * @throws IOException on queue write timeout or thread interruption.
	 */
	@Override
	public void write(int value) throws IOException {
		try {
			if (!queue.offer((byte) value, WRITE_TIMEOUT, TimeUnit.MILLISECONDS)) {
				throw new IOException("Timeout on queue write.");
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Thread interruption on queue write.", e);
		}
	}
}