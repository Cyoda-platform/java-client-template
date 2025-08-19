# Functional Requirements — Flight Search Prototype

## Overview
This document defines the entities, workflows, processors, API contracts and business rules for the Flight Search prototype. It consolidates and corrects earlier logic inconsistencies (id naming, entity statuses, lifecycle transitions, and processor responsibilities) so that the implementation and orchestration (Cyoda) are consistent and unambiguous.

Key conventions
- technicalId: the system (persistence) identifier for an entity (UUID or similar). Returned by creation endpoints and used to retrieve persisted entities.
- searchId: optional business identifier for the search (human/business reference). Not required for internal operations but can be stored.
- All processors must be idempotent and re-entrant.
- Dates/times follow ISO-8601. IATA airport codes use the 3-letter uppercase format (^[A-Z]{3}$).

---

## 1. Entity Definitions

FlightSearch
- technicalId: String (system id, persisted primary key)
- searchId: String (optional business id for the search)
- originAirportCode: String (IATA 3-letter code entered by user)
- destinationAirportCode: String (IATA 3-letter code entered by user)
- departureDate: String (ISO date, e.g. 2025-09-01)
- returnDate: String (ISO date, optional — present for round-trip searches)
- passengerCount: Integer (number of passengers, >= 1)
- cabinClass: String (optional, enum: ECONOMY|PREMIUM_ECONOMY|BUSINESS|FIRST)
- createdAt: String (ISO timestamp)
- updatedAt: String (ISO timestamp)
- status: String (PENDING | VALIDATING | QUERYING | MAPPING | SUCCESS | NO_RESULTS | ERROR)
- errorMessage: String (optional; populated on ERROR)
- rawResponse: String/JSON (optional raw API payload stored for debugging/auditing)
- suggestions: Object (optional; populated if NO_RESULTS and alternate suggestions are generated)

Notes: When persisted by POST, a technicalId is returned. Cyoda begins processing automatically when a FlightSearch with status PENDING is stored.

FlightOption
- technicalId: String (system id for the option)
- optionId: String (optional business id for this flight option)
- searchTechnicalId: String (links to FlightSearch. Use technicalId to avoid ambiguity)
- airline: String
- flightNumber: String
- departureTime: String (ISO datetime with timezone information)
- arrivalTime: String (ISO datetime with timezone information)
- durationMinutes: Integer
- priceAmount: Number
- currency: String (ISO 4217, e.g. USD)
- stops: Integer
- layovers: String (short description)
- fareRules: String (summary)
- seatAvailability: Integer
- createdAt: String (ISO timestamp)
- updatedAt: String (ISO timestamp)
- status: String (CREATED | ENRICHING | AVAILABILITY_CHECK | READY | UNAVAILABLE | SELECTED | ARCHIVED | ERROR)
- errorMessage: String (optional)

Note: FlightOption must include status. Processors set and transition status as they run.

Airport
- airportCode: String (IATA code)
- name: String (airport full name)
- city: String
- country: String
- timezone: String (IANA tz database name, e.g. America/New_York)
- createdAt: String (ISO timestamp)
- updatedAt: String (ISO timestamp)

---

## 2. Workflows and State Machines
Workflows are driven by events (entity persisted/updated) and processors in Cyoda. Each workflow below lists states and transitions and clarifies where processors run.

FlightSearch workflow (high level)
1. Persist FlightSearch with status = PENDING (POST). Cyoda begins processing.
2. VALIDATING: ValidationProcessor runs and transitions status -> VALIDATING while checking inputs.
   - On validation failure -> status = ERROR (with errorMessage) and stop.
3. QUERYING: QueryApiProcessor runs to call the external Flight/Airport provider(s). status -> QUERYING.
   - On external API/transport error -> status = ERROR (with errorMessage) and stop.
4. MAPPING: MapResultsProcessor transforms raw responses into FlightOption entities (status -> MAPPING).
   - If result set empty -> set status = NO_RESULTS and optionally run SuggestionProcessor to populate suggestions.
   - If results exist -> create FlightOption(s) with status = CREATED and persist; set FlightSearch.status = SUCCESS.
