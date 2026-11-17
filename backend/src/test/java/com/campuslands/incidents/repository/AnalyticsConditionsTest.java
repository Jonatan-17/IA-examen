package com.campuslands.incidents.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.campuslands.incidents.dto.analytics.AnalyticsFilter;
import com.campuslands.incidents.entity.enums.IncidentPriority;

class AnalyticsConditionsTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    @Test
    void noFiltersAddNoConditions() {
        AnalyticsConditions c = new AnalyticsConditions(AnalyticsFilter.none(), BOGOTA);

        assertThat(c.sql()).isEmpty();
        assertThat(c.params()).containsOnlyKeys("zone").containsEntry("zone", "America/Bogota");
    }

    @Test
    void everyFilterBecomesABindParameter() {
        AnalyticsFilter filter = new AnalyticsFilter(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), 3L, IncidentPriority.P1);

        AnalyticsConditions c = new AnalyticsConditions(filter, BOGOTA);

        assertThat(c.sql()).isEqualTo(" AND i.created_at >= :from AND i.created_at < :to"
                + " AND i.category_id = :categoryId AND i.priority = :priority");
        assertThat(c.params())
                .containsEntry("categoryId", 3L)
                .containsEntry("priority", "P1");
    }

    @Test
    void datesAreWholeDaysInTheConfiguredZone() {
        AnalyticsFilter filter = new AnalyticsFilter(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), null, null);

        AnalyticsConditions c = new AnalyticsConditions(filter, BOGOTA);

        // Start of Oct 1 in Bogotá, and the end date is inclusive: up to the start of Nov 1
        assertThat(c.params().get("from"))
                .isEqualTo(OffsetDateTime.of(2026, 10, 1, 0, 0, 0, 0, ZoneOffset.ofHours(-5)));
        assertThat(c.params().get("to"))
                .isEqualTo(OffsetDateTime.of(2026, 11, 1, 0, 0, 0, 0, ZoneOffset.ofHours(-5)));
    }
}
