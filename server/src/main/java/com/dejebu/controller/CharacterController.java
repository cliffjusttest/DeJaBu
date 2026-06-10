package com.dejebu.controller;

import com.dejebu.dto.AuthResponse;
import com.dejebu.dto.CreateCharacterRequest;
import com.dejebu.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/character")
public class CharacterController {

    private final AuthService authService;

    public CharacterController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/create")
    public AuthResponse create(@Valid @RequestBody CreateCharacterRequest request) {
        return authService.createCharacter(request);
    }
}
