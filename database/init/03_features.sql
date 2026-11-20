-- Nuevas funciones: SLA, bitácora, sugerencias de IA y notas de resolución.
-- Idempotente: en una BD existente se puede ejecutar a mano.

ALTER TABLE incidents ADD COLUMN IF NOT EXISTS resolution TEXT;
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS suggested_category_id BIGINT REFERENCES categories (id) ON DELETE SET NULL;
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS suggested_priority VARCHAR(2);

-- Horas máximas de resolución por prioridad
CREATE TABLE IF NOT EXISTS sla_policies (
    priority  VARCHAR(2) PRIMARY KEY CHECK (priority IN ('P1', 'P2', 'P3', 'P4')),
    max_hours INTEGER    NOT NULL CHECK (max_hours > 0)
);

INSERT INTO sla_policies (priority, max_hours)
VALUES ('P1', 12), ('P2', 24), ('P3', 48), ('P4', 72)
ON CONFLICT (priority) DO NOTHING;

-- Bitácora: creación, cambios de estado y comentarios
CREATE TABLE IF NOT EXISTS incident_activity (
    id          BIGSERIAL   PRIMARY KEY,
    incident_id BIGINT      NOT NULL REFERENCES incidents (id) ON DELETE CASCADE,
    type        VARCHAR(20) NOT NULL CHECK (type IN ('CREATED', 'STATUS_CHANGE', 'COMMENT')),
    from_status VARCHAR(20),
    to_status   VARCHAR(20),
    user_id     BIGINT      REFERENCES users (id) ON DELETE SET NULL,
    comment     TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_incident_activity_incident_id ON incident_activity (incident_id);

-- Bitácora inicial para los incidentes existentes
INSERT INTO incident_activity (incident_id, type, to_status, user_id, created_at)
SELECT i.id, 'CREATED', 'OPEN', i.user_id, i.created_at
FROM incidents i
WHERE NOT EXISTS (SELECT 1 FROM incident_activity a WHERE a.incident_id = i.id AND a.type = 'CREATED');

INSERT INTO incident_activity (incident_id, type, from_status, to_status, created_at)
SELECT i.id, 'STATUS_CHANGE', 'OPEN', i.status, coalesce(i.resolved_at, i.updated_at)
FROM incidents i
WHERE i.status <> 'OPEN'
  AND NOT EXISTS (SELECT 1 FROM incident_activity a WHERE a.incident_id = i.id AND a.type = 'STATUS_CHANGE');
