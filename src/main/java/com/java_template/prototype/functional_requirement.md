# Functional Requirements

Last updated: 2025-08-15

This document defines the domain entities, workflows, processors/criteria, API surface and runtime behaviour for the NBA scores fetch + subscriber notification prototype. It replaces and clarifies the previously generated logic where necessary to reflect the latest rules and invariants.

---

## 1. Entities (Domain Model)

Notes:
- Each persisted entity MUST include a technicalId (UUID or similar) when created via POST endpoints. That technicalId is returned by POST endpoints and used by GET-by-id endpoints.
- Timestamps use ISO-8601 (UTC) unless noted otherwise.

1) Subscriber
- technicalId: String (UUID)
- email: String (subscriber email address)
- subscribed_at: String (ISO-8601 timestamp when subscription was created)
- unsubscribed_at: String? (ISO-8601 timestamp when user unsubscribed)
- status: String ("pending" | "active" | "unsubscribed") — default rules described in workflow
- preferences: JSON? (optional preferences, e.g., { "preferred_time": "18:00", "timezone": "UTC", "notification_format": "text" })
- confirmed: Boolean (whether subscription confirmation completed)
- last_notification_at: String? (ISO-8601 timestamp of last notification sent)

Notes:
- If preferences.require_confirmation is true (or system configured to require confirmation), initial status MUST be "pending" and confirmed=false. Otherwise the subscription may be created as confirmed=true and status="active".


2) Game
- technicalId: String (UUID) — optional internal id; externally referenced id is game_id
- game_id: String (external identifier from sportsdata.io or generated if absent)
- date: String (YYYY-MM-DD) (game date)
- home_team: String
- away_team: String
- home_score: Integer?
- away_score: Integer?
- status: String (e.g., "scheduled", "in_progress", "final")
- venue: String?
- league: String? (e.g., "NBA")
- raw_payload: JSON (full raw payload from external API)
- last_updated: String (ISO-8601 timestamp of last persisted update)
- ready_for_notification: Boolean (derived flag; true when criteria met, e.g., status == "final")

Notes:
- Deduplication uses composite key (game_id, date). If game_id is absent from payload, system should generate a stable id based on teams + date + venue.


3) FetchJob
- technicalId: String (UUID)
- request_date: String (YYYY-MM-DD) (the today parameter sent to API)
- scheduled_time: String? (time when job was scheduled or triggered, e.g., "18:00Z")
- status: String (PENDING | RUNNING | COMPLETED | FAILED)
- started_at: String?
- completed_at: String?
- fetched_count: Integer (number of Game records successfully persisted)
- failed_count: Integer (number of failed game parses/stores)
- response_payload: JSON? (raw response from external API)
- error_message: String? (error details if failed)

Notes:
- Create operations for FetchJob are idempotent at the API level only if the client provides an idempotency key. By default, multiple fetch jobs for the same request_date are allowed unless the ValidateFetchJobCriterion disallows it by policy (see Validation section).


4) DailySummary
- technicalId: String (UUID)
- date: String (YYYY-MM-DD)
- games_summary: JSON (array of per-game summary objects, e.g., [{ game_id, home_team, away_team, home_score, away_score, status }])
- generated_at: String (ISO-8601 timestamp)
- source_fetch_job_id: String? (FetchJob.technicalId)


5) Notification
- technicalId: String (UUID)
- date: String (YYYY-MM-DD) (date the notification summarizes)
- summary_id: String? (DailySummary.technicalId)
- summary_text: String (text/html content of the email summary or a template reference)
- recipients_count: Integer
- recipients_list: Array<String>? (optional list of subscriber technicalIds or emails for auditing)
- sent_at: String? (ISO-8601 timestamp when notification was sent)
- status: String (PENDING | SENDING | SENT | FAILED)
- payload: JSON? (email payload or structured summary)
- attempt_count: Integer (number of send attempts)
- last_error: String?