5. Post-processing: Any enrichment/availability checks for each FlightOption are triggered automatically by FlightOption workflow.
6. Manual actions: User may re-run the search, adjust filters or request suggestions. Re-run should support idempotency.
7. Final states: SUCCESS, NO_RESULTS, or ERROR (complete).

FlightSearch state diagram (summary)
- PENDING -> VALIDATING -> QUERYING -> MAPPING -> {SUCCESS | NO_RESULTS}
- Any failure at VALIDATING / QUERYING / MAPPING -> ERROR

FlightOption workflow
1. CREATED: Newly persisted option from MapResultsProcessor starts in CREATED.
2. ENRICHING: EnrichFareProcessor runs if fareRules / baggage / other details missing. status -> ENRICHING.
3. AVAILABILITY_CHECK: AvailabilityProcessor runs to check seatAvailability. status -> AVAILABILITY_CHECK.
4. READY / UNAVAILABLE: Based on seatAvailability, set status -> READY (seats available) or UNAVAILABLE (no seats).
5. SELECTED: If user selects this option for booking, status -> SELECTED (manual action).
6. ARCHIVED: After TTL or search expiry, status -> ARCHIVED. Also UNAVAILABLE -> ARCHIVED can occur by expiry.
7. ERROR: Any persistent errors in enrichment/availability may set status -> ERROR with errorMessage.

FlightOption state diagram (summary)
- CREATED -> ENRICHING -> AVAILABILITY_CHECK -> READY | UNAVAILABLE -> ARCHIVED
- Manual: READY -> SELECTED
- On fatal processor errors -> ERROR

Airport workflow
1. LOOKUP: Airport data requested (on-demand or preloaded).
2. VALIDATING: AirportLookupProcessor verifies the code and fetches metadata.
3. CACHED: CacheAirportProcessor persists local metadata and returns it.
4. UPDATED: AdminUpdate can modify airport metadata.

---

## 3. Processors and Criteria (detailed)
General rules for processors
- All processors must be idempotent and safe for retries.
- Each processor should set the entity status to the corresponding processing state and update timestamps.
- Persist changes after each status change so other processors can observe progress.
- Use an optimistic locking strategy (ETag or version field) to avoid concurrent write conflicts; processors should gracefully handle conflicts by re-loading and re-evaluating.

FlightSearch processors
- ValidationProcessor
  - Criteria: ValidationPassedCriterion / ValidationFailedCriterion
  - Responsibilities: Validate origin/destination IATA codes, date formats, date logic (departure <= return when return present), passengerCount bounds, cabinClass values.
  - On success: set status = VALIDATING -> then allow transition to QUERYING.
  - On failure: set status = ERROR and persist errorMessage.

- QueryApiProcessor
  - Responsibilities: Call external provider(s) (the document previously called "Airport Gap API" — adjust to the actual provider). Store rawResponse for debugging.
  - On transient network/API error: set status = ERROR and persist errorMessage. Consider exponential backoff retries (configurable).
  - On success: store rawResponse and allow MAPPING.

- MapResultsProcessor
  - Responsibilities: Transform rawResponse into FlightOption objects and persist them. For each created FlightOption, set status = CREATED and createdAt/updatedAt.
  - If results empty -> set FlightSearch.status = NO_RESULTS; do not create FlightOption entities.
  - If results exist -> create options and set FlightSearch.status = SUCCESS.
  - Record count of options and optionally top-summary metadata on FlightSearch.

- SuggestionProcessor (optional)
  - Responsibilities: When NO_RESULTS, generate alternate suggestions (nearby airports, flexible dates +/- N days, alternative carriers) and populate FlightSearch.suggestions.

FlightOption processors
- EnrichFareProcessor
  - Criteria: EnrichmentNeededCriterion
  - Responsibilities: If fareRules or baggage details missing, call FareDetails service and summarize to fareRules; set status transitions CREATED -> ENRICHING -> (persist) -> next.
  - On failure: mark option.status = ERROR and set errorMessage (but this should not necessarily affect flightSearch.status if other options exist).

- AvailabilityProcessor
  - Criteria: SeatsAvailableCriterion (should run regardless if seatAvailability unknown)
  - Responsibilities: Call seat-check API and populate seatAvailability. Set status to AVAILABILITY_CHECK while running. After checking:
    - if seats > 0 -> set status = READY
    - else -> set status = UNAVAILABLE

