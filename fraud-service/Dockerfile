# CredChain fraud-detection service (Python + FastAPI + Tesseract OCR)
#   docker build --target test .   runs the tests (the build fails if one fails)
#   docker build .                 builds the service image (what docker compose uses)

FROM python:3.12-slim AS base
# Tesseract is the OCR program; curl is used by the health check
RUN apt-get update \
 && apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-eng curl \
 && rm -rf /var/lib/apt/lists/*
WORKDIR /service
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
ENV PYTHONUNBUFFERED=1

FROM base AS test
COPY requirements-dev.txt pytest.ini ./
RUN pip install --no-cache-dir -r requirements-dev.txt
COPY app ./app
COPY tests ./tests
RUN python -m pytest -v

FROM base AS runtime
COPY app ./app
# Run as a normal user, not root
RUN useradd --create-home --uid 10001 fraud
USER fraud
EXPOSE 8000
HEALTHCHECK --interval=15s --timeout=5s --start-period=20s --retries=3 \
  CMD curl -fsS http://127.0.0.1:8000/health || exit 1
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "2"]
