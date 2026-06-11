package com.dejebu.controller;

import com.dejebu.dto.BackpackEquipRequest;
import com.dejebu.dto.BackpackResponse;
import com.dejebu.dto.BackpackStatusRequest;
import com.dejebu.dto.BackpackUnequipRequest;
import com.dejebu.entity.User;
import com.dejebu.service.AuthService;
import com.dejebu.service.BackpackService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/backpack")
public class BackpackController {

    private final AuthService authService;
    private final BackpackService backpackService;

    public BackpackController(AuthService authService, BackpackService backpackService) {
        this.authService = authService;
        this.backpackService = backpackService;
    }

    @PostMapping("/status")
    public BackpackResponse status(@Valid @RequestBody BackpackStatusRequest request) {
        User user = authService.validateToken(request.token())
                .orElseThrow(() -> new IllegalArgumentException("登入已失效，請重新登入"));
        return backpackService.getBackpack(user);
    }

    @PostMapping("/equip")
    public BackpackResponse equip(@Valid @RequestBody BackpackEquipRequest request) {
        User user = authService.validateToken(request.token())
                .orElseThrow(() -> new IllegalArgumentException("登入已失效，請重新登入"));
        if (request.companionId() != null) {
            return backpackService.equipToCompanion(user, request.companionId(), request.itemId());
        }
        return backpackService.equipToPlayer(user, request.itemId());
    }

    @PostMapping("/unequip")
    public BackpackResponse unequip(@Valid @RequestBody BackpackUnequipRequest request) {
        User user = authService.validateToken(request.token())
                .orElseThrow(() -> new IllegalArgumentException("登入已失效，請重新登入"));
        if (request.companionId() != null) {
            return backpackService.unequipFromCompanion(user, request.companionId(), request.slot());
        }
        return backpackService.unequipFromPlayer(user, request.slot());
    }
}
