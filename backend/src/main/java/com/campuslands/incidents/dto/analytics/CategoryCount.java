package com.campuslands.incidents.dto.analytics;

/** Incidents of one category and their share of the filtered total (0 when there are none). */
public record CategoryCount(Long categoryId, String category, long incidents, double percentage) {
}
