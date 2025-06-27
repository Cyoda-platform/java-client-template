# Functional Requirements and API Design

## API Endpoints

### 1. POST /fetch-scores  
**Description:**  
Triggers fetching NBA scores for a specific date from the external API, saves data locally, and sends notifications to subscribers.  
**Request Body:**  
```json
{
  "date": "YYYY-MM-DD"
}
```  
**Response:**  
```json
{
  "status": "success",
  "fetchedDate": "YYYY-MM-DD",
  "gamesCount": 12
}
```  
**Notes:**  
- This endpoint handles external API calls asynchronously.  
- It saves fetched data and triggers email notifications.

---

### 2. POST /subscribe  
**Description:**  
Allows users to subscribe by providing their email to receive daily notifications.  
**Request Body:**  
```json
{
  "email": "user@example.com"
}
```  
**Response:**  
```json
{
  "status": "subscribed",
  "email": "user@example.com"
}
```  
**Notes:**  
- Enforces unique subscription per email.

---

### 3. GET /subscribers  
**Description:**  
Retrieves a list of all subscribed email addresses.  
**Response:**  
```json
{
  "subscribers": [
    "user1@example.com",
    "user2@example.com"
  ]
}
```

---

### 4. GET /games/all  
**Description:**  
Retrieves all NBA games stored in the system, supports pagination.  
**Query Parameters:**  
- `page` (optional, default=0)  
- `size` (optional, default=20)  
**Response:**  
```json
{
  "page": 0,
  "size": 20,
  "totalPages": 5,
  "games": [
    {
      "date": "YYYY-MM-DD",
      "homeTeam": "Lakers",
      "awayTeam": "Warriors",
      "homeScore": 110,
      "awayScore": 102
    }
  ]
}
```

---

### 5. GET /games/{date}  
**Description:**  
Retrieves all NBA games for the specified date (`YYYY-MM-DD`).  
**Response:**  
```json
{
  "date": "YYYY-MM-DD",
  "games": [
    {
      "homeTeam": "Lakers",
      "awayTeam": "Warriors",
      "homeScore": 110,
      "awayScore": 102
    }
  ]
}
```

---

# Mermaid Sequence Diagram: User & System Interaction

```mermaid
sequenceDiagram
    participant User
    participant API
    participant ExternalAPI
    participant Database
    participant EmailService

    User->>API: POST /subscribe {email}
    API->>Database: Save subscriber email
    Database-->>API: Confirm saved
    API-->>User: Subscription confirmed

    API->>API: Scheduler triggers daily fetch at 18:00 UTC
    API->>ExternalAPI: GET scores for {today}
    ExternalAPI-->>API: Return score data
    API->>Database: Save game scores
    Database-->>API: Confirmation

    API->>Database: Get all subscribers
    Database-->>API: Subscriber list

    API->>EmailService: Send notification emails with scores
    EmailService-->>API: Email status

    User->>API: GET /games/{date}
    API->>Database: Query games by date
    Database-->>API: Return games
    API-->>User: Games data

    User->>API: GET /subscribers
    API->>Database: Query subscribers
    Database-->>API: Return emails
    API-->>User: Subscribers list
```

---

# Mermaid Journey Diagram: Daily NBA Scores Notification Flow

```mermaid
journey
    title Daily NBA Scores Notification Flow
    section Subscription
      User subscribes via email: 5: User, API
    section Scheduled Fetch
      Scheduler triggers fetch: 4: API
      API calls external NBA scores API: 4: API, ExternalAPI
      Scores saved to database: 4: API, Database
    section Notification
      API retrieves subscribers: 4: API, Database
      API sends notification emails: 4: API, EmailService
    section User Retrieval
      User requests game data: 5: User, API, Database
      User views subscriber list: 5: User, API, Database
```