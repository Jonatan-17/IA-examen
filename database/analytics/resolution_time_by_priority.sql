SELECT i.priority, count(*) AS resolved,
       round((avg(extract(epoch FROM i.resolved_at - i.created_at)) / 3600)::numeric, 2) AS avg_hours
FROM incidents i
WHERE i.resolved_at IS NOT NULL
GROUP BY i.priority
ORDER BY i.priority;
