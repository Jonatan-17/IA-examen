-- Mediana (p50) y p90 por categoría; la fila con category NULL es el total
SELECT c.name AS category, count(*) AS resolved,
       round(avg(t.h)::numeric, 2)                                            AS avg_hours,
       round((percentile_cont(0.5) WITHIN GROUP (ORDER BY t.h))::numeric, 2) AS p50,
       round((percentile_cont(0.9) WITHIN GROUP (ORDER BY t.h))::numeric, 2) AS p90,
       round(max(t.h)::numeric, 2)                                            AS max_hours
FROM (SELECT i.category_id, (extract(epoch FROM i.resolved_at - i.created_at) / 3600)::float8 AS h
      FROM incidents i
      WHERE i.resolved_at IS NOT NULL) t
JOIN categories c ON c.id = t.category_id
GROUP BY ROLLUP (c.name)
ORDER BY c.name NULLS LAST;
