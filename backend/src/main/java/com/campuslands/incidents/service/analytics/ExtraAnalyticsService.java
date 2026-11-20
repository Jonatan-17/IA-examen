package com.campuslands.incidents.service.analytics;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.campuslands.incidents.dto.analytics.AnalyticsFilter;
import com.campuslands.incidents.repository.AnalyticsConditions;

// Indicadores extra (SQL de database/analytics)
@Service
public class ExtraAnalyticsService {

    public record StatusCount(String status, long queries) {
    }

    public record CategoryQuality(Long categoryId, String category, long queries, long noResults,
                                  long rated, Double avgRating) {
    }

    public record ArticleUsage(Long articleId, String title, boolean active, boolean verified,
                               long uses, Double avgSimilarity) {
    }

    public record AssistantQuality(long totalQueries, List<StatusCount> byStatus, long ratedResponses,
                                   Double avgRating, List<CategoryQuality> byCategory,
                                   List<ArticleUsage> articleUsage) {
    }

    public record CategorySla(Long categoryId, String category, long resolved, long met,
                              Double compliance, long openOverdue) {
    }

    public record ClassificationAccuracy(long categorySuggested, long categoryMatches, Double categoryAccuracy,
                                         long prioritySuggested, long priorityMatches, Double priorityAccuracy) {
    }

    public record AreaCount(String area, long incidents) {
    }

    // day: 1 = lunes ... 7 = domingo
    public record WeekdayCount(int day, long incidents) {
    }

    public record PriorityTime(String priority, long resolved, Double avgHours) {
    }

    public record ReopenRate(long total, long reopened, Double rate) {
    }

    public record PeriodComparison(LocalDate from, LocalDate to, long current,
                                   LocalDate previousFrom, LocalDate previousTo, long previous,
                                   Double changePercent) {
    }

    public record TechnicianStats(Long userId, String name, String area, long assigned, long resolved,
                                  Double resolutionRate, Double avgHours, long slaMissed) {
    }

    public record ResolutionPercentiles(String category, long resolved, Double avgHours, Double p50,
                                        Double p90, Double maxHours) {
    }

    private final JdbcClient jdbc;
    private final ZoneId zone;

    public ExtraAnalyticsService(JdbcClient jdbc, @Value("${TZ:America/Bogota}") String timeZone) {
        this.jdbc = jdbc;
        this.zone = ZoneId.of(timeZone);
    }

    public AssistantQuality assistantQuality() {
        List<StatusCount> byStatus = jdbc.sql("""
                SELECT status, count(*) AS queries FROM ai_queries GROUP BY status ORDER BY queries DESC
                """)
                .query((rs, n) -> new StatusCount(rs.getString("status"), rs.getLong("queries")))
                .list();

        long[] rated = new long[1];
        Double avgRating = jdbc.sql("SELECT count(*) AS rated, round(avg(score), 2) AS avg_rating FROM ratings")
                .query((rs, n) -> {
                    rated[0] = rs.getLong("rated");
                    return decimal(rs, "avg_rating");
                })
                .single();

        List<CategoryQuality> byCategory = jdbc.sql("""
                SELECT c.id, c.name,
                       count(q.id) AS queries,
                       count(q.id) FILTER (WHERE q.status = 'NO_RESULTS') AS no_results,
                       count(rt.id) AS rated,
                       round(avg(rt.score), 2) AS avg_rating
                FROM categories c
                LEFT JOIN incidents i    ON i.category_id = c.id
                LEFT JOIN ai_queries q   ON q.incident_id = i.id
                LEFT JOIN ai_responses r ON r.query_id = q.id
                LEFT JOIN ratings rt     ON rt.response_id = r.id
                GROUP BY c.id, c.name
                ORDER BY no_results DESC, c.name
                """)
                .query((rs, n) -> new CategoryQuality(rs.getLong("id"), rs.getString("name"),
                        rs.getLong("queries"), rs.getLong("no_results"), rs.getLong("rated"),
                        decimal(rs, "avg_rating")))
                .list();

        List<ArticleUsage> usage = jdbc.sql("""
                SELECT ka.id, ka.title, ka.active, ka.verified,
                       count(rs.id) AS uses,
                       round(avg(rs.similarity)::numeric, 4) AS avg_similarity
                FROM knowledge_articles ka
                LEFT JOIN response_sources rs ON rs.article_id = ka.id
                GROUP BY ka.id, ka.title, ka.active, ka.verified
                ORDER BY uses DESC, ka.id
                """)
                .query((rs, n) -> new ArticleUsage(rs.getLong("id"), rs.getString("title"),
                        rs.getBoolean("active"), rs.getBoolean("verified"), rs.getLong("uses"),
                        decimal(rs, "avg_similarity")))
                .list();

        long total = byStatus.stream().mapToLong(StatusCount::queries).sum();
        return new AssistantQuality(total, byStatus, rated[0], avgRating, byCategory, usage);
    }

