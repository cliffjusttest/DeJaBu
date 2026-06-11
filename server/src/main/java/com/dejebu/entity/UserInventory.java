package com.dejebu.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_inventory")
public class UserInventory {

    @EmbeddedId
    private UserInventoryId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @MapsId("itemId")
    @JoinColumn(name = "item_id")
    private Item item;

    @Column(nullable = false)
    private int quantity = 1;

    public UserInventory() {}

    public UserInventory(User user, Item item, int quantity) {
        this.user = user;
        this.item = item;
        this.id = new UserInventoryId(user.getId(), item.getId());
        this.quantity = quantity;
    }

    public UserInventoryId getId() { return id; }
    public User getUser() { return user; }
    public Item getItem() { return item; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}
