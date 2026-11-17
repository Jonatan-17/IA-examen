package com.campuslands.incidents.service.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.campuslands.incidents.dto.analytics.AnalyticsFilter;
import com.campuslands.incidents.dto.analytics.CategoryCount;
import com.campuslands.incidents.dto.analytics.CategoryResolutionTime;
import com.campuslands.incidents.dto.analytics.PeriodCount;
import com.campuslands.incidents.dto.analytics.PriorityCount;
import com.campuslands.incidents.dto.analytics.SummaryResponse;
import com.campuslands.incidents.repository.AnalyticsRepository;
import com.campuslands.incidents.repository.AnalyticsRepository.CategoryRow;
import com.campuslands.incidents.repository.AnalyticsRepository.Period;
import com.campuslands.incidents.repository.AnalyticsRepository.PriorityRow;
import com.campuslands.incidents.repository.AnalyticsRepository.Totals;

/**
 * Dashboard indicators (RF06) with date, category and priority filters (RF07).
 * Counting happens in SQL; this class only derives percentages and rounds to 2 decimals,
 * the same rounding as the SQL files, so the dashboard and a manual query show equal values.
 */
@Service
public class AnalyticsService {

    private final AnalyticsRepository repository;
    private final ZoneId zone;

    /** @param timeZone same TZ variable PostgreSQL and the incident list use (.env) */
    public AnalyticsService(AnalyticsRepository repository,
                            @Value("${TZ:America/Bogota}") String timeZone) {
        this.repository = repository;
        this.zone = ZoneId.of(timeZone);
    }

    /** Indicator cards: total, resolved, resolution rate, average resolution time and top category. */
    public SummaryResponse summary(AnalyticsFilter filter) {
        Totals totals = repository.totals(filter, zone);
        CategoryCount top = byCategory(filter).stream()
                .filter(c -> c.incidents() > 0)
                .findFirst()
                .orElse(null);
        return new SummaryResponse(
                totals.total(),
                totals.resolved(),
                totals.total() == 0 ? null : percentage(totals.resolved(), totals.total()),
                round(totals.avgResolutionHours()),
                top);
    }

    /** Frequency and percentage per category, most frequent first (the first one is the top category). */
    public List<CategoryCount> byCategory(AnalyticsFilter filter) {
        List<CategoryRow> rows = repository.countByCategory(filter, zone);
        long total = rows.stream().mapToLong(CategoryRow::incidents).sum();
        return rows.stream()
                .map(r -> new CategoryCount(r.categoryId(), r.category(), r.incidents(),
                        total == 0 ? 0 : percentage(r.incidents(), total)))
                .toList();
    }

    public List<CategoryResolutionTime> resolutionTimeByCategory(AnalyticsFilter filter) {
        return repository.resolutionTimeByCategory(filter, zone).stream()
                .map(r -> new CategoryResolutionTime(r.categoryId(), r.category(), r.resolvedIncidents(),
                        round(r.avgHours())))
                .toList();
    }

    public List<PeriodCount> weeklyTrend(AnalyticsFilter filter) {
        return byPeriod(Period.WEEK, filter);
    }

    public List<PeriodCount> monthlyTotals(AnalyticsFilter filter) {
        return byPeriod(Period.MONTH, filter);
    }

    public List<PriorityCount> byPriority(AnalyticsFilter filter) {
        List<PriorityRow> rows = repository.countByPriority(filter, zone);
        long total = rows.stream().mapToLong(PriorityRow::incidents).sum();
        return rows.stream()
                .map(r -> new PriorityCount(r.priority(), r.incidents(), percentage(r.incidents(), total)))
                .toList();
    }

    private List<PeriodCount> byPeriod(Period period, AnalyticsFilter filter) {
        return repository.countByPeriod(period, filter, zone).stream()
                .map(r -> new PeriodCount(r.periodStart(), r.incidents()))
                .toList();
    }

    static double percentage(long part, long total) {
        return round(100.0 * part / total);
    }

    /** Half-up to 2 decimals, like PostgreSQL round(numeric, 2). */
    static Double round(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
