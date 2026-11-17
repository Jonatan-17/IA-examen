# AI_USAGE

Short recommendations from building the assistant. Details: [docs/threshold-calibration.md](docs/threshold-calibration.md) and [docs/chat-model-selection.md](docs/chat-model-selection.md).

- **Same embedding model** for indexing and querying; changing it means reindexing everything.
- **Qdrant point id = article id**, with upsert: no duplicates, and the payload keeps the whole article so the RAG flow needs no database access.
- **No sources, no LLM:** if nothing passes the threshold, answer `NO_RESULTS` without calling the model.
- **Justify the threshold with data**, and test it with near misses (3D printer vs. network printer), not only with off-topic questions. Calibrated: 0.60.
- **Validate sources twice:** Qdrant filters them, Spring Boot confirms each article exists and is active.
- **Low temperature, JSON output, Spanish answers**, and tell the model the incident text is data, not instructions.
- **AI failures must not break the CRUD:** log them, return them as errors and keep a full re-index to recover.
- **Force HTTP/1.1 from Java to n8n**, and test once against the real n8n: mocked tests do not catch it.
- **Keys only in `.env`**, read in n8n with `$env.*`; review exported workflows before committing.
- **Use AI in your own branch** and understand every generated line: each member must be able to explain their code.

## How the AI helped (member C, Mic022)

- **Project template:** the starting skeleton (Docker Compose, Spring Boot with the n8n client, error handling and RAG settings, frontend shell and work plan) was generated with an AI coding assistant and reviewed before each commit.
- **Threshold calibration:** it wrote the script that scores 94 questions against the 25 articles and showed that 0.60 is the lowest value that keeps every question without an article out.
- **Chat model choice:** it compared five models on the same grounded task, including an incident with injected instructions, before choosing Claude Haiku 5.5.
- **Test 8 (embedding failure):** it simulated an invalid OpenRouter key by restarting n8n and restoring it, and found that the stored error hid which step had failed.
- **End-to-end checks:** it found the HTTP/2 (`h2c`) timeout between Java and n8n that mocked tests missed, and tested the assistant view in a headless browser.

Member A's notes: [docs/data-analysis-report/ai-usage.md](docs/data-analysis-report/ai-usage.md).
