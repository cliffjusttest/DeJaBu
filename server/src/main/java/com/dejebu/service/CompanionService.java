package com.dejebu.service;

import com.dejebu.dto.CompanionDto;
import com.dejebu.dto.CompanionListResponse;
import com.dejebu.dto.CompanionSkillDto;
import com.dejebu.entity.CompanionSkill;
import com.dejebu.entity.MonsterTemplateEntity;
import com.dejebu.entity.MonsterTemplateSkill;
import com.dejebu.entity.Skill;
import com.dejebu.entity.User;
import com.dejebu.entity.UserCompanion;
import com.dejebu.game.BattleFormation;
import com.dejebu.game.BattleSkillRuntime;
import com.dejebu.game.BattleUnit;
import com.dejebu.game.CharacterStats;
import com.dejebu.game.WildMonsterInstance;
import com.dejebu.repository.CompanionSkillRepository;
import com.dejebu.repository.MonsterTemplateRepository;
import com.dejebu.repository.MonsterTemplateSkillRepository;
import com.dejebu.repository.UserCompanionRepository;
import com.dejebu.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class CompanionService {

    public static final int MAX_LEVEL_GAP = 10;
    public static final int MAX_PARTY_COMPANIONS = BattleFormation.maxPartyCompanions();
    private static final int UPGRADE_COST = 1;
    private static final int[] PARTY_SLOTS = {0, 1, 2, 3, 4};

    private final UserCompanionRepository userCompanionRepository;
    private final MonsterTemplateRepository monsterTemplateRepository;
    private final MonsterTemplateSkillRepository monsterTemplateSkillRepository;
    private final CompanionSkillRepository companionSkillRepository;
    private final UserRepository userRepository;
    private final EquipmentService equipmentService;
    private final PlayerPartyService playerPartyService;
    private final ProgressionService progressionService;

    public CompanionService(
            UserCompanionRepository userCompanionRepository,
            MonsterTemplateRepository monsterTemplateRepository,
            MonsterTemplateSkillRepository monsterTemplateSkillRepository,
            CompanionSkillRepository companionSkillRepository,
            UserRepository userRepository,
            EquipmentService equipmentService,
            PlayerPartyService playerPartyService,
            ProgressionService progressionService
    ) {
        this.userCompanionRepository = userCompanionRepository;
        this.monsterTemplateRepository = monsterTemplateRepository;
        this.monsterTemplateSkillRepository = monsterTemplateSkillRepository;
        this.companionSkillRepository = companionSkillRepository;
        this.userRepository = userRepository;
        this.equipmentService = equipmentService;
        this.playerPartyService = playerPartyService;
        this.progressionService = progressionService;
    }

    @Transactional(readOnly = true)
    public CompanionListResponse listCompanions(User user) {
        return new CompanionListResponse(listCompanionDtos(user), "夥伴列表載入完成", user.getSkillPoints());
    }

    @Transactional
    public CompanionListResponse upgradeCompanionSkill(User user, Long companionId, Long skillId) {
        UserCompanion companion = userCompanionRepository.findByUserIdAndId(user.getId(), companionId)
                .orElseThrow(() -> new IllegalArgumentException("找不到此夥伴"));
        CompanionSkill companionSkill = companionSkillRepository.findByCompanionIdAndSkillId(companionId, skillId)
                .orElseThrow(() -> new IllegalArgumentException("此夥伴沒有該技能"));
        Skill skill = companionSkill.getSkill();
        if (companionSkill.getSkillLevel() >= skill.getMaxLevel()) {
            throw new IllegalArgumentException("「" + skill.getName() + "」已達等級上限");
        }
        if (user.getSkillPoints() < UPGRADE_COST) {
            throw new IllegalArgumentException("技能點不足");
        }

        companionSkill.setSkillLevel(companionSkill.getSkillLevel() + 1);
        companionSkillRepository.save(companionSkill);
        user.setSkillPoints(user.getSkillPoints() - UPGRADE_COST);
        userRepository.save(user);

        return new CompanionListResponse(
                listCompanionDtos(user),
                "「" + companion.getNickname() + "」的「" + skill.getName() + "」提升至 Lv." + companionSkill.getSkillLevel(),
                user.getSkillPoints()
        );
    }

    @Transactional
    public CompanionListResponse setPartyStatus(User user, Long companionId, boolean active) {
        UserCompanion companion = userCompanionRepository.findByUserIdAndId(user.getId(), companionId)
                .orElseThrow(() -> new IllegalArgumentException("找不到此夥伴"));

        if (active) {
            if (companion.getPartySlot() != null) {
                return new CompanionListResponse(listCompanionDtos(user), "此夥伴已在出戰隊伍中", user.getSkillPoints());
            }
            if (!canAssignToParty(companion)) {
                throw new IllegalArgumentException(describeBattleBlockReason(companion));
            }
            int maxCompanions = playerPartyService.isInParty(user.getId())
                    ? BattleFormation.maxCompanionsPerPlayerInParty()
                    : MAX_PARTY_COMPANIONS;
            long activeCount = userCompanionRepository.countByUserIdAndPartySlotIsNotNull(user.getId());
            if (activeCount >= maxCompanions) {
                String limitMessage = maxCompanions == 1
                        ? "組隊模式下最多只能出戰 1 名夥伴"
                        : "出戰名額已滿，最多 " + maxCompanions + " 名";
                throw new IllegalArgumentException(limitMessage);
            }
            Integer freeSlot = resolvePartySlot(user.getId());
            if (freeSlot == null) {
                throw new IllegalArgumentException("出戰名額已滿，最多 " + maxCompanions + " 名");
            }
            companion.setPartySlot(freeSlot);
        } else {
            companion.setPartySlot(null);
        }

        userCompanionRepository.save(companion);
        String message = active ? "已設定為出戰" : "已移出出戰隊伍";
        return new CompanionListResponse(listCompanionDtos(user), message, user.getSkillPoints());
    }

    @Transactional(readOnly = true)
    public List<BattleUnit> loadPartyBattleUnits(Long userId, int nextUnitIdStart) {
        return loadPartyBattleUnits(userId, nextUnitIdStart, MAX_PARTY_COMPANIONS, -1);
    }

    @Transactional(readOnly = true)
    public List<BattleUnit> loadPartyBattleUnits(Long userId, int nextUnitIdStart, int maxCompanions, int battleSlotOverride) {
        List<UserCompanion> party = userCompanionRepository.findByUserIdAndPartySlotIsNotNullOrderByPartySlotAsc(userId);
        List<BattleUnit> units = new ArrayList<>();
        int nextId = nextUnitIdStart;
        int loaded = 0;
        for (UserCompanion companion : party) {
            if (loaded >= maxCompanions) {
                break;
            }
            if (!isAvailableForBattle(companion)) {
                continue;
            }
            MonsterTemplateEntity template = companion.getTemplate();
            CharacterStats battleStats = companion.toStats()
                    .withBonus(equipmentService.getCompanionEquipmentBonus(companion.getId()));
            int battleMaxHp = battleStats.maxHp();
            int battleMaxMp = battleStats.maxMp();
            int currentHp = Math.min(Math.max(0, companion.getCurrentHp()), battleMaxHp);
            int currentMp = Math.min(Math.max(0, companion.resolveCurrentMp()), battleMaxMp);
            int battleSlot = battleSlotOverride >= 0
                    ? battleSlotOverride
                    : BattleFormation.partySlotToBattleSlot(companion.getPartySlot());
            BattleUnit unit = BattleUnit.companion(
                    nextId++,
                    battleSlot,
                    template.getId(),
                    companion.getNickname(),
                    template.getElement(),
                    companion.getLevel(),
                    battleStats,
                    battleMaxHp,
                    currentHp,
                    battleMaxMp,
                    currentMp
            );
            unit.setSkills(loadRuntimeSkillsForCompanion(companion.getId()));
            unit.setOwnerUserId(userId);
            units.add(unit);
            loaded++;
        }
        return units;
    }

    @Transactional(readOnly = true)
    public List<BattleSkillRuntime> loadRuntimeSkillsForCompanion(Long companionId) {
        return companionSkillRepository.findByCompanionIdOrderByIdAsc(companionId).stream()
                .map(entry -> BattleSkillRuntime.from(entry.getSkill(), entry.getSkillLevel()))
                .toList();
    }

    public CaptureResult evaluateCapture(
            int playerLevel,
            CharacterStats playerStats,
            int monsterLevel,
            int monsterHp,
            int monsterMaxHp,
            ThreadLocalRandom random
    ) {
        int levelGap = Math.abs(playerLevel - monsterLevel);
        if (levelGap > MAX_LEVEL_GAP) {
            return CaptureResult.failed("等級差距超過 " + MAX_LEVEL_GAP + " 級，無法捕捉！");
        }

        double hpFactor = 1.0 - ((double) monsterHp / Math.max(1, monsterMaxHp));
        double rate = 0.18 + hpFactor * 0.52 + playerStats.luck() * 0.008 - levelGap * 0.015;
        rate = Math.max(0.05, Math.min(0.92, rate));

        boolean success = random.nextDouble() < rate;
        if (success) {
            return CaptureResult.succeeded("捕捉成功！");
        }
        return CaptureResult.failed(String.format("捕捉失敗（成功率 %.0f%%）", rate * 100));
    }

    @Transactional
    public UserCompanion captureWildMonster(User user, WildMonsterInstance monster) {
        MonsterTemplateEntity template = monsterTemplateRepository.findById(monster.getTemplateId())
                .orElseThrow(() -> new IllegalStateException("怪物模板不存在: " + monster.getTemplateId()));

        UserCompanion companion = new UserCompanion();
        companion.setUser(user);
        companion.setTemplate(template);
        companion.setNickname(monster.getName());
        companion.setLevel(monster.getLevel());
        CharacterStats stats = monster.getStats();
        companion.setStatMight(stats.might());
        companion.setStatIntelligence(stats.intelligence());
        companion.setStatVitality(stats.vitality());
        companion.setStatDefense(stats.defense());
        companion.setStatSpirit(stats.spirit());
        companion.setStatLuck(stats.luck());
        companion.setStatAgility(stats.agility());
        companion.setMaxHp(monster.getMaxHp());
        companion.setCurrentHp(monster.getHp());
        int maxMp = monster.getStats().maxMp();
        companion.setMaxMp(maxMp);
        companion.setCurrentMp(maxMp);
        companion.setPartySlot(resolvePartySlot(user.getId()));
        UserCompanion saved = userCompanionRepository.save(companion);
        copyTemplateSkillsToCompanion(saved, template);
        return saved;
    }

    private void copyTemplateSkillsToCompanion(UserCompanion companion, MonsterTemplateEntity template) {
        List<MonsterTemplateSkill> templateSkills = monsterTemplateSkillRepository
                .findByTemplateIdOrderBySlotOrderAsc(template.getId());
        for (MonsterTemplateSkill templateSkill : templateSkills) {
            templateSkill.getSkill().getName();
            CompanionSkill companionSkill = new CompanionSkill();
            companionSkill.setCompanion(companion);
            companionSkill.setSkill(templateSkill.getSkill());
            companionSkill.setSkillLevel(1);
            companionSkillRepository.save(companionSkill);
        }
    }

    private Integer resolvePartySlot(Long userId) {
        long activeCount = userCompanionRepository.countByUserIdAndPartySlotIsNotNull(userId);
        if (activeCount >= MAX_PARTY_COMPANIONS) {
            return null;
        }
        for (int slot : PARTY_SLOTS) {
            if (userCompanionRepository.findByUserIdAndPartySlot(userId, slot).isEmpty()) {
                return slot;
            }
        }
        return null;
    }

    @Transactional
    public void syncPartyHp(Long userId, List<BattleUnit> allies, int playerUnitId, boolean victory) {
        for (BattleUnit ally : allies) {
            if (ally.getId() == playerUnitId || !ally.isCompanion()) {
                continue;
            }
            if (ally.getOwnerUserId() != null && !ally.getOwnerUserId().equals(userId)) {
                continue;
            }
            findCompanionForBattleUnit(userId, ally)
                    .ifPresent(companion -> {
                        int hp = victory ? Math.max(1, ally.getHp()) : ally.getHp();
                        companion.setCurrentHp(hp);
                        userCompanionRepository.save(companion);
                    });
        }
    }

    @Transactional
    public void syncPartyHp(Long userId, List<BattleUnit> allies, int playerUnitId) {
        syncPartyHp(userId, allies, playerUnitId, false);
    }

    @Transactional
    public void applyCompanionDefeat(Long userId, BattleUnit ally) {
        findCompanionForBattleUnit(userId, ally).ifPresent(companion -> {
            companion.setCurrentHp(0);
            companion.setIncapacitatedUntil(
                    Instant.now().plus(BattleDeathService.COMPANION_INCAPACITATED_MINUTES, ChronoUnit.MINUTES)
            );
            progressionService.applyExpLoss(companion);
            userCompanionRepository.save(companion);
        });
    }

    @Transactional
    public HospitalReviveResult reviveCompanionsAtHospital(User user) {
        List<String> revivedNames = new ArrayList<>();
        for (UserCompanion companion : userCompanionRepository.findByUserIdOrderByCapturedAtAsc(user.getId())) {
            if (!needsHospitalRevive(companion)) {
                continue;
            }
            companion.setCurrentHp(companion.getMaxHp());
            companion.setCurrentMp(companion.resolveMaxMp());
            companion.setIncapacitatedUntil(null);
            userCompanionRepository.save(companion);
            revivedNames.add(companion.getNickname());
        }
        if (revivedNames.isEmpty()) {
            return new HospitalReviveResult(false, "目前沒有需要治療的夥伴。");
        }
        return new HospitalReviveResult(true, "已治療夥伴：" + String.join("、", revivedNames));
    }

    public boolean isAvailableForBattle(UserCompanion companion) {
        if (companion.getCurrentHp() <= 0) {
            return false;
        }
        if (isIncapacitationCooldown(companion)) {
            return false;
        }
        return !needsHospitalRevive(companion);
    }

    public boolean canAssignToParty(UserCompanion companion) {
        if (companion.getCurrentHp() <= 0) {
            return false;
        }
        if (isIncapacitationCooldown(companion)) {
            return false;
        }
        return !needsHospitalRevive(companion);
    }

    public boolean isIncapacitationCooldown(UserCompanion companion) {
        Instant until = companion.getIncapacitatedUntil();
        return until != null && Instant.now().isBefore(until);
    }

    public boolean needsHospitalRevive(UserCompanion companion) {
        if (companion.getCurrentHp() > 0) {
            return false;
        }
        Instant until = companion.getIncapacitatedUntil();
        return until != null && !Instant.now().isBefore(until);
    }

    public long incapacitationMinutesRemaining(UserCompanion companion) {
        Instant until = companion.getIncapacitatedUntil();
        if (until == null || !Instant.now().isBefore(until)) {
            return 0;
        }
        return Math.max(1, ChronoUnit.MINUTES.between(Instant.now(), until));
    }

    private String describeBattleBlockReason(UserCompanion companion) {
        if (needsHospitalRevive(companion)) {
            return "「" + companion.getNickname() + "」需要到醫館治療後才能出戰";
        }
        if (isIncapacitationCooldown(companion)) {
            return "「" + companion.getNickname() + "」仍在休養中，約 "
                    + incapacitationMinutesRemaining(companion)
                    + " 分鐘後才能至醫館治療";
        }
        if (companion.getCurrentHp() <= 0) {
            return "「" + companion.getNickname() + "」體力耗盡，無法出戰";
        }
        return "此夥伴目前無法出戰";
    }

    @Transactional
    public void syncPartyMp(Long userId, List<BattleUnit> allies, int playerUnitId) {
        for (BattleUnit ally : allies) {
            if (ally.getId() == playerUnitId || !ally.isCompanion()) {
                continue;
            }
            if (ally.getOwnerUserId() != null && !ally.getOwnerUserId().equals(userId)) {
                continue;
            }
            findCompanionForBattleUnit(userId, ally)
                    .ifPresent(companion -> {
                        companion.setCurrentMp(ally.getMp());
                        userCompanionRepository.save(companion);
                    });
        }
    }

    private Optional<UserCompanion> findCompanionForBattleUnit(Long userId, BattleUnit ally) {
        int partySlot = BattleFormation.battleSlotToPartySlot(ally.getSlot());
        if (partySlot >= 0) {
            return userCompanionRepository.findByUserIdAndPartySlot(userId, partySlot);
        }
        return userCompanionRepository.findByUserIdAndPartySlotIsNotNullOrderByPartySlotAsc(userId).stream()
                .filter(companion -> companion.getNickname().equals(ally.getName()))
                .findFirst();
    }

    public Optional<UserCompanion> findCompanionEntityForBattleUnit(Long userId, BattleUnit ally) {
        return findCompanionForBattleUnit(userId, ally);
    }

    public Optional<Integer> findPartySlotForBattleUnit(Long userId, BattleUnit ally) {
        return findCompanionForBattleUnit(userId, ally)
                .map(UserCompanion::getPartySlot);
    }

    private List<CompanionDto> listCompanionDtos(User user) {
        return userCompanionRepository.findByUserIdOrderByCapturedAtAsc(user.getId())
                .stream()
                .map(this::toDto)
                .toList();
    }

    private CompanionDto toDto(UserCompanion companion) {
        MonsterTemplateEntity template = companion.getTemplate();
        User owner = companion.getUser();
        List<CompanionSkillDto> skills = companionSkillRepository.findByCompanionIdOrderByIdAsc(companion.getId())
                .stream()
                .map(entry -> toCompanionSkillDto(entry, owner))
                .toList();
        return new CompanionDto(
                companion.getId(),
                template.getId(),
                companion.getNickname(),
                template.getName(),
                template.getElement().getCode(),
                template.getElement().getDisplayName(),
                companion.getLevel(),
                companion.getCurrentHp(),
                companion.getMaxHp(),
                companion.getStatMight(),
                companion.getStatIntelligence(),
                companion.getStatVitality(),
                companion.getStatDefense(),
                companion.getStatSpirit(),
                companion.getStatLuck(),
                companion.getPartySlot(),
                isIncapacitationCooldown(companion),
                needsHospitalRevive(companion),
                incapacitationMinutesRemaining(companion),
                skills
        );
    }

    private CompanionSkillDto toCompanionSkillDto(CompanionSkill companionSkill, User owner) {
        Skill skill = companionSkill.getSkill();
        boolean canUpgrade = companionSkill.getSkillLevel() < skill.getMaxLevel()
                && owner.getSkillPoints() >= UPGRADE_COST;
        return new CompanionSkillDto(
                skill.getId(),
                skill.getName(),
                skill.getElement().getCode(),
                skill.getElement().getDisplayName(),
                companionSkill.getSkillLevel(),
                skill.getMaxLevel(),
                canUpgrade
        );
    }

    public record CaptureResult(boolean success, String message) {
        public static CaptureResult succeeded(String message) {
            return new CaptureResult(true, message);
        }

        public static CaptureResult failed(String message) {
            return new CaptureResult(false, message);
        }
    }

    public record HospitalReviveResult(boolean revived, String message) {}
}
