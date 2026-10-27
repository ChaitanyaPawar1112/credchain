"""
Institution trust: spots unusual activity by a college with an Isolation Forest, and gives each college a
0-100 trust score with plain-language reasons.

The backend sends one row per college per day (certificates issued and revoked, checks of its certificates, forged
copies seen). An Isolation Forest is an unsupervised machine-learning model: it learns what ordinary days look like
from all colleges' history, with no labelled fraud examples, and isolates days that are unlike the rest (for example
a college that normally issues 50 certificates suddenly issuing 2,000 at 3 a.m.). Only recent days are reported.
"""
from datetime import date, timedelta

import numpy as np
from sklearn.ensemble import IsolationForest

from app.models import CamelModel

FEATURES = ("issued", "revoked", "checks", "fake_checks", "high_risk_checks", "off_hours_issued")
LABELS = {
    "issued": "certificates issued",
    "revoked": "certificates revoked",
    "checks": "checks of its certificates",
    "fake_checks": "forged or edited copies of its certificates",
    "high_risk_checks": "high-risk photos or scans of its certificates",
    "off_hours_issued": "certificates issued between midnight and 6 a.m.",
}

MIN_ROWS_FOR_MODEL = 30      # below this the model has too little history to learn "normal"
RECENT_DAYS = 7              # anomalies older than this are history, not news
MAX_ANOMALY_PENALTY = 50
ANOMALY_PENALTY = 25         # per unusual recent day
MIN_RATIO = 4.0              # reported only when at least 4x the college's own normal
# Small numbers are never news on their own: one revocation is normal, one forged copy is worth a look
MIN_REPORTED_VALUE = {"issued": 20, "revoked": 5, "checks": 50, "fake_checks": 1, "high_risk_checks": 1,
                      "off_hours_issued": 20}


class InstitutionDay(CamelModel):
    institution_id: str
    date: date
    issued: int = 0
    revoked: int = 0
    checks: int = 0
    fake_checks: int = 0
    high_risk_checks: int = 0
    off_hours_issued: int = 0


class InstitutionInfo(CamelModel):
    institution_id: str
    name: str


class InstitutionRequest(CamelModel):
    today: date
    institutions: list[InstitutionInfo]
    days: list[InstitutionDay]


class Anomaly(CamelModel):
    date: date
    score: float          # how unusual, 0 (normal) to 1 (extremely unusual)
    detail: str


class InstitutionTrust(CamelModel):
    institution_id: str
    name: str
    trust_score: int      # 0-100, higher is more trustworthy
    level: str            # TRUSTED, WATCH or AT_RISK
    reasons: list[str]
    anomalies: list[Anomaly]


class InstitutionResponse(CamelModel):
    model: str
    model_used: bool      # False when there was too little history for the model (rules still apply)
    rows_analysed: int
    institutions: list[InstitutionTrust]


def analyse(request: InstitutionRequest) -> InstitutionResponse:
    days = [d for d in request.days if d.date <= request.today]
    anomalies = _anomalies(days, request.today) if len(days) >= MIN_ROWS_FOR_MODEL else {}

    results = []
    for info in request.institutions:
        own = [d for d in days if d.institution_id == info.institution_id]
        results.append(_trust(info, own, anomalies.get(info.institution_id, [])))
    results.sort(key=lambda r: (r.trust_score, r.name))
    return InstitutionResponse(model="IsolationForest", model_used=len(days) >= MIN_ROWS_FOR_MODEL,
                               rows_analysed=len(days), institutions=results)


def _anomalies(days: list[InstitutionDay], today: date) -> dict[str, list[Anomaly]]:
    values = np.array([[getattr(d, f) for f in FEATURES] for d in days], dtype=float)
    model = IsolationForest(n_estimators=200, contamination="auto", random_state=42)
    model.fit(np.log1p(values))
    flags = model.predict(np.log1p(values))           # -1 = unusual
    raw = -model.score_samples(np.log1p(values))      # higher = more unusual (about 0.35-0.8)

    found: dict[str, list[Anomaly]] = {}
    recent_from = today - timedelta(days=RECENT_DAYS - 1)
    for index, day in enumerate(days):
        if flags[index] != -1 or day.date < recent_from:
            continue
        explanation = _explain(index, day, days, values)
        if explanation is None:                       # unusual only in a harmless way (e.g. a quiet day)
            continue
        score = round(float(min(1.0, max(0.0, (raw[index] - 0.5) * 2.5))), 2)
        found.setdefault(day.institution_id, []).append(Anomaly(date=day.date, score=score, detail=explanation))
    return found


def _explain(index: int, day: InstitutionDay, days: list[InstitutionDay], values: np.ndarray) -> str | None:
    """
    Names the feature that is furthest above this college's own normal, or None when nothing is far above it.
    "Normal" = the college's typical value on its active days; colleges with little history are compared with
    all colleges instead.
    """
    own = np.array([i != index and d.institution_id == day.institution_id for i, d in enumerate(days)])
    best, best_ratio, best_baseline = None, 0.0, 0.0
    for column, feature in enumerate(FEATURES):
        value = values[index, column]
        if value < MIN_REPORTED_VALUE[feature]:
            continue
        baseline = _baseline(values[own, column]) if own.any() else 0.0
        if baseline == 0.0 and np.count_nonzero(values[own, column]) < 5:
            baseline = _baseline(np.delete(values[:, column], index))
        ratio = value / max(baseline, 1.0)
        if ratio > best_ratio:
            best, best_ratio, best_baseline = feature, ratio, baseline
    if best is None or best_ratio < MIN_RATIO:
        return None
    usual = "it normally has none" if best_baseline < 0.5 else f"a typical active day has about {best_baseline:.0f}"
    return f"{getattr(day, best)} {LABELS[best]} on {day.date:%d %b %Y}, while {usual}."


def _baseline(column: np.ndarray) -> float:
    """Typical value on active days (days with at least one event)."""
    active = column[column > 0]
    return float(np.median(active)) if active.size else 0.0


def _trust(info: InstitutionInfo, days: list[InstitutionDay], anomalies: list[Anomaly]) -> InstitutionTrust:
    issued = sum(d.issued for d in days)
    revoked = sum(d.revoked for d in days)
    checks = sum(d.checks for d in days)
    fakes = sum(d.fake_checks for d in days)
    high_risk = sum(d.high_risk_checks for d in days)

    score = 100
    reasons: list[str] = []
    if issued >= 5 and revoked / issued > 0.05:
        penalty = min(30, round((revoked / issued) * 150))
        score -= penalty
        reasons.append(f"{revoked} of {issued} certificates issued were revoked ({revoked / issued:.0%}).")
    if fakes + high_risk > 0:
        share = (fakes + high_risk) / max(checks, 1)
        penalty = min(25, 5 + round(share * 60))
        score -= penalty
        reasons.append(f"{fakes + high_risk} forged, edited or high-risk copies of its certificates were checked.")
    if anomalies:
        score -= min(MAX_ANOMALY_PENALTY, ANOMALY_PENALTY * len(anomalies))
        reasons += [a.detail for a in anomalies]
    if not reasons:
        reasons.append("No unusual activity found." if days else "No activity yet.")

    score = max(0, score)
    level = "TRUSTED" if score >= 80 else "WATCH" if score >= 50 else "AT_RISK"
    return InstitutionTrust(institution_id=info.institution_id, name=info.name, trust_score=score, level=level,
                            reasons=reasons, anomalies=sorted(anomalies, key=lambda a: a.date, reverse=True))
