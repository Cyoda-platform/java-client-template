### Functional Requirements - NBA Daily Scores & Notifications

This document contains the finalized functional requirements, entity definitions, workflows, and API specifications for the NBA daily scores fetching and notification system.

## 1. Entity Definitions

Game:
- game_id: String (source game identifier from external API)
- date: String (YYYY-MM-DD, date of the game)
- home_team: String (home team name)
- away_team: String (away team name)
- home_score: Integer (home team score)
- away_score: Integer (away team score)
- status: String (game status, e.g., Final, Postponed)
- venue: String (location/arena)
- raw_payload: JSON (full JSON object returned by external API)
- fetched_at: String (ISO timestamp when the game was fetched)
- persisted_at: String (ISO timestamp when saved locally)

Team:
- team_id: String (unique team identifier, optional mapping to external API id)
- name: String (team full name)
- abbreviation: String (team short name, e.g., LAL)
- city: String (team city)
- metadata: JSON (optional additional team info)

Subscriber:
- email: String (subscriber email address)
- subscribed_at: String (ISO timestamp when subscription created)
- status: String (Subscribed, Unsubscribed, PendingConfirmation)
- preferences: JSON (notification preferences; e.g., daily_time_utc)
- technical_id: String (datastore-imitation id returned by POST - not a stored field per platform rules)

Notification:
- notification_id: String (unique id for the notification event)
- date: String (YYYY-MM-DD, the games date summarized)
- summary: String (text summary of all games for the date)
- recipients_count: Integer (number of subscribers notified)
- sent_at: String (ISO timestamp when notification sent)
- status: String (PENDING, PREPARING, SENT, FAILED)
- payload: JSON (detailed content sent, e.g., list of games)

ScoreFetchJob (Orchestration entity):
- job_name: String (human-readable job name, e.g., DailyScoreFetch_2025-03-25)
- scheduled_for: String (ISO timestamp when scheduled to run; scheduler will create job)
- target_date: String (YYYY-MM-DD; value for the {today} path param)
- source_endpoint: String (external API endpoint used; default GET https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/{today}?key=test)
- status: String (CREATED, VALIDATING, FETCHING, STORING, NOTIFYING, COMPLETED, FAILED)
- started_at: String (ISO timestamp)
- completed_at: String (ISO timestamp)
- result_summary: String (short text summary of job result)
- raw_response: JSON (full response from external API persisted for auditing)

Scheduler:
- schedule_id: String (unique scheduler identifier)
- cron_expression: String (human readable or cron expression; e.g., daily at 18:00 UTC)
- next_run: String (ISO timestamp for next scheduled run)
- last_run_job_technical_id: String (technicalId of last created ScoreFetchJob)
- status: String (ACTIVE, PAUSED)
- created_at: String
- updated_at: String

## 2. Workflows

Game workflow:
1. Initial State: Game created in memory when fetched from external API
2. Deduplication: Check if game already exists for same game_id and date
3. Persist: Save game record locally
4. Enrich: Populate team references and computed fields if needed
5. Finalize: Mark as STORED and available for read APIs

Processors and criteria:
- CheckGameDedupCriterion
- GameDedupProcessor
- PersistGameProcessor
- GameEnrichmentProcessor
- MarkStoredProcessor

Subscriber workflow:
1. Initial State: Subscriber POSTed via /subscribe -> CREATED
2. Validate: Validate email format and dedupe
3. Persist: Add subscriber to subscription list
4. Confirm: Optionally send confirmation email (automatic)
5. Active: Subscriber status SUBSCRIBED
6. Manual: Unsubscribe transitions to UNSUBSCRIBED (manual)

Processors and criteria:
- EmailValidationCriterion
- ValidateEmailProcessor
- SubscriberDedupCriterion
- AddSubscriberProcessor
- SendWelcomeEmailProcessor
- MarkSubscribedProcessor

Notification workflow:
1. Initial State: Notification created when ScoreFetchJob prepares summary
2. Prepare: Build summary payload for the target date (aggregate games)
3. Queue: Queue email sends to subscribers
4. Send: Send emails to all recipients
5. Complete: Mark notification as SENT or FAILED

Processors and criteria:
- BuildNotificationSummaryProcessor
- QueueNotificationProcessor
- NotificationDispatchProcessor
- OnAllEmailsSentCriterion
- MarkSentProcessor
- MarkFailedProcessor

ScoreFetchJob workflow:
1. Initial State: Job created (either by Scheduler automatically or manual POST)
2. Validation: Check job config and target_date format
3. Fetching: Call external API asynchronously
4. Storing: For each game in response, create Game entity (each Game persistence triggers Game workflow)
5. Notification: After storing all games, create Notification entity to prepare/send emails
6. Completion: Mark job as COMPLETED or FAILED

Processors and criteria:
- ValidateFetchJobCriterion
- ValidateFetchJobProcessor
- FetchScoresProcessor
- ParseAndPersistGamesProcessor
- CreateNotificationProcessor
- MarkJobCompletedProcessor
- ErrorHandlingProcessor

