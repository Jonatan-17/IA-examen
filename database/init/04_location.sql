-- Ubicación del incidente (opcional). Idempotente.
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS location VARCHAR(100);
