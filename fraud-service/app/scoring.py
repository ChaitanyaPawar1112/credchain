"""Turns the signals into one 0-100 risk score, a level and a plain-language summary."""
from app.models import RiskLevel, Severity, Signal

MEDIUM_FROM = 25
HIGH_FROM = 55


def score(signals: list[Signal]) -> tuple[int, RiskLevel, str]:
    total = min(100, sum(signal.weight for signal in signals))
    if total >= HIGH_FROM:
        level = RiskLevel.HIGH
        summary = "Strong signs that this document was forged or edited. Do not accept it without checking the official record."
    elif total >= MEDIUM_FROM:
        level = RiskLevel.MEDIUM
        summary = "Some warning signs. Check the details against the official record before accepting it."
    else:
        level = RiskLevel.LOW
        summary = ("No warning signs found. This does not prove the document is genuine: "
                   "only the blockchain check of its QR code or PDF does that.")
    return total, level, summary


def ordered(signals: list[Signal]) -> list[Signal]:
    """Most serious first."""
    rank = {Severity.HIGH: 0, Severity.MEDIUM: 1, Severity.LOW: 2, Severity.INFO: 3}
    return sorted(signals, key=lambda signal: (rank[signal.severity], -signal.weight))
