package com.dejebu.game;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SkillTargetResolverTest {

    @Test
    void singleReturnsAnchorOnly() {
        assertEquals(List.of(7), SkillTargetResolver.resolveSlots(7, SkillTargetRange.SINGLE));
    }

    @Test
    void rowAdjacentThreeCenteredOnAnchor() {
        assertEquals(List.of(1, 2, 3), SkillTargetResolver.resolveSlots(2, SkillTargetRange.ROW_ADJACENT_THREE));
        assertEquals(List.of(0, 1), SkillTargetResolver.resolveSlots(0, SkillTargetRange.ROW_ADJACENT_THREE));
        assertEquals(List.of(3, 4), SkillTargetResolver.resolveSlots(4, SkillTargetRange.ROW_ADJACENT_THREE));
        assertEquals(List.of(6, 7, 8), SkillTargetResolver.resolveSlots(7, SkillTargetRange.ROW_ADJACENT_THREE));
    }

    @Test
    void crossAddsVerticalNeighbor() {
        assertEquals(List.of(1, 2, 3, 7), SkillTargetResolver.resolveSlots(2, SkillTargetRange.CROSS));
        assertEquals(List.of(2, 6, 7, 8), SkillTargetResolver.resolveSlots(7, SkillTargetRange.CROSS));
    }

    @Test
    void rowReturnsEntireRow() {
        assertEquals(List.of(0, 1, 2, 3, 4), SkillTargetResolver.resolveSlots(2, SkillTargetRange.ROW));
        assertEquals(List.of(5, 6, 7, 8, 9), SkillTargetResolver.resolveSlots(7, SkillTargetRange.ROW));
    }

    @Test
    void allReturnsEverySlot() {
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), SkillTargetResolver.resolveSlots(0, SkillTargetRange.ALL));
    }

    @Test
    void invalidSlotThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> SkillTargetResolver.resolveSlots(10, SkillTargetRange.SINGLE));
    }
}
