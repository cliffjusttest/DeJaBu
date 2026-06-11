package com.dejebu.dto;

import jakarta.validation.constraints.NotBlank;

public record UnequipRequest(@NotBlank String token, @NotBlank String slot) {}
