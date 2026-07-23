from __future__ import annotations

import runpy
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TEST = ROOT / "infra/production/test-db-backup-restore.sh"

text = TEST.read_text(encoding="utf-8")
old = (
    '    PAWCYCLE_BACKUP_LOCK_FILE="$LOCK_FILE" \\\n'
    '    "$SCRIPT" "$@"'
)
new = (
    '    PAWCYCLE_BACKUP_LOCK_FILE="$LOCK_FILE" \\\n'
    '    PAWCYCLE_BACKUP_PREFIX="$PREFIX" \\\n'
    '    "$SCRIPT" "$@"'
)
if old in text:
    TEST.write_text(text.replace(old, new, 1), encoding="utf-8")
elif new not in text:
    raise RuntimeError("test runner invocation contract was not found")

runpy.run_path(str(ROOT / "scripts/ops013_followup_patch_v2.py"), run_name="__main__")
