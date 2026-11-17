# AI tests: evidence (tests 1, 2, 3, 6, 7 and 8)

Mandatory tests of the RAG assistant (member C). Every call goes through Spring Boot
(`POST /api/incidents/{id}/assistance`), like the frontend; n8n, Qdrant and OpenRouter are never called directly.

**Run:** 2026-11-17 09:28, threshold 0.60, top-k 5, embeddings `baai/bge-m3`, chat `anthropic/claude-haiku-5.5`,
25 active and verified articles indexed. **Result: 6/6 passed.**

Script: [`scripts/testing/run_ai_tests.py`](../../scripts/testing/run_ai_tests.py). It creates its own incidents
and leaves the knowledge base and n8n as it found them.

## Results

| # | Test | Input | Expected | Result |
|---:|---|---|---|---|
| 1 | Connectivity incident gets relevant solutions through RAG | "No hay conexión a internet en el piso 3 desde esta mañana." | `ANSWERED` with the internet article as a source | **Pass.** Query 18: `ANSWERED`, source #1 "Sin acceso a internet en el equipo" (0.641), 7 steps, 4.7 s |
| 2 | Application runtime error retrieves the right articles | "La aplicación de facturación se cierra con un error de ejecución al generar reportes." | `ANSWERED` with the application error article | **Pass.** Query 19: `ANSWERED`, source #16 "Error de ejecución en la aplicación de facturación" (0.791), 7 steps |
| 3 | Not enough information: the model must not make up a solution | "La impresora 3D del laboratorio no completa la calibración de la cama." (topic without article) | `NO_RESULTS`, no answer, no sources | **Pass.** Query 20: `NO_RESULTS` in 0.9 s; the model is not called |
| 6 | Deactivated article is not used as a source | Article #1 deactivated with `PATCH /api/knowledge-articles/1/deactivate`, then test 1 repeated | Article #1 is not a source | **Pass.** Query 21: `NO_RESULTS`. After `PATCH .../activate`, query 22: `ANSWERED` with #1 again |
| 7 | Blank description does not run the AI flow | `{"description": "   "}` | 400, no query created | **Pass.** 400 with `fields.description = "La descripción es obligatoria"`; the incident still has only its 2 queries from test 6 |
| 8 | Embedding service failure is handled | n8n restarted with an invalid OpenRouter key | Query stored as `ERROR` with the reason, no crash | **Pass.** Query 23: HTTP 200 with `status: ERROR`, `errorMessage: "AI service responded with error 502: Embed question failed: 401 ..."` |

## Stored history

`ai_queries` with the sources saved in `response_sources` after the run:

| Incident | Query | Status | Error | Sources (article:similarity) |
|---:|---:|---|---|---|
| 9 | 18 | ANSWERED | | 1:0.641 |
| 10 | 19 | ANSWERED | | 16:0.791 |
| 11 | 20 | NO_RESULTS | | |
| 12 | 21 | NO_RESULTS | | |
| 12 | 22 | ANSWERED | | 1:0.641 |
| 13 | 23 | ERROR | AI service responded with error 502: Embed question failed: 401 … | |

## Why each test passes

- **Tests 1 and 2:** the threshold (0.60, see [threshold calibration](../threshold-calibration.md)) lets the right
  article through, and the answer is built only from it.
- **Test 3:** no article reaches 0.60, so n8n returns `NO_RESULTS` without calling the model.
- **Test 6:** two barriers. Deactivating removes the point from Qdrant (indexing listener), and Spring Boot also
  re-checks every source against the database, so an article still present in Qdrant is dropped anyway
  (checked separately by deactivating it only in the database).
- **Test 7:** `@NotBlank` on `AssistanceRequest` rejects the request before the service runs.
- **Test 8:** `AiQueryService` never throws on AI failures: the query ends as `ERROR` and keeps the failing step
  reported by n8n.

## Assistant view

Screenshots of the same cases in the frontend (`#assistant`), taken in a headless Chrome on 2026-11-17:

| Test 1 | Test 2 |
|---|---|
| ![Test 1](evidence/assistant-test1-answered.png) | ![Test 2](evidence/assistant-test2-answered.png) |

| Test 3 | Test 7 |
|---|---|
| ![Test 3](evidence/assistant-test3-no-results.png) | ![Test 7](evidence/assistant-test7-blank-description.png) |

## How to repeat

```bash
docker compose up -d postgres qdrant n8n   # plus the backend
# articles loaded and indexed: POST /api/knowledge-articles/reindex
python3 -I scripts/testing/run_ai_tests.py --json ai-tests-results.json
# --skip-test-8 avoids restarting n8n
```
