"""
Reads the printed text with Tesseract OCR (a neural-network text recogniser). The backend compares this text with
the official record of the certificate the QR code points to: a changed name or grade shows up there.
"""
import logging
import shutil

import pytesseract
from PIL import Image

from app.models import Severity, Signal, TextResult

log = logging.getLogger(__name__)
MAX_TEXT = 4000


def available() -> bool:
    return shutil.which(pytesseract.pytesseract.tesseract_cmd) is not None


def analyse(pages: list[Image.Image], enabled: bool) -> tuple[TextResult, list[Signal]]:
    if not enabled or not available():
        return TextResult(available=False), []
    parts = []
    for page in pages:
        try:
            parts.append(pytesseract.image_to_string(page.convert("L"), lang="eng", timeout=30))
        except (pytesseract.TesseractError, RuntimeError) as e:   # RuntimeError = timeout
            log.warning("OCR failed: %s", e)
            return TextResult(available=False), []
    text = "\n".join(" ".join(line.split()) for line in "\n".join(parts).splitlines() if line.strip())
    words = len(text.split())
    result = TextResult(available=True, text=text[:MAX_TEXT], word_count=words)
    if words < 5:
        return result, [Signal(code="NO_READABLE_TEXT", severity=Severity.LOW, weight=10,
                               detail="Almost no printed text could be read. The image may be blurred, too small or not a certificate.")]
    return result, []
