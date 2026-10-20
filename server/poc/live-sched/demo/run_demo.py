#!/usr/bin/env python3
"""Thin demo wrapper around the live-sched CLI (load fixture, optional consume)."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from live_sched.cli import main  # noqa: E402


if __name__ == "__main__":
    raise SystemExit(main())
