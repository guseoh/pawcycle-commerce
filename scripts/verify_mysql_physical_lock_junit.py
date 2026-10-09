#!/usr/bin/env python3
"""Fail CI when mandatory MySQL physical lock tests are absent, skipped, or failed."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
T09 = ("com.pawcycle.backend.subscription.SubscriptionOrderAutomationServiceIntegrationTests",
       "privilegedLocalMysqlLockFootprintsMatchFrozenSqlForEveryCommandLock", 1)
T10 = [
    ("com.pawcycle.backend.subscription.persistence.BillingLockWorkDiagnosisIntegrationTests",
     "fixedReadViewHistoryAndBothInsertBlockingPaths", 3),
    ("com.pawcycle.backend.subscription.persistence.BillingRecoveryPersistenceParityIntegrationTests",
     "physicalRecordIndexGapFootprintsAndCompetingWritesMatch", 1),
]
T10_SOURCES = [
    ROOT / "backend/src/test/java/com/pawcycle/backend/subscription/persistence/BillingLockWorkDiagnosisIntegrationTests.java",
    ROOT / "backend/src/test/java/com/pawcycle/backend/subscription/persistence/BillingRecoveryPersistenceParityIntegrationTests.java",
]

def verify(reports: Path, sources: list[bool]) -> list[str]:
    if any(sources) and not all(sources):
        raise ValueError("Partial T10 lock test sources")
    files = list(reports.glob("TEST-*.xml"))
    if not files:
        raise ValueError("Missing JUnit test XML")
    cases = []
    for file in files:
        cases += list(ET.parse(file).iter("testcase"))
    checked = []
    for classname, name, count in [T09, *(T10 if all(sources) else [])]:
        matches = [c for c in cases if c.get("classname") == classname and
                   (c.get("name") == name or c.get("name", "").startswith(name + "("))]
        if len(matches) != count:
            raise ValueError(f"{classname}.{name}: expected {count} executions; got {len(matches)}")
        if any(any(c.find(tag) is not None for tag in ("skipped", "error", "failure")) for c in matches):
            raise ValueError(f"{classname}.{name}: skipped or failed")
        checked.append(f"{name}={count} PASS")
    return checked

def main() -> int:
    try:
        for value in verify(ROOT / "backend/build/test-results/test", [p.is_file() for p in T10_SOURCES]):
            print(f"LOCK_CI_GATE=PASS {value}")
    except (ValueError, ET.ParseError, OSError) as exc:
        print(f"LOCK_CI_GATE=FAIL {exc}", file=sys.stderr)
        return 1
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
