package com.dejebu.dto;

import java.util.List;

public record CompanionListResponse(
        List<CompanionDto> companions,
        String message,
        int skillPoints
) {
}
