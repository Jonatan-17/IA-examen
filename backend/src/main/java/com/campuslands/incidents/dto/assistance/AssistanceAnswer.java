package com.campuslands.incidents.dto.assistance;

import java.util.List;

/**
 * Answer written by the model from the knowledge articles (user-facing text, in Spanish).
 * Same shape as {@code answer} in the n8n contract (n8n/README.md).
 *
 * @param confidence "alta", "media" o "baja"
 * @param escalate   true si el modelo recomienda escalar a soporte
 */
public record AssistanceAnswer(String solution, List<String> steps, String confidence, Boolean escalate) {

    public AssistanceAnswer {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    public AssistanceAnswer(String solution, List<String> steps) {
        this(solution, steps, null, null);
    }
}