- ExpiryProcessor
  - Responsibilities: After TTL or search expiry, set FlightOption.status = ARCHIVED; remove or archive data from active indexes.

Airport processors
- AirportLookupProcessor
  - Criteria: AirportExistsCriterion
  - Responsibilities: Validate code, fetch metadata from authoritative source.

- CacheAirportProcessor
  - Responsibilities: Persist/cache metadata locally for fast lookup/autocomplete.

---

## 4. Pseudo code (updated, reflect statuses and id fields)

ValidationProcessor
```
class ValidationProcessor {
  void process(FlightSearch s) {
    s.status = "VALIDATING"
    persist(s)

    if missing(s.originAirportCode) or missing(s.departureDate) or s.passengerCount < 1 {
      s.status = "ERROR"
      s.errorMessage = "Invalid search parameters"
      s.updatedAt = now()
      persist(s)
      return
    }

    if not matchesIata(s.originAirportCode) or not matchesIata(s.destinationAirportCode) {
      s.status = "ERROR"
      s.errorMessage = "Invalid airport code(s)"
      s.updatedAt = now()
      persist(s)
      return
    }

    // Other checks (dates, cabin class values)
    // If all validations pass, allow transition to QUERYING by clearing error
  }
}
```

QueryApiProcessor
```
class QueryApiProcessor {
  void process(FlightSearch s) {
    s.status = "QUERYING"
    s.updatedAt = now()
    persist(s)

    try {
      apiResponse = ExternalFlightsAPI.query(s)
      s.rawResponse = apiResponse
      s.updatedAt = now()
      persist(s)
    } catch (e) {
      s.status = "ERROR"
      s.errorMessage = e.message
      s.updatedAt = now()
      persist(s)
    }
  }
}
```

MapResultsProcessor
```
class MapResultsProcessor {
  void process(FlightSearch s) {
    s.status = "MAPPING"
    persist(s)
    results = transform(s.rawResponse)
    if results.empty {
      s.status = "NO_RESULTS"
      s.updatedAt = now()
      persist(s)
      // optionally trigger SuggestionProcessor
      return
    }

    for r in results {
      option = mapToFlightOption(r, s.technicalId)
      option.status = "CREATED"
      option.createdAt = now()
      option.updatedAt = now()
      persist(option) // triggers FlightOption workflow (enrichment, availability checks)
    }

    s.status = "SUCCESS"
    s.updatedAt = now()
    persist(s)
  }
}
```

EnrichFareProcessor (FlightOption)
```
class EnrichFareProcessor {
  void process(FlightOption o) {
    if missing(o.fareRules) {
      o.status = "ENRICHING"
      persist(o)
      try {
        details = ExternalFareService.fetch(o)
        o.fareRules = summarize(details)
        o.updatedAt = now()
        persist(o)
      } catch (e) {
        o.status = "ERROR"
        o.errorMessage = e.message
        persist(o)
      }
    }
  }
}
```

AvailabilityProcessor
```
class AvailabilityProcessor {
  void process(FlightOption o) {
    o.status = "AVAILABILITY_CHECK"
    persist(o)
    try {
      seats = SeatService.check(o)
      o.seatAvailability = seats
      if seats > 0 {
        o.status = "READY"
      } else {
        o.status = "UNAVAILABLE"
      }
      o.updatedAt = now()
      persist(o)
    } catch (e) {
      o.status = "ERROR"
      o.errorMessage = e.message
      persist(o)
    }
  }
}
```

---

## 5. API Endpoints and Contracts
General guidance
- POST /flight-search is the orchestration entrypoint. It persists a FlightSearch and returns the technicalId. Processing is asynchronous (background processors will run).
- POST should be idempotent when the client provides an idempotency key (recommended header: Idempotency-Key) or a searchId to deduplicate identical requests.
- GET endpoints are read-only and return persisted entities and summaries.

Endpoints

- POST /flight-search
  - Purpose: Create FlightSearch entity (triggers Cyoda workflow)
  - Request JSON (example):
    {
      "originAirportCode":"JFK",
      "destinationAirportCode":"LHR",
      "departureDate":"2025-09-01",
      "returnDate":"2025-09-10",
      "passengerCount":2,
      "cabinClass":"ECONOMY"
    }
  - Recommended headers: Idempotency-Key (optional)
  - Response (example): HTTP 202 Accepted
    {
      "technicalId":"ts-123456"
    }
  - Behavior: persists FlightSearch.status = PENDING and returns technicalId immediately. Background processors progress the search.

