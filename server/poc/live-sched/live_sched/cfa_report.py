"""Plain labeled CLI report lines for a CFA apply outcome."""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class ApplyCounts:
    total: int = 0
    new: int = 0
    override: int = 0
    duplicate: int = 0
    deletes: int = 0


@dataclass(frozen=True)
class ZipOutcome:
    zip_path: str
    status: str  # accepted | refused | failed | nothing
    sequence: str | None = None
    expected: str | None = None
    reason: str | None = None
    counts: ApplyCounts | None = None


def format_outcome(outcome: ZipOutcome) -> list[str]:
    """Return labeled stdout lines for one zip (or empty-folder) outcome."""
    lines = [f"zip: {outcome.zip_path}", f"status: {outcome.status}"]
    if outcome.sequence is not None:
        lines.append(f"sequence: {outcome.sequence}")
    if outcome.expected is not None:
        lines.append(f"expected: {outcome.expected}")
    if outcome.reason:
        lines.append(f"reason: {outcome.reason}")
    if outcome.counts is not None and outcome.status == "accepted":
        c = outcome.counts
        lines.extend(
            [
                f"total: {c.total}",
                f"new: {c.new}",
                f"override: {c.override}",
                f"duplicate: {c.duplicate}",
                f"deletes: {c.deletes}",
            ]
        )
    return lines
