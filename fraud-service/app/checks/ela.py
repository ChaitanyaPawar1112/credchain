"""
Error Level Analysis (ELA) heat map for JPEG photos and scans.

Every time a JPEG is saved it loses a little detail. Saving it once more at a fixed quality changes an untouched
picture fairly evenly; a piece pasted in from elsewhere often has a different save history and lights up
differently from the text around it. The heat map shows how much each pixel changed when re-saved.

ELA is evidence for a person to look at, not a verdict: lighting, scanners and editing apps all change it, and on
clean digital text it is easily fooled. So it adds no risk points on its own. The reliable fraud signals are the
QR / official-record comparison and the file's own history (see metadata.py).
"""
import base64
import io

import cv2
import numpy as np
from PIL import Image

RESAVE_QUALITY = 90
HEATMAP_WIDTH = 480


def heatmap(image: Image.Image) -> str | None:
    """PNG (base64) where bright areas changed the most when the image was saved again."""
    rgb = image.convert("RGB")
    original = np.asarray(rgb, dtype=np.int16)
    buffer = io.BytesIO()
    rgb.save(buffer, "JPEG", quality=RESAVE_QUALITY)
    resaved = np.asarray(Image.open(buffer).convert("RGB"), dtype=np.int16)
    error = np.abs(original - resaved).max(axis=2).astype(np.float32)   # worst colour channel per pixel

    # Stretch so the strongest 0.5% of pixels are fully bright, whatever the overall level
    scale = 255.0 / max(float(np.percentile(error, 99.5)), 1.0)
    picture = cv2.applyColorMap(np.clip(error * scale, 0, 255).astype(np.uint8), cv2.COLORMAP_INFERNO)
    height = max(1, round(picture.shape[0] * HEATMAP_WIDTH / picture.shape[1]))
    small = cv2.resize(picture, (HEATMAP_WIDTH, height), interpolation=cv2.INTER_AREA)
    ok, png = cv2.imencode(".png", small)
    return base64.b64encode(png.tobytes()).decode("ascii") if ok else None
