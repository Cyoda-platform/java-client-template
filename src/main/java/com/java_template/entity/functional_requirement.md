# Functional Requirements and API Design

## API Endpoints

### 1. Subscribe to Notifications  
**POST /subscribe**  
- **Description:** Adds a user email to the subscription list.  
- **Request Body:**  
```json
{
  "email": "user@example.com"
}
```  
- **Response:**  
```json
{
  "message": "Subscription successful"
}
```

### 2. Fetch and Store NBA Scores (Triggered internally by scheduler or manually)  
**POST /fetch-scores**  
- **Description:** Fetches NBA game scores from external API for the given date, stores them locally, and triggers notification emails.  
- **Request Body:**  
```json
{
  "date": "YYYY-MM-DD"
}
```  
- **Response:**  
```json
{
  "message": "Scores fetched and notifications sent",
  "date": "YYYY-MM-DD",
  "gamesCount": 12
}
```

### 3. Get All Subscribers  
**GET /subscribers**  
- **Description:** Returns a list of all subscribed emails.  
- **Response:**  
```json
{
  "subscribers": [
    "user1@example.com",
    "user2@example.com"
  ]
}
```

### 4. Get All Stored Games  
**GET /games/all**  
- **Description:** Retrieves all stored NBA games data. Supports optional pagination via query parameters `page` and `size`.  
- **Response:**  
```json
{
  "games": [
    {
      "gameId": 123,
      "date": "YYYY-MM-DD",
      "homeTeam": "Team A",
      "awayTeam": "Team B",
      "homeScore": 102,
      "awayScore": 99
    },
    ...
  ],
  "page": 1,
  "size": 20,
  "total": 100
}
```

### 5. Get Games by Date  
**GET /games/{date}**  
- **Description:** Retrieves all NBA games for a specific date.  
- **Response:**  
```json
{
  "date": "YYYY-MM-DD",
  "games": [
    {
      "gameId": 123,
      "homeTeam": "Team A",
      "awayTeam": "Team B",
      "homeScore": 102,
      "awayScore": 99
    },
    ...
  ]
}
```

---

## User-App Interaction Sequence (Mermaid Diagram)

```mermaid
sequenceDiagram
    participant User
    participant App
    participant NBA_API
    participant Email_Service
    participant Database

    User->>App: POST /subscribe {email}
    App->>Database: Store subscriber email
    Database-->>App: Confirmation
    App-->>User: Subscription successful

    Note over App: Scheduler triggers daily at 6 PM UTC
    App->>App: POST /fetch-scores {date}
    App->>NBA_API: GET scores for {date} (async)
    NBA_API-->>App: Scores data
    App->>Database: Store scores data
    Database-->>App: Confirmation
    App->>Database: Retrieve all subscribers
    Database-->>App: Subscribers list
    App->>Email_Service: Send summary email to subscribers
    Email_Service-->>App: Email sent confirmation
    App-->>User: Scores fetched and notifications sent

    User->>App: GET /subscribers
    App->>Database: Retrieve subscribers
    Database-->>App: Subscribers list
    App-->>User: Subscribers list

    User->>App: GET /games/all?page=1&size=20
    App->>Database: Retrieve paginated games
    Database-->>App: Games list
    App-->>User: Games list

    User->>App: GET /games/{date}
    App->>Database: Retrieve games by date
    Database-->>App: Games list
    App-->>User: Games list
```