6) (Operational) DeliveryAttempt / MailLog (optional)
- technicalId: String
- notification_id: String
- recipient: String
- status: String (SENT | FAILED)
- attempt_at: String
- error_message: String?

This optional entity is recommended for observability and retry logic.

---

## 2. Updated Workflows and Key Logic

General rules applied across workflows:
- Persisting an entity is an EVENT that triggers processors/criteria registered for that entity type.
- Processors must be idempotent where possible, and store updated timestamps and version if optimistic concurrency is used.
- All network calls (external API, SMTP) are implemented asynchronously and integrated with the FetchJob or Notification lifecycle through callbacks/futures and explicit status transitions.
- Retries and backoff are defined centrally for external calls and for email delivery.


### Subscriber workflow (updated)

1. Creation: POST /subscribe creates Subscriber with:
   - If system or Subscriber.preferences.require_confirmation == true: status="pending", confirmed=false
   - Else: status="active", confirmed=true
2. Validation: Validate email format and uniqueness. If invalid → reject POST with 400.
3. Confirmation (if required): Send confirmation email asynchronously and set status="pending" until confirmed.
4. Activation: On confirmation event (user clicks link) → confirmed=true, status="active".
5. Added to Notification List: Only Subscribers with status=="active" and confirmed==true are considered by ScheduleNotificationProcessor.
6. Manual Unsubscribe: User action (endpoint or link) sets status="unsubscribed" and unsubscribed_at timestamp.
7. Cleanup / No-op: When unsubscribed → notifications are not scheduled to this subscriber. Historical logs retained for auditing.

State transitions (concise): CREATED -> VALIDATED -> (CONFIRMATION_SENT -> ACTIVE) | ACTIVE -> UNSUBSCRIBED

Criteria & processors (high-level):
- ValidateSubscriberEmailCriterion: checks regex and uniqueness, rejects on failure.
- SendConfirmationProcessor: enqueue confirmation email (asynchronous). Creates a short-lived confirmation token persisted for verification.
- AutoActivateProcessor: sets confirmed=true when confirmation not required.
- OnConfirmationReceivedProcessor: marks confirmed and active when token validated.
- UnsubscribeRequestProcessor: sets status=unsubscribed and records unsubscribed_at.


### Game workflow (updated)

1. Creation: Games are created as part of FetchJob parsing. Each Game persistence triggers the Game workflow.
2. Deduplication: DeduplicateGameCriterion checks composite key (game_id + date) or generated stable id. If a duplicate within the same fetch is found, the MergeOrInsertGameProcessor merges instead of inserting.
3. Merge/Update: MergeOrInsertGameProcessor updates scores, status, venue, raw_payload, and last_updated. It must preserve the most recent authoritative data (based on timestamps from provider or fetch order) and incrementally update changed fields.
4. Finalization: If status == "final" (case-insensitive), set ready_for_notification=true and record finalization time.
5. Indexing: IndexGameProcessor ensures the game record is available to GET endpoints (caching/elastic indexing optional).
6. Reconciliation: If a later fetch produces different data for the same game (e.g., corrected score), ReconciliationProcessor logs the delta, updates the Game, and triggers DailySummary regeneration for that date if the difference affects summaries/notifications.

Implementation notes:
- Merge logic MUST be deterministic. If incoming payload contains an update_timestamp, prefer latest update_timestamp; otherwise, prefer non-null fields over nulls and treat numeric score increases conservatively (accept new values).
- If MergeOrInsertGameProcessor updates a Game that was previously ready_for_notification and the update changes scores or status, flag the related DailySummary for regeneration and any already-created Notification for that summary as needing update (notification may be re-sent depending on policy).


### FetchJob workflow (orchestration entity) — clarified logic

