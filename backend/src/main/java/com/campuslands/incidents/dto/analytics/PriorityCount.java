package com.campuslands.incidents.dto.analytics;

import com.campuslands.incidents.entity.enums.IncidentPriority;

/** Incidents of one priority and their share of the filtered total. */
public record PriorityCount(IncidentPriority priority, long incidents, double percentage) {
}
