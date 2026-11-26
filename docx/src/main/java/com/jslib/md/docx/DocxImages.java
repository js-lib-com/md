package com.jslib.md.docx;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;

import javax.imageio.ImageIO;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.jslib.md.docx.xml.DocxDocument;
import com.jslib.md.docx.xml.Wml;

import net.coobird.thumbnailator.Thumbnails;

/**
 * Images embedded into document as inline drawings. Images wider than {@value #MAX_WIDTH} pixels are scaled down,
 * keeping aspect ratio.
 */
final class DocxImages {
	/** Maximum image width, in pixels, that fits page content width. */
	private static final int MAX_WIDTH = 600;

	/** English Metric Units per pixel, at 96 DPI. */
	private static final int EMU_PER_PIXEL = 9525;

	private static final String SHADOW_EFFECT = """
			<a:effectLst>
			  <a:outerShdw blurRad="50800" dist="38100" dir="2700000" algn="tl" rotWithShape="0">
			    <a:prstClr val="black">
			      <a:alpha val="40000"/>
			    </a:prstClr>
			  </a:outerShdw>
			</a:effectLst>
			""";

	/**
	 * Inline drawing template; format arguments are width and height in EMU, drawing ID, image name, image relationship
	 * ID and optional shadow effect. Indentation white spaces are removed after parsing.
	 */
	private static final String DRAWING = """
			<w:drawing xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
			    xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
			    xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
			    xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"
			    xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
			  <wp:inline distT="0" distB="0" distL="0" distR="0">
			    <wp:extent cx="%1$d" cy="%2$d"/>
			    <wp:docPr id="%3$d" name="Picture %3$d" descr="%4$s"/>
			    <wp:cNvGraphicFramePr>
			      <a:graphicFrameLocks noChangeAspect="1"/>
			    </wp:cNvGraphicFramePr>
			    <a:graphic>
			      <a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">
			        <pic:pic>
			          <pic:nvPicPr>
			            <pic:cNvPr id="0" name="%4$s"/>
			            <pic:cNvPicPr>
			              <a:picLocks noChangeAspect="1"/>
			            </pic:cNvPicPr>
			          </pic:nvPicPr>
			          <pic:blipFill>
			            <a:blip r:embed="%5$s"/>
			            <a:stretch>
			              <a:fillRect/>
			            </a:stretch>
			          </pic:blipFill>
			          <pic:spPr>
			            <a:xfrm>
			              <a:off x="0" y="0"/>
			              <a:ext cx="%1$d" cy="%2$d"/>
			            </a:xfrm>
			            <a:prstGeom prst="rect">
			              <a:avLst/>
			            </a:prstGeom>
			            %6$s
			          </pic:spPr>
			        </pic:pic>
			      </a:graphicData>
			    </a:graphic>
			  </wp:inline>
			</w:drawing>
			""";

	private final DocxDocument document;
	/** Base directory for relative image paths. */
	private final File imagesDir;
	private final boolean shadow;

	DocxImages(DocxDocument document, File imagesDir, boolean shadow) {
		this.document = document;
		this.imagesDir = imagesDir;
		this.shadow = shadow;
	}

	/**
	 * Embed image into document and create inline drawing referencing it.
	 *
	 * @param path image path, relative to images directory, as declared in Markdown source.
	 * @return detached drawing element, to be appended to a run.
	 * @throws IOException if image file is missing, not readable or its format is not supported, e.g. SVG.
	 */
	Element drawing(String path) throws IOException {
		File imageFile = new File(imagesDir, path);
		if (!imageFile.isFile()) {
			throw new IOException("Missing image file " + imageFile);
		}
		BufferedImage image = ImageIO.read(imageFile);
		if (image == null) {
			// ImageIO returns null, not an exception, for unsupported formats
			throw new IOException("Unsupported image format " + imageFile);
		}

		int width = image.getWidth();
		int height = image.getHeight();
		byte[] data;
		String extension;
		if (width > MAX_WIDTH) {
			height = (int) ((float) MAX_WIDTH * height / width);
			width = MAX_WIDTH;
			ByteArrayOutputStream buffer = new ByteArrayOutputStream();
			Thumbnails.of(image).size(width, height).outputFormat("png").toOutputStream(buffer);
			data = buffer.toByteArray();
			extension = "png";
		} else {
			data = Files.readAllBytes(imageFile.toPath());
			extension = extension(imageFile.getName());
		}

		String relId;
		try {
			relId = document.addImage(data, extension);
		} catch (IllegalArgumentException e) {
			throw new IOException(e.getMessage(), e);
		}
		String name = new File(path).getName();
		String xml = String.format(DRAWING, (long) width * EMU_PER_PIXEL, (long) height * EMU_PER_PIXEL, document.nextDrawingId(), Wml.escapeXml(name), relId, shadow ? SHADOW_EFFECT : "");
		Document fragment = Wml.parse(new ByteArrayInputStream(xml.getBytes(UTF_8)));
		Wml.stripWhitespace(fragment.getDocumentElement());
		return (Element) document.body().getOwnerDocument().importNode(fragment.getDocumentElement(), true);
	}

	private static String extension(String fileName) {
		int dotIndex = fileName.lastIndexOf('.');
		return dotIndex == -1 ? "" : fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
	}
}