1. Creation: POST /fetch-jobs or scheduler creates FetchJob with status=PENDING.
2. ValidateFetchJobCriterion:
   - Ensure request_date format is YYYY-MM-DD.
   - Enforce rate limits/policy (e.g., disallow more than N FetchJobs per minute from same client or globally).
   - Optional policy: prevent duplicate FetchJob creation for the same request_date within a configurable cooldown window (e.g., 1 hour) unless forced=true present.
3. StartFetchProcessor: mark started_at, status=RUNNING, persist.
4. FetchScoresProcessor: MUST call exactly the provider endpoint (asynchronous):
   GET https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/{today}?key=test
   - {today} substituted with FetchJob.request_date (YYYY-MM-DD).
   - The HTTP client MUST be asynchronous and capture response body and status code into response_payload and error_message as appropriate.
   - Implement HTTP retry policy for transient 5xx errors (e.g., up to 2 retries with exponential backoff). Do not retry 4xx except where appropriate (429 with Retry-After).
5. ParseAndPersistGamesProcessor: parse response_payload into game objects. For each game:
   - Validate required fields.
   - Persist via gameRepo.save(game) which triggers Game workflow.
   - Increment fetched_count for successful saves; increment failed_count for parse or persist errors but continue processing remaining games.
6. GenerateDailySummaryProcessor: after persisting games for request_date, aggregate games into DailySummary.games_summary. If no games are found and policy requires at least one game, set FetchJob.failed_count and optionally mark job FAILED with reason.
7. CreateNotificationProcessor: create Notification entity (status=PENDING) for the generated DailySummary and store link to DailySummary. The Notification creation will in turn trigger the Notification workflow.
8. FinalizeFetchJobProcessor: set completed_at and status=COMPLETED if no fatal errors; otherwise set FAILED with appropriate error_message. Persist metrics (fetched_count, failed_count).
9. FetchFailureCriterion: if external call fails fatally or parsing cannot proceed (e.g., schema mismatch), set status=FAILED and capture details.

Important concurrency and idempotency notes:
- FetchJob processing must be idempotent at the Game level. Re-processing the same response should not create duplicate Game records because of the deduplication step.
- Processing should be resilient to partial failures: a failed Game parse should not abort the whole FetchJob; rather count as failed_count and continue.


### Notification workflow (updated)

1. Creation: Notification is created (status=PENDING) referencing a DailySummary.
2. Schedule Send: ScheduleNotificationProcessor gathers recipients where Subscriber.status=="active" AND Subscriber.confirmed==true and applies subscriber.preferences (timezone, preferred_time) to determine send window. If subscriber preferences dictate a future send time, mark Notification as scheduled for that recipient (implementation may split into per-recipient work items).
3. Send Attempt: SendEmailNotificationProcessor performs batched or individualized sends using mail provider API asynchronously. Update attempt_count and per-recipient DeliveryAttempt logs.
4. Retry: On failure (provider error or transient network error), retry per-recipient up to 3 attempts by default with exponential backoff (base delay configurable; default example: 2s, 4s, 8s). Honor provider Retry-After header when present.
5. Completion: If all targeted recipients successfully accepted by mail provider, set Notification.status=SENT and sent_at to the timestamp of final successful batch.
6. Failure: If after retries some or all recipients failed, set Notification.status=FAILED (or PARTIAL depending on policy), record last_error and recipients_count reflecting successes.
7. Idempotency: If a Notification is re-processed due to a requeue, do not re-send to recipients that have DeliveryAttempt status==SENT (log and skip).

Processors/Criteria summary for Notification:
- ScheduleNotificationProcessor: builds recipient list, handles preference-based scheduling.
- SendEmailNotificationProcessor: executes sends, updates attempt_count, creates DeliveryAttempt entries.
- OnSendFailureCriterion / OnSendSuccessCriterion: evaluate results and transition state.
- RetrySendProcessor: implements retry/backoff per recipient.
- MaxRetriesReachedCriterion: transition to FAILED/partial.


### DailySummary workflow (updated)

