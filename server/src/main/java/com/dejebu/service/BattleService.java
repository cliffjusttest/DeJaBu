package com.dejebu.service;

import com.dejebu.entity.User;
import com.dejebu.entity.UserCompanion;
import com.dejebu.entity.MonsterTemplateSkill;
import com.dejebu.game.BattleFormation;
import com.dejebu.game.BattleSkillRuntime;
import com.dejebu.game.BattleUnit;
import com.dejebu.game.CharacterStats;
import com.dejebu.game.Element;
import com.dejebu.game.ElementRelation;
import com.dejebu.game.ElementRelation.ElementMatchup;
import com.dejebu.game.EnemyBattleAi;
import com.dejebu.game.ItemType;
import com.dejebu.game.SkillCombatCalculator;
import com.dejebu.game.SkillTargetResolver;
import com.dejebu.game.SkillTargetSide;
import com.dejebu.game.WildMonsterInstance;
import com.dejebu.repository.MonsterTemplateSkillRepository;
import com.dejebu.repository.UserInventoryRepository;
import com.dejebu.repository.UserRepository;
import com.dejebu.service.CompanionService.CaptureResult;
import com.dejebu.service.EncounterService.PendingEncounter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class BattleService {

    private static final int COMBO_AGILITY_THRESHOLD = 15;
    private static final double COMBO_KILL_EXP_BONUS_RATE = 0.1;
    private static final double COMBO_DAMAGE_MULTIPLIER = 1.1;

    private final ObjectMapper objectMapper;
    private final EncounterService encounterService;
    private final CompanionService companionService;
    private final SkillService skillService;
    private final UserRepository userRepository;
    private final ProgressionService progressionService;
    private final MonsterTemplateSkillRepository monsterTemplateSkillRepository;
    private final AuthService authService;
    private final UserInventoryRepository inventoryRepository;
    private final LootService lootService;
    private final BattleDeathService battleDeathService;
    private final Map<String, BattleState> activeBattles = new ConcurrentHashMap<>();

    public BattleService(
            ObjectMapper objectMapper,
            EncounterService encounterService,
            CompanionService companionService,
            SkillService skillService,
            UserRepository userRepository,
            ProgressionService progressionService,
            MonsterTemplateSkillRepository monsterTemplateSkillRepository,
            AuthService authService,
            UserInventoryRepository inventoryRepository,
            LootService lootService,
            BattleDeathService battleDeathService
    ) {
        this.objectMapper = objectMapper;
        this.encounterService = encounterService;
        this.companionService = companionService;
        this.skillService = skillService;
        this.userRepository = userRepository;
        this.progressionService = progressionService;
        this.monsterTemplateSkillRepository = monsterTemplateSkillRepository;
        this.authService = authService;
        this.inventoryRepository = inventoryRepository;
        this.lootService = lootService;
        this.battleDeathService = battleDeathService;
    }

    @Transactional(readOnly = true)
    public ObjectNode startBattle(String sessionId, Long userId, String playerName, Element playerElement, CharacterStats playerStats, int playerLevel) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("玩家不存在"));
        int playerCurrentHp = user.resolveCurrentHp();
        if (playerCurrentHp <= 0) {
            throw new IllegalStateException("體力不足，無法戰鬥");
        }
        int playerCurrentMp = user.resolveCurrentMp();

        PendingEncounter encounter = encounterService.getEncounter(sessionId)
                .orElseThrow(() -> new IllegalStateException("沒有待處理的野外遭遇，請先遭遇野生怪物"));
        encounterService.consumeEncounter(sessionId);
        List<BattleUnit> partyCompanions = companionService.loadPartyBattleUnits(userId, 10);
        List<BattleSkillRuntime> playerSkills = skillService.loadRuntimeSkillsForUser(userId);
        BattleState state = new BattleState(
                sessionId,
                userId,
                playerName,
                playerElement,
                playerStats,
                playerLevel,
                playerCurrentHp,
                playerCurrentMp,
                encounter,
                partyCompanions,
                playerSkills
        );
        equipEnemySkills(state);
        state.consumables.addAll(loadConsumables(userId));
        activeBattles.put(sessionId, state);
        return toBattleSnapshot(state, userId);
    }

    @Transactional(readOnly = true)
    public ObjectNode startPartyBattle(
            String battleId,
            String leaderSessionId,
            List<PartyMemberBattleContext> members,
            PendingEncounter encounter
    ) {
        if (members.isEmpty()) {
            throw new IllegalStateException("組隊戰鬥成員為空");
        }

        List<BattleUnit> allies = new ArrayList<>();
        Map<Long, Integer> userPlayerUnitIds = new HashMap<>();
        int nextUnitId = 1;
        for (PartyMemberBattleContext member : members) {
            int memberPlayerUnitId = nextUnitId++;
            userPlayerUnitIds.put(member.userId(), memberPlayerUnitId);

            BattleUnit playerUnit = BattleUnit.player(
                    memberPlayerUnitId,
                    BattleFormation.multiplayerPlayerSlot(member.partyIndex()),
                    member.playerName(),
                    member.playerElement(),
                    member.playerStats().maxHp(),
                    member.playerCurrentHp(),
                    member.playerStats().maxMp(),
                    member.playerCurrentMp(),
                    member.playerLevel(),
                    member.playerStats()
            );
            playerUnit.setSkills(skillService.loadRuntimeSkillsForUser(member.userId()));
            playerUnit.setOwnerUserId(member.userId());
            allies.add(playerUnit);

            List<BattleUnit> memberCompanions = companionService.loadPartyBattleUnits(
                    member.userId(),
                    nextUnitId,
                    BattleFormation.maxCompanionsPerPlayerInParty(),
                    BattleFormation.multiplayerCompanionSlot(member.partyIndex())
            );
            for (BattleUnit companion : memberCompanions) {
                nextUnitId = Math.max(nextUnitId, companion.getId() + 1);
            }
            allies.addAll(memberCompanions);
        }

        BattleState state = new BattleState(battleId, leaderSessionId, members, encounter, allies, userPlayerUnitIds);
        equipEnemySkills(state);
        for (PartyMemberBattleContext member : members) {
            state.consumablesByUser.put(member.userId(), loadConsumables(member.userId()));
        }
        activeBattles.put(battleId, state);
        return toBattleSnapshot(state, members.get(0).userId());
    }

    public boolean hasActiveBattle(String battleId) {
        return activeBattles.containsKey(battleId);
    }

    public Set<String> getParticipantSessionIds(String battleId) {
        BattleState state = activeBattles.get(battleId);
        if (state == null) {
            return Set.of();
        }
        return new HashSet<>(state.participantSessionIds);
    }

    public ObjectNode getBattleSnapshot(String battleId, Long forUserId) {
        BattleState state = activeBattles.get(battleId);
        if (state == null) {
            return null;
        }
        return toBattleSnapshot(state, forUserId);
    }

    private List<ConsumableInfo> loadConsumables(Long userId) {
        return inventoryRepository.findByUserId(userId).stream()
                .filter(inv -> inv.getItem().getType() == ItemType.CONSUMABLE)
                .map(inv -> new ConsumableInfo(
                        inv.getItem().getId(),
                        inv.getItem().getName(),
                        inv.getItem().getHealHp(),
                        inv.getQuantity()
                ))
                .toList();
    }

    private void equipEnemySkills(BattleState state) {
        for (BattleUnit enemy : state.enemies) {
            if (enemy.getTemplateId() == null) {
                continue;
            }
            enemy.setSkills(loadRuntimeSkillsForTemplate(enemy.getTemplateId()));
        }
    }

    private List<BattleSkillRuntime> loadRuntimeSkillsForTemplate(String templateId) {
        return monsterTemplateSkillRepository.findByTemplateIdOrderBySlotOrderAsc(templateId).stream()
                .map(MonsterTemplateSkill::getSkill)
                .map(skill -> BattleSkillRuntime.from(skill, 1))
                .toList();
    }

    // ── Planning Phase ───────────────────────────────────────────────────────

    @Transactional
    public ObjectNode resolveAction(
            String battleId,
            Long actingUserId,
            String action,
            Integer targetId,
            Integer actorId,
            Long skillId,
            Long itemId
    ) {
        BattleState state = activeBattles.get(battleId);
        if (state == null) {
            throw new IllegalStateException("No active battle for session");
        }
        if (actorId == null || actorId <= 0) {
            throw new IllegalArgumentException("請指定行動單位");
        }

        BattleUnit actor = findUnit(state.allies, actorId)
                .orElseThrow(() -> new IllegalArgumentException("無效的行動單位"));
        if (actor.getOwnerUserId() != null && !actor.getOwnerUserId().equals(actingUserId)) {
            throw new IllegalArgumentException("只能為自己的單位指定行動");
        }
        if (!actor.isAlive()) {
            throw new IllegalArgumentException("該單位已無法戰鬥");
        }
        if (state.pendingActions.containsKey(actorId)) {
            throw new IllegalArgumentException("該單位本回合已指定行動");
        }

        validatePlanAction(state, actor, action, targetId, skillId, itemId);
        state.pendingActions.put(actorId, new PlannedAction(action, targetId, skillId, itemId));
        state.activeActorId = findNextUnplannedActorForUser(state, actingUserId);

        boolean allPlanned = aliveAlliesInActionOrder(state).stream()
                .allMatch(u -> state.pendingActions.containsKey(u.getId()));

        if (!allPlanned) {
            ObjectNode result = objectMapper.createObjectNode();
            result.put("roundExecuted", false);
            result.set("battle", toBattleSnapshot(state, actingUserId));
            return result;
        }

        return executeRound(state, battleId, actingUserId);
    }

    private void validatePlanAction(BattleState state, BattleUnit actor, String action, Integer targetId, Long skillId, Long itemId) {
        switch (action) {
            case "attack" -> {
                if (targetId == null || targetId <= 0) throw new IllegalArgumentException("請選擇攻擊目標");
                findUnit(state.enemies, targetId).orElseThrow(() -> new IllegalArgumentException("無效的攻擊目標"));
            }
            case "skill" -> {
                if (skillId == null || skillId <= 0) throw new IllegalArgumentException("請指定技能");
                BattleSkillRuntime skill = actor.findSkill(skillId);
                if (skill == null) throw new IllegalArgumentException("該單位沒有此技能");
                if (!skill.canUse(actor.getMp())) {
                    if (!skill.isReady()) {
                        throw new IllegalArgumentException("「" + skill.getName() + "」冷卻中");
                    }
                    throw new IllegalArgumentException("MP 不足");
                }
                if (targetId == null || targetId <= 0) throw new IllegalArgumentException("請選擇技能目標");
                List<BattleUnit> pool = skillTargetPool(state, actor, skill);
                findUnit(pool, targetId).orElseThrow(() -> new IllegalArgumentException("無效的技能目標"));
            }
            case "capture" -> {
                if (actor.isCompanion()) throw new IllegalArgumentException("夥伴無法進行捕捉");
                if (targetId == null || targetId <= 0) throw new IllegalArgumentException("請選擇捕捉目標");
                BattleUnit target = findUnit(state.enemies, targetId)
                        .orElseThrow(() -> new IllegalArgumentException("無效的捕捉目標"));
                if (!target.isCapturable()) throw new IllegalArgumentException("此怪物無法捕捉");
            }
            case "item" -> {
                if (itemId == null || itemId <= 0) throw new IllegalArgumentException("請指定道具");
                findConsumableForOwner(state, actor, itemId)
                        .orElseThrow(() -> new IllegalArgumentException("背包中沒有此道具"));
            }
            case "defend", "flee" -> { /* always valid */ }
            default -> throw new IllegalArgumentException("Unknown battle action: " + action);
        }
    }

    // ── Round Execution ──────────────────────────────────────────────────────

    private ObjectNode executeRound(BattleState state, String battleId, Long actingUserId) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        ObjectNode result = objectMapper.createObjectNode();
        result.put("roundExecuted", true);

        detectAndBuildCombos(state, random);

        // All combatants act in agility order (highest first)
        for (BattleUnit unit : buildRoundActionOrder(state)) {
            if (!unit.isAlive() || isBattleOver(state)) break;

            // Combo followers act with their leader; skip their own turn slot
            if (state.comboFollowers.containsKey(unit.getId())) continue;

            ObjectNode actionResult = objectMapper.createObjectNode();
            if (isAllyUnit(state, unit)) {
                PlannedAction plan = state.pendingActions.get(unit.getId());
                if (plan == null) continue;

                List<BattleUnit> followers = getComboFollowers(state, unit);
                if (!followers.isEmpty()) {
                    executeComboAttack(state, unit, followers, plan, random, actionResult, true);
                } else {
                    executeAllyAction(state, unit, plan, random, actionResult);
                }
            } else {
                List<BattleUnit> aliveAllies = state.allies.stream().filter(BattleUnit::isAlive).toList();
                List<BattleUnit> enemyFollowers = getComboFollowers(state, unit);
                if (!enemyFollowers.isEmpty()) {
                    PlannedAction enemyPlan = state.enemyComboPlans.get(unit.getId());
                    if (enemyPlan != null) {
                        executeComboAttack(state, unit, enemyFollowers, enemyPlan, random, actionResult, false);
                    }
                } else {
                    List<BattleUnit> teammates = state.enemies;
                    EnemyBattleAi.EnemyAction action = EnemyBattleAi.chooseAction(unit, teammates, aliveAllies, random);
                    if (action == null) continue;
                    if ("skill".equals(action.type())) {
                        resolveSkill(state, unit, action.skillId(), action.targetId(), random, actionResult, 1.0);
                    } else {
                        BattleUnit defender = findUnit(state.allies, action.targetId()).orElse(null);
                        if (defender != null && defender.isAlive()) {
                            resolveBasicAttack(state, unit, defender, random, actionResult, 1.0, false, false);
                        }
                    }
                }
            }
            mergeResult(result, actionResult);
            if (result.path("escaped").asBoolean()) break;
        }

        state.pendingActions.clear();
        state.comboFollowers.clear();
        state.enemyComboPlans.clear();

        if (result.path("escaped").asBoolean()) {
            state.defendingUnits.clear();
            Set<Long> respawnedPlayerIds = Set.of();
            if (hasDeathConsequences(state)) {
                respawnedPlayerIds = battleDeathService.processDefeatOrEscape(
                        state.participantUserIds,
                        state.allies,
                        state.userPlayerUnitIds,
                        result,
                        true
                ).respawnedPlayerIds();
            }
            syncBattleResources(state, false, respawnedPlayerIds);
            activeBattles.remove(battleId);
            encounterService.clearEncounter(state.encounterKey);
            appendEncounterMeta(result, state);
            result.set("battle", toBattleSnapshot(state, actingUserId));
            return result;
        }

        if (!isBattleOver(state)) {
            tickAllSkillCooldowns(state);
        }

        state.defendingUnits.clear();

        result.put("battleOver", isBattleOver(state));
        if (isBattleOver(state)) {
            if (!hasAliveEnemies(state)) {
                result.put("victory", true);
                ArrayNode killedTemplates = objectMapper.createArrayNode();
                state.enemies.stream()
                        .map(BattleUnit::getTemplateId)
                        .filter(id -> id != null && !id.isEmpty())
                        .forEach(killedTemplates::add);
                result.set("killedTemplateIds", killedTemplates);
                appendVictoryExpResult(result, state);
                appendVictoryLootResult(result, state);
                syncBattleResources(state, true, Set.of());
            } else {
                result.put("victory", false);
                BattleDeathService.ProcessOutcomeResult deathOutcome = battleDeathService.processDefeatOrEscape(
                        state.participantUserIds,
                        state.allies,
                        state.userPlayerUnitIds,
                        result,
                        false
                );
                syncBattleResources(state, false, deathOutcome.respawnedPlayerIds());
            }
            activeBattles.remove(battleId);
            encounterService.clearEncounter(state.encounterKey);
            appendEncounterMeta(result, state);
        }

        result.set("battle", toBattleSnapshot(state, actingUserId));
        return result;
    }

    private void appendEncounterMeta(ObjectNode result, BattleState state) {
        if (state.visibleEnemyId != null) {
            result.put("visibleEnemyId", state.visibleEnemyId);
        }
        result.put("fromDangerZone", state.fromDangerZone);
    }

    // ── Damage Calculation ───────────────────────────────────────────────────

    private record DamageHitResult(int damage, boolean critical) {}

    private CharacterStats statsFor(BattleState state, BattleUnit unit) {
        if (unit.getStats() != null) {
            return unit.getStats();
        }
        return isAllyUnit(state, unit) ? state.playerStats : CharacterStats.zeroBase();
    }

    private DamageHitResult computeBasicAttackHit(
            CharacterStats attackerStats,
            CharacterStats defenderStats,
            Element attackerElement,
            Element defenderElement,
            boolean defending,
            ThreadLocalRandom random,
            double damageMultiplier
    ) {
        int baseDamage = attackerStats.attackDamage();
        boolean critical = attackerStats.rollCritical(random, defenderStats.luck());
        if (critical) {
            baseDamage *= 2;
        }
        int damage = applyElementDamage(baseDamage, attackerElement, defenderElement);
        damage = (int) (damage * damageMultiplier);
        damage = defenderStats.mitigateDamage(damage, defending);
        return new DamageHitResult(damage, critical);
    }

    private DamageHitResult computeSkillHit(
            CharacterStats actorStats,
            CharacterStats defenderStats,
            BattleSkillRuntime skill,
            Element attackElement,
            Element defenderElement,
            boolean defending,
            ThreadLocalRandom random,
            double damageMultiplier
    ) {
        int baseDamage = SkillCombatCalculator.calculateDamage(actorStats, skill, random);
        boolean critical = actorStats.rollCritical(random, defenderStats.luck());
        if (critical) {
            baseDamage *= 2;
        }
        int damage = applyElementDamage(baseDamage, attackElement, defenderElement);
        damage = (int) (damage * damageMultiplier);
        damage = defenderStats.mitigateDamage(damage, defending);
        return new DamageHitResult(damage, critical);
    }

    private void applyBasicAttackHit(
            BattleState state,
            BattleUnit attacker,
            BattleUnit target,
            DamageHitResult hit,
            ObjectNode result,
            boolean comboOverflow,
            boolean recordKillCredit
    ) {
        boolean wasAlive = target.isAlive();
        if (comboOverflow) {
            target.setHpRaw(target.getHp() - hit.damage());
        } else {
            target.setHp(target.getHp() - hit.damage());
            if (recordKillCredit && wasAlive && !target.isAlive()) {
                state.killCredits.computeIfAbsent(target.getId(), k -> new ArrayList<>()).add(attacker.getId());
            }
        }
        appendMessage(result, buildAttackMessage(attacker.getName(), attacker.getElement(), target, hit.damage(), hit.critical()));
        recordAttackEvent(result, attacker.getId(), target.getId(), hit.damage(), hit.critical());
    }

    private DamageHitResult resolveBasicAttack(
            BattleState state,
            BattleUnit attacker,
            BattleUnit target,
            ThreadLocalRandom random,
            ObjectNode result,
            double damageMultiplier,
            boolean comboOverflow,
            boolean recordKillCredit
    ) {
        boolean defending = state.defendingUnits.contains(target.getId());
        DamageHitResult hit = computeBasicAttackHit(
                statsFor(state, attacker),
                statsFor(state, target),
                attacker.getElement(),
                target.getElement(),
                defending,
                random,
                damageMultiplier
        );
        applyBasicAttackHit(state, attacker, target, hit, result, comboOverflow, recordKillCredit);
        return hit;
    }

    private void applySkillDamageHit(
            BattleState state,
            BattleUnit actor,
            BattleUnit target,
            BattleUnit comboTarget,
            DamageHitResult hit,
            ObjectNode result,
            boolean recordKillCredit
    ) {
        boolean comboOverflowTarget = comboTarget != null && target.getId() == comboTarget.getId();
        boolean wasAlive = target.isAlive();
        if (comboOverflowTarget) {
            target.setHpRaw(target.getHp() - hit.damage());
        } else {
            target.setHp(target.getHp() - hit.damage());
            if (recordKillCredit && wasAlive && !target.isAlive()) {
                state.killCredits.computeIfAbsent(target.getId(), k -> new ArrayList<>()).add(actor.getId());
            }
        }
        recordAttackEvent(result, actor.getId(), target.getId(), hit.damage(), hit.critical());
    }

    // ── Combo System ─────────────────────────────────────────────────────────

    private void detectAndBuildCombos(BattleState state, ThreadLocalRandom random) {
        List<BattleUnit> turnOrder = buildRoundActionOrder(state);
        List<BattleUnit> aliveAllies = state.allies.stream().filter(BattleUnit::isAlive).toList();

        // Walk the global agility-sorted turn order, splitting into consecutive same-faction
        // windows. An opposing-faction turn between two units of the same side breaks their
        // window — those units cannot be in the same combo group.
        List<BattleUnit> window = new ArrayList<>();
        Boolean lastWasAlly = null;
        for (BattleUnit unit : turnOrder) {
            boolean isAlly = isAllyUnit(state, unit);
            if (lastWasAlly != null && isAlly != lastWasAlly) {
                flushComboWindow(state, window, lastWasAlly, aliveAllies, random);
                window.clear();
            }
            window.add(unit);
            lastWasAlly = isAlly;
        }
        if (!window.isEmpty() && lastWasAlly != null) {
            flushComboWindow(state, window, lastWasAlly, aliveAllies, random);
        }
    }

    private void flushComboWindow(BattleState state, List<BattleUnit> window, boolean isAllyWindow,
                                  List<BattleUnit> aliveAllies, ThreadLocalRandom random) {
        if (isAllyWindow) {
            detectAllyCombosInWindow(state, window, random);
        } else {
            detectEnemyCombosInWindow(state, window, aliveAllies, random);
        }
    }

    private void detectAllyCombosInWindow(BattleState state, List<BattleUnit> window, ThreadLocalRandom random) {
        Map<Integer, List<BattleUnit>> byTarget = new HashMap<>();
        for (BattleUnit ally : window) {
            if (!ally.isAlive()) continue;
            PlannedAction plan = state.pendingActions.get(ally.getId());
            if (plan == null || !isComboEligiblePlan(state, ally, plan)) continue;
            byTarget.computeIfAbsent(plan.targetId(), k -> new ArrayList<>()).add(ally);
        }
        for (List<BattleUnit> candidates : byTarget.values()) {
            if (candidates.size() < 2) continue;
            slidingPairwiseAllyDetection(state, candidates, random);
        }
    }

    private void detectEnemyCombosInWindow(BattleState state, List<BattleUnit> window,
                                           List<BattleUnit> aliveAllies, ThreadLocalRandom random) {
        if (aliveAllies.isEmpty()) return;
        List<BattleUnit> candidates = window.stream().filter(BattleUnit::isAlive).toList();
        if (candidates.size() < 2) return;
        slidingPairwiseEnemyDetection(state, candidates, aliveAllies, random);
    }

    // Sliding sequential pairwise combo detection for ally candidates (already grouped by target).
    // Failed pair: leader acts alone, partner retries with the next unit.
    // Successful pair: extend group one-by-one, each needing another 50% roll; first failure stops extension.
    private void slidingPairwiseAllyDetection(BattleState state, List<BattleUnit> candidates,
                                              ThreadLocalRandom random) {
        List<BattleUnit> sorted = sortByAgilityDesc(candidates);
        Set<Integer> assigned = new HashSet<>();
        int i = 0;
        while (i < sorted.size()) {
            BattleUnit leader = sorted.get(i);
            if (assigned.contains(leader.getId())) { i++; continue; }
            int j = nextUnassigned(sorted, assigned, i + 1);
            if (j >= sorted.size()) { i++; continue; }
            BattleUnit partner = sorted.get(j);
            if (Math.abs(unitAgility(leader) - unitAgility(partner)) > COMBO_AGILITY_THRESHOLD) { i++; continue; }
            if (random.nextDouble() < comboChance(state, true, partner)) {
                assigned.add(leader.getId());
                assigned.add(partner.getId());
                state.comboFollowers.put(partner.getId(), leader.getId());
                int k = j + 1;
                while (k <= sorted.size()) {
                    k = nextUnassigned(sorted, assigned, k);
                    if (k >= sorted.size()) break;
                    BattleUnit ext = sorted.get(k);
                    if (Math.abs(unitAgility(leader) - unitAgility(ext)) > COMBO_AGILITY_THRESHOLD) break;
                    if (random.nextDouble() < comboChance(state, true, ext)) {
                        assigned.add(ext.getId());
                        state.comboFollowers.put(ext.getId(), leader.getId());
                        k++;
                    } else { break; }
                }
                i = k;
            } else { i++; }
        }
    }

    // Same sliding detection for enemies. No pre-planned targets — each formed group randomly
    // picks a target from alive allies and stores it in enemyComboPlans.
    private void slidingPairwiseEnemyDetection(BattleState state, List<BattleUnit> candidates,
                                               List<BattleUnit> aliveAllies, ThreadLocalRandom random) {
        List<BattleUnit> sorted = sortByAgilityDesc(candidates);
        Set<Integer> assigned = new HashSet<>();
        int i = 0;
        while (i < sorted.size()) {
            BattleUnit leader = sorted.get(i);
            if (assigned.contains(leader.getId())) { i++; continue; }
            int j = nextUnassigned(sorted, assigned, i + 1);
            if (j >= sorted.size()) { i++; continue; }
            BattleUnit partner = sorted.get(j);
            if (Math.abs(unitAgility(leader) - unitAgility(partner)) > COMBO_AGILITY_THRESHOLD) { i++; continue; }
            if (random.nextDouble() < comboChance(state, false, partner)) {
                assigned.add(leader.getId());
                assigned.add(partner.getId());
                state.comboFollowers.put(partner.getId(), leader.getId());
                BattleUnit target = aliveAllies.get(random.nextInt(aliveAllies.size()));
                state.enemyComboPlans.put(leader.getId(), new PlannedAction("attack", target.getId(), null, null));
                int k = j + 1;
                while (k <= sorted.size()) {
                    k = nextUnassigned(sorted, assigned, k);
                    if (k >= sorted.size()) break;
                    BattleUnit ext = sorted.get(k);
                    if (Math.abs(unitAgility(leader) - unitAgility(ext)) > COMBO_AGILITY_THRESHOLD) break;
                    if (random.nextDouble() < comboChance(state, false, ext)) {
                        assigned.add(ext.getId());
                        state.comboFollowers.put(ext.getId(), leader.getId());
                        k++;
                    } else { break; }
                }
                i = k;
            } else { i++; }
        }
    }

    private List<BattleUnit> sortByAgilityDesc(List<BattleUnit> units) {
        return units.stream()
                .sorted(Comparator.comparingInt(BattleService::unitAgility).reversed()
                        .thenComparingInt(BattleUnit::getId))
                .toList();
    }

    private int nextUnassigned(List<BattleUnit> sorted, Set<Integer> assigned, int from) {
        int k = from;
        while (k < sorted.size() && assigned.contains(sorted.get(k).getId())) k++;
        return k;
    }

    private boolean isComboEligiblePlan(BattleState state, BattleUnit ally, PlannedAction plan) {
        if (!"attack".equals(plan.action()) && !"skill".equals(plan.action())) return false;
        // Must target an enemy
        if (plan.targetId() == null) return false;
        if (findUnit(state.enemies, plan.targetId()).isEmpty()) return false;
        // Skills must be individually combo-eligible
        if ("skill".equals(plan.action())) {
            BattleSkillRuntime skill = ally.findSkill(plan.skillId());
            if (skill == null || !skill.isComboEligible() || skill.isSupportSkill()) return false;
            if (!skill.canUse(ally.getMp())) return false;
        }
        return true;
    }

    private List<BattleUnit> getComboFollowers(BattleState state, BattleUnit leader) {
        List<BattleUnit> faction = isAllyUnit(state, leader) ? state.allies : state.enemies;
        return state.comboFollowers.entrySet().stream()
                .filter(e -> e.getValue() == leader.getId())
                .map(e -> findUnit(faction, e.getKey()).orElse(null))
                .filter(u -> u != null)
                .sorted(Comparator.comparingInt(BattleService::unitAgility).reversed()
                        .thenComparingInt(BattleUnit::getId))
                .toList();
    }

    private void executeComboAttack(
            BattleState state,
            BattleUnit leader,
            List<BattleUnit> followers,
            PlannedAction leaderPlan,
            ThreadLocalRandom random,
            ObjectNode result,
            boolean actorIsAlly
    ) {
        BattleUnit comboTarget = resolveComboTarget(state, leaderPlan, actorIsAlly, random, result);
        if (comboTarget == null) {
            return;
        }

        int total = 1 + followers.size();
        String names = buildComboNames(leader, followers);
        appendMessage(result, "【" + total + "人連擊】" + names + " 對 " + comboTarget.getName() + " 發動連擊！");

        resolveComboHit(state, leader, leaderPlan, comboTarget, random, result);
        for (BattleUnit follower : followers) {
            PlannedAction plan = actorIsAlly
                    ? state.pendingActions.get(follower.getId())
                    : new PlannedAction("attack", comboTarget.getId(), null, null);
            if (plan != null) {
                resolveComboHit(state, follower, plan, comboTarget, random, result);
            }
        }

        if (actorIsAlly && !comboTarget.isAlive()) {
            List<Integer> credits = state.killCredits.computeIfAbsent(comboTarget.getId(), k -> new ArrayList<>());
            credits.clear();
            credits.add(leader.getId());
            followers.forEach(f -> credits.add(f.getId()));
            state.comboKillEnemyIds.add(comboTarget.getId());
        }
    }

    private BattleUnit resolveComboTarget(
            BattleState state,
            PlannedAction leaderPlan,
            boolean actorIsAlly,
            ThreadLocalRandom random,
            ObjectNode result
    ) {
        int targetId = leaderPlan.targetId();
        if (actorIsAlly) {
            Optional<BattleUnit> targetOpt = findUnit(state.enemies, targetId);
            if (targetOpt.isEmpty() || !targetOpt.get().isAlive()) {
                targetOpt = pickFallbackEnemy(state);
                if (targetOpt.isEmpty()) {
                    return null;
                }
                appendMessage(result, "連擊目標已被擊倒，轉移至 " + targetOpt.get().getName());
            }
            return targetOpt.get();
        }

        Optional<BattleUnit> targetOpt = findUnit(state.allies, targetId);
        if (targetOpt.isEmpty() || !targetOpt.get().isAlive()) {
            List<BattleUnit> aliveAllies = state.allies.stream().filter(BattleUnit::isAlive).toList();
            if (aliveAllies.isEmpty()) {
                return null;
            }
            return aliveAllies.get(random.nextInt(aliveAllies.size()));
        }
        return targetOpt.get();
    }

    private String buildComboNames(BattleUnit leader, List<BattleUnit> followers) {
        StringBuilder sb = new StringBuilder(leader.getName());
        for (BattleUnit f : followers) sb.append("・").append(f.getName());
        return sb.toString();
    }

    private void resolveComboHit(
            BattleState state,
            BattleUnit actor,
            PlannedAction plan,
            BattleUnit comboTarget,
            ThreadLocalRandom random,
            ObjectNode result
    ) {
        switch (plan.action()) {
            case "attack" -> resolveComboBasicAttack(state, actor, comboTarget, random, result);
            case "skill" -> {
                BattleSkillRuntime skill = actor.findSkill(plan.skillId());
                if (skill == null || !skill.canUse(actor.getMp())) {
                    appendMessage(result, actor.getName() + " 的技能無法使用，跳過行動");
                    return;
                }
                resolveComboSkill(state, actor, skill, comboTarget, random, result);
            }
        }
    }

    private void resolveComboBasicAttack(
            BattleState state,
            BattleUnit actor,
            BattleUnit target,
            ThreadLocalRandom random,
            ObjectNode result
    ) {
        resolveBasicAttack(state, actor, target, random, result, COMBO_DAMAGE_MULTIPLIER, true, false);
    }

    private void resolveComboSkill(
            BattleState state,
            BattleUnit actor,
            BattleSkillRuntime skill,
            BattleUnit comboTarget,
            ThreadLocalRandom random,
            ObjectNode result
    ) {
        List<Integer> affectedSlots = SkillTargetResolver.resolveSlots(comboTarget.getSlot(), skill.getTargetRange());
        CharacterStats actorStats = actor.getStats() != null ? actor.getStats() : state.playerStats;
        Element attackElement = SkillCombatCalculator.resolveAttackElement(skill.getElement(), actor.getElement());

        StringBuilder message = new StringBuilder();
        message.append(actor.getName()).append(" 施放「").append(skill.getName()).append("」");

        int totalDamage = 0;
        for (BattleUnit unit : opponentsOf(state, actor)) {
            if (!affectedSlots.contains(unit.getSlot())) continue;
            boolean defending = state.defendingUnits.contains(unit.getId());
            DamageHitResult hit = computeSkillHit(
                    actorStats,
                    statsFor(state, unit),
                    skill,
                    attackElement,
                    unit.getElement(),
                    defending,
                    random,
                    COMBO_DAMAGE_MULTIPLIER
            );
            applySkillDamageHit(state, actor, unit, comboTarget, hit, result, isAllyUnit(state, actor));
            totalDamage += hit.damage();
        }
        message.append("，造成 ").append(totalDamage).append(" 點傷害");
        actor.consumeMp(skill.getMpCost());
        skill.markUsed();
        appendMessage(result, message.toString());
    }

    // ── Action Execution ──────────────────────────────────────────────────────

    private void executeAllyAction(BattleState state, BattleUnit actor, PlannedAction plan, ThreadLocalRandom random, ObjectNode result) {
        switch (plan.action()) {
            case "attack" -> {
                Optional<BattleUnit> target = findUnit(state.enemies, plan.targetId());
                int resolveTargetId = plan.targetId();
                if (target.isEmpty() || !target.get().isAlive()) {
                    Optional<BattleUnit> fallback = pickFallbackEnemy(state);
                    if (fallback.isEmpty()) {
                        appendMessage(result, actor.getName() + " 的目標已被擊倒，無其他目標");
                        return;
                    }
                    appendMessage(result, actor.getName() + " 的目標已被擊倒，轉移至 " + fallback.get().getName());
                    resolveTargetId = fallback.get().getId();
                }
                resolveAttack(state, actor, resolveTargetId, random, result, 1.0);
            }
            case "skill" -> {
                BattleSkillRuntime skill = actor.findSkill(plan.skillId());
                if (skill == null || !skill.canUse(actor.getMp())) {
                    appendMessage(result, actor.getName() + " 的技能無法使用，跳過行動");
                    return;
                }
                List<BattleUnit> pool = skillTargetPool(state, actor, skill);
                Optional<BattleUnit> target = findUnit(pool, plan.targetId());
                int resolveSkillTargetId = plan.targetId();
                if (!skill.isSupportSkill() && skill.getTargetSide() != SkillTargetSide.ALLY && (target.isEmpty() || !target.get().isAlive())) {
                    Optional<BattleUnit> fallback = pickFallbackEnemy(state);
                    if (fallback.isEmpty()) {
                        appendMessage(result, actor.getName() + " 的技能目標已被擊倒，無其他目標");
                        return;
                    }
                    appendMessage(result, actor.getName() + " 的技能目標已被擊倒，轉移至 " + fallback.get().getName());
                    resolveSkillTargetId = fallback.get().getId();
                }
                resolveSkill(state, actor, plan.skillId(), resolveSkillTargetId, random, result, 1.0);
            }
            case "capture" -> {
                Optional<BattleUnit> target = findUnit(state.enemies, plan.targetId());
                if (target.isEmpty() || !target.get().isAlive()) {
                    appendMessage(result, actor.getName() + " 的捕捉目標已被擊倒，跳過行動");
                    return;
                }
                resolveCapture(state, actor, plan.targetId(), random, result);
            }
            case "defend" -> {
                state.defendingUnits.add(actor.getId());
                appendMessage(result, actor.getName() + " 採取防禦姿態");
            }
            case "flee" -> resolveFlee(state, actor, random, result);
            case "item" -> resolveItemUse(state, actor, plan.itemId(), result);
        }
    }

    private void resolveItemUse(BattleState state, BattleUnit actor, Long itemId, ObjectNode result) {
        Long ownerUserId = actor.getOwnerUserId() != null ? actor.getOwnerUserId() : state.userId;
        List<ConsumableInfo> ownerConsumables = state.multiplayer
                ? state.consumablesByUser.getOrDefault(ownerUserId, List.of())
                : state.consumables;

        ConsumableInfo info = ownerConsumables.stream()
                .filter(c -> c.itemId() == itemId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("背包中沒有此道具"));

        int healed = 0;
        if (info.healHp() > 0) {
            int before = actor.getHp();
            actor.setHp(Math.min(actor.getMaxHp(), actor.getHp() + info.healHp()));
            healed = actor.getHp() - before;
        }

        // Update in-memory cache
        if (state.multiplayer) {
            List<ConsumableInfo> updated = new ArrayList<>(ownerConsumables);
            updated.remove(info);
            if (info.quantity() > 1) {
                updated.add(new ConsumableInfo(info.itemId(), info.name(), info.healHp(), info.quantity() - 1));
            }
            state.consumablesByUser.put(ownerUserId, updated);
        } else {
            state.consumables.remove(info);
            if (info.quantity() > 1) {
                state.consumables.add(new ConsumableInfo(info.itemId(), info.name(), info.healHp(), info.quantity() - 1));
            }
        }

        // Persist inventory change
        inventoryRepository.findByUserIdAndItemId(ownerUserId, itemId)
                .stream().findFirst()
                .ifPresent(inv -> {
                    if (inv.getQuantity() <= 1) {
                        inventoryRepository.delete(inv);
                    } else {
                        inv.setQuantity(inv.getQuantity() - 1);
                    }
                });

        String msg = healed > 0
                ? actor.getName() + " 使用了「" + info.name() + "」，恢復了 " + healed + " 點 HP"
                : actor.getName() + " 使用了「" + info.name() + "」";
        appendMessage(result, msg);
    }


    // ── Action Resolvers ─────────────────────────────────────────────────────

    private void resolveFlee(BattleState state, BattleUnit actor, ThreadLocalRandom random, ObjectNode result) {
        CharacterStats fleeStats = actor.getStats() != null ? actor.getStats() : state.playerStats;
        int enemyMaxAgility = 0;
        int enemyMaxLuck = 0;
        int enemyMaxLevel = 0;
        for (BattleUnit enemy : state.enemies) {
            if (!enemy.isAlive()) {
                continue;
            }
            CharacterStats enemyStats = enemy.getStats();
            if (enemyStats != null) {
                enemyMaxAgility = Math.max(enemyMaxAgility, enemyStats.agility());
                enemyMaxLuck = Math.max(enemyMaxLuck, enemyStats.luck());
            }
            enemyMaxLevel = Math.max(enemyMaxLevel, enemy.getLevel());
        }
        boolean escaped = random.nextDouble() < fleeStats.fleeChance(
                actor.getLevel(), enemyMaxAgility, enemyMaxLuck, enemyMaxLevel
        );
        result.put("escaped", escaped);
        appendMessage(result, escaped
                ? actor.getName() + " 帶領隊伍成功逃脫！"
                : actor.getName() + " 嘗試逃跑失敗！");
    }

    private void resolveCapture(
            BattleState state,
            BattleUnit actor,
            Integer targetId,
            ThreadLocalRandom random,
            ObjectNode result
    ) {
        BattleUnit target = findUnit(state.enemies, targetId)
                .orElseThrow(() -> new IllegalArgumentException("無效的捕捉目標"));
        if (!target.isAlive()) {
            throw new IllegalArgumentException("該目標已無法戰鬥");
        }

        User user = userRepository.findById(
                actor.getOwnerUserId() != null ? actor.getOwnerUserId() : state.userId
        )
                .orElseThrow(() -> new IllegalStateException("玩家不存在"));

        int capturePlayerLevel = state.multiplayer
                ? state.userLevels.getOrDefault(user.getId(), state.playerLevel)
                : state.playerLevel;
        CharacterStats capturePlayerStats = state.multiplayer
                ? state.userStats.getOrDefault(user.getId(), state.playerStats)
                : state.playerStats;

        CaptureResult captureResult = companionService.evaluateCapture(
                capturePlayerLevel,
                capturePlayerStats,
                target.getLevel(),
                target.getHp(),
                target.getMaxHp(),
                random
        );

        result.put("captureSuccess", captureResult.success());
        if (captureResult.success()) {
            WildMonsterInstance snapshot = state.toWildSnapshot(target);
            snapshot.setHp(target.getHp());
            UserCompanion companion = companionService.captureWildMonster(user, snapshot);
            state.enemies.removeIf(unit -> unit.getId() == targetId);
            result.put("companionId", companion.getId());
            result.put("companionName", companion.getNickname());
            result.put("companionLevel", companion.getLevel());
            appendMessage(result, captureResult.message() + "！「" + companion.getNickname() + "」加入了你的夥伴！");
        } else {
            appendMessage(result, captureResult.message() + "，浪費了一回合！");
        }
    }

    private void resolveAttack(
            BattleState state,
            BattleUnit actor,
            Integer targetId,
            ThreadLocalRandom random,
            ObjectNode result,
            double damageMultiplier
    ) {
        BattleUnit target = findUnit(state.enemies, targetId)
                .orElseThrow(() -> new IllegalArgumentException("無效的攻擊目標"));
        if (!target.isAlive()) {
            throw new IllegalArgumentException("該目標已無法戰鬥");
        }

        DamageHitResult hit = resolveBasicAttack(
                state, actor, target, random, result, damageMultiplier, false, true
        );
        result.put("damage", hit.damage());
        result.put("critical", hit.critical());
        result.put("elementMultiplier", ElementRelation.damageMultiplier(actor.getElement(), target.getElement()));
        result.put("elementMatchup", ElementRelation.matchup(actor.getElement(), target.getElement()).name());
    }

    private void resolveSkill(
            BattleState state,
            BattleUnit actor,
            Long skillId,
            Integer targetId,
            ThreadLocalRandom random,
            ObjectNode result,
            double damageMultiplier
    ) {
        BattleSkillRuntime skill = actor.findSkill(skillId);
        if (skill == null) throw new IllegalArgumentException("該單位沒有此技能");
        if (!skill.canUse(actor.getMp())) {
            if (!skill.isReady()) {
                throw new IllegalArgumentException("「" + skill.getName() + "」冷卻中");
            }
            throw new IllegalArgumentException("MP 不足");
        }

        boolean actorIsAlly = isAllyUnit(state, actor);
        List<BattleUnit> targetPool = skillTargetPool(state, actor, skill);
        BattleUnit anchor = findUnit(targetPool, targetId)
                .orElseThrow(() -> new IllegalArgumentException("無效的技能目標"));
        if (!anchor.isAlive() && !skill.isSupportSkill() && skill.getTargetSide() != SkillTargetSide.ALLY) {
            throw new IllegalArgumentException("該目標已無法戰鬥");
        }

        List<Integer> affectedSlots = SkillTargetResolver.resolveSlots(anchor.getSlot(), skill.getTargetRange());
        CharacterStats actorStats = actor.getStats() != null ? actor.getStats() : state.playerStats;
        Element attackElement = SkillCombatCalculator.resolveAttackElement(skill.getElement(), actor.getElement());

        StringBuilder message = new StringBuilder();
        message.append(actor.getName()).append(" 施放「").append(skill.getName()).append("」");

        if (skill.isHealSkill()) {
            int totalHeal = 0;
            for (BattleUnit unit : alliesOf(state, actor)) {
                if (!affectedSlots.contains(unit.getSlot()) || !unit.isAlive()) continue;
                int heal = SkillCombatCalculator.calculateHeal(actorStats, skill, random);
                int before = unit.getHp();
                unit.setHp(Math.min(unit.getMaxHp(), unit.getHp() + heal));
                totalHeal += unit.getHp() - before;
            }
            result.put("heal", totalHeal);
            message.append("，回復 ").append(totalHeal).append(" 點生命");
        } else if (skill.isReviveSkill()) {
            int revivedCount = 0;
            for (BattleUnit unit : alliesOf(state, actor)) {
                if (!affectedSlots.contains(unit.getSlot()) || unit.isAlive()) continue;
                unit.setHp(Math.max(1, unit.getMaxHp() / 4));
                revivedCount++;
            }
            result.put("revived", revivedCount);
            message.append("，復活 ").append(revivedCount).append(" 名單位");
        } else if (skill.isBuffSkill()) {
            int buffedCount = 0;
            for (BattleUnit unit : alliesOf(state, actor)) {
                if (!affectedSlots.contains(unit.getSlot()) || !unit.isAlive()) continue;
                if (unit.hasActiveBuff() || unit.hasBuff(skill.getSkillId())) continue;
                unit.applyBuff(skill.getSkillId());
                buffedCount++;
            }
            result.put("buffed", buffedCount);
            message.append("，施加輔助效果於 ").append(buffedCount).append(" 名單位");
        } else {
            int totalDamage = 0;
            for (BattleUnit unit : opponentsOf(state, actor)) {
                if (!affectedSlots.contains(unit.getSlot()) || !unit.isAlive()) continue;
                boolean defending = state.defendingUnits.contains(unit.getId());
                DamageHitResult hit = computeSkillHit(
                        actorStats,
                        statsFor(state, unit),
                        skill,
                        attackElement,
                        unit.getElement(),
                        defending,
                        random,
                        damageMultiplier
                );
                applySkillDamageHit(state, actor, unit, null, hit, result, actorIsAlly);
                totalDamage += hit.damage();
            }
            result.put("damage", totalDamage);
            message.append("，造成 ").append(totalDamage).append(" 點傷害");
        }

        actor.consumeMp(skill.getMpCost());
        skill.markUsed();
        appendMessage(result, message.toString());
    }

    // ── Experience Distribution ──────────────────────────────────────────────

    private void appendVictoryExpResult(ObjectNode result, BattleState state) {
        Map<Integer, Integer> unitExpMap = computeExpDistribution(state);

        if (state.multiplayer) {
            ArrayNode playerResults = objectMapper.createArrayNode();
            ArrayNode allCompanionResults = objectMapper.createArrayNode();
            for (Long memberId : state.participantUserIds) {
                int playerUnitId = state.userPlayerUnitIds.get(memberId);
                int playerExpGained = unitExpMap.getOrDefault(playerUnitId, 0);
                ProgressionResult playerResult = progressionService.applyVictoryExp(memberId, playerExpGained);
                ObjectNode memberNode = objectMapper.createObjectNode();
                memberNode.put("playerId", memberId);
                memberNode.put("expGained", playerResult.expGained());
                memberNode.put("playerExp", playerResult.playerExp());
                memberNode.put("expToNextLevel", playerResult.expToNextLevel());
                memberNode.put("playerLevel", playerResult.playerLevel());
                memberNode.put("skillPoints", playerResult.skillPoints());
                memberNode.put("statPoints", playerResult.statPoints());
                if (playerResult.leveledUp()) {
                    memberNode.put("leveledUp", true);
                    memberNode.put("previousLevel", playerResult.previousLevel());
                    memberNode.put("levelsGained", playerResult.levelsGained());
                    memberNode.put("skillPointsGained", playerResult.skillPointsGained());
                    memberNode.put("statPointsGained", playerResult.statPointsGained());
                }
                playerResults.add(memberNode);

                for (BattleUnit ally : state.allies) {
                    if (ally.getId() == playerUnitId || !ally.isCompanion()) continue;
                    if (ally.getOwnerUserId() == null || !ally.getOwnerUserId().equals(memberId)) continue;
                    int expGained = unitExpMap.getOrDefault(ally.getId(), 0);
                    if (expGained <= 0) continue;
                    companionService.findPartySlotForBattleUnit(memberId, ally)
                            .flatMap(partySlot -> progressionService.applyExpToCompanion(memberId, partySlot, expGained))
                            .ifPresent(cr -> {
                                ObjectNode node = objectMapper.createObjectNode();
                                node.put("playerId", memberId);
                                node.put("companionId", cr.companionId());
                                node.put("name", cr.name());
                                node.put("expGained", cr.expGained());
                                node.put("previousLevel", cr.previousLevel());
                                node.put("newLevel", cr.newLevel());
                                node.put("leveledUp", cr.leveledUp());
                                allCompanionResults.add(node);
                            });
                }
            }
            result.set("playerExpResults", playerResults);
            if (!allCompanionResults.isEmpty()) {
                result.set("companionExpResults", allCompanionResults);
            }
            return;
        }

        int playerExpGained = unitExpMap.getOrDefault(state.playerUnitId, 0);
        ProgressionResult playerResult = progressionService.applyVictoryExp(state.userId, playerExpGained);

        result.put("expGained", playerResult.expGained());
        result.put("playerExp", playerResult.playerExp());
        result.put("expToNextLevel", playerResult.expToNextLevel());
        result.put("playerLevel", playerResult.playerLevel());
        result.put("skillPoints", playerResult.skillPoints());
        result.put("statPoints", playerResult.statPoints());
        if (playerResult.leveledUp()) {
            result.put("leveledUp", true);
            result.put("previousLevel", playerResult.previousLevel());
            result.put("levelsGained", playerResult.levelsGained());
            result.put("skillPointsGained", playerResult.skillPointsGained());
            result.put("statPointsGained", playerResult.statPointsGained());
        }

        ArrayNode companionArray = objectMapper.createArrayNode();
        for (BattleUnit ally : state.allies) {
            if (ally.getId() == state.playerUnitId || !ally.isCompanion()) continue;
            int partySlot = BattleFormation.battleSlotToPartySlot(ally.getSlot());
            if (partySlot < 0) continue;
            int expGained = unitExpMap.getOrDefault(ally.getId(), 0);
            if (expGained <= 0) continue;
            progressionService.applyExpToCompanion(state.userId, partySlot, expGained)
                    .ifPresent(cr -> {
                        ObjectNode node = objectMapper.createObjectNode();
                        node.put("companionId", cr.companionId());
                        node.put("name", cr.name());
                        node.put("expGained", cr.expGained());
                        node.put("previousLevel", cr.previousLevel());
                        node.put("newLevel", cr.newLevel());
                        node.put("leveledUp", cr.leveledUp());
                        companionArray.add(node);
                    });
        }
        if (!companionArray.isEmpty()) {
            result.set("companionExpResults", companionArray);
        }
    }

    private void appendVictoryLootResult(ObjectNode result, BattleState state) {
        List<String> killedTemplateIds = state.enemies.stream()
                .map(BattleUnit::getTemplateId)
                .filter(id -> id != null && !id.isEmpty())
                .toList();
        if (killedTemplateIds.isEmpty()) {
            return;
        }

        if (state.multiplayer) {
            ArrayNode lootResults = objectMapper.createArrayNode();
            for (Long memberId : state.participantUserIds) {
                LootService.BattleLootResult loot = lootService.grantBattleLoot(
                        memberId, killedTemplateIds, ThreadLocalRandom.current());
                ObjectNode lootNode = objectMapper.createObjectNode();
                lootNode.put("playerId", memberId);
                lootNode.put("goldGained", loot.goldGained());
                lootNode.put("playerGold", loot.playerGold());
                if (!loot.items().isEmpty()) {
                    ArrayNode dropsArray = objectMapper.createArrayNode();
                    for (LootService.DroppedItem drop : loot.items()) {
                        ObjectNode dropNode = objectMapper.createObjectNode();
                        dropNode.put("itemId", drop.itemId());
                        dropNode.put("name", drop.name());
                        dropNode.put("quantity", drop.quantity());
                        dropsArray.add(dropNode);
                    }
                    lootNode.set("itemDrops", dropsArray);
                }
                lootResults.add(lootNode);
            }
            result.set("lootResults", lootResults);
            return;
        }

        LootService.BattleLootResult loot = lootService.grantBattleLoot(
                state.userId, killedTemplateIds, ThreadLocalRandom.current());

        result.put("goldGained", loot.goldGained());
        result.put("playerGold", loot.playerGold());

        if (!loot.items().isEmpty()) {
            ArrayNode dropsArray = objectMapper.createArrayNode();
            for (LootService.DroppedItem drop : loot.items()) {
                ObjectNode dropNode = objectMapper.createObjectNode();
                dropNode.put("itemId", drop.itemId());
                dropNode.put("name", drop.name());
                dropNode.put("quantity", drop.quantity());
                dropsArray.add(dropNode);
            }
            result.set("itemDrops", dropsArray);
        }
    }

    private Map<Integer, Integer> computeExpDistribution(BattleState state) {
        Map<Integer, Integer> unitExpMap = new LinkedHashMap<>();
        for (BattleUnit enemy : state.enemies) {
            if (enemy.isAlive()) continue; // only dead enemies yield exp
            int monsterLevel = Math.max(1, enemy.getLevel());

            for (BattleUnit ally : state.allies) {
                int unitLevel = expUnitLevel(state, ally);
                int monsterExp = ProgressionService.monsterExp(unitLevel, monsterLevel);
                int sharedPart = (int) (monsterExp * 0.75);
                unitExpMap.merge(ally.getId(), sharedPart, Integer::sum);
            }

            List<Integer> killers = state.killCredits.getOrDefault(enemy.getId(), List.of());
            boolean comboKill = state.comboKillEnemyIds.contains(enemy.getId());
            for (int killerId : killers) {
                BattleUnit killer = findAllyById(state, killerId);
                if (killer == null) continue;
                int unitLevel = expUnitLevel(state, killer);
                int monsterExp = ProgressionService.monsterExp(unitLevel, monsterLevel);
                int sharedPart = (int) (monsterExp * 0.75);
                int killerBonus = monsterExp - sharedPart;
                unitExpMap.merge(killerId, killerBonus, Integer::sum);
                if (comboKill) {
                    int comboBonus = (int) (monsterExp * COMBO_KILL_EXP_BONUS_RATE);
                    unitExpMap.merge(killerId, comboBonus, Integer::sum);
                }
            }
        }
        return unitExpMap;
    }

    private static double comboChance(BattleState state, boolean allyCombo, BattleUnit pairingUnit) {
        double ourAvgLevel = averageAliveLevel(allyCombo ? state.allies : state.enemies);
        double enemyAvgLevel = averageAliveLevel(allyCombo ? state.enemies : state.allies);
        double luckTerm = Math.max(0.1, unitLuck(pairingUnit) * 0.001);
        double chance = 0.5 + (ourAvgLevel - enemyAvgLevel) * 0.05 + luckTerm;
        return Math.max(0, Math.min(0.95, chance));
    }

    private static double averageAliveLevel(List<BattleUnit> units) {
        return units.stream().filter(BattleUnit::isAlive).mapToInt(BattleUnit::getLevel).average().orElse(1.0);
    }

    private static int expUnitLevel(BattleState state, BattleUnit ally) {
        if (ally.isCompanion()) {
            return ally.getLevel();
        }
        for (var entry : state.userPlayerUnitIds.entrySet()) {
            if (entry.getValue() == ally.getId()) {
                return state.userLevels.getOrDefault(entry.getKey(), ally.getLevel());
            }
        }
        return state.userLevels.getOrDefault(state.userId, state.playerLevel);
    }

    private static BattleUnit findAllyById(BattleState state, int unitId) {
        for (BattleUnit ally : state.allies) {
            if (ally.getId() == unitId) {
                return ally;
            }
        }
        return null;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void mergeResult(ObjectNode target, ObjectNode source) {
        if (source.has("message") && !source.get("message").asText().isBlank()) {
            appendMessage(target, source.get("message").asText());
        }
        if (source.has("attackEvents") && source.get("attackEvents").isArray()) {
            ArrayNode targetEvents;
            if (target.has("attackEvents") && target.get("attackEvents").isArray()) {
                targetEvents = (ArrayNode) target.get("attackEvents");
            } else {
                targetEvents = objectMapper.createArrayNode();
                target.set("attackEvents", targetEvents);
            }
            for (JsonNode event : source.get("attackEvents")) {
                targetEvents.add(event);
            }
        }
        if (source.path("escaped").asBoolean()) target.put("escaped", true);
        if (source.has("captureSuccess")) {
            target.put("captureSuccess", source.get("captureSuccess").asBoolean());
            if (source.has("companionId")) target.put("companionId", source.get("companionId").asLong());
            if (source.has("companionName")) target.put("companionName", source.get("companionName").asText());
            if (source.has("companionLevel")) target.put("companionLevel", source.get("companionLevel").asInt());
        }
    }

    private int findNextUnplannedActor(BattleState state) {
        return aliveAlliesInActionOrder(state).stream()
                .filter(unit -> !state.pendingActions.containsKey(unit.getId()))
                .map(BattleUnit::getId)
                .findFirst()
                .orElse(state.playerUnitId);
    }

    private void tickAllSkillCooldowns(BattleState state) {
        for (BattleUnit ally : state.allies) ally.tickSkillCooldowns();
        for (BattleUnit enemy : state.enemies) enemy.tickSkillCooldowns();
    }

    private List<BattleUnit> aliveAlliesInActionOrder(BattleState state) {
        return state.allies.stream()
                .filter(BattleUnit::isAlive)
                .sorted(Comparator.comparingInt(BattleService::unitAgility).reversed()
                        .thenComparingInt(BattleUnit::getId))
                .toList();
    }

    private List<BattleUnit> buildRoundActionOrder(BattleState state) {
        List<BattleUnit> all = new ArrayList<>();
        all.addAll(state.allies);
        all.addAll(state.enemies);
        return all.stream()
                .filter(BattleUnit::isAlive)
                .sorted(Comparator.comparingInt(BattleService::unitAgility).reversed()
                        .thenComparingInt(BattleUnit::getId))
                .toList();
    }

    private static int unitAgility(BattleUnit unit) {
        CharacterStats stats = unit.getStats();
        return stats != null ? stats.agility() : 0;
    }

    private static int unitLuck(BattleUnit unit) {
        CharacterStats stats = unit.getStats();
        return stats != null ? stats.luck() : 0;
    }

    private boolean isBattleOver(BattleState state) {
        return !hasAliveAllies(state) || !hasAliveEnemies(state);
    }

    private boolean isAllyUnit(BattleState state, BattleUnit actor) {
        return findUnit(state.allies, actor.getId()).isPresent();
    }

    private List<BattleUnit> alliesOf(BattleState state, BattleUnit actor) {
        return isAllyUnit(state, actor) ? state.allies : state.enemies;
    }

    private List<BattleUnit> opponentsOf(BattleState state, BattleUnit actor) {
        return isAllyUnit(state, actor) ? state.enemies : state.allies;
    }

    private List<BattleUnit> skillTargetPool(BattleState state, BattleUnit actor, BattleSkillRuntime skill) {
        return switch (skill.getTargetSide()) {
            case ALLY -> alliesOf(state, actor);
            case ENEMY -> opponentsOf(state, actor);
            case ANY -> opponentsOf(state, actor);
        };
    }

    private void recordAttackEvent(ObjectNode result, int attackerId, int defenderId, int damage, boolean critical) {
        ArrayNode events;
        if (result.has("attackEvents") && result.get("attackEvents").isArray()) {
            events = (ArrayNode) result.get("attackEvents");
        } else {
            events = objectMapper.createArrayNode();
            result.set("attackEvents", events);
        }
        ObjectNode event = objectMapper.createObjectNode();
        event.put("attackerId", attackerId);
        event.put("defenderId", defenderId);
        event.put("damage", damage);
        event.put("critical", critical);
        events.add(event);
    }

    private int applyElementDamage(int baseDamage, Element attacker, Element defender) {
        double multiplier = ElementRelation.damageMultiplier(attacker, defender);
        return Math.max(1, (int) Math.round(baseDamage * multiplier));
    }

    private String buildAttackMessage(String attackerName, Element attackerElement, BattleUnit defender, int damage, boolean critical) {
        StringBuilder message = new StringBuilder();
        message.append(attackerName).append("（").append(attackerElement.getDisplayName()).append("）攻擊 ")
                .append(defender.getName()).append(" 造成 ").append(damage).append(" 點傷害");
        if (critical) message.append("（暴擊！）");
        ElementMatchup matchup = ElementRelation.matchup(attackerElement, defender.getElement());
        if (matchup == ElementMatchup.ADVANTAGE) message.append("（屬性克制！）");
        else if (matchup == ElementMatchup.DISADVANTAGE) message.append("（屬性被克…）");
        return message.toString();
    }

    private boolean hasDeathConsequences(BattleState state) {
        for (Long userId : state.participantUserIds) {
            int playerUnitId = state.userPlayerUnitIds.get(userId);
            Optional<BattleUnit> playerUnit = findUnit(state.allies, playerUnitId);
            if (playerUnit.isPresent() && playerUnit.get().getHp() <= 0) {
                return true;
            }
        }
        return state.allies.stream()
                .anyMatch(ally -> ally.isCompanion() && ally.getHp() <= 0);
    }

    private void syncBattleResources(BattleState state, boolean victory, Set<Long> respawnedPlayerIds) {
        syncBattleHp(state, victory, respawnedPlayerIds);
        syncBattleMp(state, respawnedPlayerIds);
    }

    private void syncBattleHp(BattleState state, boolean victory, Set<Long> respawnedPlayerIds) {
        if (state.multiplayer) {
            for (Long memberId : state.participantUserIds) {
                if (respawnedPlayerIds.contains(memberId)) {
                    continue;
                }
                int playerUnitId = state.userPlayerUnitIds.get(memberId);
                state.allies.stream()
                        .filter(unit -> unit.getId() == playerUnitId)
                        .findFirst()
                        .ifPresent(playerUnit -> authService.syncPlayerHp(
                                memberId,
                                victory ? Math.max(1, playerUnit.getHp()) : playerUnit.getHp()
                        ));
                companionService.syncPartyHp(memberId, state.allies, playerUnitId, victory);
            }
            return;
        }
        if (!respawnedPlayerIds.contains(state.userId)) {
            state.allies.stream()
                    .filter(unit -> unit.getId() == state.playerUnitId)
                    .findFirst()
                    .ifPresent(playerUnit -> authService.syncPlayerHp(
                            state.userId,
                            victory ? Math.max(1, playerUnit.getHp()) : playerUnit.getHp()
                    ));
        }
        companionService.syncPartyHp(state.userId, state.allies, state.playerUnitId, victory);
    }

    private void syncBattleMp(BattleState state, Set<Long> respawnedPlayerIds) {
        if (state.multiplayer) {
            for (Long memberId : state.participantUserIds) {
                if (respawnedPlayerIds.contains(memberId)) {
                    continue;
                }
                int playerUnitId = state.userPlayerUnitIds.get(memberId);
                state.allies.stream()
                        .filter(unit -> unit.getId() == playerUnitId)
                        .findFirst()
                        .ifPresent(playerUnit -> authService.syncPlayerMp(memberId, playerUnit.getMp()));
                companionService.syncPartyMp(memberId, state.allies, playerUnitId);
            }
            return;
        }
        if (!respawnedPlayerIds.contains(state.userId)) {
            state.allies.stream()
                    .filter(unit -> unit.getId() == state.playerUnitId)
                    .findFirst()
                    .ifPresent(playerUnit -> authService.syncPlayerMp(state.userId, playerUnit.getMp()));
        }
        companionService.syncPartyMp(state.userId, state.allies, state.playerUnitId);
    }

    private void appendMessage(ObjectNode result, String text) {
        if (result.has("message") && !result.get("message").asText().isBlank()) {
            result.put("message", result.get("message").asText() + "；" + text);
        } else {
            result.put("message", text);
        }
    }

    private Optional<ConsumableInfo> findConsumableForOwner(BattleState state, BattleUnit actor, Long itemId) {
        Long ownerId = actor.getOwnerUserId() != null ? actor.getOwnerUserId() : state.userId;
        if (state.multiplayer) {
            return state.consumablesByUser.getOrDefault(ownerId, List.of()).stream()
                    .filter(c -> c.itemId() == itemId)
                    .findFirst();
        }
        return findConsumable(state, itemId);
    }

    private Optional<ConsumableInfo> findConsumable(BattleState state, Long itemId) {
        if (state.multiplayer) {
            for (List<ConsumableInfo> list : state.consumablesByUser.values()) {
                Optional<ConsumableInfo> found = list.stream()
                        .filter(c -> c.itemId() == itemId)
                        .findFirst();
                if (found.isPresent()) {
                    return found;
                }
            }
            return Optional.empty();
        }
        return state.consumables.stream()
                .filter(c -> c.itemId() == itemId)
                .findFirst();
    }

    private Optional<BattleUnit> findUnit(List<BattleUnit> units, int id) {
        return units.stream().filter(unit -> unit.getId() == id).findFirst();
    }

    private Optional<BattleUnit> pickFallbackEnemy(BattleState state) {
        return state.enemies.stream()
                .filter(BattleUnit::isAlive)
                .min(Comparator.comparingInt(BattleUnit::getSlot));
    }

    private boolean hasAliveAllies(BattleState state) {
        return state.allies.stream().anyMatch(BattleUnit::isAlive);
    }

    private boolean hasAliveEnemies(BattleState state) {
        return state.enemies.stream().anyMatch(BattleUnit::isAlive);
    }

    // ── Battle Snapshot ──────────────────────────────────────────────────────

    private ObjectNode toBattleSnapshot(BattleState state, Long forUserId) {
        ObjectNode battle = objectMapper.createObjectNode();
        battle.put("multiplayer", state.multiplayer);
        battle.put("playerName", state.playerName);
        battle.put("playerLevel", state.playerLevel);
        battle.put("playerElement", state.playerElement.getCode());
        battle.put("playerElementName", state.playerElement.getDisplayName());
        battle.set("playerStats", state.playerStats.toJsonNode(objectMapper));
        battle.set("allies", unitsToJson(state.allies));
        battle.set("enemies", unitsToJson(state.enemies));

        Long viewUserId = forUserId != null ? forUserId : state.userId;
        if (state.multiplayer && viewUserId != null) {
            battle.put("playerName", state.userNames.getOrDefault(viewUserId, state.playerName));
            battle.put("playerLevel", state.userLevels.getOrDefault(viewUserId, state.playerLevel));
            Element viewElement = state.userElements.get(viewUserId);
            if (viewElement != null) {
                battle.put("playerElement", viewElement.getCode());
                battle.put("playerElementName", viewElement.getDisplayName());
            }
            CharacterStats viewStats = state.userStats.get(viewUserId);
            if (viewStats != null) {
                battle.set("playerStats", viewStats.toJsonNode(objectMapper));
            }
        }

        int playerUnitId = state.multiplayer && viewUserId != null
                ? state.userPlayerUnitIds.getOrDefault(viewUserId, state.playerUnitId)
                : state.playerUnitId;
        BattleUnit playerUnit = state.allies.stream()
                .filter(unit -> unit.getId() == playerUnitId)
                .findFirst()
                .orElse(state.allies.get(0));
        battle.put("playerHp", playerUnit.getHp());
        battle.put("playerMaxHp", playerUnit.getMaxHp());
        battle.put("playerMp", playerUnit.getMp());
        battle.put("playerMaxMp", playerUnit.getMaxMp());

        int enemyHp = state.enemies.stream().mapToInt(BattleUnit::getHp).sum();
        int enemyMaxHp = state.enemies.stream().mapToInt(BattleUnit::getMaxHp).sum();
        battle.put("enemyHp", enemyHp);
        battle.put("enemyMaxHp", enemyMaxHp);
        battle.put("enemyName", state.enemies.size() == 1
                ? state.enemies.get(0).getName()
                : state.enemies.size() + " 名敵人");
        battle.put("activeActorId", state.multiplayer && viewUserId != null
                ? findNextUnplannedActorForUser(state, viewUserId)
                : state.activeActorId);
        battle.set("plannedActorIds", idsToJson(state.pendingActions.keySet()));

        ArrayNode consumablesNode = objectMapper.createArrayNode();
        List<ConsumableInfo> consumables = state.multiplayer && viewUserId != null
                ? state.consumablesByUser.getOrDefault(viewUserId, List.of())
                : state.consumables;
        for (ConsumableInfo c : consumables) {
            ObjectNode cn = objectMapper.createObjectNode();
            cn.put("id", c.itemId());
            cn.put("name", c.name());
            cn.put("healHp", c.healHp());
            cn.put("quantity", c.quantity());
            consumablesNode.add(cn);
        }
        battle.set("consumables", consumablesNode);
        return battle;
    }

    private int findNextUnplannedActorForUser(BattleState state, Long userId) {
        return state.allies.stream()
                .filter(BattleUnit::isAlive)
                .filter(u -> u.getOwnerUserId() != null && u.getOwnerUserId().equals(userId))
                .filter(u -> !state.pendingActions.containsKey(u.getId()))
                .map(BattleUnit::getId)
                .findFirst()
                .orElse(state.userPlayerUnitIds.getOrDefault(userId, state.playerUnitId));
    }

    private ArrayNode idsToJson(Set<Integer> ids) {
        ArrayNode array = objectMapper.createArrayNode();
        for (int id : ids) array.add(id);
        return array;
    }

    private ArrayNode unitsToJson(List<BattleUnit> units) {
        ArrayNode array = objectMapper.createArrayNode();
        for (BattleUnit unit : units) array.add(unit.toJsonNode(objectMapper));
        return array;
    }

    // ── Inner Types ──────────────────────────────────────────────────────────

    private record PlannedAction(String action, Integer targetId, Long skillId, Long itemId) {}

    public record PartyMemberBattleContext(
            Long userId,
            String sessionId,
            String playerName,
            Element playerElement,
            CharacterStats playerStats,
            int playerLevel,
            int playerCurrentHp,
            int playerCurrentMp,
            int partyIndex
    ) {}

    private record ConsumableInfo(long itemId, String name, int healHp, int quantity) {}

    private static class BattleState {
        private final String sessionId;
        private final String encounterKey;
        private final Long userId;
        private final String playerName;
        private final Element playerElement;
        private final CharacterStats playerStats;
        private final int playerLevel;
        private final int playerUnitId;
        private final boolean multiplayer;
        private final List<Long> participantUserIds = new ArrayList<>();
        private final Set<String> participantSessionIds = new HashSet<>();
        private final Map<Long, Integer> userPlayerUnitIds = new HashMap<>();
        private final Map<Long, String> userNames = new HashMap<>();
        private final Map<Long, Element> userElements = new HashMap<>();
        private final Map<Long, CharacterStats> userStats = new HashMap<>();
        private final Map<Long, Integer> userLevels = new HashMap<>();
        private final List<BattleUnit> allies = new ArrayList<>();
        private final List<BattleUnit> enemies = new ArrayList<>();
        private int activeActorId;
        private final Map<Integer, PlannedAction> pendingActions = new LinkedHashMap<>();
        private final Set<Integer> defendingUnits = new HashSet<>();
        private final List<ConsumableInfo> consumables = new ArrayList<>();
        private final Map<Long, List<ConsumableInfo>> consumablesByUser = new HashMap<>();
        private final Map<Integer, List<Integer>> killCredits = new LinkedHashMap<>();
        private final Set<Integer> comboKillEnemyIds = new HashSet<>();
        private final Map<Integer, Integer> comboFollowers = new LinkedHashMap<>();
        private final Map<Integer, PlannedAction> enemyComboPlans = new LinkedHashMap<>();
        private final String visibleEnemyId;
        private final boolean fromDangerZone;

        private BattleState(
                String sessionId,
                Long userId,
                String playerName,
                Element playerElement,
                CharacterStats playerStats,
                int playerLevel,
                int playerCurrentHp,
                int playerCurrentMp,
                PendingEncounter encounter,
                List<BattleUnit> partyCompanions,
                List<BattleSkillRuntime> playerSkills
        ) {
            this.sessionId = sessionId;
            this.encounterKey = sessionId;
            this.visibleEnemyId = encounter.getVisibleEnemyId();
            this.fromDangerZone = encounter.isFromDangerZone();
            this.userId = userId;
            this.playerName = playerName;
            this.playerElement = playerElement;
            this.playerStats = playerStats;
            this.playerLevel = playerLevel;
            this.playerUnitId = 1;
            this.multiplayer = false;
            this.activeActorId = playerUnitId;
            this.participantUserIds.add(userId);
            this.participantSessionIds.add(sessionId);
            this.userPlayerUnitIds.put(userId, playerUnitId);
            this.userNames.put(userId, playerName);
            this.userElements.put(userId, playerElement);
            this.userStats.put(userId, playerStats);
            this.userLevels.put(userId, playerLevel);

            BattleUnit playerUnit = BattleUnit.player(
                    playerUnitId,
                    BattleFormation.PLAYER_SLOT,
                    playerName,
                    playerElement,
                    playerStats.maxHp(),
                    playerCurrentHp,
                    playerStats.maxMp(),
                    playerCurrentMp,
                    playerLevel,
                    playerStats
            );
            playerUnit.setSkills(playerSkills);
            playerUnit.setOwnerUserId(userId);
            allies.add(playerUnit);
            allies.addAll(partyCompanions);

            for (WildMonsterInstance monster : encounter.getMonsters()) {
                enemies.add(monster.toBattleUnit());
            }
        }

        private BattleState(
                String battleId,
                String leaderSessionId,
                List<PartyMemberBattleContext> members,
                PendingEncounter encounter,
                List<BattleUnit> prebuiltAllies,
                Map<Long, Integer> prebuiltUserPlayerUnitIds
        ) {
            this.sessionId = leaderSessionId;
            this.encounterKey = battleId;
            this.visibleEnemyId = encounter.getVisibleEnemyId();
            this.fromDangerZone = encounter.isFromDangerZone();
            this.multiplayer = true;

            PartyMemberBattleContext leader = members.get(0);
            this.userId = leader.userId();
            this.playerName = leader.playerName();
            this.playerElement = leader.playerElement();
            this.playerStats = leader.playerStats();
            this.playerLevel = leader.playerLevel();
            this.playerUnitId = prebuiltUserPlayerUnitIds.get(leader.userId());
            this.activeActorId = this.playerUnitId;

            for (PartyMemberBattleContext member : members) {
                participantUserIds.add(member.userId());
                participantSessionIds.add(member.sessionId());
                userNames.put(member.userId(), member.playerName());
                userElements.put(member.userId(), member.playerElement());
                userStats.put(member.userId(), member.playerStats());
                userLevels.put(member.userId(), member.playerLevel());
                userPlayerUnitIds.put(member.userId(), prebuiltUserPlayerUnitIds.get(member.userId()));
            }

            allies.addAll(prebuiltAllies);

            for (WildMonsterInstance monster : encounter.getMonsters()) {
                enemies.add(monster.toBattleUnit());
            }
        }

        private WildMonsterInstance toWildSnapshot(BattleUnit target) {
            return new WildMonsterInstance(
                    target.getId(),
                    target.getSlot(),
                    target.getTemplateId(),
                    target.getName(),
                    target.getElement(),
                    target.getLevel(),
                    target.getStats(),
                    target.getMaxHp(),
                    target.isCapturable()
            );
        }
    }
}
