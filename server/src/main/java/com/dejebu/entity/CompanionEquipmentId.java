package com.dejebu.entity;

import com.dejebu.game.EquipmentSlot;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class CompanionEquipmentId implements Serializable {

    private Long companionId;

    @Enumerated(EnumType.STRING)
    private EquipmentSlot slot;

    public CompanionEquipmentId() {}

    public CompanionEquipmentId(Long companionId, EquipmentSlot slot) {
        this.companionId = companionId;
        this.slot = slot;
    }

    public Long getCompanionId() { return companionId; }
    public void setCompanionId(Long companionId) { this.companionId = companionId; }

    public EquipmentSlot getSlot() { return slot; }
    public void setSlot(EquipmentSlot slot) { this.slot = slot; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CompanionEquipmentId that)) return false;
        return Objects.equals(companionId, that.companionId) && slot == that.slot;
    }

    @Override
    public int hashCode() { return Objects.hash(companionId, slot); }
}
