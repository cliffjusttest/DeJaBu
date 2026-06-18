#!/usr/bin/env python3
"""Generate ASCII maps and maps.json from WORLD_MAP.md design."""

from __future__ import annotations

import json
import re
import sys
from collections import defaultdict
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TOOLS = Path(__file__).resolve().parent
if str(TOOLS) not in sys.path:
    sys.path.insert(0, str(TOOLS))
WORLD_MAP_MD = ROOT / "WORLD_MAP.md"
SERVER_MAPS = ROOT / "server/src/main/resources/maps"
CLIENT_MAPS = ROOT / "client/data/maps"

WALKABLE = set(".P=@")


def set_cell(grid: list[list[str]], x: int, y: int, ch: str) -> None:
    h, w = len(grid), len(grid[0])
    if 0 <= y < h and 0 <= x < w:
        grid[y][x] = ch


def fill_rect(grid: list[list[str]], x1: int, y1: int, x2: int, y2: int, ch: str) -> None:
    for y in range(min(y1, y2), max(y1, y2) + 1):
        for x in range(min(x1, x2), max(x1, x2) + 1):
            set_cell(grid, x, y, ch)


def draw_h_line(grid: list[list[str]], y: int, x1: int, x2: int, ch: str) -> None:
    for x in range(min(x1, x2), max(x1, x2) + 1):
        set_cell(grid, x, y, ch)


def draw_v_line(grid: list[list[str]], x: int, y1: int, y2: int, ch: str) -> None:
    for y in range(min(y1, y2), max(y1, y2) + 1):
        set_cell(grid, x, y, ch)


def carve_gate_path(grid: list[list[str]], ep: ExitPoint) -> None:
    """Connect outer gate to city center with a road."""
    h, w = len(grid), len(grid[0])
    cx, cy = w // 2, h // 2
    x, y = ep.x, ep.y
    while (x, y) != (cx, cy):
        if x < cx:
            x += 1
        elif x > cx:
            x -= 1
        elif y < cy:
            y += 1
        elif y > cy:
            y -= 1
        if grid[y][x] == "#":
            grid[y][x] = "P"
        elif grid[y][x] == ".":
            grid[y][x] = "P"


def scatter_trees(grid: list[list[str]], density: int = 6, margin: int = 2) -> None:
    h, w = len(grid), len(grid[0])
    for y in range(margin, h - margin):
        for x in range(margin, w - margin):
            if grid[y][x] != ".":
                continue
            if (x * 7 + y * 13) % density == 0:
                grid[y][x] = "T"
            elif (x * 3 + y * 11) % (density + 4) == 0:
                grid[y][x] = "T"


# Per-map visual themes (battlefields, ferries, capitals)
SPECIAL_THEMES: dict[str, str] = {
    "battlefield_guandu": "guandu",
    "battlefield_chibi": "chibi_shore",
    "battlefield_hanzhong": "mountain_pass",
    "battlefield_yiling": "cliff_river",
    "battlefield_xiangfan": "river_plain",
    "battlefield_hefei": "fortress",
    "battlefield_jieting": "hill_road",
    "chibi": "river_crossing",
    "mengjin_ferry": "yellow_river",
    "baima_ferry": "yellow_river",
    "hangu_pass": "mountain_gate",
    "wu_pass": "mountain_gate",
    "jianmen_pass": "mountain_gate",
    "jingxing_pass": "mountain_gate",
    "yanmen_pass": "mountain_gate",
    "luoyang": "imperial",
    "changan": "imperial",
    "chengdu": "imperial",
    "jiankang": "imperial",
    "longzhong": "hermitage",
    "rebel_camp_yingchuan": "rebel_camp",
    "rebel_camp_julu": "rebel_camp",
    "rebel_camp_nanyang": "rebel_camp",
}

CAPITAL_PALACE: dict[str, tuple[int, int]] = {
    "luoyang": (0, -2),
    "changan": (0, 0),
    "chengdu": (1, 1),
    "jiankang": (-1, 0),
}


@dataclass
class MapDef:
    map_id: str
    name: str
    map_type: str
    danger: int
    width: int
    height: int


@dataclass
class Connection:
    source: str
    direction: str
    target: str


@dataclass
class ExitPoint:
    x: int
    y: int
    direction: str
    target: str


OPPOSITE = {
    "北": "南",
    "南": "北",
    "東": "西",
    "西": "東",
    "西北": "東南",
    "東北": "西南",
    "西南": "東北",
    "東南": "西北",
}

LARGE_CITIES = {"luoyang", "changan", "chengdu", "jiankang"}
HOSPITAL_CITIES = {
    "luoyang": ("太醫署", 8, 8),
    "xuchang": ("許縣醫廬", 8, 8),
    "changan": ("長安醫館", 8, 8),
    "yingchuan": ("潁川醫館", 8, 8),
    "chenliu": ("陳留醫館", 8, 8),
    "ye": ("鄴城醫館", 8, 8),
    "wancheng": ("宛城醫館", 8, 8),
    "xiangyang": ("襄陽醫館", 8, 8),
    "jiangling": ("江陵醫館", 8, 8),
    "jiankang": ("建業醫館", 8, 8),
    "chengdu": ("成都醫館", 8, 8),
    "jinyang": ("晉陽醫館", 8, 8),
    "panyu": ("番禺醫館", 8, 8),
}

