# Tick Sighting Tracker

Video: https://youtu.be/UBbaoad5Tuc

Backend MVP for Elanco's UK tick sighting task. It downloads the raw sighting data from the Elanco API,
cleans and stores it in its own database, and exposes REST endpoints to search, filter and summarise it.

**Stack:** Java 25, Spring Boot 4.1, Spring Data JPA, H2 (file based), springdoc (Swagger UI), Maven.

## How to run

### With Docker

```
docker compose up --build
```

The API is then at <http://localhost:8080>. The database lives in a Docker volume (`tick-data`), so it survives
restarts. `docker compose down -v` also deletes the data.

### Locally

You need JDK 25. Maven is included through the wrapper.

```
./mvnw spring-boot:run     # run the app (database file is created in ./data)
./mvnw test                # run the tests (53)
```

If your default JDK is not 25, set `JAVA_HOME` first, e.g. `export JAVA_HOME=$(/usr/libexec/java_home -v 25)` on macOS.

### Useful links once it is running

| What | URL |
|---|---|
| Swagger UI (try every endpoint) | <http://localhost:8080/swagger-ui.html> |
| Health | <http://localhost:8080/health> |
| H2 database console (local dev) | <http://localhost:8080/h2-console> (JDBC URL `jdbc:h2:file:./data/ticktracker`, user `sa`, empty password) |

The first load of data happens automatically a moment after startup. Until it finishes, the data endpoints
return `503`.

## Endpoints

All filters are optional query parameters. Dates are ISO (`2025-03-01`) and both ends are inclusive.

| Method and path | What it does |
|---|---|
| `GET /sightings?region=&species=&start=&end=&page=0&size=50` | Filtered, paged list, newest first. `size` is 1 to 200. |
| `GET /sightings/{id}` | One sighting with an ID. |
| `GET /stats/regions?start=&end=` | Sightings per region, descending order. |
| `GET /stats/trends?interval=week\|month&region=&start=&end=` | Sightings over time. `interval` defaults to `month`. |
| `GET /health` | Status, last successful ingest, record counts, whether the last refresh reached upstream. |
| `POST /admin/refresh` | Re-download the data now and return a summary of the run. |

Examples:

```
GET /sightings?region=london&start=2020-01-01&end=2020-12-31&size=2
{"content":[{"id":"lGMFSErS6WoC1aJWjvCj","region":"London","species":"Tree-hole tick",
  "latinName":"Ixodes arboricola","sightingDate":"2020-11-29T03:08:49"}, ...],
 "page":0,"size":2,"totalElements":7,"totalPages":4}

GET /stats/regions
[{"region":"Nottingham","count":95},{"region":"Birmingham","count":82}, ...]

GET /stats/trends?interval=month&region=Leeds&start=2024-01-01
[{"period":"2024-02","count":1},{"period":"2024-03","count":1}, ...]
```

For weeks, `period` is the Monday the week starts on (`2025-03-03`).

Every error uses one shape, and never includes a stack trace:

```
{"status":400,"error":"Bad Request","message":"start must be on or before end",
 "path":"/sightings","timestamp":"2026-10-30T14:26:31.401004Z"}
```

| Code | When |
|---|---|
| 400 | Bad parameter: `start` after `end`, unparseable date, `size` outside 1 to 200, bad `interval`. |
| 404 | Unknown sighting id or unknown URL. |
| 409 | `POST /admin/refresh` while a refresh is already running. |
| 502 | `POST /admin/refresh` when the upstream API could not be read. |
| 503 | Data endpoints, only when no data has ever been ingested. |

## How it works

```
Elanco API -> UpstreamClient (timeouts + retry/backoff)
           -> IngestionService (validate, normalise, dedupe)
           -> Database (sightings, rejected_records, ingestion_runs)
           -> Controllers (sightings, stats, health, admin)
```

**The most important rule: user requests never call the Elanco API.** Endpoints only read our own database.
Data is refreshed on startup, every 6 hours, and on `POST /admin/refresh`. That keeps responses fast, protects the
rate-limited upstream, and means we keep serving the last good data if upstream is down.

