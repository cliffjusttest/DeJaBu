package com.dejebu.service;

import com.dejebu.entity.User;
import com.dejebu.game.StoryEra;
import com.dejebu.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class StoryContextService {

    private final PlayerPartyService playerPartyService;
    private final UserRepository userRepository;

    public StoryContextService(PlayerPartyService playerPartyService, UserRepository userRepository) {
        this.playerPartyService = playerPartyService;
        this.userRepository = userRepository;
    }

    public Long resolveStoryUserId(Long userId) {
        if (playerPartyService.isInParty(userId)) {
            return playerPartyService.getLeaderId(userId);
        }
        return userId;
    }

    public StoryEra resolveStoryEra(Long userId) {
        return StoryEra.fromCode(resolveStoryEraCode(userId));
    }

    public String resolveStoryEraCode(Long userId) {
        Long storyUserId = resolveStoryUserId(userId);
        return userRepository.findById(storyUserId)
                .map(User::getStoryEra)
                .orElse(StoryEra.E1.name());
    }

    public boolean isStoryActor(Long userId) {
        return userId.equals(resolveStoryUserId(userId));
    }
}