DEFAULT_SIZES = {
    "city": (28, 20),
    "suburb": (28, 20),
    "road": (36, 12),
    "pass": (24, 14),
    "ferry": (20, 14),
    "wild": (28, 20),
    "battle": (32, 24),
    "camp": (24, 18),
}


def parse_world_map_md() -> tuple[dict[str, MapDef], list[Connection]]:
    text = WORLD_MAP_MD.read_text(encoding="utf-8")
    sec4 = re.search(r"## 4\. 完整地圖清單(.*?)## 5\.", text, re.S)
    sec5 = re.search(r"## 5\. 完整傳點連接表(.*?)## 6\.", text, re.S)
    if not sec4 or not sec5:
        raise RuntimeError("Cannot parse WORLD_MAP.md sections")

    maps: dict[str, MapDef] = {}
    for line in sec4.group(1).splitlines():
        m = re.match(
            r"^\| `([a-z_]+)` \| ([^|]+) \| ([a-z]+) \| (\d+) \| (\d+)×(\d+) \|",
            line,
        )
        if not m:
            continue
        map_id, name, map_type, danger, w, h = m.groups()
        maps[map_id] = MapDef(
            map_id=map_id,
            name=name.strip(),
            map_type=map_type,
            danger=int(danger),
            width=int(w),
            height=int(h),
        )

    connections: list[Connection] = []
    for line in sec5.group(1).splitlines():
        m = re.match(r"^\| `([a-z_]+)` \| ([^|]+) \| `([a-z_]+)` \|", line)
        if not m:
            continue
        src, direction, tgt = m.groups()
        direction = direction.strip()
        if direction in OPPOSITE or direction in ("西北", "東北", "西南", "東南"):
            connections.append(Connection(src, direction, tgt))

    return maps, connections


def default_size(map_id: str, map_type: str) -> tuple[int, int]:
    if map_id in LARGE_CITIES:
        return 40, 30
    return DEFAULT_SIZES.get(map_type, (28, 20))


