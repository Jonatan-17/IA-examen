-- Frequency and percentage of incidents per category.
-- LEFT JOIN keeps categories with no incidents (count 0).
SELECT
    c.id                                                        AS category_id,
    c.name                                                      AS category,
    count(i.id)                                                 AS incidents,
    round(100.0 * count(i.id) / nullif(sum(count(i.id)) OVER (), 0), 2) AS percentage
FROM categories c
LEFT JOIN incidents i ON i.category_id = c.id
GROUP BY c.id, c.name
ORDER BY incidents DESC, c.name;
