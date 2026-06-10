package com.dejebu.controller;

import com.dejebu.dto.CompanionListRequest;
import com.dejebu.dto.CompanionListResponse;
import com.dejebu.dto.SetCompanionPartyRequest;
import com.dejebu.dto.UpgradeCompanionSkillRequest;
import com.dejebu.entity.User;
import com.dejebu.service.AuthService;
import com.dejebu.service.CompanionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/companions")
public class CompanionController {

    private final AuthService authService;
    private final CompanionService companionService;

    public CompanionController(AuthService authService, CompanionService companionService) {
        this.authService = authService;
        this.companionService = companionService;
    }

    @PostMapping("/list")
    public CompanionListResponse list(@Valid @RequestBody CompanionListRequest request) {
        User user = authService.validateToken(request.token())
                .orElseThrow(() -> new IllegalArgumentException("登入已失效，請重新登入"));
        return companionService.listCompanions(user);
    }

    @PostMapping("/party")
    public CompanionListResponse setParty(@Valid @RequestBody SetCompanionPartyRequest request) {
        User user = authService.validateToken(request.token())
                .orElseThrow(() -> new IllegalArgumentException("登入已失效，請重新登入"));
        return companionService.setPartyStatus(user, request.companionId(), request.active());
    }

    @PostMapping("/skills/upgrade")
    public CompanionListResponse upgradeSkill(@Valid @RequestBody UpgradeCompanionSkillRequest request) {
        User user = authService.validateToken(request.token())
                .orElseThrow(() -> new IllegalArgumentException("登入已失效，請重新登入"));
        return companionService.upgradeCompanionSkill(user, request.companionId(), request.skillId());
    }
}
