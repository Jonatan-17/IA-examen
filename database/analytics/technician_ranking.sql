SELECT u.id, u.name, u.area,
       count(i.id) AS assigned,
       count(i.id) FILTER (WHERE i.resolved_at IS NOT NULL) AS resolved,
       round((avg(extract(epoch FROM i.resolved_at - i.created_at)) FILTER (WHERE i.resolved_at IS NOT NULL) / 3600)::numeric, 2) AS avg_hours,
       count(i.id) FILTER (WHERE (i.resolved_at IS NOT NULL AND i.resolved_at > i.created_at + s.max_hours * interval '1 hour')
                              OR (i.resolved_at IS NULL AND now() > i.created_at + s.max_hours * interval '1 hour')) AS sla_missed
FROM users u
JOIN incidents i         ON i.user_id = u.id
LEFT JOIN sla_policies s ON s.priority = i.priority
GROUP BY u.id, u.name, u.area
ORDER BY resolved DESC, avg_hours NULLS LAST, u.name;
