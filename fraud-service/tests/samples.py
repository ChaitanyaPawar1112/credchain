"""Builds realistic test certificates in memory, so the tests need no binary files in the repository."""
import io

import qrcode
from PIL import Image, ImageDraw, ImageFont

CERT_HASH = "0x5f78ca9edc823fd886abc729c001bd2042c605ed9c99dcd0623a91dd8a6b104f"
VERIFY_URL = f"http://localhost:5173/verify/{CERT_HASH}"


def _font(size: int) -> ImageFont.ImageFont:
    for name in ("DejaVuSans.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", "arial.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default(size)


def certificate_image(name: str = "Sneha Joshi", qr_text: str | None = VERIFY_URL) -> Image.Image:
    """A plain A4-shaped certificate page at about 100 dpi."""
    page = Image.new("RGB", (1240, 1754), "white")
    draw = ImageDraw.Draw(page)
    draw.rectangle((40, 40, 1200, 1714), outline=(20, 40, 90), width=6)
    draw.text((620, 220), "Pune Institute of Technology", font=_font(56), fill=(20, 40, 90), anchor="mm")
    draw.text((620, 420), "This is to certify that", font=_font(36), fill="black", anchor="mm")
    draw.text((620, 540), name, font=_font(64), fill="black", anchor="mm")
    draw.text((620, 660), "has been awarded the degree of", font=_font(36), fill="black", anchor="mm")
    draw.text((620, 760), "B.Tech Computer Engineering", font=_font(48), fill="black", anchor="mm")
    draw.text((620, 880), "Enrollment No. PIT2022CS002   Year 2026", font=_font(32), fill="black", anchor="mm")
    if qr_text:
        qr = qrcode.make(qr_text, box_size=8, border=4).convert("RGB")
        page.paste(qr, (page.width - 120 - qr.width, 1250))
    return page


def jpeg_bytes(image: Image.Image, quality: int = 80) -> bytes:
    buffer = io.BytesIO()
    image.save(buffer, "JPEG", quality=quality)
    return buffer.getvalue()


def png_bytes(image: Image.Image) -> bytes:
    buffer = io.BytesIO()
    image.save(buffer, "PNG")
    return buffer.getvalue()


def edited_jpeg(quality: int = 80) -> bytes:
    """A scan that was saved, then had its name replaced with freshly typed text, then saved again."""
    scanned = Image.open(io.BytesIO(jpeg_bytes(certificate_image(), quality))).convert("RGB")
    patch = Image.new("RGB", (700, 110), "white")
    ImageDraw.Draw(patch).text((350, 55), "Rahul Sharma", font=_font(64), fill="black", anchor="mm")
    scanned.paste(patch, (270, 485))
    return jpeg_bytes(scanned, quality)


def certificate_pdf(creator: str = "CredChain", producer: str | None = None,
                    created: str = "D:20260609100000", modified: str | None = None,
                    with_qr: bool = True) -> bytes:
    """A one-page certificate PDF, drawn from the same picture as the image samples."""
    from pypdf import PdfReader, PdfWriter
    from reportlab.lib.pagesizes import A4
    from reportlab.lib.utils import ImageReader
    from reportlab.pdfgen import canvas

    buffer = io.BytesIO()
    pdf = canvas.Canvas(buffer, pagesize=A4)
    page = certificate_image(qr_text=VERIFY_URL if with_qr else None)
    pdf.drawImage(ImageReader(page), 0, 0, width=A4[0], height=A4[1])
    pdf.showPage()
    pdf.save()

    # Set the document info exactly, the way the issuing (or editing) program would
    reader = PdfReader(io.BytesIO(buffer.getvalue()))
    writer = PdfWriter()
    writer.append(reader)
    info = {"/Creator": creator, "/Producer": producer or creator, "/CreationDate": created}
    if modified:
        info["/ModDate"] = modified
    writer.add_metadata(info)
    out = io.BytesIO()
    writer.write(out)
    return out.getvalue()


def incrementally_edited(pdf: bytes) -> bytes:
    """Appends a second saved version to the PDF, the way PDF editors save changes."""
    from pypdf import PdfWriter

    writer = PdfWriter(io.BytesIO(pdf), incremental=True)
    writer.add_metadata({"/Title": "Degree certificate (edited)"})
    out = io.BytesIO()
    writer.write(out)
    return out.getvalue()


def photo_with_software(software: str) -> bytes:
    """A JPEG whose EXIF says which program saved it last."""
    image = certificate_image()
    exif = Image.Exif()
    exif[0x0131] = software
    buffer = io.BytesIO()
    image.save(buffer, "JPEG", quality=85, exif=exif)
    return buffer.getvalue()
