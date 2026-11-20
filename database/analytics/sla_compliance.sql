-- Cumplimiento de SLA por categoría
SELECT c.id, c.name,
       count(i.id) FILTER (WHERE i.resolved_at IS NOT NULL) AS resolved,
       count(i.id) FILTER (WHERE i.resolved_at <= i.created_at + s.max_hours * interval '1 hour') AS met,
       count(i.id) FILTER (WHERE i.resolved_at IS NULL
                             AND now() > i.created_at + s.max_hours * interval '1 hour') AS open_overdue
FROM categories c
LEFT JOIN incidents i    ON i.category_id = c.id
LEFT JOIN sla_policies s ON s.priority = i.priority
GROUP BY c.id, c.name
ORDER BY c.name;
