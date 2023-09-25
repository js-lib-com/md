package com.jslib.md.docx;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.List;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.xmlbeans.XmlException;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableBody;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableHead;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Heading;
import org.commonmark.node.Image;
import org.commonmark.node.Link;
import org.commonmark.node.ListItem;
import org.commonmark.node.Paragraph;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.openxmlformats.schemas.drawingml.x2006.main.CTEffectList;
import org.openxmlformats.schemas.drawingml.x2006.main.CTOuterShadowEffect;
import org.openxmlformats.schemas.drawingml.x2006.main.CTPositiveFixedPercentage;
import org.openxmlformats.schemas.drawingml.x2006.main.CTPresetColor;
import org.openxmlformats.schemas.drawingml.x2006.main.STPresetColorVal;
import org.openxmlformats.schemas.drawingml.x2006.main.STRectAlignment;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTDecimalNumber;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTHyperlink;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTNumPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTOnOff;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTString;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblLook;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;

import com.jslib.md.docx.template.DocxTemplate;
import com.jslib.md.docx.util.Dimensions;
import com.jslib.md.docx.util.Images;

public class DocxVisitor extends CustomVisitor {
	private final Images images;
	private final File imagesDir;
	private final XWPFDocument document;
	// the width of the page used for content display, i.e. page size minus left and right margins
	private final BigInteger contentPageWidth;

	@SuppressWarnings("unused")
	private final DocxStyles styles;
	private final DocxNumbering numbering;

	private XWPFParagraph currentParagraph;
	private XWPFRun currentRun;
	private boolean ignoreParagraph;
	private boolean blockQuoteDetected;

	public DocxVisitor(DocxTemplate template) throws IOException, XmlException {
		this(new XWPFDocument(template.getInputStream()));
	}

	public DocxVisitor(DocxTemplate template, File imagesDir) throws IOException, XmlException {
		this(new XWPFDocument(template.getInputStream()), imagesDir);
	}

	DocxVisitor(XWPFDocument document, File... imagesDir) throws IOException, XmlException {
		super();
		log("constructor");

		this.images = new Images();
		this.imagesDir = imagesDir.length == 1 ? imagesDir[0] : new File(".");
		this.document = document;

		log("-- Create document styles");
		this.styles = new DocxStyles(document);

		log("-- Create document numbering");
		this.numbering = new DocxNumbering(document);

		log("-- Compute available page width");
		CTSectPr sectPr = document.getDocument().getBody().getSectPr();
		CTPageSz pageSize = sectPr.getPgSz();

		CTPageMar pageMargins = sectPr.getPgMar();
		BigInteger leftMargin = (BigInteger) pageMargins.getLeft();
		BigInteger rightMargin = (BigInteger) pageMargins.getRight();

		this.contentPageWidth = ((BigInteger) pageSize.getW()).subtract(leftMargin).subtract(rightMargin);
	}

	public XWPFDocument getDocument() {
		return document;
	}

	@Override
	public void visit(Heading heading) {
		log("visit heading");

		log("-- Create paragraph");
		currentParagraph = document.createParagraph();

		int headingLevel = heading.getLevel();
		log("-- Set style: Heading", headingLevel);
		currentParagraph.setStyle(DocxStyles.getHeadingStyleId(headingLevel));

		log("-- Set heading numbering");
		// <w:pPr>
		// ..<w:numPr>
		// ....<w:ilvl w:val="3" />
		// ....<w:numId w:val="1" />
		// ..</w:numPr>
		// </w:pPr>

		CTPPr pPr = currentParagraph.getCTP().getPPr();
		if (pPr == null) {
			pPr = currentParagraph.getCTP().addNewPPr();
		}

		CTNumPr numPr = pPr.getNumPr();
		if (numPr == null) {
			numPr = pPr.addNewNumPr();
		}

		CTDecimalNumber ilvl = numPr.getIlvl();
		if (ilvl == null) {
			ilvl = numPr.addNewIlvl();
		}
		ilvl.setVal(BigInteger.valueOf(headingLevel - 1));

		CTDecimalNumber numId = numPr.getNumId();
		if (numId == null) {
			numId = numPr.addNewNumId();
		}
		numId.setVal(numbering.getHeadingsNumId());

		super.visit(heading);
	}

	@Override
	public void visit(Paragraph paragraph) {
		log("visit paragraph");
		if (!ignoreParagraph) {
			log("-- Create paragraph");
			currentParagraph = document.createParagraph();
			if (blockQuoteDetected) {
				blockQuoteDetected = false;
				log("-- Set style: Quote");
				currentParagraph.setStyle(DocxStyles.getQuoteStyleId());
			}
		}
		ignoreParagraph = false;
		super.visit(paragraph);
	}

