package com.dejebu.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

@Entity
@Table(name = "companion_equipment")
public class CompanionEquipment {

    @EmbeddedId
    private CompanionEquipmentId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("companionId")
    @JoinColumn(name = "companion_id")
    private UserCompanion companion;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    public CompanionEquipment() {}

    public CompanionEquipment(UserCompanion companion, Item item) {
        this.companion = companion;
        this.item = item;
        this.id = new CompanionEquipmentId(companion.getId(), item.getSlot());
    }

    public CompanionEquipmentId getId() { return id; }
    public UserCompanion getCompanion() { return companion; }
    public Item getItem() { return item; }
}
