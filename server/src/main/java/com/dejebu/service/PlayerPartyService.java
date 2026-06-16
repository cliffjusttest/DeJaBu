package com.dejebu.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PlayerPartyService {

    public static final int MAX_PARTY_SIZE = 5;

    private final Map<Long, Party> partiesByLeader = new ConcurrentHashMap<>();
    private final Map<Long, Long> memberToLeader = new ConcurrentHashMap<>();
    private final Map<Long, Long> pendingInvites = new ConcurrentHashMap<>();

    public boolean isInParty(Long userId) {
        return memberToLeader.containsKey(userId);
    }

    public boolean isLeader(Long userId) {
        Long leader = memberToLeader.get(userId);
        return leader != null && leader.equals(userId);
    }

    public Long getLeaderId(Long userId) {
        return memberToLeader.get(userId);
    }

    public List<Long> getMemberIds(Long leaderId) {
        Party party = partiesByLeader.get(leaderId);
        if (party == null) {
            return List.of();
        }
        return new ArrayList<>(party.memberIds);
    }

    public List<Long> getMemberIdsIfInParty(Long userId) {
        if (!isInParty(userId)) {
            return List.of(userId);
        }
        return getMemberIds(getLeaderId(userId));
    }

    public Optional<Long> getPendingInvite(Long inviteeId) {
        return Optional.ofNullable(pendingInvites.get(inviteeId));
    }

    public void invite(Long inviterId, Long inviteeId) {
        if (inviterId.equals(inviteeId)) {
            throw new IllegalArgumentException("無法邀請自己");
        }
        if (isInParty(inviteeId)) {
            throw new IllegalArgumentException("對方已在組隊中");
        }

        Long inviterLeader = memberToLeader.get(inviterId);
        if (inviterLeader != null) {
            Party party = partiesByLeader.get(inviterLeader);
            if (party != null && party.memberIds.size() >= MAX_PARTY_SIZE) {
                throw new IllegalArgumentException("隊伍已滿");
            }
        }

        pendingInvites.put(inviteeId, inviterId);
    }

    public List<Long> acceptInvite(Long inviteeId) {
        Long inviterId = pendingInvites.remove(inviteeId);
        if (inviterId == null) {
            throw new IllegalArgumentException("沒有待接受的組隊邀請");
        }
        return joinParty(inviteeId, inviterId);
    }

    public List<Long> joinParty(Long userId, Long inviterId) {
        Long leaderId = memberToLeader.get(inviterId);
        if (leaderId == null) {
            leaderId = inviterId;
            Party party = new Party(leaderId, new ArrayList<>(List.of(inviterId)));
            partiesByLeader.put(leaderId, party);
            memberToLeader.put(inviterId, leaderId);
        }

        Party party = partiesByLeader.get(leaderId);
        if (party == null) {
            throw new IllegalStateException("組隊資料異常");
        }
        if (party.memberIds.size() >= MAX_PARTY_SIZE) {
            throw new IllegalArgumentException("隊伍已滿");
        }
        if (!party.memberIds.contains(userId)) {
            party.memberIds.add(userId);
            memberToLeader.put(userId, leaderId);
        }
        return new ArrayList<>(party.memberIds);
    }

    public List<Long> leave(Long userId) {
        Long leaderId = memberToLeader.remove(userId);
        if (leaderId == null) {
            return List.of();
        }

        Party party = partiesByLeader.get(leaderId);
        if (party == null) {
            return List.of();
        }

        party.memberIds.remove(userId);
        pendingInvites.remove(userId);

        if (party.memberIds.isEmpty()) {
            partiesByLeader.remove(leaderId);
            return List.of();
        }

        if (userId.equals(leaderId)) {
            Long newLeader = party.memberIds.get(0);
            partiesByLeader.remove(leaderId);
            party.leaderId = newLeader;
            partiesByLeader.put(newLeader, party);
            for (Long memberId : party.memberIds) {
                memberToLeader.put(memberId, newLeader);
            }
        }

        return new ArrayList<>(party.memberIds);
    }

    public void kick(Long leaderId, Long targetId) {
        if (!isLeader(leaderId)) {
            throw new IllegalArgumentException("只有隊長可以踢人");
        }
        if (leaderId.equals(targetId)) {
            throw new IllegalArgumentException("無法踢出自己");
        }
        if (!isInParty(targetId) || !getLeaderId(targetId).equals(leaderId)) {
            throw new IllegalArgumentException("該玩家不在你的隊伍中");
        }
        leave(targetId);
    }

    public void declineInvite(Long inviteeId) {
        pendingInvites.remove(inviteeId);
    }

    public void onDisconnect(Long userId) {
        if (isInParty(userId)) {
            leave(userId);
        } else {
            pendingInvites.remove(userId);
            pendingInvites.entrySet().removeIf(entry -> entry.getValue().equals(userId));
        }
    }

    public String partyBattleId(Long leaderId) {
        return "party:" + leaderId;
    }

    static final class Party {
        Long leaderId;
        final List<Long> memberIds;

        Party(Long leaderId, List<Long> memberIds) {
            this.leaderId = leaderId;
            this.memberIds = memberIds;
        }
    }
}
