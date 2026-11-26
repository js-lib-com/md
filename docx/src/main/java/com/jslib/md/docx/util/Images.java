package com.jslib.md.docx.util;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Images {
	private static final Logger log = LoggerFactory.getLogger(Images.class);

	public Dimensions getDimension(File imageFile) {
		log.trace("getDimension(File imageFile)");
		try {
			BufferedImage bufferedImage = ImageIO.read(imageFile);
			return new Dimensions(bufferedImage.getWidth(), bufferedImage.getHeight());
		} catch (IOException e) {
			log.error(e.getMessage(), e);
			return new Dimensions();
		}
	}

	public File resize(File imageFile, Dimensions dimensions) {
		log.trace("resize(File imageFile, Dimensions dimensions)");
		try {
			File resizedImageFile = File.createTempFile("resized-image", ".png");
			Thumbnails.of(imageFile).size(dimensions.getWidth(), dimensions.getHeight()).toFile(resizedImageFile);
			return resizedImageFile;
		} catch (IOException e) {
			log.error(e.getMessage(), e);
			return imageFile;
		}
	}
}