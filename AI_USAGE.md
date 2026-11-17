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

## Member A — Jonatan-17 (Data & analytics)

**Tool:** an AI coding assistant, used inside the IDE as a technical tutor and reviewer.

### Assisted components

| Component | How the AI helped | My part |
|---|---|---|
| `database/init/01_schema.sql` | Proposed the table structure, CHECK constraints, indexes and delete rules | Chose the domain values, ran the script in PostgreSQL and tested every constraint with valid and invalid data |
| JPA entities, enums and repositories | Drafted the entities to match the schema | Verified them by starting the backend in Hibernate `validate` mode and running the unit tests |
| `docs/diagrams/er-diagram.md` | Drafted the Mermaid diagram from the schema | Reviewed relationships and delete rules |
| `scripts/catalog.json`, `docs/knowledge-topics.md` | Proposed the 25 topics and incident descriptions | Defined the categories, the topics without an article (test 3) and shared the contract with member C |
| Review of PR #12 | Pointed out the impact of creating incidents as resolved on the average resolution time | Ran the PR tests locally and published the review |

### Technical decisions taken with AI support

- **Incident status** `OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`; RESOLVED and CLOSED count as resolved.
- **Priority** `P1` (critical) to `P4` (low).
- **Articles** have two flags, `active` and `verified`; only articles with both can be RAG sources (RF02).
- **`resolved_at` is set if and only if the incident is resolved**, enforced by a CHECK and by `Incident#changeStatus`, so the resolution rate and time cannot be inconsistent.
- **Delete rules:** RESTRICT for users, categories and articles used as sources (history is kept); CASCADE from an incident to its AI history.
- **Column types** chosen to match Java types (`DOUBLE PRECISION` ↔ `Double`, `INTEGER` ↔ `Integer`) so Hibernate validation passes.
- **Topics shared between incidents and articles**, so RAG finds real matches, with two "trap" topics to calibrate the threshold.

Every generated change was read, run and tested before committing, so I can explain it in the presentation.
