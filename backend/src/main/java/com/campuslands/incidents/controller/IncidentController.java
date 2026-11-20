package com.campuslands.incidents.controller;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.validation.Valid;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.campuslands.incidents.dto.incident.ActivityResponse;
import com.campuslands.incidents.dto.incident.CommentRequest;
import com.campuslands.incidents.dto.incident.IncidentFilter;
import com.campuslands.incidents.dto.incident.IncidentRequest;
import com.campuslands.incidents.dto.incident.IncidentResponse;
import com.campuslands.incidents.dto.incident.IncidentStatusUpdateRequest;
import com.campuslands.incidents.dto.incident.PageResponse;
import com.campuslands.incidents.service.ai.IncidentIndexingService;
import com.campuslands.incidents.service.incident.IncidentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/incidents")
@Tag(name = "Incidents", description = "Register, list, filter and change the status of incidents (RF01)")
public class IncidentController {

    private final IncidentService incidentService;
    private final IncidentIndexingService incidentIndexing;

    public IncidentController(IncidentService incidentService, IncidentIndexingService incidentIndexing) {
        this.incidentService = incidentService;
        this.incidentIndexing = incidentIndexing;
    }

    /** Filters are query parameters; @ParameterObject shows each one as its own field in Swagger. */
    @GetMapping
    @Operation(summary = "List incidents, newest first, with optional filters",
            description = "Filters: categoryId, priority (P1-P4), status, from and to (yyyy-MM-dd, inclusive).")
    public List<IncidentResponse> findAll(@Valid @ParameterObject IncidentFilter filter) {
        return incidentService.findAll(filter);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an incident by id (404 if it does not exist)")
    public IncidentResponse findById(@PathVariable Long id) {
        return incidentService.findById(id);
    }

    @PostMapping
    @Operation(summary = "Register an incident (404 if the category or user does not exist)",
            description = "Status is optional and defaults to OPEN. The creation date is set by the server.")
    public ResponseEntity<IncidentResponse> create(@Valid @RequestBody IncidentRequest request) {
        IncidentResponse created = incidentService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Change the status of an incident",
            description = "RESOLVED and CLOSED set resolvedAt; OPEN and IN_PROGRESS clear it.")
    public IncidentResponse changeStatus(@PathVariable Long id,
                                         @Valid @RequestBody IncidentStatusUpdateRequest request) {
        IncidentResponse updated = incidentService.changeStatus(id, request);
        // Ya confirmado en BD: actualiza Qdrant para la búsqueda de similares (si falla, solo se registra)
        incidentIndexing.sync(id);
        return updated;
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edit title, description, category, priority, user and location (status is ignored)")
    public IncidentResponse update(@PathVariable Long id, @Valid @RequestBody IncidentRequest request) {
        IncidentResponse updated = incidentService.update(id, request);
        incidentIndexing.sync(id);
        return updated;
    }

    @GetMapping("/paged")
    @Operation(summary = "Paginated incident list with the same filters")
    public PageResponse<IncidentResponse> paged(@Valid @ParameterObject IncidentFilter filter,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return incidentService.findPage(filter, page, size);
    }

    @GetMapping(value = "/export", produces = "text/csv")
    @Operation(summary = "Download the filtered incident list as CSV")
    public ResponseEntity<String> export(@Valid @ParameterObject IncidentFilter filter) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"incidentes.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(incidentService.exportCsv(filter));
    }

    @GetMapping("/{id}/activity")
    @Operation(summary = "Incident timeline: creation, status changes and comments")
    public List<ActivityResponse> activity(@PathVariable Long id) {
        return incidentService.activity(id);
    }

    @PostMapping("/{id}/comments")
    @Operation(summary = "Add a comment to the incident timeline")
    public ResponseEntity<ActivityResponse> comment(@PathVariable Long id,
                                                    @Valid @RequestBody CommentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(incidentService.addComment(id, request));
    }
}
