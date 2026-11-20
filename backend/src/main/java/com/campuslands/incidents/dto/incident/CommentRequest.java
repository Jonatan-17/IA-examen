package com.campuslands.incidents.dto.incident;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

// Comentario nuevo en la bitácora
public record CommentRequest(

        @NotNull(message = "El autor es obligatorio")
        @Positive(message = "El usuario no es válido")
        Long userId,

        @NotBlank(message = "El comentario es obligatorio")
        @Size(max = 2000, message = "El comentario no puede superar 2000 caracteres")
        String comment) {
}
