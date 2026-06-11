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
import com.dejebu.game.SkillCombatCalculator;
import com.dejebu.game.SkillTargetResolver;
import com.dejebu.game.WildMonsterInstance;
import com.dejebu.repository.MonsterTemplateSkillRepository;
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

    private static final double COMBO_CHANCE = 0.5;
    private static final int COMBO_AGILITY_THRESHOLD = 15;
    private static final double COMBO_DAMAGE_MULTIPLIER = 1.1;

    private final ObjectMapper objectMapper;
    private final EncounterService encounterService;
    private final CompanionService companionService;
    private final SkillService skillService;
    private final UserRepository userRepository;
    private final ProgressionService progressionService;
    private final MonsterTemplateSkillRepository monsterTemplateSkillRepository;
    private final AuthService authService;
    private final Map<String, BattleState> activeBattles = new ConcurrentHashMap<>();

    public BattleService(
            ObjectMapper objectMapper,
            EncounterService encounterService,
            CompanionService companionService,
            SkillService skillService,
            UserRepository userRepository,
            ProgressionService progressionService,
            MonsterTemplateSkillRepository monsterTemplateSkillRepository,
            AuthService authService
    ) {
        this.objectMapper = objectMapper;
        this.encounterService = encounterService;
        this.companionService = companionService;
        this.skillService = skillService;
        this.userRepository = userRepository;
        this.progressionService = progressionService;
        this.monsterTemplateSkillRepository = monsterTemplateSkillRepository;
        this.authService = authService;
    }

    @Transactional(readOnly = true)
    public ObjectNode startBattle(String sessionId, Long userId, String playerName, Element playerElement, CharacterStats playerStats, int playerLevel) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("玩家不存在"));
        int playerCurrentHp = user.resolveCurrentHp();
        if (playerCurrentHp <= 0) {
            throw new IllegalStateException("體力不足，無法戰鬥");
        }

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
                encounter,
                partyCompanions,
                playerSkills
        );
        equipEnemySkills(state);
        activeBattles.put(sessionId, state);
        return toBattleSnapshot(state);
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

    public ObjectNode resolveAction(String sessionId, String action, Integer targetId, Integer actorId, Long skillId) {
        BattleState state = activeBattles.get(sessionId);
        if (state == null) {
            throw new IllegalStateException("No active battle for session");
        }
        if (actorId == null || actorId <= 0) {
            throw new IllegalArgumentException("請指定行動單位");
        }

        BattleUnit actor = findUnit(state.allies, actorId)
                .orElseThrow(() -> new IllegalArgumentException("無效的行動單位"));
        if (!actor.isAlive()) {
            throw new IllegalArgumentException("該單位已無法戰鬥");
        }
        if (state.pendingActions.containsKey(actorId)) {
            throw new IllegalArgumentException("該單位本回合已指定行動");
        }

        validatePlanAction(state, actor, action, targetId, skillId);
        state.pendingActions.put(actorId, new PlannedAction(action, targetId, skillId));
        state.activeActorId = findNextUnplannedActor(state);

        boolean allPlanned = aliveAlliesInActionOrder(state).stream()
                .allMatch(u -> state.pendingActions.containsKey(u.getId()));

        if (!allPlanned) {
            ObjectNode result = objectMapper.createObjectNode();
            result.put("roundExecuted", false);
            result.set("battle", toBattleSnapshot(state));
            return result;
        }

        return executeRound(state, sessionId);
    }

    private void validatePlanAction(BattleState state, BattleUnit actor, String action, Integer targetId, Long skillId) {
        switch (action) {
            case "attack" -> {
                if (targetId == null || targetId <= 0) throw new IllegalArgumentException("請選擇攻擊目標");
                findUnit(state.enemies, targetId).orElseThrow(() -> new IllegalArgumentException("無效的攻擊目標"));
            }
            case "skill" -> {
                if (skillId == null || skillId <= 0) throw new IllegalArgumentException("請指定技能");
                BattleSkillRuntime skill = actor.findSkill(skillId);
                if (skill == null) throw new IllegalArgumentException("該單位沒有此技能");
                if (!skill.isReady()) throw new IllegalArgumentException("「" + skill.getName() + "」冷卻中");
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
            case "defend", "flee" -> { /* always valid */ }
            default -> throw new IllegalArgumentException("Unknown battle action: " + action);
        }
    }

    // ── Round Execution ──────────────────────────────────────────────────────

    private ObjectNode executeRound(BattleState state, String sessionId) {
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

                List<BattleUnit> followers = getComboFollowers(state, unit.getId());
                if (!followers.isEmpty()) {
                    executeComboAttack(state, unit, followers, plan, random, actionResult);
                } else {
                    executeAllyAction(state, unit, plan, random, actionResult);
                }
            } else {
                List<BattleUnit> aliveAllies = state.allies.stream().filter(BattleUnit::isAlive).toList();
                List<BattleUnit> enemyFollowers = getComboFollowers(state, unit.getId());
                if (!enemyFollowers.isEmpty()) {
                    PlannedAction enemyPlan = state.enemyComboPlans.get(unit.getId());
                    if (enemyPlan != null) {
                        executeEnemyComboAttack(state, unit, enemyFollowers, enemyPlan, random, actionResult);
                    }
                } else {
                    EnemyBattleAi.EnemyAction action = EnemyBattleAi.chooseAction(unit, aliveAllies, random);
                    if (action == null) continue;
                    if ("skill".equals(action.type())) {
                        resolveSkill(state, unit, action.skillId(), action.targetId(), random, actionResult, 1.0);
                    } else {
                        resolveEnemyBasicAttack(state, unit, action.targetId(), random, actionResult);
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
            syncBattleHp(state);
            activeBattles.remove(sessionId);
            encounterService.clearEncounter(state.sessionId);
            result.set("battle", toBattleSnapshot(state));
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
            } else {
                result.put("victory", false);
            }
            syncBattleHp(state);
            activeBattles.remove(sessionId);
            encounterService.clearEncounter(state.sessionId);
        }

        result.set("battle", toBattleSnapshot(state));
        return result;
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
            if (random.nextDouble() < COMBO_CHANCE) {
                assigned.add(leader.getId());
                assigned.add(partner.getId());
                state.comboFollowers.put(partner.getId(), leader.getId());
                int k = j + 1;
                while (k <= sorted.size()) {
                    k = nextUnassigned(sorted, assigned, k);
                    if (k >= sorted.size()) break;
                    BattleUnit ext = sorted.get(k);
                    if (Math.abs(unitAgility(leader) - unitAgility(ext)) > COMBO_AGILITY_THRESHOLD) break;
                    if (random.nextDouble() < COMBO_CHANCE) {
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
            if (random.nextDouble() < COMBO_CHANCE) {
                assigned.add(leader.getId());
                assigned.add(partner.getId());
                state.comboFollowers.put(partner.getId(), leader.getId());
                BattleUnit target = aliveAllies.get(random.nextInt(aliveAllies.size()));
                state.enemyComboPlans.put(leader.getId(), new PlannedAction("attack", target.getId(), null));
                int k = j + 1;
                while (k <= sorted.size()) {
                    k = nextUnassigned(sorted, assigned, k);
                    if (k >= sorted.size()) break;
                    BattleUnit ext = sorted.get(k);
                    if (Math.abs(unitAgility(leader) - unitAgility(ext)) > COMBO_AGILITY_THRESHOLD) break;
                    if (random.nextDouble() < COMBO_CHANCE) {
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
            if (skill == null || !skill.isComboEligible() || skill.isHealSkill()) return false;
        }
        return true;
    }

    private List<BattleUnit> getComboFollowers(BattleState state, int leaderId) {
        return state.comboFollowers.entrySet().stream()
                .filter(e -> e.getValue() == leaderId)
                .map(e -> findUnit(state.allies, e.getKey()).orElse(null))
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
            ObjectNode result
    ) {
        int targetId = leaderPlan.targetId();
        Optional<BattleUnit> targetOpt = findUnit(state.enemies, targetId);
        if (targetOpt.isEmpty() || !targetOpt.get().isAlive()) {
            appendMessage(result, "連擊目標已被擊倒，連擊取消");
            return;
        }
        BattleUnit comboTarget = targetOpt.get();

        int total = 1 + followers.size();
        String names = buildComboNames(leader, followers);
        appendMessage(result, "【" + total + "人連擊】" + names + " 對 " + comboTarget.getName() + " 發動連擊！");

        // Leader hits first; then each follower in agility order.
        // Target death does NOT stop subsequent hits — damage overflows.
        resolveComboHit(state, leader, leaderPlan, comboTarget, random, result);
        for (BattleUnit follower : followers) {
            PlannedAction fp = state.pendingActions.get(follower.getId());
            if (fp != null) {
                resolveComboHit(state, follower, fp, comboTarget, random, result);
            }
        }

        // If the combo target died during this combo, all participants share the kill credit
        if (!comboTarget.isAlive()) {
            List<Integer> credits = state.killCredits.computeIfAbsent(comboTarget.getId(), k -> new ArrayList<>());
            credits.clear();
            credits.add(leader.getId());
            followers.forEach(f -> credits.add(f.getId()));
        }
    }

    private void executeEnemyComboAttack(
            BattleState state,
            BattleUnit leader,
            List<BattleUnit> followers,
            PlannedAction leaderPlan,
            ThreadLocalRandom random,
            ObjectNode result
    ) {
        int targetId = leaderPlan.targetId();
        Optional<BattleUnit> targetOpt = findUnit(state.allies, targetId);
        if (targetOpt.isEmpty() || !targetOpt.get().isAlive()) {
            List<BattleUnit> aliveAllies = state.allies.stream().filter(BattleUnit::isAlive).toList();
            if (aliveAllies.isEmpty()) return;
            targetOpt = Optional.of(aliveAllies.get(random.nextInt(aliveAllies.size())));
        }
        BattleUnit comboTarget = targetOpt.get();

        int total = 1 + followers.size();
        String names = buildComboNames(leader, followers);
        appendMessage(result, "【" + total + "人連擊】" + names + " 對 " + comboTarget.getName() + " 發動連擊！");

        resolveEnemyComboHit(state, leader, comboTarget, random, result);
        for (BattleUnit follower : followers) {
            resolveEnemyComboHit(state, follower, comboTarget, random, result);
        }
    }

    private void resolveEnemyComboHit(
            BattleState state,
            BattleUnit attacker,
            BattleUnit target,
            ThreadLocalRandom random,
            ObjectNode result
    ) {
        CharacterStats attackerStats = attacker.getStats() != null ? attacker.getStats() : CharacterStats.zeroBase();
        boolean defending = state.defendingUnits.contains(target.getId());
        int baseDamage = defending ? random.nextInt(1, 6) : attackerStats.rollAttackDamage(random);
        baseDamage = (int)(baseDamage * COMBO_DAMAGE_MULTIPLIER);
        int damage = applyElementDamage(baseDamage, attacker.getElement(), target.getElement());
        CharacterStats defenderStats = target.getStats() != null ? target.getStats() : state.playerStats;
        damage = defenderStats.mitigateDamage(damage, defending);
        target.setHpRaw(target.getHp() - damage);
        appendMessage(result, buildAttackMessage(attacker.getName(), attacker.getElement(), target, damage, false));
        recordAttackEvent(result, attacker.getId(), target.getId(), damage, false);
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
                if (skill == null || !skill.isReady()) {
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
        CharacterStats attackerStats = actor.getStats() != null ? actor.getStats() : state.playerStats;
        int baseDamage = attackerStats.rollAttackDamage(random);
        boolean critical = attackerStats.rollCritical(random);
        if (critical) baseDamage *= 2;
        int damage = applyElementDamage(baseDamage, actor.getElement(), target.getElement());
        damage = (int)(damage * COMBO_DAMAGE_MULTIPLIER);
        target.setHpRaw(target.getHp() - damage);  // allow overflow past 0
        appendMessage(result, buildAttackMessage(actor.getName(), actor.getElement(), target, damage, critical));
        recordAttackEvent(result, actor.getId(), target.getId(), damage, critical);
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
        for (BattleUnit unit : state.enemies) {
            if (!affectedSlots.contains(unit.getSlot())) continue;
            int baseDamage = SkillCombatCalculator.calculateDamage(actorStats, skill, random);
            boolean critical = actorStats.rollCritical(random);
            if (critical) baseDamage *= 2;
            int damage = applyElementDamage(baseDamage, attackElement, unit.getElement());
            damage = (int)(damage * COMBO_DAMAGE_MULTIPLIER);
            if (unit.getId() == comboTarget.getId()) {
                unit.setHpRaw(unit.getHp() - damage);  // combo target: overflow allowed
            } else {
                boolean wasAlive = unit.isAlive();
                unit.setHp(unit.getHp() - damage);     // other AOE targets: normal
                if (wasAlive && !unit.isAlive()) {
                    state.killCredits.computeIfAbsent(unit.getId(), k -> new ArrayList<>()).add(actor.getId());
                }
            }
            totalDamage += damage;
            recordAttackEvent(result, actor.getId(), unit.getId(), damage, critical);
        }
        message.append("，造成 ").append(totalDamage).append(" 點傷害");
        skill.markUsed();
        appendMessage(result, message.toString());
    }

    // ── Action Execution ──────────────────────────────────────────────────────

    private void executeAllyAction(BattleState state, BattleUnit actor, PlannedAction plan, ThreadLocalRandom random, ObjectNode result) {
        switch (plan.action()) {
            case "attack" -> {
                Optional<BattleUnit> target = findUnit(state.enemies, plan.targetId());
                if (target.isEmpty() || !target.get().isAlive()) {
                    appendMessage(result, actor.getName() + " 的目標已被擊倒，跳過行動");
                    return;
                }
                resolveAttack(state, actor, plan.targetId(), random, result, 1.0);
            }
            case "skill" -> {
                BattleSkillRuntime skill = actor.findSkill(plan.skillId());
                if (skill == null || !skill.isReady()) {
                    appendMessage(result, actor.getName() + " 的技能無法使用，跳過行動");
                    return;
                }
                List<BattleUnit> pool = skillTargetPool(state, actor, skill);
                Optional<BattleUnit> target = findUnit(pool, plan.targetId());
                if (!skill.isHealSkill() && (target.isEmpty() || !target.get().isAlive())) {
                    appendMessage(result, actor.getName() + " 的技能目標已被擊倒，跳過行動");
                    return;
                }
                resolveSkill(state, actor, plan.skillId(), plan.targetId(), random, result, 1.0);
            }
            case "capture" -> {
                Optional<BattleUnit> target = findUnit(state.enemies, plan.targetId());
                if (target.isEmpty() || !target.get().isAlive()) {
                    appendMessage(result, actor.getName() + " 的捕捉目標已被擊倒，跳過行動");
                    return;
                }
                resolveCapture(state, plan.targetId(), random, result);
            }
            case "defend" -> {
                state.defendingUnits.add(actor.getId());
                appendMessage(result, actor.getName() + " 採取防禦姿態");
            }
            case "flee" -> resolveFlee(state, actor, random, result);
        }
    }


    // ── Action Resolvers ─────────────────────────────────────────────────────

    private void resolveFlee(BattleState state, BattleUnit actor, ThreadLocalRandom random, ObjectNode result) {
        CharacterStats fleeStats = actor.getStats() != null ? actor.getStats() : state.playerStats;
        boolean escaped = random.nextDouble() < fleeStats.fleeChance();
        result.put("escaped", escaped);
        appendMessage(result, escaped
                ? actor.getName() + " 帶領隊伍成功逃脫！"
                : actor.getName() + " 嘗試逃跑失敗！");
    }

    private void resolveCapture(
            BattleState state,
            Integer targetId,
            ThreadLocalRandom random,
            ObjectNode result
    ) {
        BattleUnit target = findUnit(state.enemies, targetId)
                .orElseThrow(() -> new IllegalArgumentException("無效的捕捉目標"));
        if (!target.isAlive()) {
            throw new IllegalArgumentException("該目標已無法戰鬥");
        }

        User user = userRepository.findById(state.userId)
                .orElseThrow(() -> new IllegalStateException("玩家不存在"));

        CaptureResult captureResult = companionService.evaluateCapture(
                state.playerLevel,
                state.playerStats,
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

        CharacterStats attackerStats = actor.getStats() != null ? actor.getStats() : state.playerStats;
        int baseDamage = attackerStats.rollAttackDamage(random);
        boolean critical = attackerStats.rollCritical(random);
        if (critical) baseDamage *= 2;
        int damage = applyElementDamage(baseDamage, actor.getElement(), target.getElement());
        damage = (int)(damage * damageMultiplier);
        boolean wasAlive = target.isAlive();
        target.setHp(target.getHp() - damage);
        if (wasAlive && !target.isAlive()) {
            state.killCredits.computeIfAbsent(target.getId(), k -> new ArrayList<>()).add(actor.getId());
        }
        result.put("damage", damage);
        result.put("critical", critical);
        result.put("elementMultiplier", ElementRelation.damageMultiplier(actor.getElement(), target.getElement()));
        result.put("elementMatchup", ElementRelation.matchup(actor.getElement(), target.getElement()).name());
        appendMessage(result, buildAttackMessage(actor.getName(), actor.getElement(), target, damage, critical));
        recordAttackEvent(result, actor.getId(), targetId, damage, critical);
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
        if (!skill.isReady()) throw new IllegalArgumentException("「" + skill.getName() + "」冷卻中");

        boolean actorIsAlly = isAllyUnit(state, actor);
        List<BattleUnit> targetPool = skillTargetPool(state, actor, skill);
        BattleUnit anchor = findUnit(targetPool, targetId)
                .orElseThrow(() -> new IllegalArgumentException("無效的技能目標"));
        if (!anchor.isAlive() && !skill.isHealSkill()) {
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
        } else {
            int totalDamage = 0;
            for (BattleUnit unit : opponentsOf(state, actor)) {
                if (!affectedSlots.contains(unit.getSlot()) || !unit.isAlive()) continue;
                int baseDamage = SkillCombatCalculator.calculateDamage(actorStats, skill, random);
                boolean critical = actorStats.rollCritical(random);
                if (critical) baseDamage *= 2;
                int damage = applyElementDamage(baseDamage, attackElement, unit.getElement());
                damage = (int)(damage * damageMultiplier);
                if (!actorIsAlly) {
                    CharacterStats defenderStats = unit.getStats() != null ? unit.getStats() : state.playerStats;
                    boolean defending = state.defendingUnits.contains(unit.getId());
                    damage = defenderStats.mitigateDamage(damage, defending);
                }
                boolean wasAlive = unit.isAlive();
                unit.setHp(unit.getHp() - damage);
                if (actorIsAlly && wasAlive && !unit.isAlive()) {
                    state.killCredits.computeIfAbsent(unit.getId(), k -> new ArrayList<>()).add(actor.getId());
                }
                totalDamage += damage;
                recordAttackEvent(result, actor.getId(), unit.getId(), damage, critical);
            }
            result.put("damage", totalDamage);
            message.append("，造成 ").append(totalDamage).append(" 點傷害");
        }

        skill.markUsed();
        appendMessage(result, message.toString());
    }

    private void resolveEnemyBasicAttack(
            BattleState state,
            BattleUnit attacker,
            int targetId,
            ThreadLocalRandom random,
            ObjectNode result
    ) {
        BattleUnit defender = findUnit(state.allies, targetId)
                .orElseThrow(() -> new IllegalStateException("無效的敵方攻擊目標"));
        if (!defender.isAlive()) return;

        CharacterStats attackerStats = attacker.getStats() != null ? attacker.getStats() : CharacterStats.zeroBase();
        boolean defending = state.defendingUnits.contains(defender.getId());
        int baseEnemyDamage = defending
                ? random.nextInt(1, 6)
                : attackerStats.rollAttackDamage(random);
        int enemyDamage = applyElementDamage(baseEnemyDamage, attacker.getElement(), defender.getElement());
        CharacterStats defenderStats = defender.getStats() != null ? defender.getStats() : state.playerStats;
        enemyDamage = defenderStats.mitigateDamage(enemyDamage, defending);
        defender.setHp(defender.getHp() - enemyDamage);
        String counterMessage = buildAttackMessage(attacker.getName(), attacker.getElement(), defender, enemyDamage, false);
        appendMessage(result, counterMessage);
        recordAttackEvent(result, attacker.getId(), defender.getId(), enemyDamage, false);
    }

    // ── Experience Distribution ──────────────────────────────────────────────

    private void appendVictoryExpResult(ObjectNode result, BattleState state) {
        Map<Integer, Integer> unitExpMap = computeExpDistribution(state);

        int playerExpGained = unitExpMap.getOrDefault(state.playerUnitId, 0);
        ProgressionResult playerResult = progressionService.applyVictoryExp(state.userId, playerExpGained);

        result.put("expGained", playerResult.expGained());
        result.put("playerExp", playerResult.playerExp());
        result.put("expToNextLevel", playerResult.expToNextLevel());
        result.put("playerLevel", playerResult.playerLevel());
        result.put("skillPoints", playerResult.skillPoints());
        if (playerResult.leveledUp()) {
            result.put("leveledUp", true);
            result.put("previousLevel", playerResult.previousLevel());
            result.put("levelsGained", playerResult.levelsGained());
            result.put("skillPointsGained", playerResult.skillPointsGained());
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

    private Map<Integer, Integer> computeExpDistribution(BattleState state) {
        Map<Integer, Integer> unitExpMap = new LinkedHashMap<>();
        for (BattleUnit enemy : state.enemies) {
            if (enemy.isAlive()) continue; // only dead enemies yield exp
            int monsterExp = Math.max(1, enemy.getLevel()) * 5;
            int sharedPart = (int)(monsterExp * 0.75);
            int killerBonus = monsterExp - sharedPart;

            // Shared portion to all allies
            for (BattleUnit ally : state.allies) {
                unitExpMap.merge(ally.getId(), sharedPart, Integer::sum);
            }

            // Killer bonus to kill credit holders
            List<Integer> killers = state.killCredits.getOrDefault(enemy.getId(), List.of());
            for (int killerId : killers) {
                unitExpMap.merge(killerId, killerBonus, Integer::sum);
            }
        }
        return unitExpMap;
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

    private void syncBattleHp(BattleState state) {
        state.allies.stream()
                .filter(unit -> unit.getId() == state.playerUnitId)
                .findFirst()
                .ifPresent(playerUnit -> authService.syncPlayerHp(state.userId, Math.max(1, playerUnit.getHp())));
        companionService.syncPartyHp(state.userId, state.allies, state.playerUnitId);
    }

    private void appendMessage(ObjectNode result, String text) {
        if (result.has("message") && !result.get("message").asText().isBlank()) {
            result.put("message", result.get("message").asText() + "；" + text);
        } else {
            result.put("message", text);
        }
    }

    private Optional<BattleUnit> findUnit(List<BattleUnit> units, int id) {
        return units.stream().filter(unit -> unit.getId() == id).findFirst();
    }

    private boolean hasAliveAllies(BattleState state) {
        return state.allies.stream().anyMatch(BattleUnit::isAlive);
    }

    private boolean hasAliveEnemies(BattleState state) {
        return state.enemies.stream().anyMatch(BattleUnit::isAlive);
    }

    // ── Battle Snapshot ──────────────────────────────────────────────────────

    private ObjectNode toBattleSnapshot(BattleState state) {
        ObjectNode battle = objectMapper.createObjectNode();
        battle.put("playerName", state.playerName);
        battle.put("playerLevel", state.playerLevel);
        battle.put("playerElement", state.playerElement.getCode());
        battle.put("playerElementName", state.playerElement.getDisplayName());
        battle.set("playerStats", state.playerStats.toJsonNode(objectMapper));
        battle.set("allies", unitsToJson(state.allies));
        battle.set("enemies", unitsToJson(state.enemies));

        BattleUnit playerUnit = state.allies.stream()
                .filter(unit -> unit.getId() == state.playerUnitId)
                .findFirst()
                .orElse(state.allies.get(0));
        battle.put("playerHp", playerUnit.getHp());
        battle.put("playerMaxHp", playerUnit.getMaxHp());

        int enemyHp = state.enemies.stream().mapToInt(BattleUnit::getHp).sum();
        int enemyMaxHp = state.enemies.stream().mapToInt(BattleUnit::getMaxHp).sum();
        battle.put("enemyHp", enemyHp);
        battle.put("enemyMaxHp", enemyMaxHp);
        battle.put("enemyName", state.enemies.size() == 1
                ? state.enemies.get(0).getName()
                : state.enemies.size() + " 名敵人");
        battle.put("activeActorId", state.activeActorId);
        battle.set("plannedActorIds", idsToJson(state.pendingActions.keySet()));
        return battle;
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

    private record PlannedAction(String action, Integer targetId, Long skillId) {}

    private static class BattleState {
        private final String sessionId;
        private final Long userId;
        private final String playerName;
        private final Element playerElement;
        private final CharacterStats playerStats;
        private final int playerLevel;
        private final int playerUnitId;
        private final List<BattleUnit> allies = new ArrayList<>();
        private final List<BattleUnit> enemies = new ArrayList<>();
        private int activeActorId;
        private final Map<Integer, PlannedAction> pendingActions = new LinkedHashMap<>();
        private final Set<Integer> defendingUnits = new HashSet<>();
        // enemyUnitId → list of ally unit ids that landed the killing blow
        private final Map<Integer, List<Integer>> killCredits = new LinkedHashMap<>();
        // followerUnitId → leaderUnitId for active combos this round
        private final Map<Integer, Integer> comboFollowers = new LinkedHashMap<>();
        // enemyComboLeaderId → pre-assigned PlannedAction for enemy combo leaders
        private final Map<Integer, PlannedAction> enemyComboPlans = new LinkedHashMap<>();

        private BattleState(
                String sessionId,
                Long userId,
                String playerName,
                Element playerElement,
                CharacterStats playerStats,
                int playerLevel,
                int playerCurrentHp,
                PendingEncounter encounter,
                List<BattleUnit> partyCompanions,
                List<BattleSkillRuntime> playerSkills
        ) {
            this.sessionId = sessionId;
            this.userId = userId;
            this.playerName = playerName;
            this.playerElement = playerElement;
            this.playerStats = playerStats;
            this.playerLevel = playerLevel;
            this.playerUnitId = 1;
            this.activeActorId = playerUnitId;

            BattleUnit playerUnit = BattleUnit.player(
                    playerUnitId,
                    BattleFormation.PLAYER_SLOT,
                    playerName,
                    playerElement,
                    playerStats.maxHp(),
                    playerCurrentHp,
                    playerLevel,
                    playerStats
            );
            playerUnit.setSkills(playerSkills);
            allies.add(playerUnit);
            allies.addAll(partyCompanions);

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
