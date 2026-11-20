-- 1 = lunes ... 7 = domingo
SELECT extract(isodow FROM i.created_at AT TIME ZONE 'America/Bogota')::int AS day, count(*) AS incidents
FROM incidents i
GROUP BY day
ORDER BY day;
