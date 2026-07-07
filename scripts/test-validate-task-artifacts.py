#!/usr/bin/env python3
"""Compatibility entry point for the task artifact validator tests."""

from __future__ import annotations

import runpy
from pathlib import Path


if __name__ == "__main__":
    runpy.run_path(str(Path(__file__).with_name("test_validate_task_artifacts.py")), run_name="__main__")
