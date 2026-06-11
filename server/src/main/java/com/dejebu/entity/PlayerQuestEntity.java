package com.dejebu.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "player_quests", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "quest_id"}))
public class PlayerQuestEntity {

    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_COMPLETED = "COMPLETED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "quest_id", nullable = false)
    private Long questId;

    @Column(nullable = false, length = 16)
    private String status = STATUS_IN_PROGRESS;

    @Column(nullable = false)
    private int progress = 0;

    public Long getId() { return id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getQuestId() { return questId; }
    public void setQuestId(Long questId) { this.questId = questId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getProgress() { return progress; }
    public void setProgress(int progress) { this.progress = progress; }

    public boolean isReadyToClaim(int requiredCount) {
        return STATUS_IN_PROGRESS.equals(status) && progress >= requiredCount;
    }
}
