package com.campuslands.incidents.dto.incident;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.campuslands.incidents.entity.enums.IncidentPriority;
import com.campuslands.incidents.entity.enums.IncidentStatus;

/**
 * Body for registering an incident (RF01). The creation date is set by the server.
 *
 * @param status optional; when {@code null} the incident is created as {@link IncidentStatus#OPEN}
 */
public record IncidentRequest(

        @NotBlank(message = "El título es obligatorio")
        @Size(max = 200, message = "El título no puede superar 200 caracteres")
        String title,

        @NotBlank(message = "La descripción es obligatoria")
        @Size(max = 5000, message = "La descripción no puede superar 5000 caracteres")
        String description,

        @NotNull(message = "La categoría es obligatoria")
        @Positive(message = "La categoría no es válida")
        Long categoryId,

        @NotNull(message = "La prioridad es obligatoria")
        IncidentPriority priority,

        IncidentStatus status,

        @NotNull(message = "El usuario responsable es obligatorio")
        @Positive(message = "El usuario no es válido")
        Long userId,

        // Opcionales: lo que sugirió la IA, para medir su acierto
        @Positive(message = "La categoría sugerida no es válida")
        Long suggestedCategoryId,

        IncidentPriority suggestedPriority,

        @Size(max = 100, message = "La ubicación no puede superar 100 caracteres")
        String location) {

    public IncidentRequest(String title, String description, Long categoryId, IncidentPriority priority,
                           IncidentStatus status, Long userId) {
        this(title, description, categoryId, priority, status, userId, null, null, null);
    }

    /** Status to store: the one sent by the client, or OPEN when it was omitted. */
    public IncidentStatus statusOrDefault() {
        return status == null ? IncidentStatus.OPEN : status;
    }
}
