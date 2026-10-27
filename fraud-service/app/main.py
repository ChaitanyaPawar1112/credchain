"""
HTTP API of the fraud-detection service. Only the CredChain backend calls it (not browsers), with a shared key.

  GET  /health                 is the service up, and is OCR available
  POST /v1/analyze/document    forensic checks on a certificate photo, scan or PDF
"""
import hmac
import logging

from fastapi import Depends, FastAPI, File, Header, HTTPException, UploadFile, status

from app import loader, scoring
from app.checks import ela, metadata, ocr, qr
from app.config import settings
from app.models import DocumentAnalysis

VERSION = "1.0.0"
log = logging.getLogger("fraud-service")

app = FastAPI(title="CredChain fraud-detection service", version=VERSION,
              description="Forensic checks on certificate documents that are not CredChain-issued PDFs.")


def require_api_key(x_api_key: str | None = Header(default=None)) -> None:
    if settings.api_key and not hmac.compare_digest(x_api_key or "", settings.api_key):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Missing or wrong X-Api-Key")


@app.get("/health")
def health() -> dict:
    return {"status": "UP", "version": VERSION, "ocr": settings.ocr_enabled and ocr.available()}


@app.post("/v1/analyze/document", response_model=DocumentAnalysis, response_model_by_alias=True,
          dependencies=[Depends(require_api_key)])
def analyze_document(file: UploadFile = File(...)) -> DocumentAnalysis:
    # A plain "def" endpoint runs in a worker thread, so slow OCR does not block other requests
    limit = settings.max_upload_mb * 1024 * 1024
    data = file.file.read(limit + 1)
    if not data:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, "The file is empty.")
    if len(data) > limit:
        raise HTTPException(status.HTTP_413_CONTENT_TOO_LARGE, f"The file is larger than {settings.max_upload_mb} MB.")
    try:
        document = loader.load(data, settings.max_pdf_pages)
    except loader.UnsupportedFile as e:
        raise HTTPException(status.HTTP_415_UNSUPPORTED_MEDIA_TYPE, str(e)) from e
    return analyse(document)


def analyse(document: loader.LoadedDocument) -> DocumentAnalysis:
    signals = []
    if document.file_type == loader.PDF:
        signals += metadata.analyse_pdf(document.data)
    else:
        signals += metadata.analyse_image(document.original_image)

    qr_result, qr_signals = qr.analyse(document.pages)
    text_result, text_signals = ocr.analyse(document.pages, settings.ocr_enabled)
    signals += qr_signals + text_signals

    heatmap = ela.heatmap(document.pages[0]) if document.file_type == loader.JPEG else None
    risk, level, summary = scoring.score(signals)
    log.info("Analysed %s: risk %d (%s), signals %s", document.file_type, risk, level.value,
             [signal.code for signal in signals])
    return DocumentAnalysis(
        risk_score=risk, risk_level=level, summary=summary, file_type=document.file_type,
        pages_analysed=len(document.pages), qr=qr_result, text=text_result,
        signals=scoring.ordered(signals), ela_image=heatmap, service_version=VERSION)
