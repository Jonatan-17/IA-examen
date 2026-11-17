-- Resolution rate: resolved incidents over the total.
-- RESOLVED and CLOSED both count as resolved (the schema sets resolved_at for both).
SELECT
    count(*)                                                     AS total,
    count(*) FILTER (WHERE status IN ('RESOLVED', 'CLOSED'))     AS resolved,
    round(100.0 * count(*) FILTER (WHERE status IN ('RESOLVED', 'CLOSED'))
          / nullif(count(*), 0), 2)                              AS resolution_rate
FROM incidents;
