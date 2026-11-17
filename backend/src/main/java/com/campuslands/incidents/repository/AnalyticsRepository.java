package com.campuslands.incidents.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.campuslands.incidents.dto.analytics.AnalyticsFilter;
import com.campuslands.incidents.entity.enums.IncidentPriority;

/**
 * Native SQL for the dashboard indicators (RF06). The queries are the ones in
 * {@code database/analytics/*.sql} plus the optional filters, so every value the dashboard shows
 * can be checked by running those files directly on the database (tests 4 and 5).
 * Dashboard figures always come from SQL, never from the AI model.
 */
@Repository
public class AnalyticsRepository {

    /** Incidents resolved within a minute of creation are left out of time averages (see avg_resolution_time.sql). */
    private static final String MEASURABLE_RESOLUTION =
            "i.resolved_at IS NOT NULL AND i.resolved_at - i.created_at > interval '1 minute'";

    private static final String RESOLVED = "i.status IN ('RESOLVED', 'CLOSED')";

    public enum Period { WEEK, MONTH }

    public record Totals(long total, long resolved, Double avgResolutionHours) {
    }

    public record CategoryRow(Long categoryId, String category, long incidents) {
    }

    public record CategoryTimeRow(Long categoryId, String category, long resolvedIncidents, Double avgHours) {
    }

    public record PeriodRow(LocalDate periodStart, long incidents) {
    }

    public record PriorityRow(IncidentPriority priority, long incidents) {
    }

    private final JdbcClient jdbc;

    public AnalyticsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Total, resolved (resolution_rate.sql) and overall average resolution time. */
    public Totals totals(AnalyticsFilter filter, ZoneId zone) {
        AnalyticsConditions c = new AnalyticsConditions(filter, zone);
        String sql = """
                SELECT count(*) AS total,
                       count(*) FILTER (WHERE %s) AS resolved,
                       avg(extract(epoch FROM i.resolved_at - i.created_at) / 3600)
                           FILTER (WHERE %s) AS avg_hours
                FROM incidents i
                WHERE TRUE %s
                """.formatted(RESOLVED, MEASURABLE_RESOLUTION, c.sql());
        return jdbc.sql(sql).params(c.params())
                .query((rs, n) -> new Totals(rs.getLong("total"), rs.getLong("resolved"),
                        hours(rs)))
                .single();
    }

    /**
     * by_category.sql. Every category is listed, with 0 when it has no incidents in the filter;
     * a category filter keeps only that category.
     */
    public List<CategoryRow> countByCategory(AnalyticsFilter filter, ZoneId zone) {
        AnalyticsConditions c = new AnalyticsConditions(filter, zone);
        String sql = """
                SELECT c.id, c.name, count(i.id) AS incidents
                FROM categories c
                LEFT JOIN incidents i ON i.category_id = c.id %s
                %s
                GROUP BY c.id, c.name
                ORDER BY incidents DESC, c.name
                """.formatted(c.sql(), categoryOnly(filter));
        return jdbc.sql(sql).params(c.params())
                .query((rs, n) -> new CategoryRow(rs.getLong("id"), rs.getString("name"), rs.getLong("incidents")))
                .list();
    }

    /** avg_resolution_time.sql: average hours per category over measurable resolutions. */
    public List<CategoryTimeRow> resolutionTimeByCategory(AnalyticsFilter filter, ZoneId zone) {
        AnalyticsConditions c = new AnalyticsConditions(filter, zone);
        String sql = """
                SELECT c.id, c.name, count(i.id) AS resolved_incidents,
                       avg(extract(epoch FROM i.resolved_at - i.created_at) / 3600) AS avg_hours
                FROM categories c
                LEFT JOIN incidents i ON i.category_id = c.id AND %s %s
                %s
                GROUP BY c.id, c.name
                ORDER BY avg_hours DESC NULLS LAST, c.name
                """.formatted(MEASURABLE_RESOLUTION, c.sql(), categoryOnly(filter));
        return jdbc.sql(sql).params(c.params())
                .query((rs, n) -> new CategoryTimeRow(rs.getLong("id"), rs.getString("name"),
                        rs.getLong("resolved_incidents"), hours(rs)))
                .list();
    }

    /**
     * weekly_trend.sql (weeks start on Monday) or total_by_period.sql (months), grouped in the
     * configured time zone so an incident created late at night is not moved to the next day by UTC.
     */
    public List<PeriodRow> countByPeriod(Period period, AnalyticsFilter filter, ZoneId zone) {
        AnalyticsConditions c = new AnalyticsConditions(filter, zone);
        // The unit comes from the enum, never from the client, so it is safe to put it in the SQL text
        String unit = period == Period.WEEK ? "week" : "month";
        String sql = """
                SELECT date_trunc('%s', i.created_at AT TIME ZONE :zone)::date AS period_start,
                       count(*) AS incidents
                FROM incidents i
                WHERE TRUE %s
                GROUP BY period_start
                ORDER BY period_start
                """.formatted(unit, c.sql());
        return jdbc.sql(sql).params(c.params())
                .query((rs, n) -> new PeriodRow(rs.getObject("period_start", LocalDate.class),
                        rs.getLong("incidents")))
                .list();
    }

    /** by_priority.sql. */
    public List<PriorityRow> countByPriority(AnalyticsFilter filter, ZoneId zone) {
        AnalyticsConditions c = new AnalyticsConditions(filter, zone);
        String sql = """
                SELECT i.priority, count(*) AS incidents
                FROM incidents i
                WHERE TRUE %s
                GROUP BY i.priority
                ORDER BY i.priority
                """.formatted(c.sql());
        return jdbc.sql(sql).params(c.params())
                .query((rs, n) -> new PriorityRow(IncidentPriority.valueOf(rs.getString("priority")),
                        rs.getLong("incidents")))
                .list();
    }

    /**
     * avg(...) of an epoch difference is NUMERIC, which the PostgreSQL driver cannot hand back as Double.
     * NULL (nothing resolved) stays null.
     */
    private static Double hours(ResultSet rs) throws SQLException {
        BigDecimal value = rs.getBigDecimal("avg_hours");
        return value == null ? null : value.doubleValue();
    }

    /** With a category filter, the per-category lists show only that category. */
    private static String categoryOnly(AnalyticsFilter filter) {
        return filter.categoryId() == null ? "" : "WHERE c.id = :categoryId";
    }
}
