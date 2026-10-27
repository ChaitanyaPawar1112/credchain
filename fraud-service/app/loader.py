"""Works out what the uploaded file is and turns it into page images for the visual checks."""
import io
from dataclasses import dataclass, field

import pypdfium2 as pdfium
from PIL import Image, ImageOps

# Refuse "decompression bombs": a small file that expands into a huge image. 40 MP is far above any scan.
Image.MAX_IMAGE_PIXELS = 40_000_000

PDF = "application/pdf"
JPEG = "image/jpeg"
PNG = "image/png"
WEBP = "image/webp"

# PDF pages are drawn at 150 dpi: sharp enough for QR codes and OCR, still fast.
PDF_RENDER_SCALE = 150 / 72


class UnsupportedFile(Exception):
    """The file is not a PDF, JPEG, PNG or WebP, or it cannot be opened."""


@dataclass
class LoadedDocument:
    file_type: str
    data: bytes
    pages: list[Image.Image] = field(default_factory=list)   # RGB images, one per analysed page
    original_image: Image.Image | None = None                 # the upload as opened (with its metadata), for photos / scans


def sniff_type(data: bytes) -> str | None:
    """Decides the type from the first bytes, never from the file name or the browser's claim."""
    if data[:1024].find(b"%PDF-") != -1:
        return PDF
    if data.startswith(b"\xff\xd8\xff"):
        return JPEG
    if data.startswith(b"\x89PNG\r\n\x1a\n"):
        return PNG
    if data[:4] == b"RIFF" and data[8:12] == b"WEBP":
        return WEBP
    return None


def load(data: bytes, max_pdf_pages: int) -> LoadedDocument:
    file_type = sniff_type(data)
    if file_type is None:
        raise UnsupportedFile("Upload a PDF, JPEG, PNG or WebP file.")
    if file_type == PDF:
        return LoadedDocument(file_type, data, pages=_render_pdf(data, max_pdf_pages))
    image = _open_image(data)
    # Phone photos are often stored sideways with a "rotate me" flag; the metadata stays on the original
    upright = ImageOps.exif_transpose(image) or image
    return LoadedDocument(file_type, data, pages=[upright.convert("RGB")], original_image=image)


def _open_image(data: bytes) -> Image.Image:
    try:
        image = Image.open(io.BytesIO(data))
        image.load()
    except (OSError, Image.DecompressionBombError, SyntaxError) as e:
        raise UnsupportedFile("The image could not be opened. It may be damaged or too large.") from e
    return image


def _render_pdf(data: bytes, max_pages: int) -> list[Image.Image]:
    try:
        pdf = pdfium.PdfDocument(data)
    except pdfium.PdfiumError as e:
        raise UnsupportedFile("The PDF could not be read. It may be damaged or password protected.") from e
    try:
        pages = []
        for index in range(min(len(pdf), max_pages)):
            page = pdf[index]
            bitmap = page.render(scale=PDF_RENDER_SCALE)
            pages.append(bitmap.to_pil().convert("RGB"))
            page.close()
        if not pages:
            raise UnsupportedFile("The PDF has no pages.")
        return pages
    finally:
        pdf.close()
