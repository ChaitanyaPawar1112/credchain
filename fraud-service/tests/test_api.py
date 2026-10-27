import dataclasses

import pytest
from fastapi.testclient import TestClient

import app.main as main
from app.checks import ocr
from tests import samples

client = TestClient(main.app)
needs_ocr = pytest.mark.skipif(not ocr.available(), reason="Tesseract OCR is not installed")


def analyse(data: bytes, name: str = "certificate", headers: dict | None = None):
    return client.post("/v1/analyze/document", files={"file": (name, data)}, headers=headers or {})


def codes(body: dict) -> list[str]:
    return [signal["code"] for signal in body["signals"]]


def test_health():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json()["status"] == "UP"


def test_clean_photo_with_credchain_qr_is_low_risk():
    response = analyse(samples.jpeg_bytes(samples.certificate_image()))
    assert response.status_code == 200
    body = response.json()
    assert body["fileType"] == "image/jpeg"
    assert body["qr"]["found"] is True
    assert body["qr"]["certHash"] == samples.CERT_HASH
    assert body["riskLevel"] == "LOW"
    assert body["riskScore"] == 0
    assert codes(body) == ["QR_FOUND"]
    assert body["elaImage"]                       # heat map for JPEG photos
    assert "does not prove" in body["summary"]    # LOW never claims "genuine"


def test_png_scan_reads_qr_and_has_no_heatmap():
    body = analyse(samples.png_bytes(samples.certificate_image())).json()
    assert body["fileType"] == "image/png"
    assert body["qr"]["certHash"] == samples.CERT_HASH
    assert body["elaImage"] is None


def test_small_low_quality_phone_photo_still_reads_qr():
    image = samples.certificate_image().resize((700, 990))
    body = analyse(samples.jpeg_bytes(image, quality=60)).json()
    assert body["qr"]["certHash"] == samples.CERT_HASH


def test_missing_qr_is_flagged():
    body = analyse(samples.png_bytes(samples.certificate_image(qr_text=None))).json()
    assert "NO_QR_CODE" in codes(body)
    assert body["qr"]["found"] is False
    assert body["riskScore"] >= 20


def test_qr_to_another_website_is_high_signal():
    body = analyse(samples.png_bytes(samples.certificate_image(qr_text="https://fake-university.example/verify?id=123"))).json()
    assert "QR_NOT_CREDCHAIN" in codes(body)
    assert body["qr"]["certHash"] is None


def test_photoshop_photo_is_flagged():
    body = analyse(samples.photo_with_software("Adobe Photoshop 26.1 (Windows)")).json()
    assert "EDITING_SOFTWARE" in codes(body)
    assert body["signals"][0]["code"] == "EDITING_SOFTWARE"   # most serious first
    assert body["riskLevel"] == "MEDIUM"


def test_camera_photo_is_not_flagged():
    body = analyse(samples.photo_with_software("Android camera 14")).json()
    assert "EDITING_SOFTWARE" not in codes(body)


def test_issuer_style_pdf_is_low_risk():
    body = analyse(samples.certificate_pdf()).json()
    assert body["fileType"] == "application/pdf"
    assert body["pagesAnalysed"] == 1
    assert body["qr"]["certHash"] == samples.CERT_HASH
    assert body["riskLevel"] == "LOW"
    assert body["elaImage"] is None


def test_pdf_saved_again_by_editor_is_high_risk():
    edited = samples.incrementally_edited(
        samples.certificate_pdf(creator="Microsoft Word", producer="iLovePDF",
                                modified="D:20261020183000"))
    body = analyse(edited).json()
    assert {"PDF_EDITED_AFTER_SAVE", "EDITING_SOFTWARE", "PDF_MODIFIED_LATER"} <= set(codes(body))
    assert body["riskLevel"] == "HIGH"
    assert body["riskScore"] <= 100


def test_word_document_pdf_is_medium_signal():
    body = analyse(samples.certificate_pdf(creator="Microsoft Word for Microsoft 365", with_qr=False)).json()
    assert {"WORD_PROCESSOR", "NO_QR_CODE"} <= set(codes(body))
    assert body["riskLevel"] == "MEDIUM"


def test_pdf_with_credchain_data_is_pointed_to_exact_check():
    from pypdf import PdfReader, PdfWriter
    import io
    writer = PdfWriter()
    writer.append(PdfReader(io.BytesIO(samples.certificate_pdf())))
    writer.add_metadata({"/CredChain-CertHash": samples.CERT_HASH})
    out = io.BytesIO()
    writer.write(out)
    body = analyse(out.getvalue()).json()
    assert "CREDCHAIN_PDF" in codes(body)


@needs_ocr
def test_ocr_reads_the_printed_name():
    body = analyse(samples.png_bytes(samples.certificate_image(name="Sneha Joshi"))).json()
    assert body["text"]["available"] is True
    assert "Sneha Joshi" in body["text"]["text"]
    assert "PIT2022CS002" in body["text"]["text"]


def test_ocr_can_be_switched_off(monkeypatch):
    monkeypatch.setattr(main, "settings", dataclasses.replace(main.settings, ocr_enabled=False))
    body = analyse(samples.png_bytes(samples.certificate_image())).json()
    assert body["text"] == {"available": False, "text": "", "wordCount": 0}


def test_unsupported_file_is_415():
    response = analyse(b"PK\x03\x04 this is a zip file", name="certificate.pdf")
    assert response.status_code == 415


def test_file_named_jpg_but_really_text_is_rejected():
    assert analyse(b"hello", name="photo.jpg").status_code == 415


def test_broken_image_is_415():
    assert analyse(b"\xff\xd8\xff\xe0 broken jpeg").status_code == 415


def test_empty_file_is_400():
    assert analyse(b"").status_code == 400


def test_too_large_is_413(monkeypatch):
    monkeypatch.setattr(main, "settings", dataclasses.replace(main.settings, max_upload_mb=1))
    big = samples.jpeg_bytes(samples.certificate_image()) + b"\0" * (1024 * 1024)
    assert analyse(big).status_code == 413


def test_api_key_is_required_when_configured(monkeypatch):
    monkeypatch.setattr(main, "settings", dataclasses.replace(main.settings, api_key="s3cret-key"))
    data = samples.png_bytes(samples.certificate_image())
    assert analyse(data).status_code == 401
    assert analyse(data, headers={"X-Api-Key": "wrong"}).status_code == 401
    assert analyse(data, headers={"X-Api-Key": "s3cret-key"}).status_code == 200
    assert client.get("/health").status_code == 200    # health stays open for Docker's check


def test_png_saved_by_editor_is_flagged():
    from PIL import PngImagePlugin
    import io
    meta = PngImagePlugin.PngInfo()
    meta.add_text("Software", "GIMP 2.10.38")
    buffer = io.BytesIO()
    samples.certificate_image().save(buffer, "PNG", pnginfo=meta)
    assert "EDITING_SOFTWARE" in codes(analyse(buffer.getvalue()).json())
