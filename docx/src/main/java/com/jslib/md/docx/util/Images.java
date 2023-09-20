package com.jslib.md.docx.util;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import javax.imageio.ImageIO;

import org.im4java.core.ConvertCmd;
import org.im4java.core.IMOperation;

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

	public File resize_eol(File imageFile, Dimensions dimensions) {
		try {
			BufferedImage image = ImageIO.read(imageFile);
			BufferedImage resizedImage = new BufferedImage(dimensions.getWidth(), dimensions.getHeight(), BufferedImage.TYPE_INT_ARGB);

			Graphics2D g = resizedImage.createGraphics();
			g.drawImage(image, 0, 0, dimensions.getWidth(), dimensions.getHeight(), null);
			g.dispose();

			File resizedImageFile = File.createTempFile("resized-image", ".png");
			try (OutputStream outputStream = new FileOutputStream(resizedImageFile)) {
				ImageIO.write(resizedImage, "png", outputStream);
			}
			return resizedImageFile;
		} catch (IOException e) {
			e.printStackTrace();
			return imageFile;
		}
	}

	public File resize_im(File imageFile, Dimensions dimensions) {
		try {
			ConvertCmd cmd = new ConvertCmd();

			IMOperation op = new IMOperation();
			op.addImage(imageFile.getAbsolutePath());
			op.scale(dimensions.getWidth(), dimensions.getHeight());

			File resizedImageFile = File.createTempFile("resized-image", ".png");
			op.addImage(resizedImageFile.getAbsolutePath());
			cmd.run(op);

			return resizedImageFile;
		} catch (Exception e) {
			e.printStackTrace();
			return imageFile;
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