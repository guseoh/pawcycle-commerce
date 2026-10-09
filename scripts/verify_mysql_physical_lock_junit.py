#!/usr/bin/env python3
"""Fail CI when mandatory MySQL physical lock tests are absent, skipped, or failed."""
from pathlib import Path
import re
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


def require_t10_abc_source(source: str) -> None:
    """The JUnit invocation ordinals must still map to A, B, C in this order."""
    source_pattern = (
        r'@ValueSource\s*\(\s*strings\s*=\s*\{\s*"A"\s*,\s*"B"\s*,\s*"C"\s*\}\s*\)'
        r'\s*void\s+fixedReadViewHistoryAndBothInsertBlockingPaths\s*\('
    )
    if re.search(source_pattern, source) is None:
        raise ValueError("T10 fixed physical lock @ValueSource must be exactly A, B, C")


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
        if all(sources) and (classname, name, count) == T10[0]:
            # Gradle JUnit XML names: method(String)[1], [2], [3] (optional display-name suffix).
            # Counting three rows alone would accept [1],[1],[1] and mask absent B/C.
            invocations = []
            pattern = re.compile(re.escape(name) + r"\([^)]*\)\s*\[(\d+)\](?:\s+.*)?")
            for case in matches:
                match = pattern.fullmatch(case.get("name", ""))
                if match is None:
                    raise ValueError(f"{classname}.{name}: unrecognized JUnit invocation identity")
                invocations.append(int(match.group(1)))
            if sorted(invocations) != [1, 2, 3]:
                raise ValueError(f"{classname}.{name}: T10 A/B/C invocations missing or duplicated: {invocations}")
        checked.append(f"{name}={count} PASS")
    return checked

def main() -> int:
    try:
        sources = [p.is_file() for p in T10_SOURCES]
        if all(sources):
            require_t10_abc_source(T10_SOURCES[0].read_text(encoding="utf-8"))
        for value in verify(ROOT / "backend/build/test-results/test", sources):
            print(f"LOCK_CI_GATE=PASS {value}")
    except (ValueError, ET.ParseError, OSError) as exc:
        print(f"LOCK_CI_GATE=FAIL {exc}", file=sys.stderr)
        return 1
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
