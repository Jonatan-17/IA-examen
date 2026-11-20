package com.campuslands.incidents.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Texto de un incidente para clasificarlo o buscar similares
public record TextRequest(

        @Size(max = 200, message = "El título no puede superar 200 caracteres")
        String title,

        @NotBlank(message = "La descripción es obligatoria")
        @Size(max = 5000, message = "La descripción no puede superar 5000 caracteres")
        String description) {

    // Título y descripción juntos
    public String fullText() {
        return (title == null || title.isBlank() ? "" : title.trim() + "\n") + description.trim();
    }
}
