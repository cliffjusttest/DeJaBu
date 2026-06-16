package com.dejebu.service;

import com.dejebu.entity.User;
import com.dejebu.entity.UserCompanion;
import com.dejebu.game.BattleUnit;
import com.dejebu.repository.UserRepository;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class BattleDeathService {

    public static final double EXP_LOSS_RATE = ProgressionService.EXP_LOSS_RATE;
    public static final int COMPANION_INCAPACITATED_MINUTES = 10;

    private final UserRepository userRepository;
    private final AuthService authService;
    private final ProgressionService progressionService;
    private final HospitalService hospitalService;
    private final MapService mapService;
    private final CompanionService companionService;

    public BattleDeathService(
            UserRepository userRepository,
            AuthService authService,
            ProgressionService progressionService,
            HospitalService hospitalService,
            MapService mapService,
            CompanionService companionService
    ) {
        this.userRepository = userRepository;
        this.authService = authService;
        this.progressionService = progressionService;
        this.hospitalService = hospitalService;
        this.mapService = mapService;
        this.companionService = companionService;
    }

    public record ProcessOutcomeResult(Set<Long> respawnedPlayerIds) {}

    @Transactional
    public ProcessOutcomeResult processDefeatOrEscape(
            List<Long> participantUserIds,
            List<BattleUnit> allies,
            Map<Long, Integer> userPlayerUnitIds,
            ObjectNode result,
            boolean escaped
    ) {
        Set<Long> respawnedPlayerIds = new HashSet<>();
        ArrayNode outcomes = result.putArray("deathOutcomes");

        for (Long userId : participantUserIds) {
            int playerUnitId = userPlayerUnitIds.get(userId);
            Optional<BattleUnit> playerUnit = findUnit(allies, playerUnitId);
            if (playerUnit.isEmpty()) {
                continue;
            }

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalStateException("玩家不存在"));

            ObjectNode outcome = outcomes.addObject();
            outcome.put("playerId", userId);
            outcome.put("escaped", escaped);

            ArrayNode companionResults = outcome.putArray("companionResults");
            boolean playerDead = playerUnit.get().getHp() <= 0;

            if (playerDead) {
                int expLost = progressionService.applyExpLoss(user);
                HospitalService.HospitalLocation hospital = hospitalService.findNearest(
                        user.getPlayerMapId(),
                        user.getPlayerX(),
                        user.getPlayerY()
                );
                authService.updatePlayerPosition(userId, hospital.mapId(), hospital.respawnX(), hospital.respawnY());
                authService.syncPlayerHp(userId, user.resolveMaxHp());
                authService.syncPlayerMp(userId, user.resolveMaxMp());
                respawnedPlayerIds.add(userId);

                outcome.put("playerDied", true);
                outcome.put("expLost", expLost);
                outcome.put("playerExp", user.getExp());
                outcome.put("expToNextLevel", ProgressionService.expToNextLevel(user.getLevel()));
                outcome.put("playerLevel", user.getLevel());
                outcome.put("teleportMapId", hospital.mapId());
                outcome.put("teleportX", hospital.respawnX());
                outcome.put("teleportY", hospital.respawnY());
                outcome.put("teleportMapName", mapService.getMapName(hospital.mapId()));
                outcome.put("playerCurrentHp", user.resolveMaxHp());
                outcome.put("playerMaxHp", user.resolveMaxHp());
                outcome.put("playerCurrentMp", user.resolveMaxMp());
                outcome.put("playerMaxMp", user.resolveMaxMp());
                outcome.put(
                        "message",
                        String.format(
                                "你在戰鬥中陣亡，損失了 %d 經驗值，已被送往%s。",
                                expLost,
                                hospital.name()
                        )
                );
            } else {
                outcome.put("playerDied", false);
            }

            for (BattleUnit ally : allies) {
                if (!ally.isCompanion()) {
                    continue;
                }
                if (ally.getOwnerUserId() == null || !ally.getOwnerUserId().equals(userId)) {
                    continue;
                }
                if (ally.getHp() > 0) {
                    continue;
                }

                Optional<UserCompanion> companionOpt = companionService.findCompanionEntityForBattleUnit(userId, ally);
                if (companionOpt.isEmpty()) {
                    continue;
                }

                UserCompanion companion = companionOpt.get();
                int expLost = progressionService.calculateExpLoss(companion.getExp());
                companionService.applyCompanionDefeat(userId, ally);

                ObjectNode companionNode = companionResults.addObject();
                companionNode.put("companionId", companion.getId());
                companionNode.put("nickname", companion.getNickname());
                companionNode.put("expLost", expLost);
                companionNode.put("incapacitatedMinutes", COMPANION_INCAPACITATED_MINUTES);
                companionNode.put("awaitingHospitalRevive", false);
            }

            if (!playerDead && !companionResults.isEmpty()) {
                outcome.put(
                        "message",
                        "夥伴戰力耗盡，需休養 "
                                + COMPANION_INCAPACITATED_MINUTES
                                + " 分鐘後至醫館治療才能再次出戰。"
                );
            }
        }

        if (outcomes.size() == 1) {
            result.set("deathResult", outcomes.get(0));
        }

        return new ProcessOutcomeResult(respawnedPlayerIds);
    }

    private Optional<BattleUnit> findUnit(List<BattleUnit> units, int id) {
        return units.stream().filter(unit -> unit.getId() == id).findFirst();
    }
}
