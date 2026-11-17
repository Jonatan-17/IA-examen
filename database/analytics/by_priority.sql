-- Distribution of incidents by priority (P1 = most urgent), with percentage.
SELECT
    priority,
    count(*)                                              AS incidents,
    round(100.0 * count(*) / sum(count(*)) OVER (), 2)    AS percentage
FROM incidents
GROUP BY priority
ORDER BY priority;
