package com.dejebu.dto;

import jakarta.validation.constraints.NotBlank;

public record EquipmentStatusRequest(@NotBlank String token) {}
