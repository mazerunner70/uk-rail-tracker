"""Build PuLP layout-input JSON from LM stop lists + station geo.

Topology reduction mirrors server/src/networkSchematic.ts bridgedHops:
markSkippedPairs → markGeoShortcuts → dropSubsetWalks → restoreConnectivityDrops.
"""

from __future__ import annotations

import json
import math
from collections import defaultdict, deque
from pathlib import Path
from typing import Any

GEO_SCALE = 900.0
RANK_STEP = 72.0
LANE_STEP = 48.0
FLOW_X = math.sqrt(0.5)
FLOW_Y = -math.sqrt(0.5)
PERP_Y = math.sqrt(0.5)


def _edge_key(a: str, b: str) -> str:
    return f"{a}|{b}" if a < b else f"{b}|{a}"


def _load_geo(path: Path) -> dict[str, tuple[float, float]]:
    if not path.is_file():
        return {}
    try:
        rows = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {}
    out: dict[str, tuple[float, float]] = {}
    if not isinstance(rows, list):
        return out
    for row in rows:
        if not isinstance(row, dict):
            continue
        crs = row.get("crs")
        lat = row.get("lat")
        lon = row.get("lon")
        if isinstance(crs, str) and crs and isinstance(lat, (int, float)) and isinstance(lon, (int, float)):
            out[crs] = (float(lat), float(lon))
    return out


def _build_graph(
    stop_lists: list[list[str]],
) -> tuple[dict[str, dict[str, int]], dict[str, int], dict[str, int], list[list[str]]]:
    adj: dict[str, dict[str, int]] = {}
    origin_count: dict[str, int] = defaultdict(int)
    dest_count: dict[str, int] = defaultdict(int)
    kept: list[list[str]] = []
    for stops in stop_lists:
        if len(stops) < 2:
            continue
        kept.append(list(stops))
        origin_count[stops[0]] += 1
        dest_count[stops[-1]] += 1
        for crs in stops:
            adj.setdefault(crs, {})
        for i in range(len(stops) - 1):
            a, b = stops[i], stops[i + 1]
            if a == b:
                continue
            adj.setdefault(a, {})
            adj.setdefault(b, {})
            adj[a][b] = adj[a].get(b, 0) + 1
            adj[b][a] = adj[b].get(a, 0) + 1
    return adj, dict(origin_count), dict(dest_count), kept


def _mark_skipped_pairs(adj: dict[str, dict[str, int]], stop_lists: list[list[str]], drop: set[str]) -> None:
    for stops in stop_lists:
        for i in range(len(stops)):
            for j in range(i + 2, len(stops)):
                left, right = stops[i], stops[j]
                if left == right:
                    continue
                if right in adj.get(left, {}):
                    drop.add(_edge_key(left, right))


def _geo_dist(left: str, right: str, geo: dict[str, tuple[float, float]]) -> float | None:
    a = geo.get(left)
    b = geo.get(right)
    if not a or not b:
        return None
    mid_lat = ((a[0] + b[0]) / 2.0) * (math.pi / 180.0)
    dx = (b[1] - a[1]) * math.cos(mid_lat)
    dy = b[0] - a[0]
    return math.hypot(dx, dy)


def _mark_geo_shortcuts(
    adj: dict[str, dict[str, int]],
    geo: dict[str, tuple[float, float]],
    drop: set[str],
) -> None:
    max_hops = 12
    slack = 1.35
    edges: list[tuple[str, str]] = []
    seen: set[str] = set()
    for left, neighbors in adj.items():
        for right in neighbors:
            key = _edge_key(left, right)
            if key in seen or key in drop:
                continue
            seen.add(key)
            edges.append((left, right) if left < right else (right, left))

    for left, right in edges:
        direct = _geo_dist(left, right, geo)
        if direct is None or direct < 1e-9:
            continue
        blocked = _edge_key(left, right)
        queue: deque[tuple[str, int, float]] = deque([(left, 0, 0.0)])
        best: dict[str, tuple[int, float]] = {left: (0, 0.0)}
        found = False
        while queue:
            crs, hops, path_km = queue.popleft()
            if crs == right and hops >= 2:
                if path_km <= direct * slack:
                    found = True
                    break
                continue
            if hops >= max_hops:
                continue
            for nxt in adj.get(crs, {}):
                hop = _edge_key(crs, nxt)
                if hop == blocked:
                    continue
                hop_km = _geo_dist(crs, nxt, geo)
                if hop_km is None:
                    continue
                next_km = path_km + hop_km
                if next_km > direct * slack:
                    continue
                next_hops = hops + 1
                prev = best.get(nxt)
                if prev and prev[1] <= next_km and prev[0] <= next_hops:
                    continue
                best[nxt] = (next_hops, next_km)
                queue.append((nxt, next_hops, next_km))
        if found:
            drop.add(blocked)


