package com.campuslands.incidents.dto.ai;

import java.time.Instant;

import com.campuslands.incidents.entity.enums.IncidentStatus;

// Incidente resuelto parecido al que se describe
public record SimilarIncidentResponse(
        Long id,
        String title,
        String categoryName,
        IncidentStatus status,
        String resolution,
        Instant resolvedAt,
        double similarity) {
}
