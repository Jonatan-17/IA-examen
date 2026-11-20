package com.campuslands.incidents.service.incident;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.campuslands.incidents.dto.incident.ActivityResponse;
import com.campuslands.incidents.dto.incident.CommentRequest;
import com.campuslands.incidents.dto.incident.IncidentFilter;
import com.campuslands.incidents.dto.incident.IncidentRequest;
import com.campuslands.incidents.dto.incident.IncidentResponse;
import com.campuslands.incidents.dto.incident.IncidentStatusUpdateRequest;
import com.campuslands.incidents.entity.Category;
import com.campuslands.incidents.entity.Incident;
import com.campuslands.incidents.entity.IncidentActivity;
import com.campuslands.incidents.entity.SlaPolicy;
import com.campuslands.incidents.entity.User;
import com.campuslands.incidents.entity.enums.IncidentPriority;
import com.campuslands.incidents.entity.enums.IncidentStatus;
import com.campuslands.incidents.dto.incident.PageResponse;
import com.campuslands.incidents.exception.ConflictException;
import com.campuslands.incidents.exception.ResourceNotFoundException;
import com.campuslands.incidents.repository.CategoryRepository;
import com.campuslands.incidents.repository.IncidentActivityRepository;
import com.campuslands.incidents.repository.IncidentRepository;
import com.campuslands.incidents.repository.SlaPolicyRepository;
import com.campuslands.incidents.repository.UserRepository;

/**
 * Incident management (RF01): register, list with filters and change the status.
 * Incidents are not edited or deleted: they are the history the analytics are computed from.
 */
@Service
@Transactional(readOnly = true)
public class IncidentService {

    /** Newest first; id breaks ties between incidents created in the same instant. */
    static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
    static final String CLOSE_ONLY_RESOLVED = "Solo se puede cerrar un incidente que ya está resuelto";
    static final String P1_NEEDS_RESOLUTION = "Un incidente crítico (P1) necesita una nota de resolución";

    private final IncidentRepository incidentRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final SlaPolicyRepository slaRepository;
    private final IncidentActivityRepository activityRepository;
    private final ZoneId zone;

    public IncidentService(IncidentRepository incidentRepository,
                           CategoryRepository categoryRepository,
                           UserRepository userRepository,
                           SlaPolicyRepository slaRepository,
                           IncidentActivityRepository activityRepository,
                           @Value("${TZ:America/Bogota}") String timeZone) {
        this.incidentRepository = incidentRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.slaRepository = slaRepository;
        this.activityRepository = activityRepository;
        this.zone = ZoneId.of(timeZone);
    }

    public List<IncidentResponse> findAll(IncidentFilter filter) {
        Map<IncidentPriority, Integer> sla = slaHours();
        return incidentRepository.findAll(IncidentSpecifications.fromFilter(filter, zone), NEWEST_FIRST).stream()
                .map(i -> toResponse(i, sla))
                .toList();
    }

    public IncidentResponse findById(Long id) {
        return toResponse(getIncident(id), slaHours());
    }

