package com.campuslands.incidents.dto.ai;

import com.campuslands.incidents.entity.enums.IncidentPriority;

// Categoría y prioridad sugeridas por la IA
public record ClassificationResponse(Long categoryId, String categoryName, IncidentPriority priority, String reason) {
}
