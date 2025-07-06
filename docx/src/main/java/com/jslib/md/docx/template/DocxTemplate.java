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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DocxTemplate {
	private static final Logger log = LoggerFactory.getLogger(DocxTemplate.class);
	
	private final static int BYTES_BUFFER_LENGTH = 1024;
	private final static int QUEUE_CAPACITY = 4096;

	private final BlockingQueue<Byte> queue;
	private final QueueReader queueReader;
	private final Thread thread;

	public DocxTemplate(Properties properties) throws InterruptedException {
		log.trace("DocxTemplate(Properties properties)");

		log.debug("Create queue");
		this.queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
		log.debug("Create queue reader");
		this.queueReader = new QueueReader(queue);

		log.debug("Create input stream:template.docx");
		InputStream inputStream = getClass().getResourceAsStream("/template.docx");
		log.debug("Create output stream:queue writer");
		QueueWriter queueWriter = new QueueWriter(queue);

		ITransformer transformer = new VariablesInjector(BYTES_BUFFER_LENGTH, properties);
		Runnable runnable = new ProcessorThread(queueReader, inputStream, queueWriter, transformer);

		this.thread = new Thread(runnable);
		this.thread.setDaemon(false);
		log.debug("Start processor thread");
		this.thread.start();
	}

	public InputStream getInputStream() {
		log.trace("getInputStream()");
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
			log.trace("run()");

			byte[] buffer = new byte[BYTES_BUFFER_LENGTH];
			int bytesRead;

			try (ZipInputStream zipInput = new ZipInputStream(inputStream); ZipOutputStream zipOutput = new ZipOutputStream(outputStream);) {
				ZipEntry entryInput;
				while ((entryInput = zipInput.getNextEntry()) != null) {
					log.debug("Retrieve input zip entry:", entryInput.getName());

					ZipEntry entryOutput = new ZipEntry(entryInput.getName());
					zipOutput.putNextEntry(entryOutput);
					log.debug("Create ouput zip entry:", entryOutput.getName());

					while ((bytesRead = zipInput.read(buffer)) != -1) {
						transformer.write(buffer, 0, bytesRead);
						while ((bytesRead = transformer.read(buffer)) > 0) {
							//System.out.print(new String(buffer, 0, bytesRead, Charset.forName("UTF-8")));
							zipOutput.write(buffer, 0, bytesRead);
						}
					}

					log.debug("Close input zip entry");
					zipInput.closeEntry();
					log.debug("Close output zip entry");
					zipOutput.closeEntry();
					log.debug("Output zip entry size: {}", entryOutput.getSize());
				}

				log.debug("Auto-close input zip");
				log.debug("Auto-close output zip");
			} catch (Exception e) {
				e.printStackTrace();
			}

			log.debug("Close queue reader");
			queueReader.close();

			log.debug("close processor thread");
		}
	}
}
