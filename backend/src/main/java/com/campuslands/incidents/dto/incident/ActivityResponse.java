package com.campuslands.incidents.dto.incident;

import java.time.Instant;

import com.campuslands.incidents.entity.enums.IncidentStatus;

// Entrada de la bitácora de un incidente
public record ActivityResponse(
        Long id,
        String type,
        IncidentStatus fromStatus,
        IncidentStatus toStatus,
        Long userId,
        String userName,
        String comment,
        Instant createdAt) {
}
