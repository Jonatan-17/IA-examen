package com.campuslands.incidents.dto.analytics;

/**
 * Dashboard indicator cards.
 *
 * @param resolutionRate     resolved / total × 100, {@code null} when there are no incidents
 * @param avgResolutionHours over resolved incidents, {@code null} when none was resolved
 * @param topCategory        most recurrent category, {@code null} when there are no incidents
 */
public record SummaryResponse(
        long total,
        long resolved,
        Double resolutionRate,
        Double avgResolutionHours,
        CategoryCount topCategory) {
}
