package com.dejebu.dto;

import jakarta.validation.constraints.NotBlank;

public record SkillTreeRequest(
        @NotBlank(message = "請先登入")
        String token
) {
}
