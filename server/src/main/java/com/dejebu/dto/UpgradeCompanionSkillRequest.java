package com.dejebu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpgradeCompanionSkillRequest(
        @NotBlank(message = "請先登入")
        String token,

        @NotNull(message = "請指定夥伴")
        Long companionId,

        @NotNull(message = "請指定技能")
        Long skillId
) {
}
