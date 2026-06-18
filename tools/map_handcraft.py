"""Hand-tuned ASCII layouts for landmark maps (184–280 AD world)."""

from __future__ import annotations

from typing import Callable

HANDCRAFT_IDS = frozenset(
    {
        "luoyang",
        "changan",
        "chengdu",
        "jiankang",
        "chibi",
        "battlefield_chibi",
        "battlefield_guandu",
        "hangu_pass",
        "longzhong",
        "xuchang",
    }
)


def _set(grid: list[list[str]], x: int, y: int, ch: str) -> None:
    h, w = len(grid), len(grid[0])
    if 0 <= y < h and 0 <= x < w:
        grid[y][x] = ch


def _fill(grid: list[list[str]], x1: int, y1: int, x2: int, y2: int, ch: str) -> None:
    for y in range(min(y1, y2), max(y1, y2) + 1):
        for x in range(min(x1, x2), max(x1, x2) + 1):
            _set(grid, x, y, ch)


def _hline(grid: list[list[str]], y: int, x1: int, x2: int, ch: str) -> None:
    for x in range(min(x1, x2), max(x1, x2) + 1):
        _set(grid, x, y, ch)


def _vline(grid: list[list[str]], x: int, y1: int, y2: int, ch: str) -> None:
    for y in range(min(y1, y2), max(y1, y2) + 1):
        _set(grid, x, y, ch)


def _border(grid: list[list[str]], ch: str = "#") -> None:
    h, w = len(grid), len(grid[0])
    for y in range(h):
        for x in range(w):
            if y in (0, h - 1) or x in (0, w - 1):
                grid[y][x] = ch


def _gate_path(grid: list[list[str]], ex: int, ey: int) -> None:
    h, w = len(grid), len(grid[0])
    cx, cy = w // 2, h // 2
    x, y = ex, ey
    while (x, y) != (cx, cy):
        if x < cx:
            x += 1
        elif x > cx:
            x -= 1
        elif y < cy:
            y += 1
        elif y > cy:
            y -= 1
        if grid[y][x] in ("#", ".", "M"):
            grid[y][x] = "P"


def _apply_exits(grid: list[list[str]], exits) -> None:
    for ep in exits:
        grid[ep.y][ep.x] = "@"
        _gate_path(grid, ep.x, ep.y)


