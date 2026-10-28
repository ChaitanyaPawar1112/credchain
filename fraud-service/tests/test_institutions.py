import random
from datetime import date, timedelta

from fastapi.testclient import TestClient

import app.main as main

client = TestClient(main.app)
TODAY = date(2026, 10, 27)


def history(colleges: int = 8, days: int = 60, seed: int = 7) -> tuple[list[dict], list[dict]]:
    """Ordinary colleges: weekday issuing in office hours, a few checks a day, almost no revocations."""
    rng = random.Random(seed)
    infos = [{"institutionId": f"c{i}", "name": f"College {i}"} for i in range(colleges)]
    rows = []
    for info in infos:
        size = rng.choice([5, 10, 20, 40])
        for back in range(days):
            day = TODAY - timedelta(days=back)
            issued = rng.randint(0, size) if day.weekday() < 5 and rng.random() < 0.4 else 0
            rows.append({"institutionId": info["institutionId"], "date": day.isoformat(), "issued": issued,
                         "revoked": 0, "checks": rng.randint(0, 6), "fakeChecks": 0, "highRiskChecks": 0,
                         "offHoursIssued": 0})
    return infos, rows


def analyse(infos, rows):
    response = client.post("/v1/analyze/institutions",
                           json={"today": TODAY.isoformat(), "institutions": infos, "days": rows})
    assert response.status_code == 200, response.text
    return {c["institutionId"]: c for c in response.json()["institutions"]}, response.json()


def test_ordinary_colleges_are_trusted():
    infos, rows = history()
    colleges, body = analyse(infos, rows)
    assert body["modelUsed"] is True
    assert body["model"] == "IsolationForest"
    assert all(c["level"] == "TRUSTED" for c in colleges.values()), [c for c in colleges.values() if c["level"] != "TRUSTED"]


def test_night_time_mass_issuing_is_flagged():
    infos, rows = history()
    rows.append({"institutionId": "c3", "date": (TODAY - timedelta(days=1)).isoformat(), "issued": 2400,
                 "revoked": 0, "checks": 2, "fakeChecks": 0, "highRiskChecks": 0, "offHoursIssued": 2400})
    colleges, _ = analyse(infos, rows)
    flagged = colleges["c3"]
    assert flagged["level"] in ("WATCH", "AT_RISK")
    assert flagged["anomalies"], flagged
    assert "2400" in flagged["anomalies"][0]["detail"]
    assert flagged["anomalies"][0]["date"] == (TODAY - timedelta(days=1)).isoformat()
    assert all(c["level"] == "TRUSTED" for key, c in colleges.items() if key != "c3")


def test_old_unusual_days_are_history_not_news():
    infos, rows = history()
    rows.append({"institutionId": "c2", "date": (TODAY - timedelta(days=40)).isoformat(), "issued": 2400,
                 "revoked": 0, "checks": 0, "fakeChecks": 0, "highRiskChecks": 0, "offHoursIssued": 2400})
    colleges, _ = analyse(infos, rows)
    assert colleges["c2"]["anomalies"] == []


def test_revocations_and_forged_copies_lower_the_score():
    infos, rows = history()
    rows.append({"institutionId": "c1", "date": TODAY.isoformat(), "issued": 0, "revoked": 30,
                 "checks": 20, "fakeChecks": 12, "highRiskChecks": 3, "offHoursIssued": 0})
    colleges, _ = analyse(infos, rows)
    college = colleges["c1"]
    assert college["trustScore"] < 80
    assert any("revoked" in r for r in college["reasons"])
    assert any("forged" in r for r in college["reasons"])


def test_little_history_uses_rules_only():
    infos = [{"institutionId": "new", "name": "New College"}]
    rows = [{"institutionId": "new", "date": TODAY.isoformat(), "issued": 3, "revoked": 0, "checks": 1,
             "fakeChecks": 0, "highRiskChecks": 0, "offHoursIssued": 0}]
    colleges, body = analyse(infos, rows)
    assert body["modelUsed"] is False
    assert colleges["new"]["level"] == "TRUSTED"


def test_college_with_no_activity():
    colleges, _ = analyse([{"institutionId": "idle", "name": "Idle College"}], [])
    assert colleges["idle"]["trustScore"] == 100
    assert colleges["idle"]["reasons"] == ["No activity yet."]


def test_results_are_sorted_lowest_trust_first():
    infos, rows = history()
    rows.append({"institutionId": "c5", "date": TODAY.isoformat(), "issued": 0, "revoked": 40,
                 "checks": 10, "fakeChecks": 10, "highRiskChecks": 0, "offHoursIssued": 0})
    _, body = analyse(infos, rows)
    assert body["institutions"][0]["institutionId"] == "c5"


def test_bad_request_is_422():
    response = client.post("/v1/analyze/institutions", json={"today": "not a date", "institutions": [], "days": []})
    assert response.status_code == 422
