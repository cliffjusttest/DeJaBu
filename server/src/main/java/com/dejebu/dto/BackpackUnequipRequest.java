package com.dejebu.dto;

import jakarta.validation.constraints.NotBlank;

public record BackpackUnequipRequest(
        @NotBlank String token,
        @NotBlank String slot,
        Long companionId
) {}
