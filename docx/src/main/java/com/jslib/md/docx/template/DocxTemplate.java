package com.jslib.md.docx.template;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class DocxTemplate {
	private final static int BYTES_BUFFER_LENGTH = 1024;
	private final static int QUEUE_CAPACITY = 4096;

	private final BlockingQueue<Byte> queue;
	private final QueueReader queueReader;
	private final Thread thread;

	public DocxTemplate(Properties properties) throws InterruptedException {
		log("constructor");

		log("-- Create queue");
		this.queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
		log("-- Create queue reader");
		this.queueReader = new QueueReader(queue);

		log("-- Create input stream:template.docx");
		InputStream inputStream = getClass().getResourceAsStream("/template.docx");
		log("-- Create output stream:queue writer");
		QueueWriter queueWriter = new QueueWriter(queue);

		ITransformer transformer = new VariablesInjector(BYTES_BUFFER_LENGTH, properties);
		Runnable runnable = new ProcessorThread(queueReader, inputStream, queueWriter, transformer);

		this.thread = new Thread(runnable);
		this.thread.setDaemon(false);
		log("-- Start processor thread");
		this.thread.start();
	}

	public InputStream getInputStream() {
		log("get input stream");
		return queueReader;
	}

	private static class ProcessorThread implements Runnable {
		private final QueueReader queueReader;
		private final InputStream inputStream;
		private OutputStream outputStream;
		private final ITransformer transformer;

		public ProcessorThread(QueueReader queueReader, InputStream inputStream, OutputStream outputStream, ITransformer transformer) {
			this.queueReader = queueReader;
			this.inputStream = new BufferedInputStream(inputStream);
			this.outputStream = new BufferedOutputStream(outputStream);
			this.transformer = transformer;
		}

		@Override
		public void run() {
			log("run processor thread");

			byte[] buffer = new byte[BYTES_BUFFER_LENGTH];
			int bytesRead;

			try (ZipInputStream zipInput = new ZipInputStream(inputStream); ZipOutputStream zipOutput = new ZipOutputStream(outputStream);) {
				ZipEntry entryInput;
				while ((entryInput = zipInput.getNextEntry()) != null) {
					log("-- Retrieve input zip entry:", entryInput.getName());

					ZipEntry entryOutput = new ZipEntry(entryInput.getName());
					zipOutput.putNextEntry(entryOutput);
					log("-- Create ouput zip entry:", entryOutput.getName());

					while ((bytesRead = zipInput.read(buffer)) != -1) {
						transformer.write(buffer, 0, bytesRead);
						while ((bytesRead = transformer.read(buffer)) > 0) {
							//System.out.print(new String(buffer, 0, bytesRead, Charset.forName("UTF-8")));
							zipOutput.write(buffer, 0, bytesRead);
						}
					}

					log("-- Close input zip entry");
					zipInput.closeEntry();
					log("-- Close output zip entry");
					zipOutput.closeEntry();
					log("-- Output zip entry size:", entryOutput.getSize());
				}

				log("-- Auto-close input zip");
				log("-- Auto-close output zip");
			} catch (Exception e) {
				e.printStackTrace();
			}

			log("-- Close queue reader");
			queueReader.close();

			log("close processor thread");
		}
	}

	static final void log(Object... objects) {
		for (Object object : objects) {
			System.out.print(object instanceof String ? (String) object : object.toString());
			System.out.print(' ');
		}
		System.out.println();
	}
}
