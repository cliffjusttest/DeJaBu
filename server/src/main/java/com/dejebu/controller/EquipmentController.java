package com.dejebu.controller;

import com.dejebu.dto.EquipRequest;
import com.dejebu.dto.EquipmentResponse;
import com.dejebu.dto.EquipmentStatusRequest;
import com.dejebu.dto.UnequipRequest;
import com.dejebu.entity.User;
import com.dejebu.service.AuthService;
import com.dejebu.service.EquipmentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/equipment")
public class EquipmentController {

    private final AuthService authService;
    private final EquipmentService equipmentService;

    public EquipmentController(AuthService authService, EquipmentService equipmentService) {
        this.authService = authService;
        this.equipmentService = equipmentService;
    }

    @PostMapping("/status")
    public EquipmentResponse status(@Valid @RequestBody EquipmentStatusRequest request) {
        User user = authService.validateToken(request.token())
                .orElseThrow(() -> new IllegalArgumentException("登入已失效，請重新登入"));
        return equipmentService.getStatus(user);
    }

    @PostMapping("/equip")
    public EquipmentResponse equip(@Valid @RequestBody EquipRequest request) {
        User user = authService.validateToken(request.token())
                .orElseThrow(() -> new IllegalArgumentException("登入已失效，請重新登入"));
        return equipmentService.equip(user, request.itemId());
    }

    @PostMapping("/unequip")
    public EquipmentResponse unequip(@Valid @RequestBody UnequipRequest request) {
        User user = authService.validateToken(request.token())
                .orElseThrow(() -> new IllegalArgumentException("登入已失效，請重新登入"));
        return equipmentService.unequip(user, request.slot());
    }
}
