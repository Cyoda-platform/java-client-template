```markdown
# Functional Requirements Specification

## Fetching Data
- The system must fetch NBA game score data daily at a scheduled time.
- Data source:
  - External API Endpoint:
    ```
    GET https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/{today}?key=test
    ```
  - `{today}` parameter format: `YYYY-MM-DD` (e.g., `2025-03-25`).
- The request and response handling must be asynchronous to ensure non-blocking operations.

## Data Storage
- The system must persist the fetched NBA game scores locally.
- The database schema should include:
  - Game date
  - Team names (home and away)
  - Scores
  - Other relevant game details as provided by the API

## Subscription System
- Users can subscribe to daily NBA score notifications by providing their email.
- Upon subscription, the user's email is added to the notification list for future email dispatches.

## Notification System
- After fetching and storing daily NBA scores, the system will send an email notification to all subscribed users.
- The email notification will contain a summary of all NBA games played on that day.

## API Endpoints

### 1. POST `/subscribe`
- Purpose: Allow users to subscribe to daily NBA score notifications.
- Request Body:
  ```json
  {
    "email": "user@example.com"
  }
  ```
- Response: Confirmation of subscription or error message.

### 2. GET `/subscribers`
- Purpose: Retrieve a list of all subscribed email addresses.
- Response: List of emails.

### 3. GET `/games/all`
- Purpose: Retrieve all NBA game data stored in the system.
- Supports optional filtering and pagination as needed.
- Response: List of all stored game data.

### 4. GET `/games/{date}`
- Purpose: Retrieve all NBA games for a specific date.
- `{date}` path parameter format: `YYYY-MM-DD`.
- Response: List of NBA games for the requested date.

## Scheduler
- A background scheduler will run daily at a specified time (e.g., 6:00 PM UTC).
- Scheduler Responsibilities:
  - Trigger fetching of NBA score data from the external API.
  - Store the fetched data locally.
  - Send email notifications to all subscribers with the daily game summaries.
- This process runs autonomously and does not require manual API calls to trigger.

---
**Note:** All asynchronous operations, including data fetching and email notifications, should ensure reliability and error handling to maintain consistent service.
```