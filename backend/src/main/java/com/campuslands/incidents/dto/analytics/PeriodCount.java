package com.campuslands.incidents.dto.analytics;

import java.time.LocalDate;

/** Incidents created in the period (week or month) that starts on {@code periodStart}. */
public record PeriodCount(LocalDate periodStart, long incidents) {
}