    public List<CategorySla> slaByCategory(AnalyticsFilter filter) {
        AnalyticsConditions c = new AnalyticsConditions(filter, zone);
        String sql = """
                SELECT c.id, c.name,
                       count(i.id) FILTER (WHERE i.resolved_at IS NOT NULL) AS resolved,
                       count(i.id) FILTER (WHERE i.resolved_at <= i.created_at + s.max_hours * interval '1 hour') AS met,
                       count(i.id) FILTER (WHERE i.resolved_at IS NULL
                                             AND now() > i.created_at + s.max_hours * interval '1 hour') AS open_overdue
                FROM categories c
                LEFT JOIN incidents i    ON i.category_id = c.id %s
                LEFT JOIN sla_policies s ON s.priority = i.priority
                %s
                GROUP BY c.id, c.name
                ORDER BY c.name
                """.formatted(c.sql(), categoryOnly(filter));
        return jdbc.sql(sql).params(c.params())
                .query((rs, n) -> {
                    long resolved = rs.getLong("resolved");
                    long met = rs.getLong("met");
                    return new CategorySla(rs.getLong("id"), rs.getString("name"), resolved, met,
                            percentage(met, resolved), rs.getLong("open_overdue"));
                })
                .list();
    }

    public ClassificationAccuracy classificationAccuracy(AnalyticsFilter filter) {
        AnalyticsConditions c = new AnalyticsConditions(filter, zone);
        String sql = """
                SELECT count(*) FILTER (WHERE i.suggested_category_id IS NOT NULL)     AS category_suggested,
                       count(*) FILTER (WHERE i.suggested_category_id = i.category_id) AS category_matches,
                       count(*) FILTER (WHERE i.suggested_priority IS NOT NULL)        AS priority_suggested,
                       count(*) FILTER (WHERE i.suggested_priority = i.priority)       AS priority_matches
                FROM incidents i
                WHERE TRUE %s
                """.formatted(c.sql());
        return jdbc.sql(sql).params(c.params())
                .query((rs, n) -> {
                    long cs = rs.getLong("category_suggested");
                    long cm = rs.getLong("category_matches");
                    long ps = rs.getLong("priority_suggested");
                    long pm = rs.getLong("priority_matches");
                    return new ClassificationAccuracy(cs, cm, percentage(cm, cs), ps, pm, percentage(pm, ps));
                })
                .single();
    }

    public List<AreaCount> byArea(AnalyticsFilter filter) {
        AnalyticsConditions c = new AnalyticsConditions(filter, zone);
        String sql = """
                SELECT u.area, count(*) AS incidents
                FROM incidents i
                JOIN users u ON u.id = i.user_id
                WHERE TRUE %s
                GROUP BY u.area
                ORDER BY incidents DESC, u.area
                """.formatted(c.sql());
        return jdbc.sql(sql).params(c.params())
                .query((rs, n) -> new AreaCount(rs.getString("area"), rs.getLong("incidents")))
                .list();
    }

    public List<WeekdayCount> byWeekday(AnalyticsFilter filter) {
        AnalyticsConditions c = new AnalyticsConditions(filter, zone);
        String sql = """
                SELECT extract(isodow FROM i.created_at AT TIME ZONE :zone)::int AS day, count(*) AS incidents
                FROM incidents i
                WHERE TRUE %s
                GROUP BY day
                ORDER BY day
                """.formatted(c.sql());
        return jdbc.sql(sql).params(c.params())
                .query((rs, n) -> new WeekdayCount(rs.getInt("day"), rs.getLong("incidents")))
                .list();
    }

    public List<PriorityTime> resolutionTimeByPriority(AnalyticsFilter filter) {
        AnalyticsConditions c = new AnalyticsConditions(filter, zone);
        String sql = """
                SELECT i.priority, count(*) AS resolved,
                       round((avg(extract(epoch FROM i.resolved_at - i.created_at)) / 3600)::numeric, 2) AS avg_hours
                FROM incidents i
                WHERE i.resolved_at IS NOT NULL %s
                GROUP BY i.priority
                ORDER BY i.priority
                """.formatted(c.sql());
        return jdbc.sql(sql).params(c.params())
                .query((rs, n) -> new PriorityTime(rs.getString("priority"), rs.getLong("resolved"),
                        decimal(rs, "avg_hours")))
                .list();
    }

    // Reabiertos: pasaron de RESOLVED/CLOSED a OPEN/IN_PROGRESS según la bitácora
    public ReopenRate reopenRate(AnalyticsFilter filter) {
        AnalyticsConditions c = new AnalyticsConditions(filter, zone);
        String sql = """
                SELECT count(*) AS total,
                       sum(CASE WHEN EXISTS (
                           SELECT 1 FROM incident_activity a
                           WHERE a.incident_id = i.id
                             AND a.from_status IN ('RESOLVED', 'CLOSED')
                             AND a.to_status IN ('OPEN', 'IN_PROGRESS')) THEN 1 ELSE 0 END) AS reopened
                FROM incidents i
                WHERE TRUE %s
                """.formatted(c.sql());
        return jdbc.sql(sql).params(c.params())
                .query((rs, n) -> {
                    long total = rs.getLong("total");
                    long reopened = rs.getLong("reopened");
                    return new ReopenRate(total, reopened, percentage(reopened, total));
                })
                .single();
    }

