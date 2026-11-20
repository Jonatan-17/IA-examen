SELECT u.area, count(*) AS incidents
FROM incidents i
JOIN users u ON u.id = i.user_id
GROUP BY u.area
ORDER BY incidents DESC, u.area;
