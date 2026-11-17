# API collection and Swagger evidence

- **Postman collection:** [`incidents-rag.postman_collection.json`](incidents-rag.postman_collection.json). It has 45 requests over the 31 endpoints of the Spring Boot API, and each one checks its response.
- **Swagger UI:** <http://localhost:8080/swagger-ui.html> (OpenAPI JSON at `/v3/api-docs`).

## Before running it

1. Start the stack with `docker compose up -d --build`.
2. Put a valid `OPENROUTER_API_KEY` in `.env`. Only the requests in folder 5 (AI assistant) need it.
3. Make sure the n8n workflows are active and the knowledge base is indexed. Folder 4 includes `POST /api/knowledge-articles/reindex`.

## Run it

**Postman:** *Import* the JSON file, then open the collection and choose *Run collection*. Run the folders in order, because later requests reuse the ids that earlier ones save.

**Command line**, from the repository root:

```bash
npx newman run docs/postman/incidents-rag.postman_collection.json --timeout-request 90000
```

A full run takes about one minute, most of it in the reindex and the two assistant calls. The last run against the Docker stack had **45 requests, 70 assertions and 0 failures**.

Things to know before running it:
- **Data:** each run creates one category, one user, one incident, one article, two AI queries and one rating.
- **Repeat runs:** names and emails get a timestamp suffix, so you can run it again on the same database.
- **Cost:** the two assistant requests call OpenRouter.

### Variables

| Variable | Value |
|---|---|
| `baseUrl` | `http://localhost:8080/api` (backend). Use `http://localhost:8081/api` to go through nginx. |
| `categoryId`, `userId`, `incidentId`, `articleId` | Saved by the *Create* requests |
| `responseId` | Saved by *Query history of the incident*. The assistant response does not include it, so it is taken from the history. |

### Folders

| Folder | What it covers | Mandatory tests |
|---|---|---|
| 0. System | `GET /info` | |
| 1. Categories | CRUD, 400 when the name is blank, 404 when the id does not exist | |
| 2. Users | CRUD, 400 when the email is invalid | |
| 3. Incidents | Create (status defaults to `OPEN`), list, filter by category, priority, status and dates, change status, 400 when the date range is inverted, 404 when the category does not exist | |
| 4. Knowledge base | Create, update (keeps `verified` when the field is omitted), deactivate, activate, reindex | 6 |
| 5. AI assistant | `ANSWERED` with sources, `NO_RESULTS` with no invented answer, 400 when the description is blank | 1, 3, 7 |
| 6. History and rating | History with sources and similarities, rating, 409 on a second rating, 400 when the score is out of range, 404 when the answer does not exist | 9, 10 |
| 7. Analytics | The 6 dashboard endpoints. The breakdowns by category, priority, week and month must add up to the summary total, and percentages to 100. Also covers the category filter, all filters together, and a 400 for an inverted date range | 4, 5 (consistency) |
| 8. Cleanup | 409 when deleting a category or user that still has incidents | |

## Swagger screenshots

Each screenshot shows the executed request (parameters, body and `curl`) and the real server response.

| Screenshot | Request | Result |
|---|---|---|
| [01](screenshots/01-swagger-overview.png) | Overview | Every group, endpoint and schema |
| [02](screenshots/02-incident-by-id.png) | `GET /api/incidents/{id}` | 200 with category and user names |
| [03](screenshots/03-knowledge-article-by-id.png) | `GET /api/knowledge-articles/{id}` | 200 with `usableAsSource` |
| [04](screenshots/04-assistance-blank-description-400.png) | `POST /api/incidents/{id}/assistance` with a blank description | 400; the AI flow is not called (test 7) |
| [05](screenshots/05-history-test9.png) | `GET /api/incidents/{id}/queries` | 200 with a `NO_RESULTS` query and an `ANSWERED` one with solution, steps and sources (test 9) |
| [06](screenshots/06-rating-duplicate-409.png) | `POST /api/responses/{id}/rating` on an answer that was already rated | 409 "Esta respuesta ya fue calificada" (test 10) |
| [07](screenshots/07-rating-out-of-range-400.png) | `POST /api/responses/{id}/rating` with `score: 6` | 400 with the field error "La calificación máxima es 5" |
