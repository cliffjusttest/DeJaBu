package com.dejebu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank
        @Size(min = 3, max = 32)
        @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "帳號只能包含英數與底線")
        String username,

        @NotBlank
        @Size(min = 6, max = 64)
        String password
) {
}
