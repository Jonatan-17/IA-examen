package com.campuslands.incidents.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Rutas de los flujos 03-05 de n8n y umbrales de las búsquedas semánticas esto lo hacemos para que se pueda cambiar el umbral o la similutes entre
// incidentes y articulos sin la necesidad de recopilar toda la informacion de nuevo solo cambiando el valor en el application.properties
@ConfigurationProperties(prefix = "app.ai-features")
public record AiFeaturesProperties(
        String incidentIndexingPath,
        String searchPath,
        String tasksPath,
        double similarIncidentsThreshold,
        double articleSearchThreshold) {

}
