package com.dejebu.dto;

import com.dejebu.game.CharacterAppearance;
import com.dejebu.game.CharacterStats;
import com.dejebu.game.Element;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCharacterRequest(
        @NotBlank(message = "請先登入")
        String token,

        @NotBlank
        @Size(min = 1, max = 32)
        String displayName,

        @NotNull(message = "請選擇元素屬性")
        Element element,

        @NotNull(message = "請選擇角色外型")
        CharacterAppearance appearance,

        @NotNull(message = "請分配能力點數")
        CharacterStats stats
) {
}
