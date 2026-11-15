#!/usr/bin/env python3
"""Octilinear metro layout via PuLP + HiGHS (Nöllenburg-style MILP).

Reads layout-input JSON from stdin (or --input), writes one result JSON to stdout:
  { "stations": [{crs,x,y,rank,lane}, ...], "edges": [{from,to}, ...] }

Logs progress to stderr. Requires: pip install pulp highspy
"""

from __future__ import annotations

import argparse
import json
import math
import sys
from typing import Any

# Screen / geo seed uses y increasing downward (south). Directions 0..7:
#   0 E, 1 SE, 2 S, 3 SW, 4 W, 5 NW, 6 N, 7 NE
DIR_COUNT = 8
# Unit steps for constructive warm-start (y-down screen).
DIR_DELTA = (
    (1, 0),
    (1, 1),
    (0, 1),
    (-1, 1),
    (-1, 0),
    (-1, -1),
    (0, -1),
    (1, -1),
)
W_DIR = 10.0
W_LEN = 1.0
# Soft upper on edge length; must beat W_ORDER so geo order cannot stretch spurs forever.
W_LEN_CAP = 20.0
W_LEN_CAP_SPUR = 40.0
W_ORDER = 5.0
W_BEND = 2.0
W_SEP = 2.0
W_UNIQUE = 25.0
# Cap per-edge length target / soft upper at this multiple of the median raw target.
LEN_CAP_MULT = 3.0
ORDER_PAIR_CAP = 200
SEP_PAIR_CAP = 400
# Bend soft-constraints are expensive (8×8 binaries per joint); keep a small set.
LINE_BEND_CAP = 12
BIG_M_FACTOR = 4


def _load_input(path: str | None) -> dict[str, Any]:
    if path:
        with open(path, encoding="utf-8") as handle:
            return json.load(handle)
    return json.load(sys.stdin)


def _edge_key(a: str, b: str) -> tuple[str, str]:
    return (a, b) if a < b else (b, a)


def _jitter_geo(geo: dict[str, tuple[float, float]]) -> dict[str, tuple[float, float]]:
    """Separate coincident CRS so bearings are defined (stable micro-offsets)."""
    out: dict[str, tuple[float, float]] = {}
    for index, crs in enumerate(sorted(geo)):
        x, y = geo[crs]
        out[crs] = (x + (index % 7) * 1e-3, y + (index % 11) * 1e-3)
    return out


def _geo_sector(ux: float, uy: float, vx: float, vy: float) -> int:
    dx = vx - ux
    dy = vy - uy
    if abs(dx) < 1e-12 and abs(dy) < 1e-12:
        return 0
    ang = math.atan2(dy, dx)
    return int(round(ang / (math.pi / 4))) % DIR_COUNT


def _dir_cost(sector: int, direction: int) -> float:
    delta = min((direction - sector) % DIR_COUNT, (sector - direction) % DIR_COUNT)
    if delta == 0:
        return 0.0
    if delta == 1:
        return 1.0
    if delta == 2:
        return 4.0
    return 8.0