1. Creation: Generated by GenerateDailySummaryProcessor after FetchJob persisted games for the request_date.
2. Validation: ValidateSummaryCriterion ensures date format and that games_summary contains the expected fields. If policy requires at least one game and none exist, mark summary as empty and let CreateNotificationProcessor decide whether to create a notification.
3. PersistSummaryProcessor: persist summary with generated_at timestamp and link to source_fetch_job_id.
4. Trigger Notification: CreateNotificationFromSummaryProcessor creates Notification (status=PENDING) and links it to summary. This will trigger the Notification workflow.
5. Regenerate: If games change later (ReconciliationProcessor on Game updates), RegenerateSummaryProcessor re-queries games and updates the DailySummary and optionally updates/recreates Notification per policy.


---

## 3. Required Criterion and Processor Class List (updated)

- ValidateSubscriberEmailCriterion
- SendConfirmationProcessor
- AutoActivateProcessor
- OnConfirmationReceivedProcessor
- UnsubscribeRequestProcessor

- DeduplicateGameCriterion
- MergeOrInsertGameProcessor
- MarkReadyIfFinalCriterion
- IndexGameProcessor
- ReconciliationProcessor

- ValidateFetchJobCriterion
- StartFetchProcessor
- FetchScoresProcessor (MUST call GET https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/{today}?key=test)
- ParseAndPersistGamesProcessor
- GenerateDailySummaryProcessor
- CreateNotificationProcessor
- FinalizeFetchJobProcessor
- FetchFailureCriterion

- ScheduleNotificationProcessor
- SendEmailNotificationProcessor
- OnSendSuccessCriterion
- OnSendFailureCriterion
- RetrySendProcessor
- MaxRetriesReachedCriterion

- ValidateSummaryCriterion
- PersistSummaryProcessor
- CreateNotificationFromSummaryProcessor
- RegenerateSummaryProcessor

Processor implementations MUST follow the idempotency, retry and async patterns described above.


---

## 4. API Endpoints (updated design rules)

API behaviour rules applied:
- POST endpoints that create entities MUST return: 201 Created and body { "technicalId": "<uuid>" }.
- GET by technicalId MUST exist for entities created via POST endpoints (at minimum Subscriber and FetchJob). Other GET endpoints are provided for convenience and read operations.
- All request/response bodies are JSON.

Endpoints:

- POST /subscribe
  - Purpose: create Subscriber entity and trigger Subscriber workflow.
  - Request body: { "email": "user@example.com", "preferences": { ... } }
  - Response: 201 { "technicalId": "subscriber-uuid-1234" }

- GET /subscribers
  - Purpose: list subscribers (minimal fields for UI)
  - Response: 200 [ { "technicalId":"...", "email":"user@example.com", "status":"active", "subscribed_at":"..." }, ... ]

- GET /subscriber/{technicalId}
  - Purpose: full Subscriber record by technicalId
  - Response: 200 { Subscriber }

- GET /games/all
  - Purpose: retrieve all stored games (supports pagination/filters)
  - Response: 200 [ { Game }, ... ]

- GET /games/{date}
  - Purpose: retrieve games for a specific date (YYYY-MM-DD)
  - Response: 200 [ { Game }, ... ]

- (Optional) GET /games/{technicalId}
  - Purpose: retrieve a single game by internal id for debugging/inspection.

- POST /fetch-jobs
  - Purpose: create FetchJob (orchestration entity) and trigger the FetchJob workflow.
  - Request Body: { "request_date": "2025-03-25", "scheduled_time": "18:00Z", "force": false }
    - request_date must be YYYY-MM-DD
    - force=true may override duplicate-cooldown policy (if implemented)
  - Response: 201 { "technicalId": "fetchjob-uuid-5678" }

- GET /fetch-jobs/{technicalId}
  - Purpose: retrieve FetchJob by technicalId
  - Response: 200 { FetchJob }

- GET /daily-summaries/{date}
  - Purpose: retrieve DailySummary for a date
  - Response: 200 { DailySummary }

