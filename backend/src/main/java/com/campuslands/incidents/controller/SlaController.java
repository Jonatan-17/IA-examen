package com.campuslands.incidents.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.campuslands.incidents.dto.sla.SlaPolicyDto;
import com.campuslands.incidents.entity.enums.IncidentPriority;
import com.campuslands.incidents.service.sla.SlaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/sla-policies")
@Tag(name = "SLA")
public class SlaController {

    private final SlaService slaService;

    public SlaController(SlaService slaService) {
        this.slaService = slaService;
    }

    @GetMapping
    @Operation(summary = "Maximum resolution hours per priority")
    public List<SlaPolicyDto> findAll() {
        return slaService.findAll();
    }

    @PutMapping("/{priority}")
    @Operation(summary = "Change the maximum resolution hours of a priority")
    public SlaPolicyDto update(@PathVariable IncidentPriority priority, @Valid @RequestBody SlaPolicyDto body) {
        return slaService.update(priority, body.maxHours());
    }
}
