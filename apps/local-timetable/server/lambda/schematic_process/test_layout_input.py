"""Smoke tests for Python layout reduction (parity with networkSchematic.ts)."""

from __future__ import annotations

from .layout_input import build_layout_input, schematic_lines_from_stop_lists


def _edge_set(payload: dict) -> set[tuple[str, str]]:
    return {(e["from"], e["to"]) for e in payload["edges"]}


def test_worcester_dual_pattern_keeps_connectivity() -> None:
    # Same pattern as networkSchematic.test.ts WOS/WOF/DTW case
    stop_lists = [
        ["HFD", "WOF", "WOS"],
        ["WOF", "WOS"],
        ["WOS", "DTW", "BHM"],
        ["WOF", "DTW", "BHM"],
        ["WOS", "WOF", "DTW", "BHM"],
        ["WOF", "WOS", "DTW", "BHM"],
    ]
    payload = build_layout_input(stop_lists, geo_path=None)
    edges = _edge_set(payload)
    assert ("WOS", "DTW") in edges or ("DTW", "WOS") in edges or ("WOF", "DTW") in edges or (
        "DTW",
        "WOF",
    ) in edges
    assert ("HFD", "WOF") in edges or ("HFD", "WOS") in edges or ("WOF", "HFD") in edges or (
        "WOS",
        "HFD",
    ) in edges


def test_skip_pairs_drop_express_chord() -> None:
    stop_lists = [
        ["A", "B", "C"],
        ["A", "C"],
    ]
    payload = build_layout_input(stop_lists, geo_path=None)
    edges = _edge_set(payload)
    assert ("A", "B") in edges
    assert ("B", "C") in edges
    assert ("A", "C") not in edges


def test_junction_lines_not_full_journeys() -> None:
    stop_lists = [
        ["EUS", "MKC", "RUG", "CRE"],
        ["MKC", "RUG"],
        ["RUG", "NMP"],
    ]
    lines = schematic_lines_from_stop_lists(stop_lists)
    # Junction walks should include the NMP branch as its own short line
    ids = {line["id"] for line in lines}
    assert any("NMP" in line_id for line_id in ids)


if __name__ == "__main__":
    test_worcester_dual_pattern_keeps_connectivity()
    test_skip_pairs_drop_express_chord()
    test_junction_lines_not_full_journeys()
    print("ok")
