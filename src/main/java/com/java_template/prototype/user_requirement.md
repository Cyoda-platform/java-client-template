# Functional Requirements:

## Fetching Data:

- The system must fetch NBA game score data daily at a scheduled time.
- Data will be fetched from the external API with the following configuration:
  - API Endpoint: GET https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/{today}?key=test
  - The today parameter will follow the format YYYY-MM-DD (e.g., 2025-03-25).
- The system should handle the request and response asynchronously.

## Data Storage:

- The system must save the fetched data locally to persist the scores of the games.
- The database should include game details such as date, team names, score, and other relevant information.

## Subscription System:

- Users must be able to subscribe with their email to receive daily notifications with the latest NBA scores.
- Upon subscription, the user will be added to the system’s notification list.

## Notification System:

- After fetching and storing the scores, the system should send an email notification to all subscribers with the daily NBA scores.
- Notifications will include a summary of all games played on that specific day.

## API Endpoints: The system will expose the following APIs:

- POST /subscribe
  - Allows users to subscribe to daily notifications via email.
  - Request Body: { "email": "user@example.com" }
- GET /subscribers
  - Retrieves a list of all subscribed email addresses.
- GET /games/all
  - Retrieves all NBA games data stored in the system (optional filtering and pagination as needed).
- GET /games/{date}
  - Retrieves all NBA games for a specific date.
  - date parameter should be in the format YYYY-MM-DD.

## Scheduler:

- A background scheduler will fetch the NBA score data every day at a specified time (e.g., 6:00 PM UTC) without requiring an API call.
- The scheduler will trigger a process to fetch and store the data from the API, and after that, it will send email notifications to subscribers.