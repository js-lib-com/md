package com.jslib.md.docx.util;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import net.coobird.thumbnailator.Thumbnails;

public class Images {
	/**
	 * Image dimensions, in pixels.
	 *
	 * @throws IOException if image file is missing, not readable or its format is not supported, e.g. SVG.
	 */
	public Dimensions getDimension(File imageFile) throws IOException {
		if (!imageFile.isFile()) {
			throw new IOException("Missing image file " + imageFile);
		}
		BufferedImage bufferedImage = ImageIO.read(imageFile);
		if (bufferedImage == null) {
			// ImageIO returns null, not an exception, for unsupported formats
			throw new IOException("Unsupported image format " + imageFile);
		}
		return new Dimensions(bufferedImage.getWidth(), bufferedImage.getHeight());
	}

	/** Resize image to a temporary PNG file, deleted on JVM exit. */
	public File resize(File imageFile, Dimensions dimensions) throws IOException {
		File resizedImageFile = File.createTempFile("resized-image", ".png");
		resizedImageFile.deleteOnExit();
		Thumbnails.of(imageFile).size(dimensions.getWidth(), dimensions.getHeight()).toFile(resizedImageFile);
		return resizedImageFile;
	}
}