def _add_direction_links(
    model: Any,
    d_var: Any,
    xu: Any,
    yu: Any,
    xv: Any,
    yv: Any,
    direction: int,
    big_m: float,
) -> None:
    """Big-M: if d=1 then octilinear conditions for u→v hold (y-down screen)."""
    inactive = 1 - d_var
    if direction == 0:  # E
        model += yu - yv <= big_m * inactive
        model += yv - yu <= big_m * inactive
        model += xv - xu >= 1 - big_m * inactive
    elif direction == 1:  # SE
        model += (xu - yu) - (xv - yv) <= big_m * inactive
        model += (xv - yv) - (xu - yu) <= big_m * inactive
        model += (xv + yv) - (xu + yu) >= 2 - big_m * inactive
    elif direction == 2:  # S
        model += xu - xv <= big_m * inactive
        model += xv - xu <= big_m * inactive
        model += yv - yu >= 1 - big_m * inactive
    elif direction == 3:  # SW
        model += (xu + yu) - (xv + yv) <= big_m * inactive
        model += (xv + yv) - (xu + yu) <= big_m * inactive
        model += yv - yu >= 1 - big_m * inactive
    elif direction == 4:  # W
        model += yu - yv <= big_m * inactive
        model += yv - yu <= big_m * inactive
        model += xu - xv >= 1 - big_m * inactive
    elif direction == 5:  # NW
        model += (xu - yu) - (xv - yv) <= big_m * inactive
        model += (xv - yv) - (xu - yu) <= big_m * inactive
        model += (xu + yu) - (xv + yv) >= 2 - big_m * inactive
    elif direction == 6:  # N
        model += xu - xv <= big_m * inactive
        model += xv - xu <= big_m * inactive
        model += yu - yv >= 1 - big_m * inactive
    elif direction == 7:  # NE
        model += (xu + yu) - (xv + yv) <= big_m * inactive
        model += (xv + yv) - (xu + yu) <= big_m * inactive
        model += xv - xu >= 1 - big_m * inactive
    else:
        raise ValueError(f"bad direction {direction}")


def _degrees(edges_raw: list[Any]) -> dict[str, int]:
    deg: dict[str, int] = {}
    seen: set[tuple[str, str]] = set()
    for edge in edges_raw:
        a, b = str(edge["from"]), str(edge["to"])
        key = _edge_key(a, b)
        if key in seen:
            continue
        seen.add(key)
        deg[a] = deg.get(a, 0) + 1
        deg[b] = deg.get(b, 0) + 1
    return deg


def _dir_from(d: dict[tuple[str, str, int], Any], src: str, dst: str, direction: int) -> Any | None:
    """Binary for src→dst facing `direction`, keys stored as undirected (u,v) with u<v."""
    u, v = _edge_key(src, dst)
    if src == u:
        return d.get((u, v, direction))
    return d.get((u, v, (direction + 4) % DIR_COUNT))


def _dir_from_delta(dx: int, dy: int) -> int:
    """Map an octilinear delta to direction index 0..7."""
    if dx == 0 and dy == 0:
        return 0
    sx = 0 if dx == 0 else (1 if dx > 0 else -1)
    sy = 0 if dy == 0 else (1 if dy > 0 else -1)
    # Non-octilinear leftovers (shouldn't happen for seed tree edges): snap by bearing.
    if sx != 0 and sy != 0 and abs(dx) != abs(dy):
        return _geo_sector(0, 0, float(dx), float(dy))
    for r, (tx, ty) in enumerate(DIR_DELTA):
        if tx == sx and ty == sy:
            return r
    return _geo_sector(0, 0, float(dx), float(dy))


def _store_edge_dir(
    edge_dir: dict[tuple[str, str], int],
    src: str,
    dst: str,
    direction_from_src: int,
) -> None:
    u, v = _edge_key(src, dst)
    if src == u:
        edge_dir[(u, v)] = direction_from_src
    else:
        edge_dir[(u, v)] = (direction_from_src + 4) % DIR_COUNT


