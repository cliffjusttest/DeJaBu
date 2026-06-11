package com.dejebu.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "npcs")
public class NpcEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "map_id", nullable = false, length = 32)
    private String mapId;

    @Column(name = "grid_x", nullable = false)
    private int gridX;

    @Column(name = "grid_y", nullable = false)
    private int gridY;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(name = "sprite_key", nullable = false, length = 32)
    private String spriteKey = "default";

    @Column(name = "root_node_key", nullable = false, length = 64)
    private String rootNodeKey = "root";

    public String getId() { return id; }
    public String getMapId() { return mapId; }
    public int getGridX() { return gridX; }
    public int getGridY() { return gridY; }
    public String getName() { return name; }
    public String getSpriteKey() { return spriteKey; }
    public String getRootNodeKey() { return rootNodeKey; }
}
