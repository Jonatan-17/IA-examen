package com.campuslands.incidents.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.campuslands.incidents.dto.analytics.AnalyticsFilter;
import com.campuslands.incidents.service.analytics.ExtraAnalyticsService;
import com.campuslands.incidents.service.analytics.ExtraAnalyticsService.AreaCount;
import com.campuslands.incidents.service.analytics.ExtraAnalyticsService.AssistantQuality;
import com.campuslands.incidents.service.analytics.ExtraAnalyticsService.CategorySla;
import com.campuslands.incidents.service.analytics.ExtraAnalyticsService.ClassificationAccuracy;
import com.campuslands.incidents.service.analytics.ExtraAnalyticsService.PeriodComparison;
import com.campuslands.incidents.service.analytics.ExtraAnalyticsService.PriorityTime;
import com.campuslands.incidents.service.analytics.ExtraAnalyticsService.ReopenRate;
import com.campuslands.incidents.service.analytics.ExtraAnalyticsService.ResolutionPercentiles;
import com.campuslands.incidents.service.analytics.ExtraAnalyticsService.TechnicianStats;
import com.campuslands.incidents.service.analytics.ExtraAnalyticsService.WeekdayCount;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/analytics")
@Tag(name = "Analytics (extra)")
public class ExtraAnalyticsController {

    private final ExtraAnalyticsService service;

    public ExtraAnalyticsController(ExtraAnalyticsService service) {
        this.service = service;
    }

    @GetMapping("/assistant")
    @Operation(summary = "AI assistant quality (whole history)")
    public AssistantQuality assistant() {
        return service.assistantQuality();
    }

    @GetMapping("/sla")
    @Operation(summary = "SLA compliance per category")
    public List<CategorySla> sla(@Valid @ParameterObject AnalyticsFilter filter) {
        return service.slaByCategory(filter);
    }

    @GetMapping("/classification")
    @Operation(summary = "Accuracy of the AI category and priority suggestions")
    public ClassificationAccuracy classification(@Valid @ParameterObject AnalyticsFilter filter) {
        return service.classificationAccuracy(filter);
    }

    @GetMapping("/by-area")
    @Operation(summary = "Incidents per area of the responsible user")
    public List<AreaCount> byArea(@Valid @ParameterObject AnalyticsFilter filter) {
        return service.byArea(filter);
    }

    @GetMapping("/by-weekday")
    @Operation(summary = "Incidents per day of the week (1 = Monday)")
    public List<WeekdayCount> byWeekday(@Valid @ParameterObject AnalyticsFilter filter) {
        return service.byWeekday(filter);
    }

    @GetMapping("/resolution-time-by-priority")
    @Operation(summary = "Average resolution hours per priority")
    public List<PriorityTime> resolutionTimeByPriority(@Valid @ParameterObject AnalyticsFilter filter) {
        return service.resolutionTimeByPriority(filter);
    }

    @GetMapping("/reopen-rate")
    @Operation(summary = "Share of incidents reopened after being resolved")
    public ReopenRate reopenRate(@Valid @ParameterObject AnalyticsFilter filter) {
        return service.reopenRate(filter);
    }

    @GetMapping("/technicians")
    @Operation(summary = "Ranking of responsible users: resolved, average hours and SLA missed")
    public List<TechnicianStats> technicians(@Valid @ParameterObject AnalyticsFilter filter) {
        return service.technicianRanking(filter);
    }

    @GetMapping("/resolution-percentiles")
    @Operation(summary = "Median (p50) and p90 resolution hours per category; the row with null category is the total")
    public List<ResolutionPercentiles> percentiles(@Valid @ParameterObject AnalyticsFilter filter) {
        return service.resolutionPercentiles(filter);
    }

    @GetMapping("/compare")
    @Operation(summary = "Incidents in [from, to] vs the previous period of the same length (from and to required)")
    public PeriodComparison compare(@Valid @ParameterObject AnalyticsFilter filter) {
        return service.compare(filter);
    }
}
