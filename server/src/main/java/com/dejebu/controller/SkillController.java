package com.dejebu.controller;

import com.dejebu.dto.LearnSkillRequest;
import com.dejebu.dto.SkillTreeRequest;
import com.dejebu.dto.SkillTreeResponse;
import com.dejebu.dto.UpgradeSkillRequest;
import com.dejebu.entity.User;
import com.dejebu.service.AuthService;
import com.dejebu.service.SkillService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/skills")
public class SkillController {

    private final AuthService authService;
    private final SkillService skillService;

    public SkillController(AuthService authService, SkillService skillService) {
        this.authService = authService;
        this.skillService = skillService;
    }

    @PostMapping("/tree")
    public SkillTreeResponse tree(@Valid @RequestBody SkillTreeRequest request) {
        User user = authService.validateToken(request.token())
                .orElseThrow(() -> new IllegalArgumentException("登入已失效，請重新登入"));
        return skillService.getSkillTree(user);
    }

    @PostMapping("/learn")
    public SkillTreeResponse learn(@Valid @RequestBody LearnSkillRequest request) {
        User user = authService.validateToken(request.token())
                .orElseThrow(() -> new IllegalArgumentException("登入已失效，請重新登入"));
        return skillService.learnSkill(user, request.skillId());
    }

    @PostMapping("/upgrade")
    public SkillTreeResponse upgrade(@Valid @RequestBody UpgradeSkillRequest request) {
        User user = authService.validateToken(request.token())
                .orElseThrow(() -> new IllegalArgumentException("登入已失效，請重新登入"));
        return skillService.upgradeSkill(user, request.skillId());
    }
}
