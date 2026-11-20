package com.campuslands.incidents.dto.incident;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.campuslands.incidents.entity.enums.IncidentStatus;

/**
 * Body for {@code PATCH /api/incidents/{id}/status} (RF01).
 * Moving to RESOLVED or CLOSED sets {@code resolvedAt}; moving back to OPEN or IN_PROGRESS clears it.
 */
public record IncidentStatusUpdateRequest(

        @NotNull(message = "El estado es obligatorio")
        IncidentStatus status,

        // Opcional: cómo se resolvió
        @Size(max = 5000, message = "La resolución no puede superar 5000 caracteres")
        String resolution,

        // Opcional: quién hizo el cambio (para la bitácora)
        @Positive(message = "El usuario no es válido")
        Long userId) {

    public IncidentStatusUpdateRequest(IncidentStatus status) {
        this(status, null, null);
    }
}
