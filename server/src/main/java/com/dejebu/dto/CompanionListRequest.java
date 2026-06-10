package com.dejebu.dto;

import jakarta.validation.constraints.NotBlank;

public record CompanionListRequest(
        @NotBlank String token
) {
}
