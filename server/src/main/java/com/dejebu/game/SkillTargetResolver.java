package com.dejebu.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SkillTargetResolver {

    private SkillTargetResolver() {
    }

    public static List<Integer> resolveSlots(int anchorSlot, SkillTargetRange range) {
        validateSlot(anchorSlot);

        return switch (range) {
            case SINGLE -> List.of(anchorSlot);
            case ROW_ADJACENT_THREE -> adjacentThreeInRow(anchorSlot);
            case CROSS -> crossSlots(anchorSlot);
            case ROW -> rowSlots(slotRow(anchorSlot));
            case ALL -> allSlots();
        };
    }

    private static List<Integer> adjacentThreeInRow(int anchorSlot) {
        int row = slotRow(anchorSlot);
        int col = slotColumn(anchorSlot);
        int rowStart = row * BattleUnit.SLOTS_PER_ROW;
        List<Integer> slots = new ArrayList<>();
        for (int offset = -1; offset <= 1; offset++) {
            int targetCol = col + offset;
            if (targetCol < 0 || targetCol >= BattleUnit.SLOTS_PER_ROW) {
                continue;
            }
            slots.add(rowStart + targetCol);
        }
        return Collections.unmodifiableList(slots);
    }

    private static List<Integer> crossSlots(int anchorSlot) {
        int row = slotRow(anchorSlot);
        int col = slotColumn(anchorSlot);
        List<Integer> slots = new ArrayList<>(adjacentThreeInRow(anchorSlot));

        int otherRow = row == 0 ? 1 : 0;
        int verticalSlot = otherRow * BattleUnit.SLOTS_PER_ROW + col;
        if (!slots.contains(verticalSlot)) {
            slots.add(verticalSlot);
        }
        Collections.sort(slots);
        return Collections.unmodifiableList(slots);
    }

    private static List<Integer> rowSlots(int row) {
        int rowStart = row * BattleUnit.SLOTS_PER_ROW;
        List<Integer> slots = new ArrayList<>(BattleUnit.SLOTS_PER_ROW);
        for (int col = 0; col < BattleUnit.SLOTS_PER_ROW; col++) {
            slots.add(rowStart + col);
        }
        return Collections.unmodifiableList(slots);
    }

    private static List<Integer> allSlots() {
        List<Integer> slots = new ArrayList<>(BattleUnit.MAX_UNITS_PER_SIDE);
        for (int slot = 0; slot < BattleUnit.MAX_UNITS_PER_SIDE; slot++) {
            slots.add(slot);
        }
        return Collections.unmodifiableList(slots);
    }

    private static int slotRow(int slot) {
        validateSlot(slot);
        return slot / BattleUnit.SLOTS_PER_ROW;
    }

    private static int slotColumn(int slot) {
        validateSlot(slot);
        return slot % BattleUnit.SLOTS_PER_ROW;
    }

    private static void validateSlot(int slot) {
        if (slot < 0 || slot >= BattleUnit.MAX_UNITS_PER_SIDE) {
            throw new IllegalArgumentException("Invalid battle slot: " + slot);
        }
    }
}