    @Transactional
    public IncidentResponse create(IncidentRequest request) {
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", request.categoryId()));
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.userId()));

        Incident incident = new Incident(
                request.title().trim(), request.description().trim(), category, user, request.priority());
        incident.setSuggestedCategoryId(request.suggestedCategoryId());
        incident.setSuggestedPriority(request.suggestedPriority());
        incident.setLocation(blankToNull(request.location()));
        incident = incidentRepository.save(incident);
        activityRepository.save(new IncidentActivity(incident, IncidentActivity.Type.CREATED,
                null, IncidentStatus.OPEN, user, null));

        // Saved first, so createdAt is set before a RESOLVED/CLOSED status sets resolvedAt:
        // the other way round resolved_at would be earlier than created_at and break the DB CHECK.
        IncidentStatus status = request.statusOrDefault();
        if (status != IncidentStatus.OPEN) {
            incident.changeStatus(status);
            activityRepository.save(new IncidentActivity(incident, IncidentActivity.Type.STATUS_CHANGE,
                    IncidentStatus.OPEN, status, user, null));
        }
        return toResponse(incident, slaHours());
    }

    /** RESOLVED/CLOSED set resolvedAt and OPEN/IN_PROGRESS clear it (Incident#changeStatus). */
    @Transactional
    public IncidentResponse changeStatus(Long id, IncidentStatusUpdateRequest request) {
        Incident incident = getIncident(id);
        IncidentStatus from = incident.getStatus();
        User user = request.userId() == null ? null : userRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.userId()));
        String resolution = blankToNull(request.resolution());

        if (request.status() == IncidentStatus.CLOSED && !from.isResolved()) {
            throw new ConflictException(CLOSE_ONLY_RESOLVED);
        }
        boolean resolving = request.status().isResolved() && !from.isResolved();
        if (resolving && incident.getPriority() == IncidentPriority.P1
                && resolution == null && incident.getResolution() == null) {
            throw new ConflictException(P1_NEEDS_RESOLUTION);
        }

        incident.changeStatus(request.status());
        if (resolution != null) {
            incident.setResolution(resolution);
        }
        // Se registra en la bitácora solo si algo cambió
        if (from != request.status() || resolution != null) {
            activityRepository.save(new IncidentActivity(incident, IncidentActivity.Type.STATUS_CHANGE,
                    from, request.status(), user, resolution));
        }
        return toResponse(incident, slaHours());
    }

    @Transactional
    public IncidentResponse update(Long id, IncidentRequest request) {
        Incident incident = getIncident(id);
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", request.categoryId()));
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.userId()));
        incident.setTitle(request.title().trim());
        incident.setDescription(request.description().trim());
        incident.setCategory(category);
        incident.setUser(user);
        incident.setPriority(request.priority());
        incident.setLocation(blankToNull(request.location()));
        activityRepository.save(new IncidentActivity(incident, IncidentActivity.Type.COMMENT,
                null, null, null, "Incidente editado"));
        return toResponse(incident, slaHours());
    }

    public PageResponse<IncidentResponse> findPage(IncidentFilter filter, int page, int size) {
        Map<IncidentPriority, Integer> sla = slaHours();
        Page<Incident> result = incidentRepository.findAll(IncidentSpecifications.fromFilter(filter, zone),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), NEWEST_FIRST));
        return new PageResponse<>(result.getContent().stream().map(i -> toResponse(i, sla)).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    public List<ActivityResponse> activity(Long id) {
        getIncident(id);
        return activityRepository.findByIncidentIdOrderByCreatedAtAscIdAsc(id).stream()
                .map(IncidentService::toActivity)
                .toList();
    }

    @Transactional
    public ActivityResponse addComment(Long id, CommentRequest request) {
        Incident incident = getIncident(id);
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.userId()));
        IncidentActivity saved = activityRepository.save(new IncidentActivity(incident,
                IncidentActivity.Type.COMMENT, null, null, user, request.comment().trim()));
        return toActivity(saved);
    }

    // CSV con los mismos filtros del listado (UTF-8 con BOM para que Excel muestre las tildes)
    public String exportCsv(IncidentFilter filter) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(zone);
        StringBuilder csv = new StringBuilder("﻿");
        csv.append("id;titulo;categoria;prioridad;estado;responsable;creado;resuelto;horas_resolucion;sla\n");
        for (IncidentResponse i : findAll(filter)) {
            String hours = i.resolvedAt() == null ? ""
                    : String.format("%.2f", Duration.between(i.createdAt(), i.resolvedAt()).toMinutes() / 60.0);
            csv.append(String.join(";",
                    String.valueOf(i.id()), csvText(i.title()), csvText(i.categoryName()),
                    i.priority().name(), i.status().name(), csvText(i.userName()),
                    fmt.format(i.createdAt()), i.resolvedAt() == null ? "" : fmt.format(i.resolvedAt()),
                    hours, i.slaStatus() == null ? "" : i.slaStatus()))
                    .append('\n');
        }
        return csv.toString();
    }

    private Incident getIncident(Long id) {
        return incidentRepository.findWithCategoryAndUserById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident", id));
    }

    private Map<IncidentPriority, Integer> slaHours() {
        Map<IncidentPriority, Integer> hours = new EnumMap<>(IncidentPriority.class);
        for (SlaPolicy p : slaRepository.findAll()) {
            hours.put(p.getPriority(), p.getMaxHours());
        }
        return hours;
    }

    static IncidentResponse toResponse(Incident incident, Map<IncidentPriority, Integer> sla) {
        Integer maxHours = sla.get(incident.getPriority());
        Instant due = maxHours == null || incident.getCreatedAt() == null
                ? null : incident.getCreatedAt().plus(Duration.ofHours(maxHours));
        return new IncidentResponse(
                incident.getId(),
                incident.getTitle(),
                incident.getDescription(),
                incident.getPriority(),
                incident.getStatus(),
                incident.getCategory().getId(),
                incident.getCategory().getName(),
                incident.getUser().getId(),
                incident.getUser().getName(),
                incident.getCreatedAt(),
                incident.getResolvedAt(),
                incident.getResolution(),
                incident.getSuggestedCategoryId(),
                incident.getSuggestedPriority(),
                due,
                slaStatus(due, incident.getResolvedAt()),
                incident.getLocation());
    }

    // MET/MISSED si está resuelto, ON_TIME/OVERDUE si sigue abierto
    static String slaStatus(Instant due, Instant resolvedAt) {
        if (due == null) {
            return null;
        }
        if (resolvedAt != null) {
            return resolvedAt.isAfter(due) ? "MISSED" : "MET";
        }
        return Instant.now().isAfter(due) ? "OVERDUE" : "ON_TIME";
    }

    private static ActivityResponse toActivity(IncidentActivity a) {
        return new ActivityResponse(a.getId(), a.getType().name(), a.getFromStatus(), a.getToStatus(),
                a.getUser() == null ? null : a.getUser().getId(),
                a.getUser() == null ? null : a.getUser().getName(),
                a.getComment(), a.getCreatedAt());
    }

    // Evita que ; o saltos de línea rompan las columnas del CSV
    private static String csvText(String value) {
        if (value == null) {
            return "";
        }
        return "\"" + value.replace("\"", "\"\"").replace('\n', ' ').replace('\r', ' ') + "\"";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
