# AI usage — Member A (Data & analytics)

Moved from `AI_USAGE.md`, which keeps only short recommendations. Written by Jonatan-17.

**Tool:** an AI coding assistant, used inside the IDE as a technical tutor and reviewer.

### Assisted components

| Component | How the AI helped | My part |
|---|---|---|
| `database/init/01_schema.sql` | Proposed the table structure, CHECK constraints, indexes and delete rules | Chose the domain values, ran the script in PostgreSQL and tested every constraint with valid and invalid data |
| JPA entities, enums and repositories | Drafted the entities to match the schema | Verified them by starting the backend in Hibernate `validate` mode and running the unit tests |
| `docs/diagrams/er-diagram.md` | Drafted the Mermaid diagram from the schema | Reviewed relationships and delete rules |
| `scripts/catalog.json`, `docs/knowledge-topics.md` | Proposed the 25 topics and incident descriptions | Defined the categories, the topics without an article (test 3) and shared the contract with member C |
| Review of PR #12 | Pointed out the impact of creating incidents as resolved on the average resolution time | Ran the PR tests locally and published the review |
| `scripts/generate_seed.py`, `02_seed.sql` | Drafted the generator: weighted dates, priorities, statuses and resolution times | Chose the rules and the extraction date, checked every CHECK constraint and loaded the seed on an empty PostgreSQL |
| `database/analytics/*.sql` | Drafted the 7 queries | Ran each one on the seeded database and compared it with the generator output |
| `AnalyticsService`, `AnalyticsController` | Drafted the endpoints, filters and unit tests | Tested every endpoint against a real database; found and fixed a NUMERIC to Double conversion error that the mocked tests did not catch |
| Dashboard view | Drafted the Chart.js view | Checked the API data behind every card and chart against SQL |
| Data analysis report, tests 4 and 5 | Suggested the structure and candidate conclusions | Computed the evidence with SQL and verified every figure |

### Technical decisions taken with AI support

- **Incident status** `OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`; RESOLVED and CLOSED count as resolved.
- **Priority** `P1` (critical) to `P4` (low).
- **Articles** have two flags, `active` and `verified`; only articles with both can be RAG sources (RF02).
- **`resolved_at` is set if and only if the incident is resolved**, enforced by a CHECK and by `Incident#changeStatus`, so the resolution rate and time cannot be inconsistent.
- **Delete rules:** RESTRICT for users, categories and articles used as sources (history is kept); CASCADE from an incident to its AI history.
- **Column types** chosen to match Java types (`DOUBLE PRECISION` ↔ `Double`, `INTEGER` ↔ `Integer`) so Hibernate validation passes.
- **Topics shared between incidents and articles**, so RAG finds real matches, with two "trap" topics to calibrate the threshold.
- **Analytics run as native SQL** with bind parameters, the same queries as `database/analytics/`, so the dashboard can be verified with psql (tests 4 and 5).
- **Days, weeks and months in Bogotá time**, the same rule as the incident list filters.
- **Average resolution time ignores incidents resolved within one minute of creation** (created directly as resolved).

Every generated change was read, run and tested before committing, so I can explain it in the presentation.
