package com.jslib.md.docx.template;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Bytes queue reader using input stream interface. Bytes queue is created externally and injected via constructor. Because
 * queue is blocking stream read operation waits with timeout till queue value is available.
 * 
 * @author Iulian Rotaru
 */
class QueueReader extends InputStream {
	/** Timeout value in milliseconds for read operation. */
	private static final int READ_TIMEOUT = 4000;

	private final BlockingQueue<Byte> queue;
	private final Thread readerThread;
	private boolean closed;

	public QueueReader(BlockingQueue<Byte> queue) {
		super();
		this.queue = queue;
		this.readerThread = Thread.currentThread();
	}

	/**
	 * Read a byte from the blocking queue. Returns an integer value with byte extracted from queue as least significant octet.
	 * Return -1 to signal that input stream was closed - see {@link #close()}
	 * 
	 * @return byte value from queue or -1 on input stream closed.
	 * @throws IOException on queue write timeout or thread interruption.
	 */
	@Override
	public int read() throws IOException {
		if (closed && queue.isEmpty()) {
			return -1;
		}
		for (;;) {
			try {
				Byte value = queue.poll(READ_TIMEOUT, TimeUnit.MILLISECONDS);
				if (value == null) {
					throw new IOException("Timeout on queue read.");
				}
				return value & 0xFF;
			} catch (InterruptedException e) {
				if (closed) {
					return -1;
				}
				Thread.currentThread().interrupt();
				throw new IOException("Thread interruption on queue read.", e);
			}
		}
	}

	/**
	 * Close this queue reader. If current thread is blocked on queue read operation and queue is empty this method perform
	 * thread interruption and {@link #read()} method terminates with -1.
	 */
	@Override
	public void close() {
		closed = true;
		if (queue.isEmpty()) {
			readerThread.interrupt();
		}
	}
}