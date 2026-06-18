package com.dejebu.service;

import com.dejebu.dto.StatAllocationResponse;
import com.dejebu.entity.User;
import com.dejebu.game.CharacterStats;
import com.dejebu.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

@Service
public class CharacterService {

    private static final Map<String, StatField> STAT_FIELDS = Map.of(
            "might", StatField.MIGHT,
            "intelligence", StatField.INTELLIGENCE,
            "vitality", StatField.VITALITY,
            "defense", StatField.DEFENSE,
            "spirit", StatField.SPIRIT,
            "luck", StatField.LUCK,
            "agility", StatField.AGILITY
    );

    private static final Set<String> VALID_STATS = STAT_FIELDS.keySet();

    private final UserRepository userRepository;

    public CharacterService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public StatAllocationResponse allocateStatPoint(User user, String statCode) {
        if (!user.isHasCharacter()) {
            throw new IllegalArgumentException("請先創建角色");
        }
        if (user.getStatPoints() <= 0) {
            throw new IllegalArgumentException("屬性點不足");
        }

        StatField field = STAT_FIELDS.get(statCode);
        if (field == null) {
            throw new IllegalArgumentException("無效的屬性：" + statCode);
        }

        field.increment(user);
        user.setStatPoints(user.getStatPoints() - 1);
        user.setPlayerCurrentHp(user.resolveCurrentHp());
        user.setPlayerCurrentMp(user.resolveCurrentMp());
        userRepository.save(user);

        CharacterStats stats = CharacterStats.fromUser(user);
        return new StatAllocationResponse(
                stats,
                user.getStatPoints(),
                user.resolveMaxHp(),
                user.resolveMaxMp(),
                user.resolveCurrentHp(),
                user.resolveCurrentMp(),
                field.displayName() + " +1"
        );
    }

    public static boolean isValidStatCode(String statCode) {
        return VALID_STATS.contains(statCode);
    }

    private enum StatField {
        MIGHT("武力") {
            @Override
            void increment(User user) {
                user.setStatMight(user.getStatMight() + 1);
            }
        },
        INTELLIGENCE("智力") {
            @Override
            void increment(User user) {
                user.setStatIntelligence(user.getStatIntelligence() + 1);
            }
        },
        VITALITY("體力") {
            @Override
            void increment(User user) {
                user.setStatVitality(user.getStatVitality() + 1);
            }
        },
        DEFENSE("防禦") {
            @Override
            void increment(User user) {
                user.setStatDefense(user.getStatDefense() + 1);
            }
        },
        SPIRIT("精神") {
            @Override
            void increment(User user) {
                user.setStatSpirit(user.getStatSpirit() + 1);
            }
        },
        LUCK("幸運") {
            @Override
            void increment(User user) {
                user.setStatLuck(user.getStatLuck() + 1);
            }
        },
        AGILITY("敏捷") {
            @Override
            void increment(User user) {
                user.setStatAgility(user.getStatAgility() + 1);
            }
        };

        private final String displayName;

        StatField(String displayName) {
            this.displayName = displayName;
        }

        String displayName() {
            return displayName;
        }

        abstract void increment(User user);
    }
}