Scheduler workflow:
1. Initial State: Scheduler ACTIVE
2. On schedule trigger (automatic): Create ScoreFetchJob for today's date at configured time
3. Record: Persist job metadata and let ScoreFetchJob workflow run
4. Repeat: Update next_run timestamp

Processors and criteria:
- CreateScoreFetchJobProcessor
- UpdateNextRunProcessor
- CronTriggerProcessor

Event-driven behavior:
- Each entity add operation is an EVENT that triggers automated processing.
- Persisting a ScoreFetchJob triggers ScoreFetchJob workflow which will call FetchScoresProcessor.
- Persisting a Game record is performed by ParseAndPersistGamesProcessor emitting a GameCreated event; once the Game is persisted, Cyoda starts the Game workflow automatically.
- Creating a Notification entity triggers Notification workflow to prepare and send emails to subscribers.

## 3. API Endpoints

Rules:
- POST endpoints create entities and trigger events. A POST response must return only entity technicalId.
- GET endpoints return stored results.
- GET by technicalId exists for all entities created via POST.

1) POST /subscribe
- Purpose: Create Subscriber (triggers Subscriber workflow)
- Request JSON: { "email": "user@example.com" }
- Response JSON: { "technicalId": "sub_XXXXXXXXXXXX" }

2) GET /subscribers
- Purpose: Retrieve all subscribers
- Response JSON example: [ { "technicalId": "sub_XXXXXXXXXXXX", "email": "user@example.com", "status": "Subscribed", "subscribed_at": "2025-03-25T18:00:00Z", "preferences": {} } ]

3) GET /subscribers/{technicalId}
- Purpose: Retrieve subscriber by technicalId
- Response JSON example: { "technicalId": "sub_XXXXXXXXXXXX", "email": "user@example.com", "status": "Subscribed", "subscribed_at": "2025-03-25T18:00:00Z", "preferences": {} }

4) POST /score-fetch-job
- Purpose: Create ScoreFetchJob (orchestration entity) that triggers fetching/storing/notification
- Request JSON example: { "job_name": "DailyScoreFetch_2025-03-25", "scheduled_for": "2025-03-25T18:00:00Z", "target_date": "2025-03-25", "source_endpoint": "https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/{today}?key=test" }
- Response JSON: { "technicalId": "job_XXXXXXXXXXXX" }

5) GET /score-fetch-job/{technicalId}
- Purpose: Retrieve ScoreFetchJob by technicalId
- Response JSON example: { "technicalId": "job_XXXXXXXXXXXX", "job_name": "DailyScoreFetch_2025-03-25", "status": "COMPLETED", "started_at": "2025-03-25T18:00:00Z", "completed_at": "2025-03-25T18:02:00Z", "result_summary": "Fetched 12 games, 12 persisted, notifications queued" }

6) GET /games/all
- Purpose: Retrieve all games stored in the system (supports optional filtering/pagination)
- Response JSON example: [ { "technicalId": "game_XXXXXXXXXXXX", "game_id": "12345", "date": "2025-03-25", "home_team": "Lakers", "away_team": "Warriors", "home_score": 102, "away_score": 99, "status": "Final" } ]

7) GET /games/{date}
- Purpose: Retrieve all NBA games for a specific date (date format YYYY-MM-DD)
- Example request: GET /games/2025-03-25
- Response JSON example: [ { "technicalId": "game_XXXXXXXXXXXX", "game_id": "12345", "date": "2025-03-25", "home_team": "Lakers", "away_team": "Warriors", "home_score": 102, "away_score": 99, "status": "Final" } ]

API Notes:
- External API used to fetch data: GET https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/{today}?key=test
- The today parameter will follow the format YYYY-MM-DD (e.g., 2025-03-25).
- The system should handle the request and response asynchronously (ScoreFetchJob.FetchScoresProcessor uses httpClient.getAsync).
- The scheduler will create ScoreFetchJobs automatically (e.g., daily at 6:00 PM UTC). Scheduler creates job which triggers the ScoreFetchJob workflow and ultimately Notification workflow.

## 4. Business Rules
- The system must fetch NBA game score data daily at a scheduled time.
- The system must save the fetched data locally to persist the scores of the games.
- The database should include game details such as date, team names, score, and other relevant information.
- Users must be able to subscribe with their email to receive daily notifications with the latest NBA scores.
- Upon subscription, the user will be added to the system’s notification list.
- After fetching and storing the scores, the system should send an email notification to all subscribers with the daily NBA scores.
- Notifications will include a summary of all games played on that specific day.
- A background scheduler will fetch the NBA score data every day at a specified time (e.g., 6:00 PM UTC) without requiring an API call.
- The scheduler will trigger a process to fetch and store the data from the API, and after that, it will send email notifications to subscribers.


Please review and tell me if you want any further adjustments. When you are satisfied, I will finalize by creating any remaining workflow resources and finish the discussion.