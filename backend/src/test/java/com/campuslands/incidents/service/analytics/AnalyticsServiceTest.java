package com.campuslands.incidents.service.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.campuslands.incidents.dto.analytics.AnalyticsFilter;
import com.campuslands.incidents.dto.analytics.CategoryCount;
import com.campuslands.incidents.dto.analytics.PriorityCount;
import com.campuslands.incidents.dto.analytics.SummaryResponse;
import com.campuslands.incidents.entity.enums.IncidentPriority;
import com.campuslands.incidents.repository.AnalyticsRepository;
import com.campuslands.incidents.repository.AnalyticsRepository.CategoryRow;
import com.campuslands.incidents.repository.AnalyticsRepository.CategoryTimeRow;
import com.campuslands.incidents.repository.AnalyticsRepository.Period;
import com.campuslands.incidents.repository.AnalyticsRepository.PeriodRow;
import com.campuslands.incidents.repository.AnalyticsRepository.PriorityRow;
import com.campuslands.incidents.repository.AnalyticsRepository.Totals;

class AnalyticsServiceTest {

    private final AnalyticsRepository repository = mock(AnalyticsRepository.class);
    private final AnalyticsService service = new AnalyticsService(repository, "America/Bogota");
    private final AnalyticsFilter filter = AnalyticsFilter.none();

    @Test
    void summaryComputesTheRateAndPicksTheTopCategory() {
        when(repository.totals(eq(filter), any())).thenReturn(new Totals(150, 132, 22.123456));
        when(repository.countByCategory(eq(filter), any())).thenReturn(List.of(
                new CategoryRow(1L, "Conectividad", 41), new CategoryRow(3L, "Software", 37)));

        SummaryResponse summary = service.summary(filter);

        assertThat(summary.total()).isEqualTo(150);
        assertThat(summary.resolved()).isEqualTo(132);
        assertThat(summary.resolutionRate()).isEqualTo(88.0);
        assertThat(summary.avgResolutionHours()).isEqualTo(22.12);
        assertThat(summary.topCategory().category()).isEqualTo("Conectividad");
    }

    @Test
    void summaryWithNoIncidentsHasNoRateAndNoTopCategory() {
        when(repository.totals(eq(filter), any())).thenReturn(new Totals(0, 0, null));
        when(repository.countByCategory(eq(filter), any())).thenReturn(List.of(
                new CategoryRow(1L, "Conectividad", 0)));

        SummaryResponse summary = service.summary(filter);

        assertThat(summary.resolutionRate()).isNull();
        assertThat(summary.avgResolutionHours()).isNull();
        assertThat(summary.topCategory()).isNull();
    }

    @Test
    void categoryPercentagesUseTheFilteredTotalAndRoundLikeSql() {
        when(repository.countByCategory(eq(filter), any())).thenReturn(List.of(
                new CategoryRow(1L, "A", 2), new CategoryRow(2L, "B", 1), new CategoryRow(3L, "C", 0)));

        List<CategoryCount> rows = service.byCategory(filter);

        assertThat(rows).extracting(CategoryCount::percentage).containsExactly(66.67, 33.33, 0.0);
    }

    @Test
    void priorityPercentagesAddUpToTheTotal() {
        when(repository.countByPriority(eq(filter), any())).thenReturn(List.of(
                new PriorityRow(IncidentPriority.P1, 1), new PriorityRow(IncidentPriority.P3, 3)));

        List<PriorityCount> rows = service.byPriority(filter);

        assertThat(rows).extracting(PriorityCount::percentage).containsExactly(25.0, 75.0);
    }

    @Test
    void resolutionTimeKeepsCategoriesWithoutResolvedIncidents() {
        when(repository.resolutionTimeByCategory(eq(filter), any())).thenReturn(List.of(
                new CategoryTimeRow(4L, "Aplicaciones", 22, 41.3381), new CategoryTimeRow(5L, "Cuentas", 0, null)));

        assertThat(service.resolutionTimeByCategory(filter))
                .extracting(r -> r.avgHours())
                .containsExactly(41.34, null);
    }

    @Test
    void weeklyAndMonthlyAskForTheirPeriod() {
        when(repository.countByPeriod(eq(Period.WEEK), eq(filter), any()))
                .thenReturn(List.of(new PeriodRow(LocalDate.of(2026, 8, 10), 9)));
        when(repository.countByPeriod(eq(Period.MONTH), eq(filter), any()))
                .thenReturn(List.of(new PeriodRow(LocalDate.of(2026, 8, 1), 31)));

        assertThat(service.weeklyTrend(filter).getFirst().incidents()).isEqualTo(9);
        assertThat(service.monthlyTotals(filter).getFirst().incidents()).isEqualTo(31);
    }
}
