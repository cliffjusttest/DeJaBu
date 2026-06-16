package com.dejebu.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "hospitals")
public class HospitalEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "map_id", nullable = false, length = 32)
    private String mapId;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(name = "npc_grid_x", nullable = false)
    private int npcGridX;

    @Column(name = "npc_grid_y", nullable = false)
    private int npcGridY;

    @Column(name = "respawn_x", nullable = false)
    private int respawnX;

    @Column(name = "respawn_y", nullable = false)
    private int respawnY;

    public String getId() {
        return id;
    }

    public String getMapId() {
        return mapId;
    }

    public String getName() {
        return name;
    }

    public int getNpcGridX() {
        return npcGridX;
    }

    public int getNpcGridY() {
        return npcGridY;
    }

    public int getRespawnX() {
        return respawnX;
    }

    public int getRespawnY() {
        return respawnY;
    }
}
