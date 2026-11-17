package com.campuslands.incidents.dto.analytics;

import java.time.LocalDate;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;

import org.springframework.format.annotation.DateTimeFormat;

import com.campuslands.incidents.entity.enums.IncidentPriority;

/**
 * Query parameters shared by every {@code /api/analytics} endpoint (RF06, RF07).
 * Every field is optional; {@code null} means "no filter". Dates filter on the creation date
 * and are inclusive, the same rule as the incident list, so both screens show the same incidents.
 */
public record AnalyticsFilter(

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate from,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate to,

        @Positive(message = "La categoría no es válida")
        Long categoryId,

        IncidentPriority priority) {

    public static AnalyticsFilter none() {
        return new AnalyticsFilter(null, null, null, null);
    }

    @AssertTrue(message = "La fecha inicial no puede ser posterior a la final")
    public boolean isDateRangeValid() {
        return from == null || to == null || !from.isAfter(to);
    }
}