def _seed_octilinear(
    stations: list[str],
    undirected: list[tuple[str, str]],
    geo: dict[str, tuple[float, float]],
) -> tuple[dict[str, tuple[int, int]], dict[tuple[str, str], int]]:
    """Feasible octilinear placement via BFS along the graph (warm start / fallback)."""
    adj: dict[str, list[str]] = {crs: [] for crs in stations}
    for u, v in undirected:
        adj[u].append(v)
        adj[v].append(u)

    pos: dict[str, tuple[int, int]] = {}
    edge_dir: dict[tuple[str, str], int] = {}
    occupied: set[tuple[int, int]] = set()

    def place_free(near: tuple[int, int], prefer_sector: int) -> tuple[int, int]:
        cx, cy = near
        for step in range(0, 80):
            deltas = (0, 1, -1, 2, -2, 3, -3, 4) if step else (0,)
            for delta in deltas:
                r = (prefer_sector + delta) % DIR_COUNT
                sx, sy = DIR_DELTA[r]
                nx, ny = (cx, cy) if step == 0 else (cx + sx * step, cy + sy * step)
                if (nx, ny) not in occupied:
                    return nx, ny
        raise RuntimeError("metro_layout: seed grid exhausted")

    for root in stations:
        if root in pos:
            continue
        origin = (0, 0) if not pos else next(iter(occupied))
        rx, ry = place_free(origin, 0)
        pos[root] = (rx, ry)
        occupied.add((rx, ry))
        queue = [root]
        while queue:
            crs = queue.pop(0)
            cx, cy = pos[crs]
            for other in sorted(adj[crs]):
                if other in pos:
                    ox, oy = pos[other]
                    _store_edge_dir(edge_dir, crs, other, _dir_from_delta(ox - cx, oy - cy))
                    continue
                sector = _geo_sector(*geo[crs], *geo[other])
                placed = False
                for step in range(1, 12):
                    for delta in (0, 1, -1, 2, -2, 3, -3, 4):
                        r = (sector + delta) % DIR_COUNT
                        sx, sy = DIR_DELTA[r]
                        nx, ny = cx + sx * step, cy + sy * step
                        if (nx, ny) in occupied:
                            continue
                        pos[other] = (nx, ny)
                        occupied.add((nx, ny))
                        _store_edge_dir(edge_dir, crs, other, r)
                        queue.append(other)
                        placed = True
                        break
                    if placed:
                        break
                if not placed:
                    nx, ny = place_free((cx, cy), sector)
                    pos[other] = (nx, ny)
                    occupied.add((nx, ny))
                    _store_edge_dir(
                        edge_dir, crs, other, _dir_from_delta(nx - cx, ny - cy)
                    )
                    queue.append(other)
    for u, v in undirected:
        if (u, v) not in edge_dir:
            edge_dir[(u, v)] = _geo_sector(*geo[u], *geo[v])
    return pos, edge_dir


def _result_from_seed(
    pos: dict[str, tuple[int, int]],
    undirected: list[tuple[str, str]],
    meta: dict[str, dict[str, Any]],
) -> dict[str, Any]:
    stations = [
        {
            "crs": crs,
            "x": float(pos[crs][0]),
            "y": float(pos[crs][1]),
            "rank": meta[crs]["rank"],
            "lane": meta[crs]["lane"],
        }
        for crs in sorted(pos)
    ]
    edges = [{"from": a, "to": b} for a, b in undirected]
    edges.sort(key=lambda row: (row["from"], row["to"]))
    return {"stations": stations, "edges": edges}