- GET /flight-search/{technicalId}
  - Purpose: Retrieve persisted FlightSearch including status, timestamps, and optional suggestions.
  - Response JSON (example):
    {
      "technicalId":"ts-123456",
      "searchId":"s-001",
      "originAirportCode":"JFK",
      "destinationAirportCode":"LHR",
      "departureDate":"2025-09-01",
      "returnDate":"2025-09-10",
      "passengerCount":2,
      "status":"SUCCESS",
      "errorMessage":null,
      "createdAt":"2025-07-01T12:00:00Z",
      "updatedAt":"2025-07-01T12:00:30Z"
    }

- GET /flight-search/{technicalId}/results
  - Purpose: Retrieve list of FlightOption objects created for that search (summary or full option objects depending on query params)
  - Response (summary example):
    {
      "options":[
        {
          "technicalId":"o-1",
          "airline":"ExampleAir",
          "departureTime":"2025-09-01T08:00:00Z",
          "arrivalTime":"2025-09-01T20:00:00Z",
          "durationMinutes":720,
          "priceAmount":750,
          "currency":"USD",
          "stops":1,
          "status":"READY"
        }
      ]
    }

- GET /airports/{airportCode}
  - Purpose: Retrieve airport metadata for display/validation
  - Response JSON: Airport object (see entity definition)

Notes
- POST returns technicalId only to emphasize asynchronous processing. Clients poll GET /flight-search/{technicalId} and /results.
- FlightOption and Airport entities are produced/maintained by workflows — updates are performed by processors or admin actions. Clients should not POST FlightOption or Airport for orchestration (except admin endpoints for Airport updates which are out of scope here).

---

## 6. Business Rules, Validation and Non-Functional Requirements
Validation rules
- IATA codes must match ^[A-Z]{3}$.
- Dates must be ISO-8601. departureDate must be present. If returnDate is present (round-trip), departureDate <= returnDate.
- passengerCount must be integer >= 1 and within seller limits (default max 9). Validate per-provider if required.
- cabinClass must be one of allowed enum values.

Idempotency
- POST /flight-search should support an Idempotency-Key header. If the same key is used, return the same technicalId and do not create duplicate processing runs.

Errors and retries
- Processors should classify errors: transient (retryable) vs permanent. Transient errors should be retried with backoff; after configured attempts mark ERROR with sufficient diagnostics.

Timestamps and timezones
- Store timestamps in UTC and store departure/arrival times with timezone (or as ISO-8601 with offset) to allow accurate duration computation.

Concurrency
- Use optimistic locking/versioning to avoid overwriting concurrent processor updates. Processors should re-fetch entity on version conflict and re-run logic.

Data retention and TTL
- FlightOption entities should have a configurable TTL relative to FlightSearch.createdAt. After expiry, ExpiryProcessor moves them to ARCHIVED and optionally deletes or archives external storage.

Observability
- Persist rawResponse for traceability and debugging. Emit events/metrics for search count, average latency, no-results rate, and error rates.

Security
- Sanitize and validate all external provider responses before persisting.
- Secure sensitive fields and PII as per security policy.

---

## 7. Open Questions / Options
1. Round-trip support: The model supports returnDate optional for round-trip. Confirm you want this behavior retained (default: yes).
2. NO_RESULTS suggestions: Should the system generate alternate suggestions (nearby airports or flexible dates)? If yes, add SuggestionProcessor and fields on FlightSearch.suggestions to store them. (Recommended: Yes — common UX improvement.)
3. Idempotency semantics: Should we require an Idempotency-Key header or allow a client-provided searchId? (Recommended: support both; Idempotency-Key for dedup of identical requests, optional searchId for business correlation.)
4. Response status code: POST returns 202 Accepted in this spec to emphasize asynchronous processing. If you prefer synchronous/blocking behavior, we can change to 201 Created with a blocking wait (not recommended for long-running external queries).

---

If you confirm answers for the open questions (especially #2 and #3) I will update the specification to include the SuggestionProcessor design and the exact idempotency handling semantics.