	@Override
	public void visit(BlockQuote blockQuote) {
		log("visit block quote");
		blockQuoteDetected = true;
		super.visit(blockQuote);
	}

	@Override
	public void visit(FencedCodeBlock fencedCodeBlock) {
		log("visit code block");
		if (!ignoreParagraph) {
			log("-- Create paragraph");
			currentParagraph = document.createParagraph();
			log("-- Create run");
			XWPFRun run = currentParagraph.createRun();
			run.setText(fencedCodeBlock.getLiteral());
			// TODO: use fencedCodeBlock.getInfo() to set style depending on language, e.g. java
		}
		ignoreParagraph = false;
		super.visit(fencedCodeBlock);
	}

	@Override
	public void visit(Emphasis emphasis) {
		log("visit emphasis");
		assert currentParagraph != null;
		log("-- Create run");
		currentRun = currentParagraph.createRun();
		log("-- Set run italic");
		currentRun.setItalic(true);
		super.visit(emphasis);
	}

	@Override
	public void visit(StrongEmphasis strongEmphasis) {
		log("visit strong empahsis");
		assert currentParagraph != null;
		log("-- Create run");
		currentRun = currentParagraph.createRun();
		log("-- Set run bold");
		currentRun.setBold(true);
		super.visit(strongEmphasis);
	}

	@Override
	public void visit(Text text) {
		log("visit text");
		if (currentRun == null) {
			assert currentParagraph != null;
			log("-- Create run");
			currentRun = currentParagraph.createRun();
		}
		String textLiteral = text.getLiteral();
		if (pictureDescription) {
			pictureDescription = false;
			StringBuilder textBuilder = new StringBuilder();
			textBuilder.append("Figure ");
			textBuilder.append(++pictureIndex);
			textBuilder.append(" - ");
			textBuilder.append(textLiteral);
			textLiteral = textBuilder.toString();
		}
		log("-- Set run text: |", textLiteral, "|");
		currentRun.setText(textLiteral);
		currentRun = null;
	}

	// ------------------------------------------------------------------------

	@Override
	public void visit(Link link) {
		log("visit link");

		log("-- Create hyperlink run to:", link.getDestination());
		assert currentParagraph != null;
		currentRun = currentParagraph.createHyperlinkRun(link.getDestination());

		// <w:hyperlink r:id="rId7">
		// ..<w:r>
		// ....<w:rPr>
		// ......<w:color w:val="0000FF" />
		// ......<w:rStyle w:val="Hyperlink" />
		// ....</w:rPr>
		// ....<w:t>see visit(Text)</w:t>
		// ..</w:r>
		// </w:hyperlink>

		log("-- Set hyperlink style:", DocxStyles.getLinkStyleId());
		// w:hyperlink
		CTHyperlink ctHyperlink = ((XWPFHyperlinkRun) currentRun).getCTHyperlink();
		assert ctHyperlink.getRArray().length == 1;
		// w:r
		CTR ctr = ctHyperlink.getRArray(0);
		// w:rPr
		CTRPr ctrPr = ctr.getRPr();
		if (ctrPr == null) {
			ctrPr = ctr.addNewRPr();
		}

		// w:rStyle
		CTString rStyle = CTString.Factory.newInstance();
		rStyle.setVal(DocxStyles.getLinkStyleId());
		ctrPr.setRStyleArray(new CTString[] { rStyle });

		// crazy: without next statement above w:rStyle is not written in document xml
		// w:color
		ctrPr.addNewColor().setVal("0000FF");

		// w:t
		super.visit(link);
	}

	// ------------------------------------------------------------------------

	private int pictureIndex;
	private boolean pictureDescription;