    // Compara [from, to] con el periodo anterior de la misma duración
    public PeriodComparison compare(AnalyticsFilter filter) {
        if (filter.from() == null || filter.to() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from and to are required");
        }
        long days = ChronoUnit.DAYS.between(filter.from(), filter.to()) + 1;
        LocalDate prevTo = filter.from().minusDays(1);
        LocalDate prevFrom = prevTo.minusDays(days - 1);
        long current = count(filter);
        long previous = count(new AnalyticsFilter(prevFrom, prevTo, filter.categoryId(), filter.priority()));
        Double change = previous == 0 ? null : AnalyticsService.round(100.0 * (current - previous) / previous);
        return new PeriodComparison(filter.from(), filter.to(), current, prevFrom, prevTo, previous, change);
    }

    // Ranking de responsables: más resueltos primero, luego el más rápido
    public List<TechnicianStats> technicianRanking(AnalyticsFilter filter) {
        AnalyticsConditions c = new AnalyticsConditions(filter, zone);
        String sql = """
                SELECT u.id, u.name, u.area,
                       count(i.id) AS assigned,
                       count(i.id) FILTER (WHERE i.resolved_at IS NOT NULL) AS resolved,
                       round((avg(extract(epoch FROM i.resolved_at - i.created_at))
                             FILTER (WHERE i.resolved_at IS NOT NULL) / 3600)::numeric, 2) AS avg_hours,
                       count(i.id) FILTER (WHERE (i.resolved_at IS NOT NULL
                                                  AND i.resolved_at > i.created_at + s.max_hours * interval '1 hour')
                                              OR (i.resolved_at IS NULL
                                                  AND now() > i.created_at + s.max_hours * interval '1 hour')) AS sla_missed
                FROM users u
                JOIN incidents i         ON i.user_id = u.id %s
                LEFT JOIN sla_policies s ON s.priority = i.priority
                GROUP BY u.id, u.name, u.area
                ORDER BY resolved DESC, avg_hours NULLS LAST, u.name
                """.formatted(c.sql());
        return jdbc.sql(sql).params(c.params())
                .query((rs, n) -> {
                    long assigned = rs.getLong("assigned");
                    long resolved = rs.getLong("resolved");
                    return new TechnicianStats(rs.getLong("id"), rs.getString("name"), rs.getString("area"),
                            assigned, resolved, percentage(resolved, assigned), decimal(rs, "avg_hours"),
                            rs.getLong("sla_missed"));
                })
                .list();
    }

    // p50 y p90 por categoría; la última fila (category = null) es el total
    public List<ResolutionPercentiles> resolutionPercentiles(AnalyticsFilter filter) {
        AnalyticsConditions c = new AnalyticsConditions(filter, zone);
        String sql = """
                SELECT c.name AS category, count(*) AS resolved,
                       round(avg(t.h)::numeric, 2) AS avg_hours,
                       round((percentile_cont(0.5) WITHIN GROUP (ORDER BY t.h))::numeric, 2) AS p50,
                       round((percentile_cont(0.9) WITHIN GROUP (ORDER BY t.h))::numeric, 2) AS p90,
                       round(max(t.h)::numeric, 2) AS max_hours
                FROM (SELECT i.category_id, (extract(epoch FROM i.resolved_at - i.created_at) / 3600)::float8 AS h
                      FROM incidents i
                      WHERE i.resolved_at IS NOT NULL %s) t
                JOIN categories c ON c.id = t.category_id
                GROUP BY ROLLUP (c.name)
                ORDER BY c.name NULLS LAST
                """.formatted(c.sql());
        return jdbc.sql(sql).params(c.params())
                .query((rs, n) -> new ResolutionPercentiles(rs.getString("category"), rs.getLong("resolved"),
                        decimal(rs, "avg_hours"), decimal(rs, "p50"), decimal(rs, "p90"), decimal(rs, "max_hours")))
                .list();
    }

    private long count(AnalyticsFilter filter) {
        AnalyticsConditions c = new AnalyticsConditions(filter, zone);
        return jdbc.sql("SELECT count(*) FROM incidents i WHERE TRUE " + c.sql())
                .params(c.params())
                .query(Long.class)
                .single();
    }

    private static String categoryOnly(AnalyticsFilter filter) {
        return filter.categoryId() == null ? "" : "WHERE c.id = :categoryId";
    }

    private static Double percentage(long part, long total) {
        return total == 0 ? null : AnalyticsService.percentage(part, total);
    }

    private static Double decimal(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return value == null ? null : value.doubleValue();
    }
}
