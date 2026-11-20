-- Artículos marcados para revisión por respuestas mal calificadas. Idempotente.
ALTER TABLE knowledge_articles ADD COLUMN IF NOT EXISTS needs_review BOOLEAN NOT NULL DEFAULT FALSE;