def run_layout(payload: dict[str, Any], time_limit_sec: float) -> dict[str, Any]:
    import pulp

    nodes_raw = payload.get("nodes") or []
    edges_raw = payload.get("edges") or []
    lines_raw = payload.get("lines") or []

    if not nodes_raw:
        return {"stations": [], "edges": []}

    meta: dict[str, dict[str, Any]] = {}
    geo_raw: dict[str, tuple[float, float]] = {}
    for node in nodes_raw:
        crs = str(node["crs"])
        meta[crs] = {
            "rank": int(node.get("rank") or 0),
            "lane": int(node.get("lane") or 0),
        }
        geo_raw[crs] = (float(node["x"]), float(node["y"]))

    geo = _jitter_geo(geo_raw)
    stations = sorted(geo.keys())
    n = len(stations)
    for crs, degree in _degrees(edges_raw).items():
        if degree > 8:
            raise ValueError(
                f"Station {crs} has degree {degree}; "
                "octilinear layout requires degree ≤ 8."
            )

    undirected: list[tuple[str, str]] = []
    seen_e: set[tuple[str, str]] = set()
    for edge in edges_raw:
        a, b = str(edge["from"]), str(edge["to"])
        if a == b or a not in geo or b not in geo:
            continue
        key = _edge_key(a, b)
        if key in seen_e:
            continue
        seen_e.add(key)
        undirected.append(key)

    if not undirected:
        return {
            "stations": [
                {
                    "crs": crs,
                    "x": float(i),
                    "y": 0.0,
                    "rank": meta[crs]["rank"],
                    "lane": meta[crs]["lane"],
                }
                for i, crs in enumerate(stations)
            ],
            "edges": [],
        }

    # Free integer coords (may be negative); TS fit() shifts into the SVG box.
    coord_span = max(2 * n, 8)
    coord_lo = -coord_span
    coord_hi = coord_span
    big_m = float(BIG_M_FACTOR * (coord_hi - coord_lo))

    print(
        f"metro_layout: building MILP ({n} stations, {len(undirected)} edges, "
        f"time_limit={time_limit_sec}s)",
        file=sys.stderr,
        flush=True,
    )

    seed_pos, seed_dir = _seed_octilinear(stations, undirected, geo)
    print("metro_layout: constructive seed ready", file=sys.stderr, flush=True)

    model = pulp.LpProblem("octilinear_metro", pulp.LpMinimize)
    x = {
        crs: pulp.LpVariable(
            f"x_{crs}", lowBound=coord_lo, upBound=coord_hi, cat="Integer"
        )
        for crs in stations
    }
    y = {
        crs: pulp.LpVariable(
            f"y_{crs}", lowBound=coord_lo, upBound=coord_hi, cat="Integer"
        )
        for crs in stations
    }

    # d[(u,v,r)]: undirected edge {u,v} (u<v) drawn so v lies in direction r from u.
    # All 8 directions are allowed; geography is soft (hubs often share a sector).
    d: dict[tuple[str, str, int], Any] = {}
    neighbors: dict[str, list[str]] = {crs: [] for crs in stations}

    for u, v in undirected:
        neighbors[u].append(v)
        neighbors[v].append(u)
        for r in range(DIR_COUNT):
            d[(u, v, r)] = pulp.LpVariable(f"d_{u}_{v}_{r}", cat="Binary")
        model += pulp.lpSum(d[(u, v, r)] for r in range(DIR_COUNT)) == 1
        for r in range(DIR_COUNT):
            _add_direction_links(model, d[(u, v, r)], x[u], y[u], x[v], y[v], r, big_m)

    obj_terms: list[Any] = []

    # Soft: prefer distinct compass exits at a station (hard uniqueness made hubs infeasible).
    for crs in stations:
        for r in range(DIR_COUNT):
            outs = [
                var
                for other in neighbors[crs]
                if (var := _dir_from(d, crs, other, r)) is not None
            ]
            if len(outs) <= 1:
                continue
            excess = pulp.LpVariable(f"uniq_{crs}_{r}", lowBound=0, cat="Continuous")
            model += excess >= pulp.lpSum(outs) - 1
            obj_terms.append(W_UNIQUE * excess)

    for u, v in undirected:
        sector = _geo_sector(*geo[u], *geo[v])
        for r in range(DIR_COUNT):
            cost = _dir_cost(sector, r)
            if cost:
                obj_terms.append(W_DIR * cost * d[(u, v, r)])

    geo_lens = [
        max(abs(geo[u][0] - geo[v][0]) + abs(geo[u][1] - geo[v][1]), 1e-3)
        for u, v in undirected
    ]
    med = sorted(geo_lens)[len(geo_lens) // 2]
    scale = 1.0 / med
    raw_targets = [max(1.0, glen * scale * (n**0.5)) for glen in geo_lens]
    med_hop = sorted(raw_targets)[len(raw_targets) // 2]
    cap_limit = LEN_CAP_MULT * med_hop
    print(
        f"metro_layout: length cap={cap_limit:.1f} cells "
        f"(median hop target {med_hop:.1f} × {LEN_CAP_MULT:g})",
        file=sys.stderr,
        flush=True,
    )
    for (u, v), raw in zip(undirected, raw_targets):
        # Prefer geo-scaled length, but never ask for more than 3× median hop.
        target = min(raw, cap_limit)
        excess = pulp.LpVariable(f"len_{u}_{v}", lowBound=0, cat="Continuous")
        over = pulp.LpVariable(f"lencap_{u}_{v}", lowBound=0, cat="Continuous")
        dx_pos = pulp.LpVariable(f"dxp_{u}_{v}", lowBound=0, cat="Continuous")
        dx_neg = pulp.LpVariable(f"dxn_{u}_{v}", lowBound=0, cat="Continuous")
        dy_pos = pulp.LpVariable(f"dyp_{u}_{v}", lowBound=0, cat="Continuous")
        dy_neg = pulp.LpVariable(f"dyn_{u}_{v}", lowBound=0, cat="Continuous")
        model += x[u] - x[v] == dx_pos - dx_neg
        model += y[u] - y[v] == dy_pos - dy_neg
        length = dx_pos + dx_neg + dy_pos + dy_neg
        model += excess >= length - target
        # Soft upper: sep/uniqueness can still stretch past the cap when needed.
        model += over >= length - cap_limit
        obj_terms.append(W_LEN * excess)
        spur = min(len(neighbors[u]), len(neighbors[v])) <= 2
        obj_terms.append((W_LEN_CAP_SPUR if spur else W_LEN_CAP) * over)

    xs = [geo[c][0] for c in stations]
    ys = [geo[c][1] for c in stations]
    span_x = max(xs) - min(xs) or 1.0
    span_y = max(ys) - min(ys) or 1.0
    thr_x = 0.15 * span_x
    thr_y = 0.15 * span_y
    order_pairs: list[tuple[str, str, str]] = []
    for i, a in enumerate(stations):
        for b in stations[i + 1 :]:
            ax, ay = geo[a]
            bx, by = geo[b]
            if ax - bx > thr_x:
                order_pairs.append((a, b, "x"))
            elif bx - ax > thr_x:
                order_pairs.append((b, a, "x"))
            if ay - by > thr_y:
                order_pairs.append((a, b, "y"))
            elif by - ay > thr_y:
                order_pairs.append((b, a, "y"))
            if len(order_pairs) >= ORDER_PAIR_CAP:
                break
        if len(order_pairs) >= ORDER_PAIR_CAP:
            break
    for left, right, axis in order_pairs:
        viol = pulp.LpVariable(f"ord_{axis}_{left}_{right}", lowBound=0, cat="Continuous")
        if axis == "x":
            model += viol >= x[right] - x[left]
        else:
            model += viol >= y[right] - y[left]
        obj_terms.append(W_ORDER * viol)

    by_line: dict[str, list[tuple[str, str]]] = {}
    for hop in lines_raw:
        name = str(hop["linename"])
        a, b = str(hop["from"]), str(hop["to"])
        if _edge_key(a, b) not in seen_e:
            continue
        by_line.setdefault(name, []).append((a, b))
    ranked_lines = sorted(by_line.items(), key=lambda item: -len(item[1]))[:LINE_BEND_CAP]
    for name, hops in ranked_lines:
        for i in range(len(hops) - 1):
            a, b = hops[i]
            c, nxt = hops[i + 1]
            if b != c:
                continue
            bend = pulp.LpVariable(f"bend_{name}_{i}", lowBound=0, cat="Continuous")
            for r in range(DIR_COUNT):
                dir_ab = _dir_from(d, a, b, r)
                if dir_ab is None:
                    continue
                for s in range(DIR_COUNT):
                    dir_bn = _dir_from(d, b, nxt, s)
                    if dir_bn is None:
                        continue
                    delta = min((r - s) % DIR_COUNT, (s - r) % DIR_COUNT)
                    if delta == 0:
                        continue
                    model += bend >= delta * (dir_ab + dir_bn - 1)
            obj_terms.append(W_BEND * bend)

    sep_added = 0
    geo_close = 0.08 * max(span_x, span_y)
    for i, a in enumerate(stations):
        for b in stations[i + 1 :]:
            if _edge_key(a, b) in seen_e:
                continue
            dist = math.hypot(geo[a][0] - geo[b][0], geo[a][1] - geo[b][1])
            if dist > geo_close:
                continue
            gap = pulp.LpVariable(f"sep_{a}_{b}", lowBound=0, cat="Continuous")
            dxp = pulp.LpVariable(f"sepdxp_{a}_{b}", lowBound=0, cat="Continuous")
            dxn = pulp.LpVariable(f"sepdxn_{a}_{b}", lowBound=0, cat="Continuous")
            dyp = pulp.LpVariable(f"sepdyp_{a}_{b}", lowBound=0, cat="Continuous")
            dyn = pulp.LpVariable(f"sepdyn_{a}_{b}", lowBound=0, cat="Continuous")
            model += x[a] - x[b] == dxp - dxn
            model += y[a] - y[b] == dyp - dyn
            model += gap >= 1 - (dxp + dxn)
            model += gap >= 1 - (dyp + dyn)
            obj_terms.append(W_SEP * gap)
            sep_added += 1
            if sep_added >= SEP_PAIR_CAP:
                break
        if sep_added >= SEP_PAIR_CAP:
            break

    model += pulp.lpSum(obj_terms) if obj_terms else 0
    anchor = stations[0]
    model += x[anchor] == 0
    model += y[anchor] == 0

    # Warm-start from constructive octilinear seed so HiGHS has a feasible incumbent.
    for crs in stations:
        sx, sy = seed_pos[crs]
        x[crs].setInitialValue(sx)
        y[crs].setInitialValue(sy)
    for u, v in undirected:
        chosen = seed_dir.get((u, v), 0)
        for r in range(DIR_COUNT):
            d[(u, v, r)].setInitialValue(1.0 if r == chosen else 0.0)

    print("metro_layout: solving with HiGHS…", file=sys.stderr, flush=True)
    solver = pulp.HiGHS(
        msg=False,
        timeLimit=float(time_limit_sec),
        mip=True,
        warmStart=True,
    )
    status = model.solve(solver)
    status_name = pulp.LpStatus.get(status, str(status))
    print(f"metro_layout: status={status_name}", file=sys.stderr, flush=True)

    def mip_ok() -> bool:
        if pulp.LpStatus[status] in ("Infeasible", "Unbounded", "Undefined"):
            return False
        points: set[tuple[float, float]] = set()
        for crs in stations:
            xv = pulp.value(x[crs])
            yv = pulp.value(y[crs])
            if xv is None or yv is None:
                return False
            points.add((float(xv), float(yv)))
        # Reject collapsed / default-zero solutions with no real incumbent.
        return len(points) >= max(3, n // 20)

    if not mip_ok():
        print(
            "metro_layout: MIP returned no usable incumbent; using constructive seed",
            file=sys.stderr,
            flush=True,
        )
        return _result_from_seed(seed_pos, undirected, meta)

    out_stations = []
    for crs in stations:
        xv = pulp.value(x[crs])
        yv = pulp.value(y[crs])
        out_stations.append(
            {
                "crs": crs,
                "x": float(xv),
                "y": float(yv),
                "rank": meta[crs]["rank"],
                "lane": meta[crs]["lane"],
            }
        )
    out_stations.sort(key=lambda row: row["crs"])
    out_edges = [{"from": a, "to": b} for a, b in undirected]
    out_edges.sort(key=lambda row: (row["from"], row["to"]))

    print(
        f"metro_layout: done ({len(out_stations)} stations, {len(out_edges)} edges)",
        file=sys.stderr,
        flush=True,
    )
    return {"stations": out_stations, "edges": out_edges}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Octilinear layout via PuLP + HiGHS")
    parser.add_argument("--input", help="Path to layout-input JSON (default: stdin)")
    parser.add_argument(
        "--time-limit-sec",
        type=float,
        default=1200.0,
        help="HiGHS time limit in seconds (default 1200)",
    )
    args = parser.parse_args(argv)

    try:
        payload = _load_input(args.input)
        result = run_layout(payload, args.time_limit_sec)
    except Exception as exc:  # noqa: BLE001 — CLI boundary
        print(f"metro_layout error: {exc}", file=sys.stderr, flush=True)
        return 1

    json.dump(result, sys.stdout, separators=(",", ":"))
    sys.stdout.write("\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
