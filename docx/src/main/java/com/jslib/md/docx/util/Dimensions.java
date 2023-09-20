package com.jslib.md.docx.util;

public class Dimensions {
	private int width;
	private int height;

	public Dimensions() {
		this.width = 0;
		this.height = 0;
	}

	public Dimensions(int width, int height) {
		this.width = width;
		this.height = height;
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public void scaleWidth(int width) {
		this.height = (int) ((float) width * (float) this.height / (float) this.width);
		this.width = width;
	}

	@Override
	public String toString() {
		return width + " " + height;
	}
}