Code layout (`com.example.elancobackend`): `client` (upstream calls), `ingestion` (cleaning and the refresh job),
`model` (database entities), `repository` (queries), `service` (filter and stats logic), `api` (controllers and
response types), `error` (error handling), `config` (settings and beans).

## What the upstream data looks like

Inspected on 30 October 2026 (`GET https://dev-task.elancoapps.com/data/tick-sightings`):

- One call returns every record (1,000 of them). There is no pagination.
- Five fields: `id`, `date` (`2025-08-14T17:30:00`, no timezone), `location` (always a UK city, 14 seen),
  `species` (5 seen), `latinName`.
- In this snapshot the data was clean: unique ids, one date format, no blanks. The code still defends against
  bad data, because a later download could differ.

## Decisions and why

- **Upstream `id` is our primary key.** It is unique and always present, so duplicates are impossible, and
  "insert or update" is just a save. No hash needed.
- **`location` is stored as `region`.** It always holds a city, and the brief talks about regions.
- **Bad records are kept, not dropped.** A record with a missing field or an unparseable date goes to the
  `rejected_records` table with its raw JSON and the reason, linked to the run that found it.
- **Every refresh is recorded** in `ingestion_runs`: fetched, inserted, updated, duplicates, rejected. "Duplicates"
  means a repeated id within one download, or a record we already hold unchanged, so the numbers add up.
- **A failed refresh changes nothing.** Storing a download is one database transaction, so a failure part way
  never leaves half-updated data. The failed run is recorded and `/health` shows `DEGRADED`.
- **Retries only where they help.** Timeouts and 5xx errors are retried with a doubling wait (1s, 2s, 4s, up to
  3 retries). 4xx errors are not, including 429 (rate limited), where retrying quickly would make it worse.
  Spring Framework 7's built in `@Retryable` is used, so no extra library.
- **Only one refresh at a time.** Startup, the schedule and `/admin/refresh` cannot overlap.
- **Filtering and counting happen in the database** (JPQL `WHERE` and `GROUP BY`), never by loading everything
  into memory. There is an index on `(region, sighting_date)`.
- **Trends are counted per day in SQL, then added up into weeks or months in Java.** Week numbers in SQL are easy
  to get wrong around New Year and differ between databases. The Java step handles at most one row per day.
- **Sightings have a time, so `end=2025-03-01` includes the whole day** (up to 23:59:59).
- **`/health` does not call upstream.** It reports what the last refresh saw, which follows the rule above.
- **Settings live in `application.yml`** and can be overridden with environment variables
  (e.g. `INGESTION_REFRESH_INTERVAL=PT1H`).

## Tests

53 tests, run with `./mvnw test`. They never call the real Elanco API.

- **Cleaning and dedupe** (unit tests): trimming, casing, date formats, every rejection reason.
- **Ingestion** (with a mocked upstream): inserts, rejects, duplicates, updates, failed runs keep old data,
  overlapping runs are refused.
- **Upstream client** (against a small local HTTP server): success, retry then success, giving up, no retry on
  4xx, retry on timeout.
- **Endpoints** (MockMvc): every filter, paging, date edge cases (23:59, 00:00, year boundary), all error codes,
  health states, refresh.

## Known limitations and what I would improve

- **No authentication.** `POST /admin/refresh` is open. It should be protected, because the upstream is rate limited.
- **H2 is a file database for one instance.** For several instances I would switch to Postgres (a config change plus
  the driver). I would re-run the tests on Postgres first, because the "optional filter" queries rely on how H2
  treats null parameters.
- **Rejected records pile up.** The same bad record is stored again on every refresh. I would store each only once.
- **Trends skip empty periods.** A week or month with no sightings is left out; a chart would want zeros.
- **Inserts check the database once per record.** Fine for 1,000 records; for millions I would use batch upserts.
- **Tables are created with `ddl-auto: update`.** A real deployment should use migrations (Flyway).
- **`/health` can be up to 6 hours out of date** about upstream, since it reflects the last refresh.
- **Not done:** the optional forecasting extension.
