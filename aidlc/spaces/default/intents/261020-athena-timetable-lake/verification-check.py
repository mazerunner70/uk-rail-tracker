"""Document check for each Athena timetable-lake Bolt."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[5]
DOC = ROOT / "docs" / "athena-timetable-lake.md"

REQUIRED_HEADINGS = [
    "## Environments",
    "## CFA receipt → validate → apply",
    "## Cheap lookups",
    "## Kafka archive/overlay",
    "## TOC extension",
    "## Later package layering",
    "## Deferred / not in this document",
]


def main() -> None:
    if not DOC.is_file():
        raise SystemExit(f"missing file: {DOC}")
    text = DOC.read_text(encoding="utf-8")
    missing = [heading for heading in REQUIRED_HEADINGS if heading not in text]
    if missing:
        raise SystemExit("missing headings: " + ", ".join(missing))
    print("ok")


if __name__ == "__main__":
    main()
