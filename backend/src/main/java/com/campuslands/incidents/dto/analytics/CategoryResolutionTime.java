package com.campuslands.incidents.dto.analytics;

/** Average resolution time of one category; {@code avgHours} is {@code null} if none was resolved. */
public record CategoryResolutionTime(Long categoryId, String category, long resolvedIncidents, Double avgHours) {
}