def exit_positions(width: int, height: int, direction: str, index: int, total: int) -> tuple[int, int]:
    cx = width // 2
    cy = height // 2
    if total <= 1:
        offsets = [0]
    else:
        step = max(3, (width - 6) // max(1, total - 1))
        start = cx - step * (total - 1) // 2
        offsets = [start + i * step for i in range(total)]

    idx = min(index, len(offsets) - 1)
    ox = offsets[idx]

    if direction == "北":
        return max(1, min(width - 2, ox)), 1
    if direction == "南":
        return max(1, min(width - 2, ox)), height - 2
    if direction == "東":
        return width - 2, max(1, min(height - 2, ox))
    if direction == "西":
        return 1, max(1, min(height - 2, ox))
    if direction == "西北":
        return 1, 1
    if direction == "東北":
        return width - 2, 1
    if direction == "西南":
        return 1, height - 2
    if direction == "東南":
        return width - 2, height - 2
    return cx, cy


def assign_exits(maps: dict[str, MapDef], connections: list[Connection]) -> dict[str, list[ExitPoint]]:
    by_source: dict[str, list[Connection]] = defaultdict(list)
    for c in connections:
        by_source[c.source].append(c)

    exits: dict[str, list[ExitPoint]] = defaultdict(list)
    for src, conns in by_source.items():
        mdef = maps.get(src)
        if not mdef:
            continue
        by_dir: dict[str, list[Connection]] = defaultdict(list)
        for c in conns:
            by_dir[c.direction].append(c)
        used: set[tuple[int, int]] = set()
        for direction, dir_conns in by_dir.items():
            for i, c in enumerate(dir_conns):
                x, y = exit_positions(mdef.width, mdef.height, direction, i, len(dir_conns))
                while (x, y) in used:
                    x = min(mdef.width - 2, x + 1)
                    if (x, y) in used:
                        y = min(mdef.height - 2, y + 1)
                used.add((x, y))
                exits[src].append(ExitPoint(x, y, direction, c.target))
    return exits


def create_grid(width: int, height: int, fill: str = ".") -> list[list[str]]:
    return [[fill for _ in range(width)] for _ in range(height)]


def carve_border(grid: list[list[str]], wall: str = "#") -> None:
    h = len(grid)
    w = len(grid[0])
    for y in range(h):
        for x in range(w):
            if y == 0 or y == h - 1 or x == 0 or x == w - 1:
                grid[y][x] = wall


def paint_city(grid: list[list[str]], map_type: str, map_id: str, exits: list[ExitPoint]) -> None:
    h = len(grid)
    w = len(grid[0])
    cx, cy = w // 2, h // 2
    carve_border(grid)

    wall = 3 if map_id in LARGE_CITIES else 2
    inner_x1, inner_y1 = wall, wall
    inner_x2, inner_y2 = w - wall - 1, h - wall - 1

    for y in range(inner_y1, inner_y2 + 1):
        for x in range(inner_x1, inner_x2 + 1):
            grid[y][x] = "."

    exit_cells = {(ep.x, ep.y) for ep in exits}
    for x in range(inner_x1, inner_x2 + 1):
        grid[inner_y1][x] = "P" if (x, inner_y1) in exit_cells else "#"
        grid[inner_y2][x] = "P" if (x, inner_y2) in exit_cells else "#"
    for y in range(inner_y1, inner_y2 + 1):
        grid[y][inner_x1] = "P" if (inner_x1, y) in exit_cells else "#"
        grid[y][inner_x2] = "P" if (inner_x2, y) in exit_cells else "#"

    draw_h_line(grid, cy, inner_x1 + 1, inner_x2 - 1, "P")
    draw_v_line(grid, cx, inner_y1 + 1, inner_y2 - 1, "P")
    if map_id in LARGE_CITIES or w >= 32:
        draw_h_line(grid, cy - h // 6, inner_x1 + 1, inner_x2 - 1, "P")
        draw_h_line(grid, cy + h // 6, inner_x1 + 1, inner_x2 - 1, "P")
        draw_v_line(grid, cx - w // 6, inner_y1 + 1, inner_y2 - 1, "P")
        draw_v_line(grid, cx + w // 6, inner_y1 + 1, inner_y2 - 1, "P")

    plaza_r = 2 if map_id not in LARGE_CITIES else 3
    px, py = cx, cy
    if map_id in CAPITAL_PALACE:
        ox, oy = CAPITAL_PALACE[map_id]
        px, py = cx + ox, cy + oy
    fill_rect(grid, px - plaza_r, py - plaza_r, px + plaza_r, py + plaza_r, "=")
    if map_id in LARGE_CITIES:
        fill_rect(grid, px - plaza_r - 1, py - plaza_r - 1, px + plaza_r + 1, py + plaza_r + 1, "#")
        fill_rect(grid, px - plaza_r, py - plaza_r, px + plaza_r, py + plaza_r, "=")

    if SPECIAL_THEMES.get(map_id) == "imperial":
        for gx in (inner_x1 + 4, inner_x2 - 4):
            for gy in (inner_y1 + 3, inner_y2 - 3):
                if abs(gx - px) > 5 and abs(gy - py) > 4:
                    fill_rect(grid, gx - 1, gy - 1, gx + 1, gy + 1, "T")

    if map_type == "suburb":
        scatter_trees(grid, density=5, margin=1)
        market_y = min(inner_y2 - 1, cy + 2)
        for x in range(inner_x1 + 2, inner_x2 - 1, 4):
            if grid[market_y][x] in (".", "P"):
                grid[market_y][x] = "P"
    else:
        scatter_trees(grid, density=7, margin=wall + 1)

    for ep in exits:
        grid[ep.y][ep.x] = "@"
        carve_gate_path(grid, ep)


def paint_road(grid: list[list[str]], map_id: str, exits: list[ExitPoint]) -> None:
    h = len(grid)
    w = len(grid[0])
    carve_border(grid)
    mid_y = h // 2
    mid_x = w // 2

    dirs = {ep.direction for ep in exits}
    vertical = ("北" in dirs or "南" in dirs) and not ("東" in dirs or "西" in dirs)

    if vertical:
        draw_v_line(grid, mid_x, 1, h - 2, "P")
        for y in range(1, h - 1):
            set_cell(grid, mid_x, y, "=" if y % 4 == 0 else "P")
            if 1 < mid_x < w - 2:
                set_cell(grid, mid_x - 1, y, "T" if y % 3 else ".")
                set_cell(grid, mid_x + 1, y, "T" if (y + 1) % 3 else ".")
    else:
        draw_h_line(grid, mid_y, 1, w - 2, "P")
        for x in range(1, w - 1):
            set_cell(grid, x, mid_y, "=" if x % 4 == 0 else "P")
            if 1 < mid_y < h - 2:
                set_cell(grid, x, mid_y - 1, "T" if x % 5 == 0 else ".")
                set_cell(grid, x, mid_y + 1, "T" if (x + 2) % 5 == 0 else ".")
        if "river" in map_id or (len(map_id) + w) % 5 == 0:
            stream_x = w // 3
            for y in range(1, h - 1):
                set_cell(grid, stream_x, y, "W")
            draw_h_line(grid, mid_y, max(1, stream_x - 1), min(w - 2, stream_x + 2), "=")

    rest_x, rest_y = w // 2, mid_y
    fill_rect(grid, rest_x - 1, rest_y - 1, rest_x + 1, rest_y + 1, ".")

    for ep in exits:
        grid[ep.y][ep.x] = "@"


def paint_pass(grid: list[list[str]], map_id: str, exits: list[ExitPoint]) -> None:
    h = len(grid)
    w = len(grid[0])
    carve_border(grid, "M")
    mid = h // 2
    gap = max(3, w // 4) if SPECIAL_THEMES.get(map_id) == "mountain_gate" else max(3, w // 4)

    for y in range(1, h - 1):
        for x in range(1, w - 1):
            grid[y][x] = "M"

    left = w // 2 - gap // 2
    right = left + gap - 1
    for y in range(1, h - 1):
        for x in range(left, right + 1):
            grid[y][x] = "P" if y == mid else "."
        if y in (2, h - 3):
            set_cell(grid, left - 1, y, "#")
            set_cell(grid, right + 1, y, "#")

    draw_h_line(grid, mid, left, right, "=")
    scatter_trees(grid, density=4, margin=1)

    for ep in exits:
        grid[ep.y][ep.x] = "@"


def paint_ferry(grid: list[list[str]], map_id: str, exits: list[ExitPoint]) -> None:
    h = len(grid)
    w = len(grid[0])
    carve_border(grid)
    river = h // 2
    theme = SPECIAL_THEMES.get(map_id, "river")

    for x in range(1, w - 1):
        grid[river][x] = "W"
        if theme == "yellow_river" and river - 1 > 0:
            grid[river - 1][x] = "W" if x % 3 == 0 else "."

    north_bank = max(1, river - 2)
    south_bank = min(h - 2, river + 2)
    dock_w = 4 if w > 22 else 3
    dock_x = w // 2 - dock_w // 2
    fill_rect(grid, dock_x, north_bank, dock_x + dock_w - 1, north_bank, "P")
    fill_rect(grid, dock_x, south_bank, dock_x + dock_w - 1, south_bank, "P")
    draw_v_line(grid, w // 2, north_bank, south_bank, "P")

    scatter_trees(grid, density=6, margin=1)
    for ep in exits:
        grid[ep.y][ep.x] = "@"


def paint_wild(grid: list[list[str]], map_id: str, exits: list[ExitPoint], danger: int) -> None:
    h = len(grid)
    w = len(grid[0])
    cx, cy = w // 2, h // 2
    carve_border(grid)

    for y in range(1, h - 1):
        for x in range(1, w - 1):
            grid[y][x] = "."

    scatter_trees(grid, density=max(4, 8 - danger), margin=1)

    x = 2
    y = cy
    while x < w - 2:
        grid[y][x] = "P"
        x += 1
        if (x + y) % 7 == 0 and 1 < y < h - 2:
            y += 1 if x % 2 == 0 else -1

    if map_id == "longzhong":
        fill_rect(grid, cx - 2, cy - 1, cx + 2, cy + 1, "#")
        set_cell(grid, cx, cy, ".")

    for ep in exits:
        grid[ep.y][ep.x] = "@"
        carve_gate_path(grid, ep)


def paint_battle(grid: list[list[str]], map_id: str, exits: list[ExitPoint]) -> None:
    theme = SPECIAL_THEMES.get(map_id, "plain")
    h = len(grid)
    w = len(grid[0])
    cx, cy = w // 2, h // 2
    carve_border(grid)

    for y in range(1, h - 1):
        for x in range(1, w - 1):
            grid[y][x] = "."

    if theme == "guandu":
        draw_h_line(grid, h - 4, 2, w - 3, "W")
        fill_rect(grid, cx - 4, cy - 3, cx + 4, cy + 3, ".")
        draw_h_line(grid, cy, cx - 5, cx + 5, "=")
    elif theme == "chibi_shore":
        for y in range(h // 2, h - 1):
            for x in range(1, w - 1):
                grid[y][x] = "W" if y > h * 2 // 3 else "."
        draw_h_line(grid, h // 3, 2, w - 3, "P")
    elif theme == "river_plain":
        draw_v_line(grid, w // 4, 2, h - 3, "W")
        fill_rect(grid, cx - 3, cy - 2, cx + 3, cy + 2, "=")
    elif theme == "cliff_river":
        for x in range(1, w // 2):
            for y in range(1, h - 1):
                if x + y < w // 2:
                    grid[y][x] = "M" if (x + y) % 3 else "#"
        draw_h_line(grid, h - 3, w // 2, w - 2, "W")
    elif theme == "mountain_pass":
        for y in range(1, h - 1):
            for x in range(1, w // 3):
                grid[y][x] = "M"
            for x in range(2 * w // 3, w - 1):
                grid[y][x] = "M"
        draw_h_line(grid, cy, w // 3, 2 * w // 3, "P")
    elif theme == "fortress":
        fill_rect(grid, cx - 3, cy - 3, cx + 3, cy + 3, "#")
        fill_rect(grid, cx - 2, cy - 2, cx + 2, cy + 2, "=")
    elif theme == "hill_road":
        scatter_trees(grid, density=5, margin=1)
        draw_h_line(grid, cy, 2, w - 3, "P")
    else:
        fill_rect(grid, cx - 3, cy - 2, cx + 3, cy + 2, "=")
        scatter_trees(grid, density=6, margin=2)

    for ep in exits:
        grid[ep.y][ep.x] = "@"
        carve_gate_path(grid, ep)


def paint_camp(grid: list[list[str]], map_id: str, exits: list[ExitPoint]) -> None:
    paint_wild(grid, map_id, exits, 4)
    h = len(grid)
    w = len(grid[0])
    cx, cy = w // 2, h // 2

    fill_rect(grid, cx - 4, cy - 3, cx + 4, cy + 3, "#")
    for tx in range(cx - 3, cx + 4, 3):
        for ty in range(cy - 2, cy + 3, 2):
            if grid[ty][tx] == "#":
                grid[ty][tx] = "."
    fill_rect(grid, cx - 1, cy - 1, cx + 1, cy + 1, "T")
    set_cell(grid, cx, cy, "P")

    for ep in exits:
        grid[ep.y][ep.x] = "@"
        carve_gate_path(grid, ep)


def paint_river_crossing(grid: list[list[str]], map_id: str, exits: list[ExitPoint]) -> None:
    h = len(grid)
    w = len(grid[0])
    carve_border(grid)
    river_y = h // 2
    for x in range(1, w - 1):
        grid[river_y][x] = "W"
    for y in range(1, h - 1):
        for x in range(1, w - 1):
            if y != river_y:
                grid[y][x] = "."
    draw_h_line(grid, river_y, w // 2 - 2, w // 2 + 2, "P")
    scatter_trees(grid, density=8, margin=1)
    for ep in exits:
        grid[ep.y][ep.x] = "@"


def generate_ascii(mdef: MapDef, exits: list[ExitPoint]) -> list[str]:
    from map_handcraft import try_handcraft

    handcrafted = try_handcraft(mdef.map_id, mdef.width, mdef.height, exits)
    if handcrafted is not None:
        return handcrafted

    grid = create_grid(mdef.width, mdef.height)
    theme = SPECIAL_THEMES.get(mdef.map_id, "")

    if theme == "river_crossing" and mdef.map_type == "ferry":
        paint_river_crossing(grid, mdef.map_id, exits)
    elif mdef.map_type == "city" or mdef.map_type == "suburb":
        paint_city(grid, mdef.map_type, mdef.map_id, exits)
    elif mdef.map_type == "road":
        paint_road(grid, mdef.map_id, exits)
    elif mdef.map_type == "pass":
        paint_pass(grid, mdef.map_id, exits)
    elif mdef.map_type == "ferry":
        paint_ferry(grid, mdef.map_id, exits)
    elif mdef.map_type == "wild":
        paint_wild(grid, mdef.map_id, exits, mdef.danger)
    elif mdef.map_type == "battle":
        paint_battle(grid, mdef.map_id, exits)
    elif mdef.map_type == "camp":
        paint_camp(grid, mdef.map_id, exits)
    else:
        paint_wild(grid, mdef.map_id, exits, mdef.danger)

    return ["".join(row) for row in grid]


def danger_zones_for(mdef: MapDef, lines: list[str]) -> list[dict]:
    if mdef.danger < 2 or mdef.map_type in ("city", "suburb", "ferry"):
        # Tutorial suburb keeps low-level encounters
        if mdef.map_id != "yingchuan_suburb":
            return []
    h = len(lines)
    w = len(lines[0])
    zones = []
    if mdef.map_type == "road":
        mid = h // 2
        zones.append(
            {
                "id": f"{mdef.map_id}_verge",
                "rect": {"x1": 2, "y1": max(1, mid - 2), "x2": w - 3, "y2": min(h - 2, mid + 2)},
            }
        )
    elif mdef.map_type in ("wild", "battle", "camp", "pass") or mdef.map_id == "yingchuan_suburb":
        zones.append(
            {
                "id": f"{mdef.map_id}_wild",
                "rect": {"x1": 2, "y1": 2, "x2": w - 3, "y2": h - 3},
            }
        )
    return zones


def find_walkable_at(lines: list[str], pref_x: int, pref_y: int) -> tuple[int, int]:
    h = len(lines)
    w = len(lines[0])
    if 0 <= pref_y < h and 0 <= pref_x < w and lines[pref_y][pref_x] in WALKABLE:
        return pref_x, pref_y
    return find_walkable_near(lines, pref_x, pref_y, 0)


def find_walkable_near(lines: list[str], cx: int, cy: int, dx: int) -> tuple[int, int]:
    h = len(lines)
    w = len(lines[0])
    for radius in range(0, max(w, h)):
        for y in range(max(1, cy - radius), min(h - 1, cy + radius + 1)):
            for x in range(max(1, cx - radius), min(w - 1, cx + radius + 1)):
                if lines[y][x] in WALKABLE and x >= cx + dx - 2:
                    return x, y
    return max(1, min(w - 2, cx + dx)), max(1, min(h - 2, cy))


def visible_enemies_for(mdef: MapDef, lines: list[str] | None = None) -> list[dict]:
    cx = mdef.width // 2
    cy = mdef.height // 2
    if mdef.map_id == "yingchuan_suburb":
        pos1 = find_walkable_near(lines or [], cx, cy, -4) if lines else (6, 14)
        pos2 = find_walkable_near(lines or [], cx, cy, 4) if lines else (20, 8)
        return [
            {
                "id": "yingchuan_wolf_1",
                "templateId": "wild_wolf",
                "x": pos1[0],
                "y": pos1[1],
                "chaseRange": 5,
                "loseRange": 8,
            },
            {
                "id": "yingchuan_wolf_2",
                "templateId": "wild_wolf",
                "x": pos2[0],
                "y": pos2[1],
                "chaseRange": 5,
                "loseRange": 8,
            },
        ]
    if mdef.map_type != "camp":
        return []
    template = "wild_wolf"
    if "rebel" in mdef.map_id or "yingchuan" in mdef.map_id or "nanyang" in mdef.map_id:
        template = "shadow_wisp"
    pos1 = find_walkable_near(lines or [], cx, cy, -3) if lines else (max(2, cx - 4), cy)
    return [
        {
            "id": f"{mdef.map_id}_guard_1",
            "templateId": template,
            "x": pos1[0],
            "y": pos1[1],
            "chaseRange": 6,
            "loseRange": 10,
        }
    ]


def encounter_settings(mdef: MapDef, lines: list[str] | None = None) -> dict:
    visible = visible_enemies_for(mdef, lines)
    if visible:
        return {
            "maxVisibleEnemies": min(5, len(visible)),
            "maxDarkEnemies": 3 if mdef.danger < 3 else 4,
        }
    if mdef.map_type in ("city", "suburb"):
        return {}
    if mdef.map_type in ("battle", "camp"):
        return {"maxVisibleEnemies": 2, "maxDarkEnemies": 4}
    if mdef.danger >= 3:
        return {"maxVisibleEnemies": 1, "maxDarkEnemies": 4}
    if mdef.danger >= 2:
        return {"maxVisibleEnemies": 1, "maxDarkEnemies": 3}
    return {}


def build_teleports(
    maps: dict[str, MapDef],
    exits_by_map: dict[str, list[ExitPoint]],
    connections: list[Connection],
) -> dict[str, dict]:
    exit_lookup: dict[tuple[str, str, str], tuple[int, int]] = {}
    for src, exits in exits_by_map.items():
        for ep in exits:
            exit_lookup[(src, ep.direction, ep.target)] = (ep.x, ep.y)

    teleports: dict[str, dict] = {}
    for conn in connections:
        if conn.source not in maps or conn.target not in maps:
            continue
        opp = OPPOSITE.get(conn.direction)
        if not opp:
            continue
        src_pos = exit_lookup.get((conn.source, conn.direction, conn.target))
        dst_pos = exit_lookup.get((conn.target, opp, conn.source))
        if not src_pos or not dst_pos:
            continue
        sx, sy = src_pos
        dx, dy = dst_pos
        teleports[f"{conn.source}:{sx},{sy}"] = {"map": conn.target, "x": dx, "y": dy}
    return teleports


def validate(maps: dict[str, MapDef], lines_by_id: dict[str, list[str]], teleports: dict) -> None:
    errors = []
    for key, target in teleports.items():
        src, coords = key.split(":", 1)
        x_str, y_str = coords.split(",")
        x, y = int(x_str), int(y_str)
        lines = lines_by_id[src]
        if src not in lines_by_id:
            errors.append(f"missing map {src}")
            continue
        if y >= len(lines) or x >= len(lines[0]):
            errors.append(f"OOB teleport {key}")
            continue
        if lines[y][x] not in WALKABLE:
            errors.append(f"non-walkable teleport {key} tile={lines[y][x]}")
        tgt = target["map"]
        tx, ty = target["x"], target["y"]
        tlines = lines_by_id.get(tgt)
        if not tlines:
            errors.append(f"missing target map {tgt}")
            continue
        if ty >= len(tlines) or tx >= len(tlines[0]):
            errors.append(f"OOB target {tgt}:{tx},{ty}")
            continue
        if tlines[ty][tx] not in WALKABLE:
            errors.append(f"non-walkable target {tgt}:{tx},{ty}")

    # bidirectional check (unique keys only)
    for key, target in teleports.items():
        rev = f"{target['map']}:{target['x']},{target['y']}"
        back = teleports.get(rev)
        src_map = key.split(":")[0]
        if not back:
            errors.append(f"missing reverse teleport for {key}")
        elif back["map"] != src_map:
            errors.append(f"mismatched reverse for {key} -> {rev} points to {back['map']}")

    if errors:
        print("\n".join(errors[:50]), file=sys.stderr)
        if len(errors) > 50:
            print(f"... and {len(errors)-50} more", file=sys.stderr)
        raise SystemExit(f"Validation failed with {len(errors)} errors")


def npcs_for_maps(
    maps: dict[str, MapDef], lines_by_id: dict[str, list[str]]
) -> dict[str, list[dict]]:
    npcs: dict[str, list[dict]] = {}
    for map_id, (name, hx, hy) in HOSPITAL_CITIES.items():
        if map_id not in maps:
            continue
        lines = lines_by_id.get(map_id)
        if lines:
            hx, hy = find_walkable_at(lines, hx, hy)
        npcs[map_id] = [
            {
                "id": f"hospital_{map_id}",
                "name": name,
                "x": hx,
                "y": hy,
                "spriteKey": "healer",
            }
        ]
    if "xuchang" in maps:
        lines = lines_by_id.get("xuchang", [])
        ex, ey = find_walkable_at(lines, 12, 10) if lines else (12, 10)
        npcs.setdefault("xuchang", []).append(
            {"id": "xuchang_elder", "name": "鄉老", "x": ex, "y": ey, "spriteKey": "elder"}
        )
    if "yingchuan_suburb" in maps:
        lines = lines_by_id.get("yingchuan_suburb", [])
        sx, sy = find_walkable_at(lines, 10, 6) if lines else (10, 6)
        npcs.setdefault("yingchuan_suburb", []).append(
            {"id": "yingchuan_scout", "name": "斥候", "x": sx, "y": sy, "spriteKey": "merchant"}
        )
    return npcs


def symmetrize_connections(connections: list[Connection]) -> list[Connection]:
    existing = {(c.source, c.direction, c.target) for c in connections}
    extra: list[Connection] = []
    for c in connections:
        opp = OPPOSITE.get(c.direction)
        if not opp:
            continue
        rev = (c.target, opp, c.source)
        if rev not in existing:
            extra.append(Connection(c.target, opp, c.source))
            existing.add(rev)
    return connections + extra


def main() -> None:
    maps, connections = parse_world_map_md()
    connections = symmetrize_connections(connections)
    # Fill missing sizes
    for mid, mdef in list(maps.items()):
        if mdef.width <= 0 or mdef.height <= 0:
            w, h = default_size(mid, mdef.map_type)
            maps[mid] = MapDef(mid, mdef.name, mdef.map_type, mdef.danger, w, h)

    exits_by_map = assign_exits(maps, connections)
    lines_by_id: dict[str, list[str]] = {}
    maps_json_maps: dict = {}

    for mid, mdef in sorted(maps.items()):
        exits = exits_by_map.get(mid, [])
        lines = generate_ascii(mdef, exits)
        lines_by_id[mid] = lines

        entry: dict = {"file": f"{mid}.txt", "name": mdef.name}
        entry.update(encounter_settings(mdef, lines))
        dz = danger_zones_for(mdef, lines)
        if dz:
            entry["dangerZones"] = dz
        ve = visible_enemies_for(mdef, lines)
        if ve:
            entry["visibleEnemies"] = ve
        maps_json_maps[mid] = entry

    teleports = build_teleports(maps, exits_by_map, connections)
    validate(maps, lines_by_id, teleports)

    config = {
        "defaultMap": "xuchang",
        "maps": maps_json_maps,
        "teleports": teleports,
        "npcs": npcs_for_maps(maps, lines_by_id),
    }

    for out_dir in (SERVER_MAPS, CLIENT_MAPS):
        out_dir.mkdir(parents=True, exist_ok=True)
        for mid, lines in lines_by_id.items():
            (out_dir / f"{mid}.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")
        (out_dir / "maps.json").write_text(
            json.dumps(config, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )

    # Remove legacy maps
    for legacy in ("village.txt", "forest.txt"):
        for out_dir in (SERVER_MAPS, CLIENT_MAPS):
            p = out_dir / legacy
            if p.exists():
                p.unlink()

    print(f"Generated {len(maps)} maps, {len(teleports)} teleports")
    print(f"Output: {SERVER_MAPS} and {CLIENT_MAPS}")
    write_migration_sql(maps, config)


def write_migration_sql(maps: dict[str, MapDef], config: dict) -> None:
    migration = ROOT / "server/src/main/resources/db/migration/V31__world_map.sql"
    dark_maps = [
        mid for mid, entry in config["maps"].items() if entry.get("dangerZones")
    ]
    visible_maps = [
        mid for mid, entry in config["maps"].items() if entry.get("visibleEnemies")
    ]

    lines = [
        "-- World map rollout: 184-280 Three Kingdoms geography",
        "",
        "UPDATE users SET player_map_id = 'xuchang', player_x = 8, player_y = 8",
        "WHERE player_map_id IN ('village', 'forest');",
        "",
        "ALTER TABLE users ALTER COLUMN player_map_id SET DEFAULT 'xuchang';",
        "",
    ]

    hospital_rows = []
    npc_rows = []
    dialogue_rows = []
    for map_id, npc_list in config["npcs"].items():
        for npc in npc_list:
            if not npc["id"].startswith("hospital_"):
                continue
            name = npc["name"]
            hx, hy = npc["x"], npc["y"]
            hid = npc["id"]
            hospital_rows.append(
                f"    ('{hid}', '{map_id}', '{name}', {hx}, {hy}, {hx}, {hy + 1})"
            )
            npc_rows.append(
                f"    ('{hid}', '{map_id}', {hx}, {hy}, '{name}', 'healer', 'root', 'hospital')"
            )
            dialogue_rows.append(
                f"    ('{hid}', 'root', '歡迎來到{name}。受傷的旅人與夥伴都可以在這裡休養。', "
                f"'[{{\"text\":\"治療夥伴\",\"action\":\"hospital_revive\"}},{{\"text\":\"離開\",\"nextKey\":null}}]')"
            )

    story_npcs = []
    for map_id, npc_list in config["npcs"].items():
        for npc in npc_list:
            if npc["id"] in ("xuchang_elder", "yingchuan_scout"):
                story_npcs.append(
                    f"    ('{npc['id']}', '{map_id}', {npc['x']}, {npc['y']}, "
                    f"'{npc['name']}', '{npc['spriteKey']}', 'root', 'default')"
                )

    lines.append("INSERT INTO hospitals (id, map_id, name, npc_grid_x, npc_grid_y, respawn_x, respawn_y) VALUES")
    lines.append(",\n".join(hospital_rows) + ";")
    lines.append("")
    lines.append("INSERT INTO npcs (id, map_id, grid_x, grid_y, name, sprite_key, root_node_key, role) VALUES")
    lines.append(",\n".join(npc_rows) + ";")
    lines.append("")
    if story_npcs:
        lines.extend([
            "INSERT INTO npcs (id, map_id, grid_x, grid_y, name, sprite_key, root_node_key, role) VALUES",
            ",\n".join(story_npcs) + ";",
            "",
        ])
    lines.extend([
        "UPDATE quests SET giver_npc_id = 'xuchang_elder' WHERE giver_npc_id = 'village_elder';",
        "UPDATE quests SET giver_npc_id = 'yingchuan_scout' WHERE giver_npc_id = 'forest_merchant';",
        "UPDATE shop_items SET npc_id = 'yingchuan_scout' WHERE npc_id = 'forest_merchant';",
        "UPDATE shop_items SET npc_id = 'xuchang_elder' WHERE npc_id = 'village_elder';",
        "",
        "DELETE FROM dialogue_nodes WHERE npc_id IN (",
        "    'village_elder', 'forest_merchant', 'village_healer', 'forest_healer'",
        ");",
        "DELETE FROM npcs WHERE id IN (",
        "    'village_elder', 'forest_merchant', 'village_healer', 'forest_healer'",
        ");",
        "DELETE FROM hospitals WHERE id IN ('village_hospital', 'forest_hospital');",
        "",
        "INSERT INTO dialogue_nodes (npc_id, node_key, text, choices_json) VALUES",
        ",\n".join(dialogue_rows) + ";",
        "",
    ])

    lines.extend([
        "INSERT INTO dialogue_nodes (npc_id, node_key, text, choices_json) VALUES",
        "    ('xuchang_elder', 'root',",
        "     '許縣近来不太平靜，黃巾賊在潁川一帶蠢動。旅人，需要幫忙嗎？',",
        "     '[{\"text\":\"我想接任務\",\"nextKey\":\"quest_offer\"},{\"text\":\"再見\",\"nextKey\":null}]'),",
        "    ('xuchang_elder', 'quest_offer',",
        "     '潁川郊野有野狼出沒，能幫忙清除三隻嗎？',",
        "     '[{\"text\":\"接受任務（初試身手）\",\"nextKey\":\"quest_accepted\",\"questAccept\":1},{\"text\":\"暫時不需要\",\"nextKey\":\"root\"}]'),",
        "    ('xuchang_elder', 'quest_accepted',",
        "     '請前往潁川郊野，小心行事。',",
        "     '[{\"text\":\"好的，出發\",\"nextKey\":null}]'),",
        "    ('xuchang_elder', 'quest_already',",
        "     '你已接下任務，加油！',",
        "     '[{\"text\":\"明白了\",\"nextKey\":null}]'),",
        "    ('xuchang_elder', 'quest_complete',",
        "     '做得好！這是你的報酬。',",
        "     '[{\"text\":\"領取報酬\",\"nextKey\":null,\"questComplete\":1}]'),",
        "    ('yingchuan_scout', 'root',",
        "     '潁川方向傳來黃巾旗號，這裡需要更多補給。',",
        "     '[{\"text\":\"我想接任務\",\"nextKey\":\"quest_offer\"},{\"text\":\"看看商品\",\"action\":\"open_shop\"},{\"text\":\"離開\",\"nextKey\":null}]'),",
        "    ('yingchuan_scout', 'quest_offer',",
        "     '賊營中有黑霧精靈出沒，能幫忙消滅兩隻嗎？',",
        "     '[{\"text\":\"接受任務（黑霧調查）\",\"nextKey\":\"quest_accepted\",\"questAccept\":2},{\"text\":\"暫時不需要\",\"nextKey\":\"root\"}]'),",
        "    ('yingchuan_scout', 'quest_accepted',",
        "     '賊營在潁川城北，請小心。',",
        "     '[{\"text\":\"出發\",\"nextKey\":null}]'),",
        "    ('yingchuan_scout', 'quest_already',",
        "     '任務尚未完成，加油。',",
        "     '[{\"text\":\"明白\",\"nextKey\":null}]'),",
        "    ('yingchuan_scout', 'quest_complete',",
        "     '辛苦了，這是酬勞。',",
        "     '[{\"text\":\"領取報酬\",\"nextKey\":null,\"questComplete\":2}]');",
        "",
    ])

    lines.append("DELETE FROM monster_template_spawn_maps WHERE map_id IN ('village', 'forest');")
    lines.append("")
    lines.append("INSERT INTO monster_template_spawn_maps (template_id, map_id) VALUES")

    spawn_values = []
    for mid in sorted(set(dark_maps)):
        spawn_values.append(f"    ('wild_wolf', '{mid}')")
        if maps[mid].danger >= 3:
            spawn_values.append(f"    ('shadow_wisp', '{mid}')")
    for mid in sorted(set(visible_maps)):
        spawn_values.append(f"    ('wild_wolf', '{mid}')")
        spawn_values.append(f"    ('shadow_wisp', '{mid}')")

    # dedupe while preserving order
    seen = set()
    unique_spawns = []
    for row in spawn_values:
        key = row.strip().strip(",").strip("()").replace("'", "").replace(" ", "")
        if key not in seen:
            seen.add(key)
            unique_spawns.append(row)

    lines.append(",\n".join(unique_spawns))
    lines.append("ON CONFLICT DO NOTHING;")
    lines.append("")

    migration.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Wrote migration: {migration}")


if __name__ == "__main__":
    main()
