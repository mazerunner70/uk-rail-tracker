"""Turn MILP {stations, edges} into full network-schematic.json body."""

from __future__ import annotations

from typing import Any

OCT_CELL_PX = 48
PAD = 36
SPINE_COLOR = "#7dffa8"
BRANCH_COLORS = ("#f0b45a", "#e07a3d", "#3dbebf", "#6aa0ff")


def _color_for_lane(lane: int) -> str:
    if lane == 0:
        return SPINE_COLOR
    index = (lane - 1) * 2 if lane > 0 else (-lane) * 2 - 1
    return BRANCH_COLORS[index % len(BRANCH_COLORS)]


def _stroke_color(from_lane: int, to_lane: int) -> str:
    if from_lane == 0 and to_lane == 0:
        return SPINE_COLOR
    return _color_for_lane(from_lane if from_lane != 0 else to_lane)


def diagram_from_oct_coords(
    stations: list[dict[str, Any]],
    edges: list[dict[str, str]],
    lines: list[dict[str, Any]],
    *,
    load_day: str,
    hd_sequence: str,
    scale_px: float = OCT_CELL_PX,
) -> dict[str, Any]:
    scaled = [
        {
            "crs": s["crs"],
            "x": float(s["x"]) * scale_px,
            "y": float(s["y"]) * scale_px,
            "rank": int(s.get("rank", 0)),
            "lane": int(s.get("lane", 0)),
        }
        for s in stations
    ]
    by_crs = {s["crs"]: s for s in scaled}
    corridors: list[dict[str, Any]] = []
    for edge in edges:
        frm = by_crs.get(edge["from"])
        to = by_crs.get(edge["to"])
        if not frm or not to:
            continue
        color = _stroke_color(frm["lane"], to["lane"])
        points = [
            {"x": frm["x"], "y": frm["y"]},
            {"x": to["x"], "y": to["y"]},
        ]
        if frm["crs"] < to["crs"]:
            corridors.append(
                {
                    "from": frm["crs"],
                    "to": to["crs"],
                    "via": [],
                    "points": points,
                    "color": color,
                }
            )
        else:
            corridors.append(
                {
                    "from": to["crs"],
                    "to": frm["crs"],
                    "via": [],
                    "points": list(reversed(points)),
                    "color": color,
                }
            )

    xs = [s["x"] for s in scaled] + [p["x"] for c in corridors for p in c["points"]]
    ys = [s["y"] for s in scaled] + [p["y"] for c in corridors for p in c["points"]]
    if not xs or not ys:
        return {
            "stations": [],
            "corridors": [],
            "lines": [],
            "width": PAD * 2,
            "height": PAD * 2,
            "loadDay": load_day,
            "hdSequence": hd_sequence,
        }
    min_x, max_x = min(xs), max(xs)
    min_y, max_y = min(ys), max(ys)
    shift_x = PAD - min_x
    shift_y = PAD - min_y
    return {
        "stations": sorted(
            (
                {**s, "x": s["x"] + shift_x, "y": s["y"] + shift_y}
                for s in scaled
            ),
            key=lambda s: s["crs"],
        ),
        "corridors": sorted(
            (
                {
                    **c,
                    "points": [
                        {"x": p["x"] + shift_x, "y": p["y"] + shift_y} for p in c["points"]
                    ],
                }
                for c in corridors
            ),
            key=lambda c: f'{c["from"]}|{c["to"]}',
        ),
        "lines": lines,
        "width": max_x - min_x + PAD * 2 + 28,
        "height": max_y - min_y + PAD * 2 + 8,
        "loadDay": load_day,
        "hdSequence": hd_sequence,
    }
