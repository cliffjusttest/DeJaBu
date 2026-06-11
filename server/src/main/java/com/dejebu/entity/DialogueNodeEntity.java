package com.dejebu.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "dialogue_nodes", uniqueConstraints = @UniqueConstraint(columnNames = {"npc_id", "node_key"}))
public class DialogueNodeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "npc_id", nullable = false, length = 64)
    private String npcId;

    @Column(name = "node_key", nullable = false, length = 64)
    private String nodeKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(name = "choices_json", nullable = false, columnDefinition = "TEXT")
    private String choicesJson = "[]";

    public Long getId() { return id; }
    public String getNpcId() { return npcId; }
    public String getNodeKey() { return nodeKey; }
    public String getText() { return text; }
    public String getChoicesJson() { return choicesJson; }
}
