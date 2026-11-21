"""Extract *MCA*.txt from a timetable zip and stamp the operating day."""

from __future__ import annotations

import re
import zipfile
from datetime import datetime, timedelta
from pathlib import Path
from zoneinfo import ZoneInfo

MCA_NAME_RE = re.compile(r"MCA.*\.txt$|MCA$", re.IGNORECASE)


def mca_day_from_last_modified(last_modified: datetime | str) -> str:
    """Europe/London calendar date of LastModified, plus one day."""
    if isinstance(last_modified, str):
        raw = last_modified.strip().replace("Z", "+00:00")
        dt = datetime.fromisoformat(raw)
    else:
        dt = last_modified
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=ZoneInfo("UTC"))
    london = dt.astimezone(ZoneInfo("Europe/London"))
    op = london.date() + timedelta(days=1)
    return op.strftime("%Y%m%d")


def find_mca_member(zip_path: Path) -> str:
    with zipfile.ZipFile(zip_path) as zf:
        members = zf.namelist()
    for name in members:
        base = Path(name).name
        if MCA_NAME_RE.search(base):
            return name
    raise FileNotFoundError(f"no *MCA*.txt member in {zip_path}")


def extract_and_stamp_mca(
    zip_path: Path,
    dest_dir: Path,
    mca_day: str,
) -> Path:
    """Extract the MCA member and write dest_dir/<stem>-{mca_day}.txt."""
    dest_dir.mkdir(parents=True, exist_ok=True)
    member = find_mca_member(zip_path)
    base = Path(member).name
    ext = base.rsplit(".", 1)[-1] if "." in base else "txt"
    stem = base.rsplit(".", 1)[0] if "." in base else base
    stem = re.sub(r"-\d{8}$", "", stem)
    dest_name = f"{stem}-{mca_day}.{ext}"
    dest = dest_dir / dest_name
    with zipfile.ZipFile(zip_path) as zf:
        with zf.open(member) as src, dest.open("wb") as out:
            while True:
                chunk = src.read(1024 * 1024)
                if not chunk:
                    break
                out.write(chunk)
    return dest
