package com.credchain.modules.certificate.document;

import org.openpdf.text.Chunk;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.Image;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfDictionary;
import org.openpdf.text.pdf.PdfName;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfString;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Draws one certificate as an A4 landscape PDF with a QR code to the verification page.
 *
 * The PDF also carries the data needed to re-check it later (Phase 5: "upload PDF -> VALID / REVOKED / FAKE"),
 * stored as custom entries in the PDF's document info: CredChain-Payload (the exact JSON that was hashed),
 * CredChain-Proof, CredChain-MerkleRoot, CredChain-ChainId, CredChain-Contract and CredChain-CertHash.
 */
@Component
public class CertificatePdfRenderer {

    /** Prefix of the custom document-info keys; Phase 5 reads these back. */
    public static final String META_PREFIX = "CredChain-";
    public static final String META_FORMAT_VERSION = "1";

    private static final Color NAVY = new Color(0x1F, 0x3A, 0x5F);
    private static final Color GOLD = new Color(0xB8, 0x86, 0x0B);
    private static final Color GREY = new Color(0x55, 0x5B, 0x66);

    private static final Font INSTITUTION = new Font(Font.HELVETICA, 24, Font.BOLD, NAVY);
    private static final Font HEADING = new Font(Font.TIMES_ROMAN, 15, Font.ITALIC, GREY);
    private static final Font STUDENT = new Font(Font.TIMES_ROMAN, 30, Font.BOLD, Color.BLACK);
    private static final Font TITLE = new Font(Font.HELVETICA, 19, Font.BOLD, NAVY);
    private static final Font BODY = new Font(Font.HELVETICA, 12, Font.NORMAL, Color.BLACK);
    private static final Font LABEL = new Font(Font.HELVETICA, 9, Font.BOLD, GREY);
    private static final Font VALUE = new Font(Font.HELVETICA, 9, Font.NORMAL, Color.BLACK);
    private static final Font SMALL = new Font(Font.COURIER, 7, Font.NORMAL, GREY);

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm 'UTC'", Locale.ENGLISH).withZone(ZoneOffset.UTC);

    private static final int QR_PIXELS = 400;
    private static final float QR_POINTS = 110f;

    public byte[] render(CertificateDocument doc) {
        Rectangle page = PageSize.A4.rotate();
        Document document = new Document(page, 50, 50, 45, 40);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            addMetadata(document, writer, doc);
            document.open();

            drawBorder(writer.getDirectContentUnder(), page);

            centered(document, doc.institutionName(), INSTITUTION, 18);
            centered(document, "This is to certify that", HEADING, 8);
            centered(document, doc.studentName(), STUDENT, 6);
            centered(document, "Enrollment No. " + doc.enrollmentNo(), BODY, 12);
            centered(document, "has been awarded the", HEADING, 6);
            centered(document, doc.title(), TITLE, 4);
            if (hasText(doc.program())) {
                centered(document, "in " + doc.program(), BODY, 4);
            }
            centered(document, achievementLine(doc), BODY, 4);
            centered(document, "Awarded on " + DATE.format(doc.awardedOn()), BODY, 18);

            document.add(footer(doc));
            document.close();
            return out.toByteArray();
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException("Could not create the certificate PDF", e);
        }
    }

    // ---------- layout ----------

    /** Bottom section: certificate and blockchain details on the left, QR code on the right. */
    private PdfPTable footer(CertificateDocument doc) throws IOException {
        PdfPTable table = new PdfPTable(new float[]{4.2f, 1.3f});
        table.setWidthPercentage(100);

        PdfPCell details = new PdfPCell();
        details.setBorder(Rectangle.NO_BORDER);
        details.addElement(detail("Certificate No.", doc.certificateNumber()));
        details.addElement(detail("Type", doc.type().name()));
        details.addElement(detail("Issued by wallet", doc.issuerAddress()));
        details.addElement(detail("Recorded on blockchain",
                networkName(doc.chainId()) + ", " + TIMESTAMP.format(doc.anchoredAt())));
        details.addElement(code("Certificate hash: " + doc.certHash()));
        details.addElement(code("Transaction: " + doc.txHash()));
        if (doc.explorerTxUrl() != null) {
            details.addElement(code(doc.explorerTxUrl()));
        }
        table.addCell(details);

        Image qr = Image.getInstance(QrCodeGenerator.png(doc.verificationUrl(), QR_PIXELS));
        qr.scaleAbsolute(QR_POINTS, QR_POINTS);
        PdfPCell qrCell = new PdfPCell();
        qrCell.setBorder(Rectangle.NO_BORDER);
        qrCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        qrCell.addElement(qr);
        Paragraph scan = new Paragraph("Scan to verify", LABEL);
        scan.setAlignment(Element.ALIGN_CENTER);
        qrCell.addElement(scan);
        table.addCell(qrCell);
        return table;
    }

    private static void drawBorder(PdfContentByte canvas, Rectangle page) {
        canvas.saveState();
        canvas.setColorStroke(NAVY);
        canvas.setLineWidth(3f);
        canvas.rectangle(20, 20, page.getWidth() - 40, page.getHeight() - 40);
        canvas.stroke();
        canvas.setColorStroke(GOLD);
        canvas.setLineWidth(1f);
        canvas.rectangle(28, 28, page.getWidth() - 56, page.getHeight() - 56);
        canvas.stroke();
        canvas.restoreState();
    }

    private static void centered(Document document, String text, Font font, float spacingAfter) {
        Paragraph paragraph = new Paragraph(text, font);
        paragraph.setAlignment(Element.ALIGN_CENTER);
        paragraph.setSpacingAfter(spacingAfter);
        document.add(paragraph);
    }

    private static Paragraph detail(String label, String value) {
        Paragraph paragraph = new Paragraph();
        paragraph.add(new Chunk(label + ":  ", LABEL));
        paragraph.add(new Chunk(value, VALUE));
        paragraph.setSpacingAfter(2);
        return paragraph;
    }

    private static Paragraph code(String text) {
        return new Paragraph(new Phrase(text, SMALL));
    }

    private static String networkName(long chainId) {
        if (chainId == 1L) {
            return "Ethereum Mainnet";
        }
        return chainId == 11155111L ? "Ethereum Sepolia" : "Ethereum chain " + chainId;
    }

    private static String achievementLine(CertificateDocument doc) {
        StringBuilder line = new StringBuilder();
        if (hasText(doc.grade())) {
            line.append(doc.grade());
        }
        if (doc.cgpa() != null) {
            line.append(line.isEmpty() ? "" : "  |  ").append("CGPA ").append(doc.cgpa()).append(" / 10");
        }
        return line.toString();
    }

    // ---------- embedded verification data ----------

    private static void addMetadata(Document document, PdfWriter writer, CertificateDocument doc) {
        document.addTitle(doc.title() + " - " + doc.studentName());
        document.addSubject("Certificate " + doc.certificateNumber() + " issued by " + doc.institutionName());
        document.addAuthor(doc.institutionName());
        document.addCreator("CredChain");

        PdfDictionary info = writer.getInfo();
        put(info, "Version", META_FORMAT_VERSION);
        put(info, "CertHash", doc.certHash());
        put(info, "Payload", doc.canonicalPayload());
        put(info, "Proof", doc.merkleProof());
        put(info, "MerkleRoot", doc.merkleRoot());
        put(info, "ChainId", Long.toString(doc.chainId()));
        put(info, "Contract", doc.contractAddress());
    }

    private static void put(PdfDictionary info, String key, String value) {
        info.put(new PdfName(META_PREFIX + key), new PdfString(value, PdfString.TEXT_UNICODE));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}