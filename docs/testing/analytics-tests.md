# Tests 4 and 5 — analytics against the relational database

Run on 2026-11-17 on an empty PostgreSQL 16 database loaded with `01_schema.sql` and
`02_seed.sql`, with the backend connected to it (`TZ=America/Bogota`). Each API value is
compared with a direct SQL query on the same database.

## Test 4 — incidents of a specific category match the relational database

**Steps:** filter the incident list and the dashboard by category *Aplicaciones* (id 4), then
count the same incidents with SQL.

| Check | API | SQL | Result |
|---|---|---|---|
| `GET /api/incidents?categoryId=4` | 27 incidents, all with category "Aplicaciones" | `SELECT count(*) FROM incidents WHERE category_id = 4` → 27 | ✅ |
| First 10 ids | 2, 3, 6, 10, 20, 23, 39, 46, 47, 57 | `array_agg(id ORDER BY id)` → same ids | ✅ |
| `GET /api/analytics/by-category?categoryId=4` | 27 incidents, 100 % | 27 | ✅ |
| `GET /api/incidents?categoryId=4&from=2026-10-01&to=2026-10-31` | 10 | `... AND created_at >= '2026-10-01 00:00-05' AND created_at < '2026-11-01 00:00-05'` → 10 | ✅ |
| `GET /api/analytics/summary?categoryId=4&from=2026-10-01&to=2026-10-31` | total 10, resolved 9 (90 %) | 10 | ✅ |
| `GET /api/analytics/by-category` (no filter) | 41 / 37 / 27 / 23 / 22 | `database/analytics/by_category.sql` → 41 / 37 / 27 / 23 / 22 | ✅ |
| `GET /api/analytics/summary?from=2026-10-01&to=2026-10-31&categoryId=1&priority=P3` | total 9, resolved 8 | `count(*)` → 9, resolved → 8 | ✅ |

The list and the dashboard use the same date rule (whole days in Bogotá time, end date
inclusive), so both show the same incidents for the same filters.

## Test 5 — weekly evolution checked with SQL

**Steps:** open the dashboard with no filters, read the "Evolución semanal" chart, and run
`database/analytics/weekly_trend.sql`.

| Week (Monday) | Dashboard (`/api/analytics/weekly-trend`) | `weekly_trend.sql` |
|---|---:|---:|
| 2026-08-10 | 9 | 9 |
| 2026-08-17 | 10 | 10 |
| 2026-08-24 | 11 | 11 |
| 2026-08-31 | 7 | 7 |
| 2026-09-07 | 17 | 17 |
| 2026-09-14 | 15 | 15 |
| 2026-09-21 | 12 | 12 |
| 2026-09-28 | 12 | 12 |
| 2026-10-05 | 16 | 16 |
| 2026-10-12 | 16 | 16 |
| 2026-10-19 | 15 | 15 |
| 2026-10-26 | 10 | 10 |

All 12 weeks match (total 150). ✅

The other indicators match their SQL files as well: resolution rate 88.00 %, average resolution
time per category (41.34, 32.51, 25.52, 10.96 and 4.17 hours), priority distribution
(17 / 35 / 67 / 31) and monthly totals (31 / 55 / 64).

## Validation errors

| Request | Result |
|---|---|
| `GET /api/analytics/summary?from=2026-11-01&to=2026-10-01` | 400, "La fecha inicial no puede ser posterior a la final" ✅ |
| `GET /api/analytics/by-priority?priority=P9` | 400 (unit test `AnalyticsControllerTest`) ✅ |

Dashboard screenshots: add them here (`dashboard-weekly.png`, `dashboard-category.png`) when
taking the presentation evidence.
