"""Finds QR codes on the page and pulls out the CredChain certificate hash (0x + 64 hex characters)."""
import re

import cv2
import numpy as np
from PIL import Image

from app.models import QrResult, Severity, Signal

CERT_HASH = re.compile(r"0x[0-9a-fA-F]{64}")


def analyse(pages: list[Image.Image]) -> tuple[QrResult, list[Signal]]:
    texts: list[str] = []
    for page in pages:
        for text in _decode(page):
            if text not in texts:
                texts.append(text)

    hashes = []
    for text in texts:
        for match in CERT_HASH.findall(text):
            if match.lower() not in hashes:
                hashes.append(match.lower())

    result = QrResult(found=bool(texts), texts=texts, cert_hash=hashes[0] if len(hashes) == 1 else None)
    if not texts:
        return result, [Signal(code="NO_QR_CODE", severity=Severity.MEDIUM, weight=20,
                               detail="No QR code was found. Every CredChain certificate carries a verification QR code.")]
    if not hashes:
        return result, [Signal(code="QR_NOT_CREDCHAIN", severity=Severity.HIGH, weight=30,
                               detail="The QR code does not point to a CredChain certificate.")]
    if len(hashes) > 1:
        return result, [Signal(code="QR_CONFLICT", severity=Severity.HIGH, weight=30,
                               detail=f"The document carries {len(hashes)} different certificate QR codes.")]
    return result, [Signal(code="QR_FOUND", severity=Severity.INFO, weight=0,
                           detail="A CredChain verification QR code was found. Its certificate is checked against the official record.")]


def _decode(page: Image.Image) -> list[str]:
    """Tries the page as it is, then larger (small QR codes on phone photos), then sharpened black and white."""
    gray = np.asarray(page.convert("L"))
    detector = cv2.QRCodeDetector()
    attempts = [gray]
    if max(gray.shape) < 2000:
        attempts.append(cv2.resize(gray, None, fx=2, fy=2, interpolation=cv2.INTER_CUBIC))
    attempts.append(cv2.adaptiveThreshold(gray, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C, cv2.THRESH_BINARY, 31, 10))
    for image in attempts:
        try:
            ok, decoded, _, _ = detector.detectAndDecodeMulti(image)
        except cv2.error:
            ok, decoded = False, ()
        found = [text for text in decoded if text] if ok else []
        if not found:
            try:
                single = detector.detectAndDecode(image)[0]
            except cv2.error:
                single = ""
            found = [single] if single else []
        if found:
            return found
    return []