	@Override
	public void visit(Image image) {
		log("visit image");

		log("-- Create run");
		XWPFRun run = currentParagraph.createRun();

		File imageFile = new File(imagesDir, image.getDestination());
		Dimensions dimensions = images.getDimension(imageFile);
		log("-- Image dimensions", dimensions);
		if (dimensions.getWidth() > 600) {
			dimensions.scaleWidth(600);
			log("-- Resize image to", dimensions);
			imageFile = images.resize(imageFile, dimensions);
		}

		try (InputStream inputStream = new FileInputStream(imageFile)) {
			int width = Units.pixelToEMU(dimensions.getWidth());
			int height = Units.pixelToEMU(dimensions.getHeight());
			log("-- Add run picture:", imageFile);
			XWPFPicture picture = run.addPicture(inputStream, pictureType(imageFile.getName()), imageFile.getName(), width, height);

			log("-- Add shadow effect");
			shadowEffect(picture);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// separated paragraph for image caption
		log("-- Create image caption");
		currentParagraph = document.createParagraph();
		pictureDescription = true;

		super.visit(image);
	}

	private static int pictureType(String path) {
		switch (getFileExtension(path)) {
		case "emf":
			return XWPFDocument.PICTURE_TYPE_EMF;
		case "wmf":
			return XWPFDocument.PICTURE_TYPE_WMF;
		case "pict":
			return XWPFDocument.PICTURE_TYPE_PICT;
		case "jpeg":
		case "jpg":
			return XWPFDocument.PICTURE_TYPE_JPEG;
		case "png":
			return XWPFDocument.PICTURE_TYPE_PNG;
		case "dib":
			return XWPFDocument.PICTURE_TYPE_DIB;
		case "gif":
			return XWPFDocument.PICTURE_TYPE_GIF;
		case "tiff":
			return XWPFDocument.PICTURE_TYPE_TIFF;
		case "eps":
			return XWPFDocument.PICTURE_TYPE_EPS;
		case "bmp":
			return XWPFDocument.PICTURE_TYPE_BMP;
		case "wpg":
			return XWPFDocument.PICTURE_TYPE_WPG;
		default:
			System.err.println("Unsupported picture:" + path + ". Expected emf|wmf|pict|jpeg|png|dib|gif|tiff|eps|bmp|wpg");
			return 0;
		}
	}

	private static void shadowEffect(XWPFPicture picture) {
		// <a:effectLst>
		// ..<a:outerShdw blurRad="50800" dist="38100" dir="2700000" algn="tl" rotWithShape="false">
		// ....<a:prstClr val="black">
		// ......<a:alpha val="40000" />
		// ....</a:prstClr>
		// ..</a:outerShdw>
		// </a:effectLst>

		// a:outerShdw
		CTOuterShadowEffect outerShadow = CTOuterShadowEffect.Factory.newInstance();
		outerShadow.setBlurRad(50800);
		outerShadow.setDist(38100);
		outerShadow.setDir(2700000);
		outerShadow.setAlgn(STRectAlignment.TL);
		outerShadow.setRotWithShape(false);

		// a:alpha
		CTPositiveFixedPercentage transparency = CTPositiveFixedPercentage.Factory.newInstance();
		transparency.setVal(new BigInteger("40000"));

		// a:prstClr
		CTPresetColor presetColor = CTPresetColor.Factory.newInstance();
		presetColor.setVal(STPresetColorVal.BLACK);
		presetColor.setAlphaArray(new CTPositiveFixedPercentage[] { transparency });
		outerShadow.setPrstClr(presetColor);

		// a:effectLst
		CTEffectList effectList = CTEffectList.Factory.newInstance();
		effectList.setOuterShdw(outerShadow);

		picture.getCTPicture().getSpPr().setEffectLst(effectList);
	}

	private static String getFileExtension(String path) {
		if (path == null || path.isEmpty()) {
			return "";
		}
		int lastDotIndex = path.lastIndexOf(".");
		if (lastDotIndex == -1 || lastDotIndex == path.length() - 1) {
			return "";
		}
		return path.substring(lastDotIndex + 1);
	}

	// ------------------------------------------------------------------------

	@Override
	public void visit(BulletList bulletList) {
		log("visit bullet list");
		super.visit(bulletList);
	}

	@Override
	public void visit(ListItem listItem) {
		log("visit list item");

		log("-- Create bullet item");
		currentParagraph = document.createParagraph();
		currentParagraph.setStyle(DocxStyles.getListStyleId());
		ignoreParagraph = true;

		log("-- Set list item numbering");
		// <w:pPr>
		// ..<w:numPr>
		// ....<w:ilvl w:val="0" />
		// ....<w:numId w:val="1" />
		// ..</w:numPr>
		// </w:pPr>

		CTPPr pPr = currentParagraph.getCTP().getPPr();
		if (pPr == null) {
			pPr = currentParagraph.getCTP().addNewPPr();
		}

		CTNumPr numPr = pPr.getNumPr();
		if (numPr == null) {
			numPr = pPr.addNewNumPr();
		}

		CTDecimalNumber ilvl = numPr.getIlvl();
		if (ilvl == null) {
			ilvl = numPr.addNewIlvl();
		}
		ilvl.setVal(BigInteger.ZERO);

		CTDecimalNumber numId = numPr.getNumId();
		if (numId == null) {
			numId = numPr.addNewNumId();
		}
		numId.setVal(numbering.getBulletsNumId());

		super.visit(listItem);
	}

	// ------------------------------------------------------------------------

	private XWPFTable currentTable;
	private XWPFTableRow currentTableRow;
	private boolean tableRowIndexInitialized;
	private int tableRowIndex;
	private int tableColumnIndex;

	@Override
	public void visit(TableBlock table) {
		log("visit table block");

		log("-- Create table");
		currentTable = document.createTable();

		log("-- Set table style ID ", DocxStyles.getTableStyleId());
		currentTable.setStyleID(DocxStyles.getTableStyleId());

		CTTblPr tblPr = currentTable.getCTTbl().getTblPr();
		log("-- Unset table borders");
		tblPr.unsetTblBorders();

		log("-- Set table width to fill page");
		CTTblWidth width = CTTblWidth.Factory.newInstance();
		width.setW(contentPageWidth);
		width.setType(STTblWidth.DXA);
		tblPr.setTblW(width);

		log("-- Set table look");
		CTTblLook tblLook = tblPr.isSetTblLook() ? tblPr.getTblLook() : tblPr.addNewTblLook();
		try {
			tblLook.setVal(Hex.decodeHex("04A0".toCharArray()));
		} catch (DecoderException e) {
			e.printStackTrace();
		}

		super.visit(table);

		int tableRowsCount = tableRowIndex;
		assert tableRowsCount > 0;
		int tableColumnsCount = currentTable.getRow(0).getTableCells().size();

		CTOnOff noWrapFlag = CTOnOff.Factory.newInstance();
		noWrapFlag.setVal("1");

		for (XWPFTableRow row : currentTable.getRows()) {
			for (int columnIndex = 0; columnIndex < tableColumnsCount - 1; ++columnIndex) {
				XWPFTableCell cell = row.getTableCells().get(columnIndex);
				CTTcPr properties = cell.getCTTc().getTcPr();
				if (properties == null) {
					properties = cell.getCTTc().addNewTcPr();
				}
				properties.setNoWrap(noWrapFlag);
			}
		}

		System.out.println(currentTable.getCTTbl().xmlText());

		log("-- Add space after table");
		XWPFParagraph space = document.createParagraph();
		space.setSpacingAfter(0);
		space.setSpacingBefore(0);
		space.createRun().setFontSize(0);
	}

	@Override
	public void visit(TableHead head) {
		log("visit table head");
		tableRowIndexInitialized = true;
		tableRowIndex = 0;
		super.visit(head);
	}

	@Override
	public void visit(TableBody body) {
		log("visit table body");
		if (!tableRowIndexInitialized) {
			tableRowIndex = 0;
		}
		tableRowIndexInitialized = false;
		super.visit(body);
	}

	@Override
	public void visit(TableRow row) {
		log("visit table row");

		assert currentTable != null;
		if (tableRowIndex < currentTable.getNumberOfRows()) {
			log("-- Retrieve row ", tableRowIndex);
			currentTableRow = currentTable.getRow(tableRowIndex);
		} else {
			log("-- Create row ", tableRowIndex);
			currentTableRow = currentTable.createRow();
		}

		++tableRowIndex;
		tableColumnIndex = 0;
		super.visit(row);
	}

	@Override
	public void visit(TableCell mdCell) {
		log("visit table cell");

		XWPFTableCell docxCell = null;
		assert currentTableRow != null;
		if (tableColumnIndex < currentTableRow.getTableCells().size()) {
			log("-- Retrieve cell ", tableColumnIndex);
			docxCell = currentTableRow.getCell(tableColumnIndex);
		} else {
			log("-- Create cell ", tableColumnIndex);
			docxCell = currentTableRow.createCell();
		}
		++tableColumnIndex;

		log("-- Create paragraph");
		List<XWPFParagraph> paragraphs = docxCell.getParagraphs();
		if (paragraphs.isEmpty()) {
			currentParagraph = docxCell.addParagraph();
		} else {
			currentParagraph = paragraphs.get(0);
		}

		ParagraphAlignment alignment = null;
		if (mdCell.getAlignment() != null) {
			switch (mdCell.getAlignment()) {
			case LEFT:
				alignment = ParagraphAlignment.LEFT;
				break;
			case CENTER:
				alignment = ParagraphAlignment.CENTER;
				break;
			case RIGHT:
				alignment = ParagraphAlignment.RIGHT;
				break;
			}
		}
		if (alignment != null) {
			log("-- Set paragraph alignment ", alignment);
			currentParagraph.setAlignment(alignment);
		}

		super.visit(mdCell);
	}

	// ------------------------------------------------------------------------

	private static final void log(Object... objects) {
		for (Object object : objects) {
			System.out.print(object instanceof String ? (String) object : object.toString());
			System.out.print(' ');
		}
		System.out.println();
	}
}
