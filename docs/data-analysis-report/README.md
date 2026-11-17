# Data analysis report

Indicators computed on the initial dataset (`database/init/02_seed.sql`, 150 incidents from
2026-08-10 to 2026-11-01). The data is synthetic and reproducible: its generation rules are in
[synthetic-data.md](synthetic-data.md). All dates are grouped in Bogotá time (UTC-5).

Every value comes from SQL. The dashboard calls `/api/analytics/*`, which runs the same queries
as the files in [`database/analytics/`](../../database/analytics/) plus the optional filters
(dates, category, priority), so any figure can be checked by running a file with `psql`.

## Indicators

### 1. Total incidents per period — `total_by_period.sql`

| Month | Incidents |
|---|---:|
| 2026-08 (from the 10th) | 31 |
| 2026-09 | 55 |
| 2026-10 | 64 |
| **Total** | **150** |

### 2. Frequency and percentage per category — `by_category.sql`

| Category | Incidents | % |
|---|---:|---:|
| Conectividad | 41 | 27.33 |
| Software y sistema operativo | 37 | 24.67 |
| Aplicaciones | 27 | 18.00 |
| Hardware | 23 | 15.33 |
| Cuentas y accesos | 22 | 14.67 |

### 3. Weekly evolution — `weekly_trend.sql`

| Week (Monday) | Incidents | Week (Monday) | Incidents |
|---|---:|---|---:|
| 2026-08-10 | 9 | 2026-09-21 | 12 |
| 2026-08-17 | 10 | 2026-09-28 | 12 |
| 2026-08-24 | 11 | 2026-10-05 | 16 |
| 2026-08-31 | 7 | 2026-10-12 | 16 |
| 2026-09-07 | 17 | 2026-10-19 | 15 |
| 2026-09-14 | 15 | 2026-10-26 | 10 |

### 4. Resolution rate — `resolution_rate.sql`

132 of 150 incidents are resolved (`RESOLVED` or `CLOSED`): **88.00 %**. The other 18 are
`OPEN` (11) or `IN_PROGRESS` (7).

### 5. Average resolution time per category — `avg_resolution_time.sql`

| Category | Resolved incidents | Average (hours) |
|---|---:|---:|
| Aplicaciones | 22 | 41.34 |
| Hardware | 21 | 32.51 |
| Software y sistema operativo | 34 | 25.52 |
| Conectividad | 38 | 10.96 |
| Cuentas y accesos | 17 | 4.17 |

Overall average: **22.33 hours**. Incidents resolved within one minute of their creation are
excluded: the API allows registering an incident directly as resolved, and those zero
durations would lower the average without describing real work.

### 6. Most recurrent category — `top_category.sql`

**Conectividad**, with 41 incidents (27.33 %).

### 7. Distribution by priority — `by_priority.sql`

| Priority | Incidents | % |
|---|---:|---:|
| P1 (critical) | 17 | 11.33 |
| P2 (high) | 35 | 23.33 |
| P3 (medium) | 67 | 44.67 |
| P4 (low) | 31 | 20.67 |

## Conclusions

**1. Connectivity drives the volume, but applications drive the effort.**
Conectividad is the most frequent category (41 incidents, 27.33 %) yet is solved in 10.96 hours
on average. Aplicaciones has fewer incidents (27) but takes 41.34 hours, almost 4 times longer,
and Hardware 32.51 hours. Measured as volume × average time, Aplicaciones (22 × 41.34 ≈ 909 h)
consumes more support hours than Conectividad (38 × 10.96 ≈ 416 h). Knowledge articles and
training aimed at application errors would save the most time.

**2. Demand grows week after week.**
The first four weeks average 9.25 incidents per week (37 in total), the last four 14.25 (57):
**+54 %**. Monthly totals confirm it: 55 in September and 64 in October (+16 %). The last week
(10) is lower because its incidents are the most recent and the data was extracted on Monday
2026-11-02. If the trend continues, the team needs more capacity or more self-service through
the AI assistant.

**3. Priority is respected, and the backlog concentrates in fast categories.**
Average resolution time grows with lower urgency: P1 11.64 h, P2 16.47 h, P3 25.35 h,
P4 28.01 h, so critical incidents are attended first. However, Cuentas y accesos, the fastest
category (4.17 h), has 5 of its 22 incidents unresolved (23 %), the highest share of any
category, which suggests those requests wait on approvals rather than on technical work.

The data is synthetic: these conclusions show how the indicators are read and that they are
consistent with the generation rules, not facts about a real organization.

## How to verify

```bash
# From the repository root, with the values of .env
docker compose exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" < database/analytics/by_category.sql
```

Or copy any file of `database/analytics/` into pgAdmin. The comparison between the dashboard and
direct SQL (tests 4 and 5) is in [docs/testing/analytics-tests.md](../testing/analytics-tests.md).
