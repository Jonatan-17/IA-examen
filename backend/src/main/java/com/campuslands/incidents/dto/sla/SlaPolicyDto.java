package com.campuslands.incidents.dto.sla;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import com.campuslands.incidents.entity.enums.IncidentPriority;

// Horas máximas de resolución de una prioridad
public record SlaPolicyDto(
        IncidentPriority priority,

        @Min(value = 1, message = "Debe ser al menos 1 hora")
        @Max(value = 2000, message = "No puede superar 2000 horas")
        int maxHours) {
}
