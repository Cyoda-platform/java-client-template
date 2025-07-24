```markdown
# NBA Daily Scores Notification Application - Requirements Specification

## Functional Requirements

### 1. Fetching Data
- The system must fetch NBA game score data **daily** at a scheduled time.
- Data source:
  - External API Endpoint:  
    `GET https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/{today}?key=test`
  - `{today}` parameter format: `YYYY-MM-DD` (e.g., `2025-03-25`).
- The request and response handling must be **asynchronous** to avoid blocking operations.

### 2. Data Storage
- The system must store the fetched NBA game scores **locally** in a database.
- Stored data must include:
  - Game date
  - Team names (home and away)
  - Scores of each team
  - Other relevant game details as provided by the API response (e.g., game status, venue, etc.).

### 3. Subscription System
- Users can **subscribe** by providing their email address to receive daily notifications.
- Subscription API:
  - `POST /subscribe`
    - Request Body: `{ "email": "user@example.com" }`
  - Upon subscription, the email is added to the notification list.
- Ability to retrieve the list of subscribed users:
  - `GET /subscribers`
    - Returns all subscribed email addresses.

### 4. Notification System
- After daily data fetching and storage, the system must **send an email notification** to all subscribers.
- The email content includes a **summary of all NBA games played on that day**.
- Email sending should be reliable and scalable to handle multiple subscribers.

### 5. API Endpoints
- **POST /subscribe**  
  - Purpose: Add a user email to the subscription list.
  - Request Body:  
    ```json
    {
      "email": "user@example.com"
    }
    ```
- **GET /subscribers**  
  - Purpose: Retrieve all subscribed email addresses.
- **GET /games/all**  
  - Purpose: Retrieve all stored NBA games data.
  - Supports optional filtering and pagination.
- **GET /games/{date}**  
  - Purpose: Retrieve all NBA games for a specific date.
  - Path Parameter: `date` in format `YYYY-MM-DD`.

### 6. Scheduler
- A **background scheduler** will trigger the entire fetch-store-notify process automatically every day at a fixed time (e.g., 6:00 PM UTC).
- Scheduler responsibilities:
  - Fetch NBA game scores from the external API asynchronously.
  - Store the fetched data locally.
  - Send email notifications to all subscribers with the daily scores.

## Technical Stack and Constraints

- **Programming Language:** Java 21 with Spring Boot framework.
- **API Calls:** Asynchronous HTTP requests to the external NBA scores API.
- **Data Storage:** Local persistent database (e.g., relational DB like PostgreSQL or embedded DB).
- **Email Notifications:** Integrate with an SMTP server or email service provider to send emails.
- **Scheduler:** Use a reliable Java scheduling library (e.g., Spring Scheduler) to trigger daily jobs.
- **API Design:** RESTful endpoints following the specifications above.
- **Data Format:** JSON for API requests and responses.

---

This detailed specification preserves all business logic, API details, and technical requirements as requested.
```