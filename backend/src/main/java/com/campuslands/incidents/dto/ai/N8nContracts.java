package com.campuslands.incidents.dto.ai;

import java.util.List;

// Cuerpos que se envían y reciben de los flujos 03, 04 y 05 de n8n
public final class N8nContracts {

    private N8nContracts() {
    }

    // Flujo 03: indexar incidentes (active=false lo borra de Qdrant)
    public record IncidentPayload(long id, String title, String category, String description,
                                  String resolution, boolean active) {
    }

    public record IndexIncidentsRequest(List<IncidentPayload> incidents) {
    }

    // Flujo 04: búsqueda semántica en "articles" o "incidents"
    public record SearchRequest(String collection, String text, double threshold, int topK) {
    }

    public record SearchHit(long id, double score) {
    }

    public record SearchResponse(List<SearchHit> hits) {
        public SearchResponse {
            hits = hits == null ? List.of() : hits;
        }
    }

    // Flujo 05: tareas con el modelo
    public record CategoryOption(Long id, String name, String description) {
    }

    public record ClassifyRequest(String task, String text, List<CategoryOption> categories) {
    }

    public record ClassifyResult(Long categoryId, String priority, String reason) {
    }

    public record DraftRequest(String task, String title, String category, String description,
                               String resolution, List<String> comments) {
    }

    public record DraftResult(String title, String problem, String symptoms, String cause, String solution) {
    }

    public record SummaryRequest(String task, String title, String description, String status,
                                 String resolution, List<String> events) {
    }

    public record SummaryResult(String summary) {
    }
}
