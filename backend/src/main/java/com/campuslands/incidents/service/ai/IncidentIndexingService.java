package com.campuslands.incidents.service.ai;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import com.campuslands.incidents.client.N8nClient;
import com.campuslands.incidents.config.AiFeaturesProperties;
import com.campuslands.incidents.dto.ai.N8nContracts.IncidentPayload;
import com.campuslands.incidents.dto.ai.N8nContracts.IndexIncidentsRequest;
import com.campuslands.incidents.dto.indexing.IndexingResult;
import com.campuslands.incidents.entity.Incident;
import com.campuslands.incidents.exception.AiServiceException;
import com.campuslands.incidents.repository.IncidentRepository;

// Mantiene los incidentes resueltos en Qdrant (flujo 03) para buscar similares
@Service
public class IncidentIndexingService {

    private static final int BATCH_SIZE = 20;
    private static final Logger log = LoggerFactory.getLogger(IncidentIndexingService.class);

    private final IncidentRepository incidentRepository;
    private final N8nClient n8nClient;
    private final AiFeaturesProperties props;

    public IncidentIndexingService(IncidentRepository incidentRepository, N8nClient n8nClient,
                                   AiFeaturesProperties props) {
        this.incidentRepository = incidentRepository;
        this.n8nClient = n8nClient;
        this.props = props;
    }

    // Se llama después de cambiar el estado; si falla solo se registra en el log
    public void sync(Long incidentId) {
        incidentRepository.findWithCategoryAndUserById(incidentId).ifPresent(incident -> {
            IndexingResult result = send(List.of(toPayload(incident)));
            if (!result.successful()) {
                log.warn("Incident {} not synced with the vector store: {}", incidentId, result.errors());
            }
        });
    }

    // Reindexa todos: resueltos se insertan, el resto se borra
    public IndexingResult reindexAll() {
        // Especificación vacía para usar el EntityGraph (categoría ya cargada)
        List<IncidentPayload> all = incidentRepository.findAll(Specification.allOf(List.of()), Sort.by("id")).stream()
                .map(IncidentIndexingService::toPayload)
                .toList();
        int indexed = 0;
        int deleted = 0;
        List<String> errors = new ArrayList<>();
        for (int from = 0; from < all.size(); from += BATCH_SIZE) {
            IndexingResult r = send(all.subList(from, Math.min(from + BATCH_SIZE, all.size())));
            indexed += r.indexed();
            deleted += r.deleted();
            errors.addAll(r.errors());
        }
        return new IndexingResult(indexed, deleted, errors);
    }

    private IndexingResult send(List<IncidentPayload> incidents) {
        try {
            IndexingResult result = n8nClient.post(props.incidentIndexingPath(),
                    new IndexIncidentsRequest(incidents), IndexingResult.class);
            return result == null ? IndexingResult.failed("Empty response from the indexing flow") : result;
        } catch (AiServiceException | RestClientException e) {
            return IndexingResult.failed("Could not index incidents: " + e.getMessage());
        }
    }

    private static IncidentPayload toPayload(Incident i) {
        return new IncidentPayload(i.getId(), i.getTitle(), i.getCategory().getName(), i.getDescription(),
                i.getResolution(), i.getStatus().isResolved());
    }
}