def _imperial_capital(
    grid: list[list[str]],
    exits,
    palace_dx: int,
    palace_dy: int,
    garden_seed: int,
) -> None:
    h, w = len(grid), len(grid[0])
    cx, cy = w // 2, h // 2
    _border(grid)
    wall = 3
    x1, y1 = wall, wall
    x2, y2 = w - wall - 1, h - wall - 1
    exit_cells = {(ep.x, ep.y) for ep in exits}

    for y in range(1, h - 1):
        for x in range(1, w - 1):
            grid[y][x] = "."

    for y in range(y1, y2 + 1):
        for x in range(x1, x2 + 1):
            grid[y][x] = "."

    for x in range(x1, x2 + 1):
        grid[y1][x] = "P" if (x, y1) in exit_cells else "#"
        grid[y2][x] = "P" if (x, y2) in exit_cells else "#"
    for y in range(y1, y2 + 1):
        grid[y][x1] = "P" if (x1, y) in exit_cells else "#"
        grid[y][x2] = "P" if (x2, y) in exit_cells else "#"

    _hline(grid, cy, x1 + 1, x2 - 1, "P")
    _vline(grid, cx, y1 + 1, y2 - 1, "P")
    _hline(grid, cy - h // 6, x1 + 1, x2 - 1, "P")
    _hline(grid, cy + h // 6, x1 + 1, x2 - 1, "P")
    _vline(grid, cx - w // 6, y1 + 1, y2 - 1, "P")
    _vline(grid, cx + w // 6, y1 + 1, y2 - 1, "P")

    px, py = cx + palace_dx, cy + palace_dy
    _fill(grid, px - 5, py - 4, px + 5, py + 4, "#")
    _fill(grid, px - 4, py - 3, px + 4, py + 3, "=")
    _fill(grid, px - 2, py - 1, px + 2, py + 1, "=")
    _hline(grid, py, px - 6, px + 6, "P")

    corners = (
        (x1 + 5, y1 + 4),
        (x2 - 5, y1 + 4),
        (x1 + 5, y2 - 4),
        (x2 - 5, y2 - 4),
    )
    for gx, gy in corners:
        if abs(gx - px) > 7 and abs(gy - py) > 5:
            _fill(grid, gx - 2, gy - 2, gx + 2, gy + 2, "T")

    for y in range(y1 + 2, y2 - 1, 5):
        for x in range(x1 + 2, x2 - 1, 6):
            if grid[y][x] == "." and (x * 3 + y * 7 + garden_seed) % 11 == 0:
                grid[y][x] = "T"

    _apply_exits(grid, exits)


def _handcraft_luoyang(grid: list[list[str]], exits) -> None:
    _imperial_capital(grid, exits, palace_dx=0, palace_dy=-3, garden_seed=1)


def _handcraft_changan(grid: list[list[str]], exits) -> None:
    _imperial_capital(grid, exits, palace_dx=0, palace_dy=0, garden_seed=2)
    h, w = len(grid), len(grid[0])
    _fill(grid, 4, h // 2 - 2, 6, h // 2 + 2, "M")


def _handcraft_chengdu(grid: list[list[str]], exits) -> None:
    _imperial_capital(grid, exits, palace_dx=1, palace_dy=2, garden_seed=3)
    h, w = len(grid), len(grid[0])
    for x in range(w // 4, 3 * w // 4):
        _set(grid, x, h - 4, "W")


def _handcraft_jiankang(grid: list[list[str]], exits) -> None:
    _imperial_capital(grid, exits, palace_dx=-1, palace_dy=1, garden_seed=4)
    h, w = len(grid), len(grid[0])
    for y in range(h // 2, h - 2):
        for x in range(w - 8, w - 2):
            if (x + y) % 3 == 0:
                _set(grid, x, y, "W")


def _handcraft_xuchang(grid: list[list[str]], exits) -> None:
    h, w = len(grid), len(grid[0])
    cx, cy = w // 2, h // 2
    _border(grid)
    x1, y1, x2, y2 = 2, 2, w - 3, h - 3
    exit_cells = {(ep.x, ep.y) for ep in exits}

    for y in range(y1, y2 + 1):
        for x in range(x1, x2 + 1):
            grid[y][x] = "."

    for x in range(x1, x2 + 1):
        grid[y1][x] = "P" if (x, y1) in exit_cells else "#"
        grid[y2][x] = "P" if (x, y2) in exit_cells else "#"
    for y in range(y1, y2 + 1):
        grid[y][x1] = "P" if (x1, y) in exit_cells else "#"
        grid[y][x2] = "P" if (x2, y) in exit_cells else "#"

    _hline(grid, cy, x1 + 1, x2 - 1, "P")
    _vline(grid, cx, y1 + 1, y2 - 1, "P")
    _fill(grid, cx - 2, cy - 1, cx + 2, cy + 1, "=")
    _fill(grid, x1 + 4, cy + 3, x2 - 4, cy + 3, "P")
    for x in range(x1 + 3, x2 - 2, 5):
        _set(grid, x, y1 + 3, "T")
        _set(grid, x, y2 - 3, "T")
    _apply_exits(grid, exits)


def _handcraft_chibi(grid: list[list[str]], exits) -> None:
    h, w = len(grid), len(grid[0])
    _border(grid)
    river = h // 2

    for y in range(1, h - 1):
        for x in range(1, w - 1):
            grid[y][x] = "."

    _hline(grid, river, 1, w - 2, "W")
    _hline(grid, river + 1, 1, w - 2, "W")
    _hline(grid, river - 1, 4, w - 5, "P")
    _vline(grid, w // 2, 1, h - 2, "P")
    _fill(grid, w // 2 - 1, river - 1, w // 2 + 1, river, "=")

    for x in range(2, w // 3):
        for y in range(2, river - 2):
            if x + y < w // 2:
                _set(grid, x, y, "M" if (x + y) % 4 else "T")
    for x in range(2 * w // 3, w - 2):
        for y in range(river + 2, h - 2):
            _set(grid, x, y, "T" if (x + y) % 3 else ".")

    _apply_exits(grid, exits)


def _handcraft_battlefield_chibi(grid: list[list[str]], exits) -> None:
    h, w = len(grid), len(grid[0])
    cx, cy = w // 2, h // 2
    _border(grid)

    for y in range(1, h - 1):
        for x in range(1, w - 1):
            grid[y][x] = "."

    for y in range(h * 2 // 3, h - 1):
        for x in range(1, w - 1):
            grid[y][x] = "W" if y > h * 3 // 4 else "."
    for x in range(1, w // 3):
        for y in range(1, h * 2 // 3):
            grid[y][x] = "M" if (x + y) % 3 else "#"

    _hline(grid, cy - 2, w // 4, 3 * w // 4, "P")
    _fill(grid, cx - 4, cy - 4, cx + 4, cy - 2, "=")
    _fill(grid, cx - 2, cy - 6, cx + 2, cy - 4, "T")
    _fill(grid, w - 8, 3, w - 3, 7, "#")
    _set(grid, w - 6, 5, "P")
    _apply_exits(grid, exits)


def _handcraft_battlefield_guandu(grid: list[list[str]], exits) -> None:
    h, w = len(grid), len(grid[0])
    cx, cy = w // 2, h // 2
    _border(grid)

    for y in range(1, h - 1):
        for x in range(1, w - 1):
            grid[y][x] = "."

    _hline(grid, h - 4, 2, w - 3, "W")
    _hline(grid, h - 5, 2, w - 3, "W")
    _fill(grid, cx - 5, cy - 3, cx + 5, cy + 3, ".")
    _hline(grid, cy, cx - 6, cx + 6, "=")
    _fill(grid, 4, 3, 10, 8, "#")
    _fill(grid, w - 11, 3, w - 5, 8, "#")
    _set(grid, 7, 5, "P")
    _set(grid, w - 8, 5, "P")
    for x in range(3, w - 3, 4):
        _set(grid, x, cy - 5, "T")
        _set(grid, x, cy + 5, "T")
    _apply_exits(grid, exits)


def _handcraft_hangu_pass(grid: list[list[str]], exits) -> None:
    h, w = len(grid), len(grid[0])
    _border(grid, "M")
    mid = h // 2
    gap = max(4, w // 3)

    for y in range(1, h - 1):
        for x in range(1, w - 1):
            grid[y][x] = "M"

    left = w // 2 - gap // 2
    right = left + gap - 1
    for y in range(1, h - 1):
        for x in range(left, right + 1):
            grid[y][x] = "P" if y == mid else "."

    _fill(grid, left - 2, 2, left - 1, h - 3, "#")
    _fill(grid, right + 1, 2, right + 2, h - 3, "#")
    _hline(grid, mid, left, right, "=")
    _set(grid, left - 1, mid, "P")
    _set(grid, right + 1, mid, "P")
    _apply_exits(grid, exits)


def _handcraft_longzhong(grid: list[list[str]], exits) -> None:
    h, w = len(grid), len(grid[0])
    cx, cy = w // 2, h // 2
    _border(grid)

    for y in range(1, h - 1):
        for x in range(1, w - 1):
            grid[y][x] = "T" if (x * 5 + y * 7) % 9 == 0 else "."

    x = 2
    y = h - 3
    while x < w - 2:
        grid[y][x] = "P"
        x += 1
        if x % 5 == 0 and y > 2:
            y -= 1

    _fill(grid, cx - 3, cy - 2, cx + 3, cy + 2, "#")
    _fill(grid, cx - 2, cy - 1, cx + 2, cy + 1, ".")
    _set(grid, cx, cy, ".")
    _set(grid, cx - 1, cy, "T")
    _set(grid, cx + 1, cy, "T")
    _apply_exits(grid, exits)


_HANDCRAFT: dict[str, Callable[[list[list[str]], list], None]] = {
    "luoyang": _handcraft_luoyang,
    "changan": _handcraft_changan,
    "chengdu": _handcraft_chengdu,
    "jiankang": _handcraft_jiankang,
    "xuchang": _handcraft_xuchang,
    "chibi": _handcraft_chibi,
    "battlefield_chibi": _handcraft_battlefield_chibi,
    "battlefield_guandu": _handcraft_battlefield_guandu,
    "hangu_pass": _handcraft_hangu_pass,
    "longzhong": _handcraft_longzhong,
}


def try_handcraft(
    map_id: str, width: int, height: int, exits
) -> list[str] | None:
    fn = _HANDCRAFT.get(map_id)
    if not fn:
        return None
    grid = [["." for _ in range(width)] for _ in range(height)]
    fn(grid, exits)
    return ["".join(row) for row in grid]
