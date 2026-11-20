SELECT count(*) AS total,
       sum(CASE WHEN EXISTS (
           SELECT 1 FROM incident_activity a
           WHERE a.incident_id = i.id
             AND a.from_status IN ('RESOLVED', 'CLOSED')
             AND a.to_status IN ('OPEN', 'IN_PROGRESS')) THEN 1 ELSE 0 END) AS reopened
FROM incidents i;
