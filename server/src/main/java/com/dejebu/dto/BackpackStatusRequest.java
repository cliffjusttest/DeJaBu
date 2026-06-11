package com.dejebu.dto;

import jakarta.validation.constraints.NotBlank;

public record BackpackStatusRequest(@NotBlank String token) {}