def _open_neighbors(adj: dict[str, dict[str, int]], crs: str, drop: set[str]) -> list[str]:
    return sorted(
        neighbor
        for neighbor in adj.get(crs, {})
        if _edge_key(crs, neighbor) not in drop
    )


def _is_junction(adj: dict[str, dict[str, int]], crs: str, drop: set[str]) -> bool:
    return len(_open_neighbors(adj, crs, drop)) != 2


def _other_neighbor(
    adj: dict[str, dict[str, int]], crs: str, previous: str, drop: set[str]
) -> str | None:
    for neighbor in _open_neighbors(adj, crs, drop):
        if neighbor != previous:
            return neighbor
    return None


def _order_walk(start: str, end: str, via: list[str]) -> tuple[str, str, list[str]]:
    if start < end:
        return start, end, list(via)
    return end, start, list(reversed(via))


def _walks_between_junctions(
    adj: dict[str, dict[str, int]], drop: set[str]
) -> list[tuple[str, str, list[str]]]:
    walks: list[tuple[str, str, list[str]]] = []
    seen: set[str] = set()
    junctions = sorted(crs for crs in adj if _is_junction(adj, crs, drop))
    for start in junctions:
        for nxt in _open_neighbors(adj, start, drop):
            if _edge_key(start, nxt) in seen:
                continue
            via: list[str] = []
            prev = start
            current = nxt
            seen.add(_edge_key(start, nxt))
            while not _is_junction(adj, current, drop):
                via.append(current)
                onward = _other_neighbor(adj, current, prev, drop)
                if not onward or _edge_key(current, onward) in seen:
                    break
                seen.add(_edge_key(current, onward))
                prev = current
                current = onward
            if _is_junction(adj, current, drop):
                walks.append(_order_walk(start, current, via))
    return walks


def _walk_hops(walk: tuple[str, str, list[str]]) -> list[str]:
    start, end, via = walk
    seq = [start, *via, end]
    return [_edge_key(seq[i], seq[i + 1]) for i in range(len(seq) - 1)]


def _is_proper_subset(left: set[str], right: set[str]) -> bool:
    if len(left) >= len(right):
        return False
    return left.issubset(right)


def _drop_subset_walks(walks: list[tuple[str, str, list[str]]], drop: set[str]) -> None:
    sets = [set(via) for _a, _b, via in walks]
    for i, walk in enumerate(walks):
        smaller = sets[i]
        larger = [
            walks[j]
            for j, via_set in enumerate(sets)
            if j != i and _is_proper_subset(smaller, via_set)
        ]
        if not larger:
            continue
        kept = {hop for other in larger for hop in _walk_hops(other)}
        for hop in _walk_hops(walk):
            if hop not in kept:
                drop.add(hop)


def _restore_connectivity_drops(adj: dict[str, dict[str, int]], drop: set[str]) -> None:
    if not drop:
        return
    parent: dict[str, str] = {crs: crs for crs in adj}

    def find(crs: str) -> str:
        cur = parent.get(crs, crs)
        if cur == crs:
            return crs
        root = find(cur)
        parent[crs] = root
        return root

    def unite(left: str, right: str) -> bool:
        a, b = find(left), find(right)
        if a == b:
            return False
        parent[a] = b
        return True

    kept: list[tuple[str, str, int]] = []
    candidates: list[tuple[str, str, int]] = []
    seen: set[str] = set()
    for left, neighbors in adj.items():
        for right, weight in neighbors.items():
            key = _edge_key(left, right)
            if key in seen:
                continue
            seen.add(key)
            a, b = (left, right) if left < right else (right, left)
            edge = (a, b, weight)
            if key in drop:
                candidates.append(edge)
            else:
                kept.append(edge)

    for a, b, _w in kept:
        unite(a, b)

    candidates.sort(key=lambda e: (-e[2], _edge_key(e[0], e[1])))
    for a, b, _w in candidates:
        if unite(a, b):
            drop.discard(_edge_key(a, b))


