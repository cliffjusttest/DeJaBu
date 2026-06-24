package com.dejebu.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "quests")
public class QuestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "quest_type", nullable = false, length = 16)
    private String questType;

    @Column(name = "target_id", nullable = false, length = 64)
    private String targetId;

    @Column(name = "required_count", nullable = false)
    private int requiredCount = 1;

    @Column(name = "reward_exp", nullable = false)
    private int rewardExp = 0;

    @Column(name = "reward_skill_points", nullable = false)
    private int rewardSkillPoints = 0;

    @Column(name = "giver_npc_id", length = 64)
    private String giverNpcId;

    @Column(name = "required_era", nullable = false, length = 4)
    private String requiredEra = "E1";

    @Column(name = "prerequisite_quest_id")
    private Long prerequisiteQuestId;

    @Column(name = "unlocks_era", length = 4)
    private String unlocksEra;

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getQuestType() { return questType; }
    public String getTargetId() { return targetId; }
    public int getRequiredCount() { return requiredCount; }
    public int getRewardExp() { return rewardExp; }
    public int getRewardSkillPoints() { return rewardSkillPoints; }
    public String getGiverNpcId() { return giverNpcId; }
    public String getRequiredEra() { return requiredEra; }
    public Long getPrerequisiteQuestId() { return prerequisiteQuestId; }
    public String getUnlocksEra() { return unlocksEra; }
}
