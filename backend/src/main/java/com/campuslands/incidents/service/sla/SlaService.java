package com.campuslands.incidents.service.sla;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.campuslands.incidents.dto.sla.SlaPolicyDto;
import com.campuslands.incidents.entity.SlaPolicy;
import com.campuslands.incidents.entity.enums.IncidentPriority;
import com.campuslands.incidents.repository.SlaPolicyRepository;

// Consulta y edición de las horas de SLA por prioridad
@Service
@Transactional(readOnly = true)
public class SlaService {

    private final SlaPolicyRepository repository;

    public SlaService(SlaPolicyRepository repository) {
        this.repository = repository;
    }

    public List<SlaPolicyDto> findAll() {
        return repository.findAll(Sort.by("priority")).stream()
                .map(p -> new SlaPolicyDto(p.getPriority(), p.getMaxHours()))
                .toList();
    }

    // Crea la política si no existe
    @Transactional
    public SlaPolicyDto update(IncidentPriority priority, int maxHours) {
        SlaPolicy policy = repository.findById(priority).orElseGet(() -> new SlaPolicy(priority, maxHours));
        policy.setMaxHours(maxHours);
        repository.save(policy);
        return new SlaPolicyDto(priority, maxHours);
    }
}
