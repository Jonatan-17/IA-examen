package com.campuslands.incidents.dto.incident;

import java.time.LocalDate;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import org.springframework.format.annotation.DateTimeFormat;

import com.campuslands.incidents.entity.enums.IncidentPriority;
import com.campuslands.incidents.entity.enums.IncidentStatus;

/**
 * Query parameters of {@code GET /api/incidents} (RF01). Every field is optional; {@code null} means "no filter".
 * Dates filter on the creation date and are inclusive, e.g. {@code ?from=2026-10-01&to=2026-10-31}.
 */
public record IncidentFilter(

        @Positive(message = "La categoría no es válida")
        Long categoryId,

        IncidentPriority priority,

        IncidentStatus status,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate from,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate to,

        // Texto en título o descripción
        @Size(max = 100, message = "La búsqueda no puede superar 100 caracteres")
        String q,

        @Positive(message = "El usuario no es válido")
        Long userId) {

    public IncidentFilter(Long categoryId, IncidentPriority priority, IncidentStatus status,
                          LocalDate from, LocalDate to) {
        this(categoryId, priority, status, from, to, null, null);
    }

    @AssertTrue(message = "La fecha inicial no puede ser posterior a la final")
    public boolean isDateRangeValid() {
        return from == null || to == null || !from.isAfter(to);
    }
}
