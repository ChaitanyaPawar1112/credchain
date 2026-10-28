# CredChain fraud-detection service

A small Python service (FastAPI) that looks for signs of forgery in certificate documents that are **not**
CredChain-issued PDFs: phone photos, scans, and PDFs made in other programs. The CredChain backend calls it;
browsers never do.

The public endpoint is `POST /api/v1/public/verify/document` on the backend. The backend sends the file here, looks up
the certificate the QR code points to, and compares the printed text with that official record.

On the website this is the **Photo or scan** tab of the Verify page; the trust scores below are the super admin's
**College trust scores** page.

It never says a document is genuine. Only the blockchain check (QR code or "Upload PDF") proves that. This service
gives a **risk score** with reasons, so a verifier knows what to look at.

## What it checks

| Check | How | Risk points |
|-------|-----|-------------|
| QR code | Finds and reads every QR code (OpenCV), pulls out the CredChain certificate hash | No QR: 20. QR to another site: 30. Two different certificate QRs: 30 |
| Editing software | The program that last saved the file (PDF Producer/Creator, photo EXIF "Software") | Photoshop, Canva, iLovePDF and similar: 30. Word, LibreOffice: 20 |
| PDF edited after saving | Counts the saved versions inside the PDF (each editor save adds one) | 35 |
| PDF modified later | Modification date well after the creation date | 15 |
| Printed text (OCR) | Tesseract, a neural-network text reader, reads the page. The backend compares it with the official record (step 7.2) | Almost no readable text: 10 |
| ELA heat map | Error Level Analysis for JPEG photos: shows areas with a different save history. For a person to look at | 0 (evidence only) |

Score 0–24 is **LOW**, 25–54 **MEDIUM**, 55–100 **HIGH**.

## College trust score (machine learning)

`POST /v1/analyze/institutions` gets one row per college per day from the backend (certificates issued and revoked,
public checks, forged copies seen, certificates issued between midnight and 6 a.m.). It returns a 0–100 trust score
per college with reasons:

- An **Isolation Forest** (scikit-learn, unsupervised: it needs no examples of fraud) learns what ordinary days look
  like from every college's history and flags days unlike the rest. A flagged day from the last 7 days is reported
  when one number is at least 4 times that college's own normal, for example
  "2400 certificates issued between midnight and 6 a.m. on 26 Oct 2026, while it normally has none". Each one costs
  25 points (at most 50).
- Rules: more than 5% of its certificates revoked costs up to 30 points; forged, edited or high-risk copies of its
  certificates being checked cost up to 25.
- With fewer than 30 days of rows on the whole platform, only the rules run (`modelUsed: false`).

80–100 is **TRUSTED**, 50–79 **WATCH**, 0–49 **AT_RISK**. The backend runs this every night at 02:30 and on demand
(`POST /api/v1/admin/institution-trust/recompute`).

## API

- `POST /v1/analyze/institutions` (JSON: `today`, `institutions[]`, `days[]`) returns a trust score per college
- `GET /health` returns `{"status":"UP","version":"1.1.0","ocr":true}`
- `POST /v1/analyze/document` (multipart field `file`: PDF, JPEG, PNG or WebP, up to 5 MB) returns
  `riskScore`, `riskLevel`, `summary`, `qr.certHash`, `text.text`, `signals[]` and `elaImage` (base64 PNG)
- Interactive docs: http://localhost:8090/docs

When `FRAUD_API_KEY` is set, every `/v1/...` call needs the header `X-Api-Key: <that value>`.

## Settings (environment variables)

| Variable | Default | Meaning |
|----------|---------|---------|
| `FRAUD_API_KEY` | empty | Shared key with the backend. Empty means no key (local testing only) |
| `FRAUD_MAX_UPLOAD_MB` | 5 | Largest upload |
| `FRAUD_MAX_PDF_PAGES` | 2 | PDF pages analysed |
| `FRAUD_OCR_ENABLED` | true | Turn OCR off |

## Run it (Docker, nothing else to install)

From the `infra` folder:

```
docker compose up -d --build fraud
```

Then open http://localhost:8090/health.

## Run the tests (Docker)

From the repository root:

```
docker build --target test --progress=plain fraud-service
```

The build prints the pytest results and fails if any test fails. The test certificates are drawn in code
(`tests/samples.py`), so there are no binary test files in the repository.

## Run without Docker (optional)

Needs Python 3.12 and, for OCR, Tesseract on the PATH.

```
python -m venv .venv
.venv\Scripts\activate          (Windows)   or   source .venv/bin/activate
pip install -r requirements-dev.txt
python -m pytest
uvicorn app.main:app --port 8090
```
