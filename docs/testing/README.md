# Testing document

This document consolidates the results of the 10 mandatory functional tests in the assignment (section 10). Each module owner ran their own tests and wrote the evidence:

| Tests | Module | Evidence |
|---|---|---|
| 1, 2, 3, 6, 7, 8 | C: AI and RAG (Mic022) | [ai-tests.md](ai-tests.md) |
| 4, 5 | A: data and analytics (Jonatan-17) | [analytics-tests.md](analytics-tests.md) |
| 9, 10 | B: management and operations (Nic-tolb) | [history-rating-tests.md](history-rating-tests.md) |

Member B consolidated them in this document.

## Environment

| Item | Value |
|---|---|
| Stack | `docker compose up -d --build`: PostgreSQL 16, Spring Boot 4 (Java 21), nginx, n8n, Qdrant |
| Models (OpenRouter) | Embeddings `baai/bge-m3` (1024 dimensions), chat `anthropic/claude-haiku-5.5` |
| RAG settings | Similarity threshold 0.60, top-k 5 ([calibration](../threshold-calibration.md)) |
| Knowledge base | 25 active and verified articles indexed in Qdrant. At least one topic has no article, for test 3 ([topics](../knowledge-topics.md)) |
| Analytics data (tests 4, 5) | Clean database loaded with `01_schema.sql` and `02_seed.sql`: 150 synthetic incidents from 2026-08-10 to 2026-11-01, grouped in Bogotá time ([generation rules](../data-analysis-report/synthetic-data.md)) |
| Date | 2026-11-17 |

Every test goes through Spring Boot, either through the API or through the frontend served by nginx. n8n, Qdrant and OpenRouter are never called directly, as the business rules require.

## Summary

| # | Test (assignment) | Result | Key evidence |
|---:|---|---|---|
| 1 | Registrar un incidente de conectividad a internet y obtener soluciones relevantes mediante RAG | **Pass** | `ANSWERED`. Source "Sin acceso a internet en el equipo" (similarity 0.641), 7 steps |
| 2 | Consultar un error de ejecución en una aplicación y verificar los artículos recuperados | **Pass** | `ANSWERED`. Source "Error de ejecución en la aplicación de facturación" (0.791) |
| 3 | Registrar un incidente sin información suficiente y comprobar que el modelo no invente soluciones | **Pass** | `NO_RESULTS` with no answer and no sources. The language model is not called |
| 4 | Consultar incidentes de una categoría específica y verificar que coincidan con la base relacional | **Pass** | Category *Aplicaciones*: 27 incidents in the list, in the dashboard and in SQL, with the same ids. With date, category and priority filters the counts still match |
| 5 | Visualizar la evolución semanal y comprobar los valores mediante consultas SQL | **Pass** | The 12 weeks of the dashboard chart match `weekly_trend.sql` (150 incidents in total) |
| 6 | Desactivar un artículo y verificar que no vuelva a utilizarse como fuente | **Pass** | With the article deactivated, the same question gives `NO_RESULTS`. Once reactivated, it is a source again |
| 7 | Enviar una descripción vacía y comprobar que no se ejecute el flujo inteligente | **Pass** | 400 with `fields.description`. No query is stored |
| 8 | Simular un fallo en el servicio de embeddings y verificar el manejo de errores | **Pass** | Invalid OpenRouter key: HTTP 200, the query is stored as `ERROR` and the message names the step that failed (`Embed question failed: 401`) |
| 9 | Consultar el historial de asistencias generadas para un incidente | **Pass** | History with `ANSWERED` and `NO_RESULTS` queries, steps, sources with similarity and rating. Unknown incident: 404 |
| 10 | Calificar una respuesta y verificar la persistencia del registro | **Pass** | Rated 4 from the UI: 201, row stored in `ratings`, returned by the history. A second rating gets 409 |

**Result: 10 of 10 passed.**

## Details by test

### Tests 1, 2 and 3: RAG with and without enough information

- **Tests 1 and 2.** The threshold lets through the article that matches the problem. The answer (solution and steps) is built only from that article, and the source is returned with its similarity.
- **Test 3.** It asks about a topic that has no article. No article reaches 0.60, so n8n answers `NO_RESULTS` without calling the language model, and the UI tells the user to escalate the incident.

Evidence: [ai-tests.md](ai-tests.md) with screenshots of the Asistente IA view (`evidence/assistant-test*.png`).

### Test 4: incidents of a category vs the relational database

