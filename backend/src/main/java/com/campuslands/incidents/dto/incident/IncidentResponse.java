package com.campuslands.incidents.dto.incident;

import java.time.Instant;

import com.campuslands.incidents.entity.enums.IncidentPriority;
import com.campuslands.incidents.entity.enums.IncidentStatus;

/**
 * Incident returned by the API (RF01). Category and user names are included so the frontend
 * table can show them without extra requests.
 *
 * @param resolvedAt {@code null} while the incident is OPEN or IN_PROGRESS
 */
public record IncidentResponse(
        Long id,
        String title,
        String description,
        IncidentPriority priority,
        IncidentStatus status,
        Long categoryId,
        String categoryName,
        Long userId,
        String userName,
        Instant createdAt,
        Instant resolvedAt,
        String resolution,
        Long suggestedCategoryId,
        IncidentPriority suggestedPriority,
        Instant slaDueAt,
        // ON_TIME / OVERDUE (abierto) o MET / MISSED (resuelto); null si no hay SLA
        String slaStatus,
        String location) {

    public IncidentResponse(Long id, String title, String description, IncidentPriority priority,
                            IncidentStatus status, Long categoryId, String categoryName, Long userId,
                            String userName, Instant createdAt, Instant resolvedAt) {
        this(id, title, description, priority, status, categoryId, categoryName, userId, userName,
                createdAt, resolvedAt, null, null, null, null, null, null);
    }
}
