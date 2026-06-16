package com.dejebu.game;

import java.util.HashSet;
import java.util.Set;

public record DangerZone(
        String id,
        Set<DangerZoneCell> cells
) {
    public boolean contains(int x, int y) {
        return cells.contains(new DangerZoneCell(x, y));
    }

    public record DangerZoneCell(int x, int y) {}
}
