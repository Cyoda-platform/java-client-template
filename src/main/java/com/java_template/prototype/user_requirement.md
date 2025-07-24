```markdown
# Application Requirements for NBA Game Scores Fetching, Storage, and Notification System

## Overview
Build a Java 21 Spring Boot application that fetches NBA game scores daily from an external API, persists the data locally, manages user subscriptions, and sends daily email notifications with the latest NBA scores.

---

## Functional Requirements

### 1. Fetching Data
- The system must fetch NBA game score data **daily at a scheduled time**.
- Fetch data from the external API:
  - **API Endpoint:**  
    `GET https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/{today}?key=test`
  - `{today}` parameter format: `YYYY-MM-DD` (e.g., `2025-03-25`).
- The request and response handling must be **asynchronous**.
- The fetching process is triggered **automatically by a scheduler** at a specified time (e.g., 6:00 PM UTC) without manual API calls.

### 2. Data Storage
- Store fetched NBA game data **locally** in a database.
- Persist relevant game details including but not limited to:
  - Date of the game
  - Team names
  - Scores
  - Other relevant game information returned by the API
- The database schema should support querying games by date and retrieving all stored games.

### 3. Subscription System
- Users can **subscribe** using their email addresses to receive daily NBA score notifications.
- Subscription is done via an exposed API endpoint.
- Upon subscription, the user’s email is added to the notification list.

### 4. Notification System
- Once the daily NBA scores are fetched and stored, **send email notifications** to all subscribed users.
- Email content includes a **summary of all games played on that day**.
- Notification sending is triggered automatically after data fetching and storage.

---

## API Endpoints

| Method | Endpoint           | Description                                    | Request Body                          | Response                        |
|--------|--------------------|------------------------------------------------|-------------------------------------|--------------------------------|
| POST   | `/subscribe`       | Subscribe user to daily email notifications    | `{ "email": "user@example.com" }`   | Confirmation of subscription    |
| GET    | `/subscribers`     | Retrieve all subscribed email addresses        | None                                | List of emails                  |
| GET    | `/games/all`       | Retrieve all NBA games stored                   | Query params for optional filtering and pagination | List of all games              |
| GET    | `/games/{date}`    | Retrieve NBA games for a specific date         | Path param: date in `YYYY-MM-DD`    | List of games for that date     |

---

## Scheduler Details
- The scheduler runs **daily at a configured time (e.g., 6:00 PM UTC)**.
- It triggers:
  1. Fetching NBA scores asynchronously from the external API.
  2. Storing the fetched scores in the local database.
  3. Sending notification emails with the daily summary to all subscribers.

---

## Technical Stack and Design Considerations

- **Language & Framework:** Java 21 with Spring Boot.
- **Asynchronous Processing:** Use Spring’s `@Async` or reactive programming support (e.g., WebClient, Project Reactor) for API calls and processing.
- **Scheduling:** Use Spring’s `@Scheduled` with cron expression to trigger daily fetch.
- **Data Persistence:** Use Spring Data JPA or equivalent with a relational database (e.g., PostgreSQL).
- **Email Notifications:** Use Spring Boot mail support (e.g., JavaMailSender) for sending emails.
- **API Client:** Implement REST client using Spring WebClient to call the external NBA API asynchronously.
- **Entity Design:**
  - Define `Game` entity to represent game data.
  - Define `Subscriber` entity for user subscriptions.
- **Error Handling:** Gracefully handle API failures, network issues, and email sending errors with retries or logging.
- **Security:** Basic request validation (e.g., validate email format on subscribe).

---

## Summary

This application is a **complex, event-driven system** where:
- The **entity** (Game, Subscriber) workflows are triggered by scheduled events.
- The **scheduler** acts as an event trigger invoking the workflow:
  - Fetch → Store → Notify.
- The system exposes REST APIs for subscription management and data retrieval.

The system aligns with Cyoda design values by structuring around entities and workflows triggered by events (the daily scheduler event).

---

If you want, I can proceed to provide the detailed architecture, entity models, and sample code for this Java Spring Boot application based on these requirements.
```