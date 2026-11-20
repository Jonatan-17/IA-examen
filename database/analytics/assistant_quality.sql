-- Consultas por estado
SELECT status, count(*) AS queries
FROM ai_queries
GROUP BY status
ORDER BY queries DESC;

-- Calificación promedio
SELECT count(*) AS rated_responses, round(avg(score), 2) AS avg_rating FROM ratings;

-- Por categoría: consultas, sin resultados (falta documentación) y calificación
SELECT c.id, c.name,
       count(q.id)                                        AS queries,
       count(q.id) FILTER (WHERE q.status = 'NO_RESULTS') AS no_results,
       count(rt.id)                                       AS rated,
       round(avg(rt.score), 2)                            AS avg_rating
FROM categories c
LEFT JOIN incidents i    ON i.category_id = c.id
LEFT JOIN ai_queries q   ON q.incident_id = i.id
LEFT JOIN ai_responses r ON r.query_id = q.id
LEFT JOIN ratings rt     ON rt.response_id = r.id
GROUP BY c.id, c.name
ORDER BY no_results DESC, c.name;

-- Uso de cada artículo como fuente (0 = nunca usado)
SELECT ka.id, ka.title, ka.active, ka.verified,
       count(rs.id)                          AS uses,
       round(avg(rs.similarity)::numeric, 4) AS avg_similarity
FROM knowledge_articles ka
LEFT JOIN response_sources rs ON rs.article_id = ka.id
GROUP BY ka.id, ka.title, ka.active, ka.verified
ORDER BY uses DESC, ka.id;
