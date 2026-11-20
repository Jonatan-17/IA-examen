-- Acierto de la clasificación sugerida por IA
SELECT count(*) FILTER (WHERE suggested_category_id IS NOT NULL)    AS category_suggested,
       count(*) FILTER (WHERE suggested_category_id = category_id)  AS category_matches,
       count(*) FILTER (WHERE suggested_priority IS NOT NULL)       AS priority_suggested,
       count(*) FILTER (WHERE suggested_priority = priority)        AS priority_matches
FROM incidents;
