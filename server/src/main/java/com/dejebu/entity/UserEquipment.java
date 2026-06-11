package com.dejebu.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_equipment")
public class UserEquipment {

    @EmbeddedId
    private UserEquipmentId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    public UserEquipment() {}

    public UserEquipment(User user, Item item) {
        this.id = new UserEquipmentId(user.getId(), item.getSlot());
        this.user = user;
        this.item = item;
    }

    public UserEquipmentId getId() { return id; }

    public User getUser() { return user; }

    public Item getItem() { return item; }
    public void setItem(Item item) {
        this.item = item;
        if (this.id != null) {
            this.id.setSlot(item.getSlot());
        }
    }
}
