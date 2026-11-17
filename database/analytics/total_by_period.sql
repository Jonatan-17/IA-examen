-- Total incidents per month, plus the grand total (last row, month = NULL).
-- Months are taken in Bogotá time so an incident created on the last evening
-- of a month is not moved to the next one by UTC.
SELECT
    to_char(date_trunc('month', created_at AT TIME ZONE 'America/Bogota'), 'YYYY-MM') AS month,
    count(*)                                                                         AS incidents
FROM incidents
GROUP BY ROLLUP (date_trunc('month', created_at AT TIME ZONE 'America/Bogota'))
ORDER BY date_trunc('month', created_at AT TIME ZONE 'America/Bogota') NULLS LAST;
