package com.dejebu.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserHpTest {

    @Test
    void resolveMaxHpUsesVitality() {
        User user = new User();
        user.setStatVitality(20);

        assertEquals(150, user.resolveMaxHp());
    }

    @Test
    void resolveCurrentHpClampsToMax() {
        User user = new User();
        user.setStatVitality(10);
        user.setPlayerCurrentHp(999);

        assertEquals(100, user.resolveCurrentHp());
    }

    @Test
    void resolveCurrentHpNeverReturnsNegative() {
        User user = new User();
        user.setPlayerCurrentHp(-5);

        assertEquals(0, user.resolveCurrentHp());
    }
}