def _bridged_hops(
    adj: dict[str, dict[str, int]],
    stop_lists: list[list[str]],
    geo: dict[str, tuple[float, float]] | None,
) -> set[str]:
    drop: set[str] = set()
    _mark_skipped_pairs(adj, stop_lists, drop)
    if geo:
        _mark_geo_shortcuts(adj, geo, drop)
    groups: dict[str, list[tuple[str, str, list[str]]]] = defaultdict(list)
    for walk in _walks_between_junctions(adj, drop):
        groups[f"{walk[0]}|{walk[1]}"].append(walk)
    for walks in groups.values():
        _drop_subset_walks(walks, drop)
    _restore_connectivity_drops(adj, drop)
    return drop


def _reduced_edges(
    adj: dict[str, dict[str, int]],
    stop_lists: list[list[str]],
    geo: dict[str, tuple[float, float]] | None,
) -> list[dict[str, Any]]:
    drop = _bridged_hops(adj, stop_lists, geo)
    edges: list[dict[str, Any]] = []
    seen: set[str] = set()
    for left, neighbors in adj.items():
        for right, weight in neighbors.items():
            key = _edge_key(left, right)
            if key in seen or key in drop:
                continue
            seen.add(key)
            a, b = (left, right) if left < right else (right, left)
            edges.append({"from": a, "to": b, "weight": weight})
    return edges


def _cap_degree(edges: list[dict[str, Any]], max_degree: int = 8) -> list[dict[str, Any]]:
    """Last-resort: drop lightest edges until every station degree ≤ max_degree."""
    working = list(edges)
    while True:
        degree: dict[str, list[tuple[int, str, str]]] = defaultdict(list)
        for edge in working:
            a, b, w = edge["from"], edge["to"], int(edge["weight"])
            degree[a].append((w, a, b))
            degree[b].append((w, a, b))
        offenders = [crs for crs, rows in degree.items() if len(rows) > max_degree]
        if not offenders:
            return working
        crs = sorted(offenders)[0]
        rows = sorted(degree[crs], key=lambda row: (row[0], row[1], row[2]))
        _w, a, b = rows[0]
        key = _edge_key(a, b)
        print(
            f"layout_input: degree cap dropping {_edge_key(a, b)} at {crs} (>{max_degree})",
            flush=True,
        )
        working = [e for e in working if _edge_key(e["from"], e["to"]) != key]


def _mst_links(edges: list[dict[str, Any]]) -> dict[str, list[str]]:
    parent: dict[str, str] = {}

    def find(crs: str) -> str:
        cur = parent.get(crs, crs)
        if cur == crs:
            return crs
        root = find(cur)
        parent[crs] = root
        return root

    for edge in edges:
        parent.setdefault(edge["from"], edge["from"])
        parent.setdefault(edge["to"], edge["to"])
    sorted_edges = sorted(edges, key=lambda e: (-int(e["weight"]), _edge_key(e["from"], e["to"])))
    links: dict[str, list[str]] = defaultdict(list)
    for edge in sorted_edges:
        a, b = edge["from"], edge["to"]
        ra, rb = find(a), find(b)
        if ra == rb:
            continue
        parent[ra] = rb
        links[a].append(b)
        links[b].append(a)
    for nbrs in links.values():
        nbrs.sort()
    return dict(links)


def _farthest(links: dict[str, list[str]], start: str, members: list[str]) -> str:
    dist = {start: 0}
    queue: deque[str] = deque([start])
    while queue:
        crs = queue.popleft()
        for neighbor in links.get(crs, []):
            if neighbor in dist:
                continue
            dist[neighbor] = dist[crs] + 1
            queue.append(neighbor)
    best = start
    best_dist = 0
    for crs in members:
        d = dist.get(crs, -1)
        if d > best_dist or (d == best_dist and crs < best):
            best, best_dist = crs, d
    return best


def _path_between(links: dict[str, list[str]], start: str, end: str) -> list[str]:
    if start == end:
        return [start]
    prev: dict[str, str | None] = {start: None}
    queue: deque[str] = deque([start])
    while queue:
        crs = queue.popleft()
        for neighbor in links.get(crs, []):
            if neighbor in prev:
                continue
            prev[neighbor] = crs
            queue.append(neighbor)
    if end not in prev:
        return [start]
    path: list[str] = []
    cur: str | None = end
    while cur is not None:
        path.append(cur)
        cur = prev.get(cur)
    path.reverse()
    return path


def _score_end(crs: str, origin: dict[str, int], dest: dict[str, int]) -> int:
    return dest.get(crs, 0) - origin.get(crs, 0)


def _lower_left_end(spine: list[str], origin: dict[str, int], dest: dict[str, int]) -> str:
    left, right = spine[0], spine[-1]
    if left == "EUS" or right == "EUS":
        return "EUS"
    ls = _score_end(left, origin, dest)
    rs = _score_end(right, origin, dest)
    if ls == rs:
        return left if left < right else right
    return left if ls > rs else right


