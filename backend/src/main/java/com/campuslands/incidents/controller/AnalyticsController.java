package com.campuslands.incidents.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.campuslands.incidents.dto.analytics.AnalyticsFilter;
import com.campuslands.incidents.dto.analytics.CategoryCount;
import com.campuslands.incidents.dto.analytics.CategoryResolutionTime;
import com.campuslands.incidents.dto.analytics.PeriodCount;
import com.campuslands.incidents.dto.analytics.PriorityCount;
import com.campuslands.incidents.dto.analytics.SummaryResponse;
import com.campuslands.incidents.service.analytics.AnalyticsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Dashboard indicators. Every endpoint accepts the same optional filters:
 * {@code from} and {@code to} (yyyy-MM-dd, inclusive, on the creation date), {@code categoryId}
 * and {@code priority} (P1-P4).
 */
@RestController
@RequestMapping("/api/analytics")
@Tag(name = "Analytics", description = "Dashboard indicators computed with SQL, with date, category and priority filters (RF06, RF07)")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/summary")
    @Operation(summary = "Total, resolved, resolution rate, average resolution time (hours) and top category")
    public SummaryResponse summary(@Valid @ParameterObject AnalyticsFilter filter) {
        return analyticsService.summary(filter);
    }

    @GetMapping("/by-category")
    @Operation(summary = "Incidents and percentage per category, most frequent first")
    public List<CategoryCount> byCategory(@Valid @ParameterObject AnalyticsFilter filter) {
        return analyticsService.byCategory(filter);
    }

    @GetMapping("/resolution-time")
    @Operation(summary = "Average resolution time per category, in hours",
            description = "Incidents resolved within one minute of creation are excluded.")
    public List<CategoryResolutionTime> resolutionTime(@Valid @ParameterObject AnalyticsFilter filter) {
        return analyticsService.resolutionTimeByCategory(filter);
    }

    @GetMapping("/weekly-trend")
    @Operation(summary = "Incidents created per week (weeks start on Monday)")
    public List<PeriodCount> weeklyTrend(@Valid @ParameterObject AnalyticsFilter filter) {
        return analyticsService.weeklyTrend(filter);
    }

    @GetMapping("/monthly")
    @Operation(summary = "Incidents created per month")
    public List<PeriodCount> monthly(@Valid @ParameterObject AnalyticsFilter filter) {
        return analyticsService.monthlyTotals(filter);
    }

    @GetMapping("/by-priority")
    @Operation(summary = "Incidents and percentage per priority")
    public List<PriorityCount> byPriority(@Valid @ParameterObject AnalyticsFilter filter) {
        return analyticsService.byPriority(filter);
    }
}
