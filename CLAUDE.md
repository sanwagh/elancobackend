# CLAUDE.md

## Project

Backend MVP for Elanco's UK **Tick Sighting Tracker** (placement programme technical task).
It ingests raw tick sighting data from `https://dev-task.elancoapps.com/`, cleans and stores it,
and exposes REST endpoints for searching, filtering and aggregate insights.

**Deadline:** 9am, 1 November 2026. This is an MVP: prefer simple, clear and finished over clever and partial.

The author must be able to explain every line in an interview. Favour readable code over abstraction.

## Brief requirements (source of truth)

1. **Data handling:** consume and process data from the provided API; handle large datasets, duplicates and incomplete data.
2. **Search and filtering:** filter sightings by time range and location.
3. **Data reporting:** aggregate endpoints for sightings per region and trends over time (weekly or monthly).
4. **Error handling:** handle upstream API failures and data inconsistencies gracefully.
5. *Extension (optional, only after 1–4 are done and tested):* simple AI/ML insight, e.g. a per-region forecast.

## Stack

- Java 25, Spring Boot 4.1.x, Maven
- Spring Web (use `RestClient` for upstream calls), Spring Data JPA, Bean Validation
- H2 (file-based, so it runs with zero setup); keep it swappable for Postgres via config
- Spring Framework 7 built-in resilience (`@Retryable` + `@EnableResilientMethods`) for upstream calls.
  Do NOT add the separate `spring-retry` dependency.
- springdoc-openapi 3.x (`springdoc-openapi-starter-webmvc-ui`, Swagger UI at `/swagger-ui.html`)
- JUnit 5, MockMvc, Mockito for tests
- Use Java records for DTOs. No Lombok.

**Ask before adding any dependency not listed here.**

## Architecture

```
Elanco API -> UpstreamClient (timeouts + retry/backoff)
           -> IngestionService (validate, normalise, dedupe)
           -> Database (sightings, rejected_records, ingestion_runs)
           -> Controllers (filters, stats, health)
```

Key rules:
- **Never call the upstream API during a user request.** Endpoints only read from our own database.
  Ingestion runs on startup, on a schedule, and via `POST /admin/refresh`.
- If the upstream API is down, keep serving the last good data and report staleness in `/health`.
- Do aggregation in SQL (`GROUP BY`) or JPQL, not by loading everything into memory.
- Index `(region, sighting_date)`.

## Package layout (keep it flat)

```
com.example.elancobackend
  client/       UpstreamClient + upstream DTOs
  ingestion/    IngestionService, cleaning/normalisation helpers, scheduler
  model/        JPA entities (Sighting, RejectedRecord, IngestionRun)
  repository/   Spring Data repositories
  api/          Controllers + response DTOs
  service/      Query and stats logic
  error/        @ControllerAdvice + error response type
  config/       App config (upstream URL, timeouts, retry settings, RestClient bean)
```

## API design

Filters are **query parameters**, not path segments. All filters optional.

```
GET  /sightings?region=&start=&end=&species=&page=0&size=50
GET  /sightings/{id}
GET  /stats/regions?start=&end=
GET  /stats/trends?interval=week|month&region=&start=&end=
GET  /health                  -> status, last successful ingest, record counts, upstream reachable?
POST /admin/refresh           -> trigger re-ingest, returns run summary
```

- Dates are ISO-8601 (`2025-03-01`). Validate that `start <= end`; return 400 otherwise.
- Paginate list endpoints; cap `size` (e.g. max 200).
- Region matching is case-insensitive against the normalised region value.

## Data quality rules

> Fill in once the real API responses have been inspected. Do not guess field names.

- **Upstream API:** `GET https://dev-task.elancoapps.com/data/tick-sightings`. Public, no auth, rate limited (429).
  Returns ALL records in one JSON array (no pagination). Inspected 30 Oct 2026: 1000 records, all with the same
  5 fields, unique ids, one date format, no blanks.
- **Upstream fields:** `id` (20-char string), `date` (`2025-08-14T17:30:00`, date+time, no timezone),
  `location` (a UK city, 14 seen, our "region"), `species` (5 seen), `latinName` (5 seen, 1:1 with species).
- **Duplicates:** the upstream `id` is the primary key of `sightings`, so saving an existing id updates it.
  A record with no id is rejected.
- **Normalisation:** trim whitespace, consistent casing for regions/species, parse all date formats seen.
- **Incomplete or invalid records:** never silently drop. Save them to `rejected_records` with the raw
  payload and a reason.
- **Each ingestion run:** log and persist a summary: fetched / inserted / updated / duplicates / rejected.

## Error handling

- Upstream: connect/read timeouts, retry with exponential backoff on 5xx/timeouts, no retry on 4xx.
  The upstream has no pagination, so a failed fetch means nothing is stored (never a half-written run).
  429 is a 4xx, so it is not retried.
- One consistent JSON error shape from `@ControllerAdvice`:
  `{ "status": 400, "error": "Bad Request", "message": "...", "path": "/sightings", "timestamp": "..." }`
- 400 for invalid params, 404 for unknown id, 503 only if no data has ever been ingested.
- Never leak stack traces in responses.

## Testing

- Write tests alongside each feature, not at the end.
- Priority: cleaning/normalisation and dedupe logic (unit), filter and stats endpoints (MockMvc),
  upstream failure and retry behaviour (mocked client).
- `mvn test` must pass before a step is considered done.

## How to work in this repo

- Work in **small steps, one feature at a time**. Stop after each step so it can be reviewed and committed.
- Use plan mode for anything touching more than a couple of files.
- Explain non-obvious decisions briefly in comments or in your reply. No comments that just restate the code.
- No speculative abstractions (no interfaces with one implementation, no generic frameworks).
- Use constructor injection; keep config in `application.yml`.
- Don't write the README, Dockerfile or ML extension until asked.

## Build order

1. Scaffold project, entities, H2 config, Swagger
2. UpstreamClient with timeouts and retry
3. IngestionService: cleaning, dedupe, rejected records, run summary + tests
4. `GET /sightings` with filters and pagination + tests
5. `/stats/regions` and `/stats/trends` + tests
6. Error handling polish, `/health`, `/admin/refresh`, scheduled refresh
7. Dockerfile / docker compose
8. README (decisions, how to run, what I'd improve)
9. Optional: ML extension

## Commands

```
mvn spring-boot:run      # run locally
mvn test                 # run tests
```
