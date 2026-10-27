"""Settings, read once from environment variables (all optional)."""
import os
from dataclasses import dataclass


def _bool(name: str, default: bool) -> bool:
    value = os.getenv(name)
    return default if value is None else value.strip().lower() in ("1", "true", "yes", "on")


@dataclass(frozen=True)
class Settings:
    # Shared secret the backend sends in the X-Api-Key header. Empty = no key needed (local development only).
    api_key: str = os.getenv("FRAUD_API_KEY", "")
    # Largest upload accepted, in megabytes.
    max_upload_mb: int = int(os.getenv("FRAUD_MAX_UPLOAD_MB", "5"))
    # Only the first pages of a PDF are analysed: certificates are one page, and rendering is the slow part.
    max_pdf_pages: int = int(os.getenv("FRAUD_MAX_PDF_PAGES", "2"))
    # Turn OCR off when the Tesseract program is not installed (the other checks still run).
    ocr_enabled: bool = _bool("FRAUD_OCR_ENABLED", True)


settings = Settings()
