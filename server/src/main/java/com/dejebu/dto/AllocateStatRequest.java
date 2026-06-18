package com.dejebu.dto;

import com.dejebu.game.CharacterStats;
import jakarta.validation.constraints.NotBlank;

public record AllocateStatRequest(
        @NotBlank(message = "請先登入")
        String token,

        @NotBlank(message = "請指定屬性")
        String stat
) {
}