def _ranks_from(links: dict[str, list[str]], root: str) -> dict[str, int]:
    rank = {root: 0}
    queue: deque[str] = deque([root])
    while queue:
        crs = queue.popleft()
        for neighbor in links.get(crs, []):
            if neighbor in rank:
                continue
            rank[neighbor] = rank[crs] + 1
            queue.append(neighbor)
    return rank


def _lanes_for(links: dict[str, list[str]], spine: list[str]) -> dict[str, int]:
    lane = {crs: 0 for crs in spine}
    on_spine = set(spine)
    roots: list[str] = []
    for i, crs in enumerate(spine):
        parent = None if i == 0 else spine[i - 1]
        children = [n for n in links.get(crs, []) if n != parent and n not in on_spine]
        roots.extend(children)
    for index, root in enumerate(roots):
        step = index // 2 + 1
        branch_lane = step if index % 2 == 0 else -step
        stack = [root]
        while stack:
            crs = stack.pop()
            if crs in lane:
                continue
            lane[crs] = branch_lane
            for neighbor in links.get(crs, []):
                if neighbor not in lane:
                    stack.append(neighbor)
    return lane


def _raw_point(rank: int, lane: int) -> tuple[float, float]:
    return (
        (rank * RANK_STEP + lane * LANE_STEP) * FLOW_X,
        rank * RANK_STEP * FLOW_Y + lane * LANE_STEP * PERP_Y,
    )


def _place_stations(
    members: list[str],
    links: dict[str, list[str]],
    origin: dict[str, int],
    dest: dict[str, int],
) -> dict[str, dict[str, Any]]:
    """MST spine ranks/lanes (fallback coords before geo projection)."""
    placed: dict[str, dict[str, Any]] = {}
    allowed = set(members)
    seen: set[str] = set()
    lane_shift = 0
    for start in sorted(members):
        if start in seen:
            continue
        comp: list[str] = []
        queue: deque[str] = deque([start])
        seen.add(start)
        while queue:
            crs = queue.popleft()
            comp.append(crs)
            for neighbor in links.get(crs, []):
                if neighbor in seen or neighbor not in allowed:
                    continue
                seen.add(neighbor)
                queue.append(neighbor)
        if not links:
            for i, crs in enumerate(sorted(comp)):
                placed[crs] = {"crs": crs, "rank": i, "lane": 0, "x": float(i), "y": 0.0}
            continue
        far = _farthest(links, sorted(comp)[0], comp)
        other = _farthest(links, far, comp)
        spine = _path_between(links, far, other)
        pole = _lower_left_end(spine, origin, dest)
        if spine[0] != pole:
            spine = list(reversed(spine))
        rank = _ranks_from(links, spine[0])
        lane = _lanes_for(links, spine)
        for crs in comp:
            r = rank.get(crs, 0)
            ln = lane.get(crs, 0)
            x, y = _raw_point(r, ln + lane_shift)
            placed[crs] = {"crs": crs, "rank": r, "lane": ln, "x": x, "y": y}
        lane_shift += 3
    return placed


def _project_geo(geo: dict[str, tuple[float, float]], scale: float = GEO_SCALE) -> dict[str, tuple[float, float]]:
    lats = [lat for lat, _ in geo.values()]
    lons = [lon for _, lon in geo.values()]
    lat0 = sum(lats) / len(lats)
    lon0 = sum(lons) / len(lons)
    cos = math.cos(lat0 * math.pi / 180)
    return {
        crs: ((lon - lon0) * cos * scale, (lat0 - lat) * scale)
        for crs, (lat, lon) in geo.items()
    }


