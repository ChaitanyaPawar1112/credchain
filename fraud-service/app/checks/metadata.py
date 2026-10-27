"""
The file's own history: which program made or last saved it, and whether a PDF was edited after it was first saved.
These are facts written inside the file, so they are reliable signals (a forger can strip them, but rarely does).
"""
import io
import re
from datetime import datetime, timedelta

from PIL import Image
from pypdf import PdfReader
from pypdf.errors import PdfReadError

from app.models import Severity, Signal

# Programs that edit documents or images. A certificate last saved by one of them was changed by hand.
EDITORS = {
    "photoshop": "Adobe Photoshop",
    "gimp": "GIMP",
    "paint.net": "Paint.NET",
    "pixlr": "Pixlr",
    "canva": "Canva",
    "illustrator": "Adobe Illustrator",
    "coreldraw": "CorelDRAW",
    "inkscape": "Inkscape",
    "acrobat pro": "Adobe Acrobat (editor)",
    "pdfescape": "PDFescape",
    "sejda": "Sejda PDF editor",
    "ilovepdf": "iLovePDF",
    "smallpdf": "Smallpdf",
    "pdf-xchange": "PDF-XChange Editor",
    "foxit phantompdf": "Foxit PhantomPDF",
    "foxit pdf editor": "Foxit PDF Editor",
    "nitro": "Nitro PDF",
    "pdffiller": "pdfFiller",
    "snapseed": "Snapseed",
    "picsart": "PicsArt",
}

# Word processors: real certificates come from the college's system, not a typed-up document.
WORD_PROCESSORS = {
    "microsoft word": "Microsoft Word",
    "libreoffice": "LibreOffice",
    "openoffice": "OpenOffice",
    "google docs": "Google Docs",
    "wps": "WPS Office",
}

PDF_DATE = re.compile(r"D:(\d{4})(\d{2})?(\d{2})?(\d{2})?(\d{2})?(\d{2})?")


def analyse_pdf(data: bytes) -> list[Signal]:
    signals: list[Signal] = []
    try:
        reader = PdfReader(io.BytesIO(data), strict=False)
        info = reader.metadata or {}
    except (PdfReadError, ValueError, OSError):
        return [Signal(code="PDF_STRUCTURE_DAMAGED", severity=Severity.MEDIUM, weight=15,
                       detail="The PDF's internal structure is damaged, which can happen when a file is edited badly.")]

    if any(str(key).startswith("/CredChain-") for key in info.keys()):
        signals.append(Signal(code="CREDCHAIN_PDF", severity=Severity.INFO, weight=0,
                              detail="This PDF carries CredChain verification data. Check it with \"Upload PDF\" for an exact result."))

    tools = " / ".join(str(info.get(key, "")) for key in ("/Producer", "/Creator") if info.get(key))
    signals += _tool_signals(tools)

    revisions = _saved_revisions(data)
    if revisions > 1:
        signals.append(Signal(code="PDF_EDITED_AFTER_SAVE", severity=Severity.HIGH, weight=35,
                              detail=f"The PDF was changed after it was first saved: it contains {revisions} saved versions."))

    created, modified = _pdf_date(info.get("/CreationDate")), _pdf_date(info.get("/ModDate"))
    if created and modified and modified - created > timedelta(minutes=5):
        signals.append(Signal(code="PDF_MODIFIED_LATER", severity=Severity.MEDIUM, weight=15,
                              detail=f"The PDF was modified on {modified:%d %b %Y}, after it was created on {created:%d %b %Y}."))
    return signals


def analyse_image(image: Image.Image) -> list[Signal]:
    exif = image.getexif()
    software = str(exif.get(0x0131, "") or "")   # 0x0131 = "Software" tag
    signals = _tool_signals(software)
    if image.format == "PNG":
        for key in ("Software", "software"):
            if key in image.info:
                signals += _tool_signals(str(image.info[key]))
    return signals


def _tool_signals(text: str) -> list[Signal]:
    lowered = text.lower()
    for needle, name in EDITORS.items():
        if needle in lowered:
            return [Signal(code="EDITING_SOFTWARE", severity=Severity.HIGH, weight=30,
                           detail=f"The file was last saved by {name}, a program for editing documents or images.")]
    for needle, name in WORD_PROCESSORS.items():
        if needle in lowered:
            return [Signal(code="WORD_PROCESSOR", severity=Severity.MEDIUM, weight=20,
                           detail=f"The file was made in {name}. Real certificates come from the college's issuing system.")]
    return []


def _saved_revisions(data: bytes) -> int:
    """Each save that appends to a PDF ("incremental update") adds another %%EOF marker."""
    count = data.count(b"%%EOF")
    # A "linearized" (fast web view) PDF legitimately has two markers from its first save
    if count > 1 and b"/Linearized" in data[:2048]:
        count -= 1
    return max(count, 1)


def _pdf_date(value) -> datetime | None:
    match = PDF_DATE.match(str(value or ""))
    if not match:
        return None
    parts = [int(part) if part else default for part, default in zip(match.groups(), (0, 1, 1, 0, 0, 0))]
    try:
        return datetime(*parts)
    except ValueError:
        return None
