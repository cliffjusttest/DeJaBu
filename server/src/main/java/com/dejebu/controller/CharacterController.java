package com.dejebu.controller;

import com.dejebu.dto.AuthResponse;
import com.dejebu.dto.AllocateStatRequest;
import com.dejebu.dto.CreateCharacterRequest;
import com.dejebu.dto.StatAllocationResponse;
import com.dejebu.service.AuthService;
import com.dejebu.service.CharacterService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/character")
public class CharacterController {

    private final AuthService authService;
    private final CharacterService characterService;

    public CharacterController(AuthService authService, CharacterService characterService) {
        this.authService = authService;
        this.characterService = characterService;
    }

    @PostMapping("/create")
    public AuthResponse create(@Valid @RequestBody CreateCharacterRequest request) {
        return authService.createCharacter(request);
    }

    @PostMapping("/allocate-stat")
    public StatAllocationResponse allocateStat(@Valid @RequestBody AllocateStatRequest request) {
        var user = authService.validateToken(request.token())
                .orElseThrow(() -> new IllegalArgumentException("登入已失效，請重新登入"));
        return characterService.allocateStatPoint(user, request.stat());
    }
}
