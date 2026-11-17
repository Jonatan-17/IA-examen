-- Most recurrent category. WITH TIES returns every category sharing the top count.
SELECT
    c.id        AS category_id,
    c.name      AS category,
    count(i.id) AS incidents
FROM categories c
JOIN incidents i ON i.category_id = c.id
GROUP BY c.id, c.name
ORDER BY incidents DESC
FETCH FIRST 1 ROWS WITH TIES;
