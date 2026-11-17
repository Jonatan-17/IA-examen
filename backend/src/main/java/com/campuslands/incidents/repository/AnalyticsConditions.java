package com.campuslands.incidents.repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

import com.campuslands.incidents.dto.analytics.AnalyticsFilter;

/**
 * Turns the optional analytics filters into SQL conditions on the {@code incidents} table
 * (alias {@code i}) plus their named parameters. Only the filters the client sent are added,
 * and values always travel as bind parameters, never concatenated into the SQL.
 */
final class AnalyticsConditions {

    private final StringBuilder sql = new StringBuilder();
    private final Map<String, Object> params = new LinkedHashMap<>();

    /**
     * @param zone time zone that defines what a "day" is: the same one the incident list uses,
     *             so the dashboard and the list count the same incidents for a date range
     */
    AnalyticsConditions(AnalyticsFilter filter, ZoneId zone) {
        params.put("zone", zone.getId());
        if (filter.from() != null) {
            add("i.created_at >= :from", "from", startOfDay(filter.from(), zone));
        }
        if (filter.to() != null) {
            // Inclusive end date: everything before the start of the next day
            add("i.created_at < :to", "to", startOfDay(filter.to().plusDays(1), zone));
        }
        if (filter.categoryId() != null) {
            add("i.category_id = :categoryId", "categoryId", filter.categoryId());
        }
        if (filter.priority() != null) {
            add("i.priority = :priority", "priority", filter.priority().name());
        }
    }

    /** Conditions, each one starting with " AND " (empty when there are no filters). */
    String sql() {
        return sql.toString();
    }

    /** Named parameters of {@link #sql()}, plus {@code zone} for grouping by week or month. */
    Map<String, Object> params() {
        return params;
    }

    private void add(String condition, String name, Object value) {
        sql.append(" AND ").append(condition);
        params.put(name, value);
    }

    private static OffsetDateTime startOfDay(LocalDate day, ZoneId zone) {
        return day.atStartOfDay(zone).toOffsetDateTime();
    }
}
