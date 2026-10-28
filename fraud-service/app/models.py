"""Response shapes (camelCase JSON, to match the Java backend)."""
from enum import Enum
from typing import Optional

from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel


class CamelModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class Severity(str, Enum):
    INFO = "INFO"      # worth knowing, adds nothing to the risk
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"


class RiskLevel(str, Enum):
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"


class Signal(CamelModel):
    """One finding. weight is how many risk points it adds (0-100 scale)."""
    code: str
    severity: Severity
    weight: int
    detail: str


class QrResult(CamelModel):
    found: bool
    texts: list[str] = []
    # The CredChain certificate hash read from the QR code (0x + 64 hex), when there is one.
    cert_hash: Optional[str] = None


class TextResult(CamelModel):
    available: bool          # False when OCR is turned off or Tesseract is missing
    text: str = ""           # what OCR read, trimmed to a few thousand characters
    word_count: int = 0


class DocumentAnalysis(CamelModel):
    risk_score: int          # 0 (no warning signs) to 100
    risk_level: RiskLevel
    summary: str
    file_type: str           # application/pdf, image/jpeg, image/png or image/webp
    pages_analysed: int
    qr: QrResult
    text: TextResult
    signals: list[Signal]
    # Error Level Analysis heat map as a small PNG (base64), for JPEG photos and scans only.
    ela_image: Optional[str] = None
    service_version: str
