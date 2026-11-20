package com.campuslands.incidents.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Rutas de los flujos 03-05 de n8n y umbrales de las búsquedas semánticas
@ConfigurationProperties(prefix = "app.ai-features")
public record AiFeaturesProperties(
        String incidentIndexingPath,
        String searchPath,
        String tasksPath,
        double similarIncidentsThreshold,
        double articleSearchThreshold) {
}
