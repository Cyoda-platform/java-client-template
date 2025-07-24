```markdown
# NBA Daily Scores Notification Application - Requirements Specification

## Functional Requirements

### 1. Fetching NBA Game Scores Daily
- The system must fetch NBA game score data every day at a scheduled time.
- Data Source:
  - External API endpoint:  
    `GET https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/{today}?key=test`
  - The `{today}` path parameter uses the date format `YYYY-MM-DD` (e.g., `2025-03-25`).
- Fetching must be performed asynchronously to avoid blocking.
- The scheduled fetch time is daily at a specified time (e.g., 6:00 PM UTC).

### 2. Data Storage
- The system must persistently store the fetched NBA game scores locally.
- Stored data should include:
  - Date of the game
  - Team names (home and away)
  - Scores
  - Other relevant game details as provided by the API response
- The storage should support querying by date and retrieval of all stored games.

### 3. Subscription System
- Users can subscribe to daily NBA score notifications via email.
- API to add subscribers:
  - `POST /subscribe`
  - Request body: `{ "email": "user@example.com" }`
- On subscription, the user's email is added to the notification list.
- API to retrieve subscribers:
  - `GET /subscribers`
  - Returns a list of all subscribed email addresses.

### 4. Notification System
- After daily fetching and storing of NBA scores, the system sends an email summary to all subscribers.
- Email notifications include summaries of all games played on that day.
- The system must handle email sending reliably and asynchronously.

### 5. API Endpoints
- `POST /subscribe`  
  Allows users to subscribe with their email for daily notifications.  
  **Request:** `{ "email": "user@example.com" }`
- `GET /subscribers`  
  Returns a list of all subscribed emails.
- `GET /games/all`  
  Retrieves all stored NBA games data. Supports optional filtering and pagination.
- `GET /games/{date}`  
  Retrieves all NBA games for a specific date.  
  - `date` path parameter format: `YYYY-MM-DD`

### 6. Scheduler
- A background scheduler triggers daily at a configured time (e.g., 6:00 PM UTC).
- Scheduler workflow:
  1. Fetch NBA scores from the external API asynchronously.
  2. Store the fetched game data locally.
  3. Send email notifications containing game summaries to all subscribed emails.

## Technical Details

- **Programming Language:** Java 21 Spring Boot (chosen tool)
- **External API:**  
  - URL template: `https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/{today}?key=test`  
  - HTTP method: GET  
  - Authentication: API key in query parameter (`key=test`)
- **Asynchronous Processing:**  
  - Use asynchronous HTTP client for external API calls.  
  - Asynchronous email sending to avoid blocking the scheduler.
- **Data Persistence:**  
  - Use a relational database (e.g., PostgreSQL or H2 for dev) to store games and subscribers.  
  - Entities:  
    - `Game` (date, home team, away team, scores, other details)  
    - `Subscriber` (email)
- **Email Service:**  
  - Use JavaMailSender or similar for SMTP email sending.  
  - Emails include daily summaries with all games from that day.
- **Scheduler:**  
  - Use Spring Boot's `@Scheduled` annotation or Quartz Scheduler to trigger daily fetch/store/send workflow.

---

This design preserves all business logic and technical details as requested, ready for implementation using Java 21 Spring Boot.
```