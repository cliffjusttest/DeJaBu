package com.dejebu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UseConsumableRequest(@NotBlank String token, @NotNull Long itemId) {}
