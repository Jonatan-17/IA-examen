# Synthetic data — generation rules

The initial data in `database/init/02_seed.sql` is synthetic. It is produced by
`scripts/generate_seed.py` with a **fixed seed (2026)**, so running the script again
always produces the same file and every indicator in this report can be recalculated.

```bash
python scripts/generate_seed.py            # rewrites database/init/02_seed.sql
docker compose down -v && docker compose up -d   # reloads the database (deletes current data)
```

Do not edit `02_seed.sql` by hand: change the script or its inputs and regenerate it.

## Inputs

| File | Content |
|---|---|
| `scripts/catalog.json` | 5 categories with their weight, resolution time range and topics (incident titles and descriptions) |
| `scripts/articles.json` | The 25 knowledge articles written by member C, one per topic with `has_article = true` |

Both files share the same topic keys, so every incident whose topic has an article
describes a problem that the knowledge base can actually solve (RAG tests 1 and 2).

## Volumes

| Set | Generated | Minimum required |
|---|---:|---:|
| Incidents | 150 | 150 |
| Knowledge articles | 25 | 25 |
| Categories | 5 | 5 |
| Users | 10 | 10 |
| Incident statuses | 4 | 4 |
| Priority levels | 4 | 4 |

## Rules

**Categories.** Ids 1 to 5 in catalog order. Each incident picks a category by weight:

| Category | Weight | Resolution hours | Incidents |
|---|---:|---|---:|
| Conectividad | 30 | 1–24 | 41 |
| Hardware | 20 | 4–72 | 23 |
| Software y sistema operativo | 20 | 2–48 | 37 |
| Aplicaciones | 18 | 2–96 | 27 |
| Cuentas y accesos | 12 | 1–8 | 22 |

**Topics.** Inside its category, each incident picks a topic. Topics without an article
have a weight of 0.3 instead of 1, so they are rare (6 incidents) but present for test 3
(the assistant must not invent a solution).

**Users.** 10 users with unique first and last names (sampled without replacement), an
accent-free lower-case email `name.lastname@empresa.com` and a random area.

**Dates.** 12 full weeks, Monday 2026-08-10 to Sunday 2026-11-01, in Bogotá time (UTC-5,
no daylight saving time). Each day has a weight:

- weekends weigh 0.25 of a weekday (support receives fewer requests);
- every week weighs 4 % more than the previous one (a mild upward trend to analyse).

The hour is between 07:00 and 18:59. Incident ids follow creation order.

**Priorities.** P1 10 %, P2 25 %, P3 40 %, P4 25 % (result: 17, 35, 67, 31).

**Statuses and resolution dates.** The data is treated as extracted on
**2026-11-02 09:00** (Bogotá):

1. Resolution time = random value inside the category range × a priority factor
   (P1 0.4, P2 0.7, P3 1.0, P4 1.3): urgent incidents are attended faster.
2. If the resolution would fall after the extraction moment, or the incident is one of the
   8 % left stalled (waiting on a third party), it stays `OPEN` or `IN_PROGRESS`, with
   `resolved_at = NULL`.
3. Otherwise it is `RESOLVED` with `resolved_at` = creation + resolution time. If it was
   resolved more than 48 hours before the extraction, it is `CLOSED` with a 60 % probability.

Result: 76 `CLOSED`, 56 `RESOLVED`, 11 `OPEN`, 7 `IN_PROGRESS`. These rules match the
schema checks: `resolved_at` is set only for `RESOLVED` and `CLOSED`, and it is never
before `created_at`.

**Articles.** Ids 1 to 25 in `articles.json` order, all active and verified, created on
2026-08-03 (the week before the first incident). They use no randomness, so they do not
change the generated incidents.

**Sequences.** The rows use explicit ids, so the script moves every id sequence past the
maximum id; records created later through the API continue from there.

## Validation

The generated file was loaded on an empty `postgres:16-alpine` database on top of
`01_schema.sql` with no errors, and returned the counts above.
