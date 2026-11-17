-- Weekly evolution: incidents created per week (weeks start on Monday, Bogotá time).
-- Used by the dashboard trend chart and by test 5.
SELECT
    date_trunc('week', created_at AT TIME ZONE 'America/Bogota')::date AS week_start,
    count(*)                                                          AS incidents
FROM incidents
GROUP BY week_start
ORDER BY week_start;