The incident list (`GET /api/incidents?categoryId=4`) and the dashboard (`GET /api/analytics/by-category?categoryId=4`) were compared with direct SQL counts on the same database:
- **Category alone:** 27 incidents of *Aplicaciones* in the list, in the dashboard and in SQL, and the ids match.
- **With filters:** adding a date range, or a category plus a priority, the counts still match (10 and 9 incidents).
- **Without filters:** the per-category distribution matches `by_category.sql` (41 / 37 / 27 / 23 / 22).

The list and the dashboard use the same date rule (whole days in Bogotá time, end date inclusive), so both screens always show the same incidents. Evidence: [analytics-tests.md](analytics-tests.md).

### Test 5: weekly trend vs SQL

The *Evolución semanal* chart (`GET /api/analytics/weekly-trend`) and `database/analytics/weekly_trend.sql` give the same count for each of the 12 weeks, from 2026-08-10 to 2026-10-26, with 150 incidents in total.

The other indicators match their SQL files too:
- resolution rate: 88.00 %;
- average resolution time per category;
- distribution by priority;
- monthly totals.

An inverted date range gets 400. Evidence: [analytics-tests.md](analytics-tests.md), with the week-by-week comparison table. The indicators are interpreted in the [data analysis report](../data-analysis-report/README.md).

### Test 6: deactivated article

Two barriers keep a deactivated article out of the answers:
1. When the article is deactivated, it is removed from Qdrant. Indexing runs after the transaction commits.
2. Spring Boot checks every source against PostgreSQL and drops the inactive ones, even if they are still in Qdrant.

The same question went from `ANSWERED` to `NO_RESULTS`, and back to `ANSWERED` after reactivating the article. Evidence: [ai-tests.md](ai-tests.md). The Postman collection repeats the deactivate and activate steps and checks `usableAsSource`.

### Test 7: blank description

`@NotBlank` on the request rejects it with 400 before the service runs, so no query is stored and n8n is not called. Evidence:
- [ai-tests.md](ai-tests.md), with a screenshot of the browser-side check;
- the Swagger screenshot [04](../postman/screenshots/04-assistance-blank-description-400.png);
- the Postman collection.

### Test 8: embedding service failure

n8n was restarted with an invalid OpenRouter key. The API still answers, the query is stored as `ERROR` with the step that failed, and it shows up in the history as *Error*. Evidence: [ai-tests.md](ai-tests.md).

### Tests 9 and 10: history and rating

- **Test 9:** the History view and `GET /api/incidents/{id}/queries` list every query of the incident, newest first. Each one shows its status, answer, numbered steps, sources with similarity and rating.
- **Test 10:** an answer was rated from the UI. The row was then checked in the `ratings` table and in the history response.
- **Rules around the rating:** 409 for a second rating, 400 for a score out of range, and 404 for an unknown answer.

Evidence: [history-rating-tests.md](history-rating-tests.md), Swagger screenshots [05–07](../postman/screenshots/) and screenshots of the view (`evidence/history-test*.png`).

## Supporting checks

| Check | How | Result |
|---|---|---|
| API regression | [Postman collection](../postman/README.md) with newman: the 31 endpoints, including error cases and analytics cross-checks | 45 requests, 70 assertions, 0 failures |
| Backend unit tests | `mvn test` in `backend/` (services, validation, filters) | Passing, run before every module PR |
| Threshold calibration | 75 relevant incidents and 19 questions without an article, against the 25 articles ([report](../threshold-calibration.md)) | At 0.60 no question without an article passes (0/19), and 67/75 relevant incidents find their article. The 8 misses get `NO_RESULTS` instead of a wrong answer |

## Defects found while testing

| Defect | Found in | Fix |
|---|---|---|
| Every POST, PUT and PATCH from the browser got 403 | End-to-end UI tests (registering incidents, rating) | nginx sent `Host` without the port, so Spring CORS treated same-origin requests as cross-origin. Changed to `$http_host` (PR #17) |
| A failed AI query only said "error 502" | Test 8 | The stored error now keeps the step reported by n8n (PR #18) |
| Swagger showed real 400 and 409 rating answers as *Undocumented* | Swagger evidence | Declared the response codes with the `ApiError` schema (PR #19) |

## How to repeat

```bash
docker compose up -d --build        # plus the first-run steps in the main README
python3 -I scripts/testing/run_ai_tests.py                                         # tests 1, 2, 3, 6, 7, 8
npx newman run docs/postman/incidents-rag.postman_collection.json --timeout-request 90000   # tests 1, 3, 6, 7, 9, 10
```

Tests 9 and 10 can also be repeated from the UI, at `http://localhost:8081/#history`.
