package com.campuslands.incidents.config;

import org.springframework.boot.context.properties.ConfigurationProperties;


// incidentes y articulos sin la necesidad de recopilar toda la informacion de nuevo solo cambiando el valor en el application.properties
@ConfigurationProperties(prefix = "app.ai-features")
public record AiFeaturesProperties(
        String incidentIndexingPath,
        String searchPath,
        String tasksPath,
        double similarIncidentsThreshold,
        double articleSearchThreshold) {

}