- GET /notifications/{technicalId}
  - Purpose: inspect Notification status and audit data
  - Response: 200 { Notification }

Notes:
- The system runs a scheduled FetchJob automatically each day at the configured scheduled_time (e.g., 18:00Z). The Scheduler creates a FetchJob entity which follows the same validation and processing pipeline as POST-created ones.


---

## 5. Request / Response Examples

POST /subscribe
- Request: { "email": "user@example.com" }
- Response: { "technicalId": "subscriber-uuid-1234" }

POST /fetch-jobs
- Request: { "request_date": "2025-03-25", "scheduled_time": "18:00Z" }
- Response: { "technicalId": "fetchjob-uuid-5678" }

GET /games/2025-03-25
- Response: [ { "game_id": "game-1", "date":"2025-03-25", "home_team":"LAL", "away_team":"BKN", "home_score":120, "away_score":115, "status":"final", "venue":"Staples Center", "raw_payload": {...} }, ... ]

GET /fetch-jobs/{technicalId}
- Response: { "request_date": "2025-03-25", "status":"COMPLETED", "fetched_count": 15, "failed_count": 0, "started_at":"2025-03-25T18:00:05Z", "completed_at":"2025-03-25T18:00:30Z", "response_payload": {...} }


---

## 6. Scheduler Behaviour (clarified)

- A background scheduler creates a FetchJob daily at the configured time (example default: 18:00Z).
- The Scheduler sets request_date = current date in UTC (YYYY-MM-DD) and scheduled_time accordingly.
- Creation of this FetchJob triggers the full FetchJob workflow.
- FetchScoresProcessor MUST call: GET https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/{today}?key=test with {today} = request_date.
- The HTTP call is asynchronous; transient failures should be retried per policy.


---

## 7. Error Handling, Retries, Rate Limits

- External API calls: retry for transient 5xx up to 2 retries with exponential backoff. Honor 429 and Retry-After.
- Email/send provider: retry up to 3 attempts per recipient with exponential backoff (configurable base). Respect provider-specific guidance in responses.
- FetchJob: partial failures (some game parse errors) do not automatically mark the entire job FAILED; they increment failed_count. Fatal failures (e.g., schema mismatch) mark job FAILED.
- Subscribers: invalid email during POST -> 400 returned to client; do not create Subscriber record.
- Rate limiting: ValidateFetchJobCriterion enforces global or per-client limits. Scheduler-created FetchJobs are subject to the same limits but may use an internal privileged path.


---

## 8. Event-driven Notes and Triggers (summary)

- Persisting Subscriber triggers: ValidateSubscriberEmailCriterion, SendConfirmationProcessor (if required), AutoActivateProcessor.
- Persisting Game triggers: DeduplicateGameCriterion, MergeOrInsertGameProcessor, MarkReadyIfFinalCriterion and possibly ReconciliationProcessor which may cause DailySummary regeneration.
- Creating FetchJob triggers: FetchScoresProcessor -> ParseAndPersistGamesProcessor -> GenerateDailySummaryProcessor -> CreateNotificationProcessor -> FinalizeFetchJobProcessor.
- Creating DailySummary triggers: CreateNotificationFromSummaryProcessor.
- Creating Notification triggers: SendEmailNotificationProcessor with retries and DeliveryAttempt logs.

---

## 9. Operational Recommendations

- Store DeliveryAttempt or mail logs for each recipient to support idempotency and debugging.
- Expose metrics: fetch_job.count, fetch_job.duration, games.persisted, notification.sent_count, notification.failure_count.
- Implement feature flags for: requiring confirmation, duplicate FetchJob cooldown, notification re-send on summary regeneration.
- Maintain an audit trail for reconciliations: store diffs and source timestamps.

---

If you want any additional changes (extra endpoints, stricter idempotency, different retry policy, or additional fields to entities), tell me which part to update and I will revise the functional requirements accordingly.