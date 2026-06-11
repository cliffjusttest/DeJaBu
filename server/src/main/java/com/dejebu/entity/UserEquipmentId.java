package com.dejebu.entity;

import com.dejebu.game.EquipmentSlot;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class UserEquipmentId implements Serializable {

    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "slot", length = 16)
    private EquipmentSlot slot;

    public UserEquipmentId() {}

    public UserEquipmentId(Long userId, EquipmentSlot slot) {
        this.userId = userId;
        this.slot = slot;
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public EquipmentSlot getSlot() { return slot; }
    public void setSlot(EquipmentSlot slot) { this.slot = slot; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserEquipmentId that)) return false;
        return Objects.equals(userId, that.userId) && slot == that.slot;
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, slot);
    }
}
