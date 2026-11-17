-- Average resolution time per category, in hours.
-- Only resolved incidents count. Incidents resolved within one minute of their
-- creation are excluded: the API allows registering an incident directly as
-- resolved, and those zero durations would pull the average down.
SELECT
    c.id                                                                   AS category_id,
    c.name                                                                 AS category,
    count(i.id)                                                            AS resolved_incidents,
    round(avg(extract(epoch FROM i.resolved_at - i.created_at) / 3600)::numeric, 2) AS avg_hours
FROM categories c
LEFT JOIN incidents i
       ON i.category_id = c.id
      AND i.resolved_at IS NOT NULL
      AND i.resolved_at - i.created_at > interval '1 minute'
GROUP BY c.id, c.name
ORDER BY avg_hours DESC NULLS LAST, c.name;