def _north_up_place(
    meta: dict[str, dict[str, Any]],
    adj: dict[str, dict[str, int]],
    geo: dict[str, tuple[float, float]],
) -> dict[str, dict[str, Any]]:
    """Project known CRS; fill missing via neighbour average (networkSchematic northUpPlace)."""
    usable = {crs: geo[crs] for crs in meta if crs in geo}
    if not usable:
        return meta
    xy = _project_geo(usable)
    by_crs = {crs: dict(row) for crs, row in meta.items()}
    placed: set[str] = set()
    for crs, (x, y) in xy.items():
        by_crs[crs]["x"] = x
        by_crs[crs]["y"] = y
        placed.add(crs)

    pending = [crs for crs in meta if crs not in placed]
    guard = len(pending) + 3
    while pending and guard > 0:
        guard -= 1
        still: list[str] = []
        progressed = False
        for crs in pending:
            neighbors = [n for n in adj.get(crs, {}) if n in placed]
            if not neighbors:
                still.append(crs)
                continue
            by_crs[crs]["x"] = sum(by_crs[n]["x"] for n in neighbors) / len(neighbors)
            by_crs[crs]["y"] = sum(by_crs[n]["y"] for n in neighbors) / len(neighbors)
            placed.add(crs)
            progressed = True
        if not progressed:
            anchored = [by_crs[c] for c in placed]
            cx = sum(s["x"] for s in anchored) / max(1, len(anchored))
            cy = sum(s["y"] for s in anchored) / max(1, len(anchored))
            for crs in still:
                by_crs[crs]["x"] = cx
                by_crs[crs]["y"] = cy
                placed.add(crs)
            break
        pending = still
    return by_crs


def _line_hops_from_stop_lists(
    stop_lists: list[list[str]], edge_keys: set[str]
) -> list[dict[str, str]]:
    hops: list[dict[str, str]] = []
    seen_paths: set[str] = set()
    line_index = 0
    for stops in stop_lists:
        path_key = "|".join(stops)
        if path_key in seen_paths:
            continue
        seen_paths.add(path_key)
        linename = f"L{line_index}"
        line_index += 1
        for i in range(len(stops) - 1):
            a, b = stops[i], stops[i + 1]
            if _edge_key(a, b) not in edge_keys:
                continue
            hops.append({"linename": linename, "from": a, "to": b})
    return hops


def build_layout_input(
    stop_lists: list[list[str]],
    geo_path: Path | None = None,
) -> dict[str, Any]:
    adj, origin_count, dest_count, kept = _build_graph(stop_lists)
    if not adj:
        return {"nodes": [], "edges": [], "lines": []}

    geo_all = _load_geo(geo_path) if geo_path else {}
    geo = {crs: geo_all[crs] for crs in adj if crs in geo_all} if geo_all else {}

    edges = _reduced_edges(adj, kept, geo if geo else None)
    edges = _cap_degree(edges)
    edges.sort(key=lambda e: _edge_key(e["from"], e["to"]))

    links = _mst_links(edges)
    meta = _place_stations(sorted(adj.keys()), links, origin_count, dest_count)
    if geo:
        meta = _north_up_place(meta, adj, geo)

    edge_keys = {_edge_key(e["from"], e["to"]) for e in edges}
    nodes = [
        {
            "crs": crs,
            "x": float(meta[crs]["x"]),
            "y": float(meta[crs]["y"]),
            "rank": int(meta[crs]["rank"]),
            "lane": int(meta[crs]["lane"]),
        }
        for crs in sorted(meta)
    ]
    return {
        "nodes": nodes,
        "edges": edges,
        "lines": _line_hops_from_stop_lists(kept, edge_keys),
    }


def schematic_lines_from_stop_lists(
    stop_lists: list[list[str]],
    geo_path: Path | None = None,
) -> list[dict[str, Any]]:
    """Junction→junction walks on the reduced graph (matches linePathsFromJourneys)."""
    adj, _o, _d, kept = _build_graph(stop_lists)
    if not adj:
        return []
    geo_all = _load_geo(geo_path) if geo_path else {}
    geo = {crs: geo_all[crs] for crs in adj if crs in geo_all} if geo_all else {}
    drop = _bridged_hops(adj, kept, geo if geo else None)
    # Degree-cap drops are not in `drop`; approximate by rebuilding from reduced edges.
    edges = _reduced_edges(adj, kept, geo if geo else None)
    edges = _cap_degree(edges)
    keep_keys = {_edge_key(e["from"], e["to"]) for e in edges}
    # Rebuild drop as everything not kept (so walks see the same reduced graph)
    drop = set()
    seen: set[str] = set()
    for left, neighbors in adj.items():
        for right in neighbors:
            key = _edge_key(left, right)
            if key in seen:
                continue
            seen.add(key)
            if key not in keep_keys:
                drop.add(key)

    walks = _walks_between_junctions(adj, drop)
    lines: list[dict[str, Any]] = []
    seen_ids: set[str] = set()
    for start, end, via in walks:
        stations = [start, *via, end]
        if len(stations) < 2:
            continue
        line_id = "|".join(stations)
        if line_id in seen_ids:
            continue
        seen_ids.add(line_id)
        lines.append({"id": line_id, "stations": stations})
    lines.sort(key=lambda row: row["id"])
    return lines
