package com.jslib.md.docx.util;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import net.coobird.thumbnailator.Thumbnails;

public class Images {

	public Dimensions getDimension(File imageFile) {
		try {
			BufferedImage bufferedImage = ImageIO.read(imageFile);
			return new Dimensions(bufferedImage.getWidth(), bufferedImage.getHeight());
		} catch (IOException e) {
			e.printStackTrace();
			return new Dimensions();
		}
	}

	public File resize(File imageFile, Dimensions dimensions) {
		try {
			File resizedImageFile = File.createTempFile("resized-image", ".png");
			Thumbnails.of(imageFile).size(dimensions.getWidth(), dimensions.getHeight()).toFile(resizedImageFile);
			return resizedImageFile;
		} catch (IOException e) {
			e.printStackTrace();
			return imageFile;
		}
	}
}