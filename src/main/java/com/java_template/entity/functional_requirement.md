# Functional Requirements and API Design

## API Endpoints

### 1. Subscribe User  
**POST /subscribe**  
- Description: Add a user email to the notification list.  
- Request Body:  
```json
{
  "email": "user@example.com"
}
```  
- Response:  
```json
{
  "message": "Subscription successful",
  "email": "user@example.com"
}
```

### 2. Fetch and Store NBA Scores  
**POST /games/fetch**  
- Description: Trigger fetching NBA scores for the current day from the external API, store them locally, and send notifications to subscribers.  
- Request Body:  
```json
{
  "date": "YYYY-MM-DD"  // Optional, defaults to current date if not provided
}
```  
- Response:  
```json
{
  "message": "Scores fetched, stored, and notifications sent",
  "date": "YYYY-MM-DD",
  "gamesCount": 15
}
```

### 3. Get Subscribers  
**GET /subscribers**  
- Description: Retrieve all subscribed email addresses.  
- Response:  
```json
[
  "email1@example.com",
  "email2@example.com"
]
```

### 4. Get All Games  
**GET /games/all**  
- Description: Retrieve all stored NBA games (with optional pagination and filtering).  
- Query Parameters (optional):  
  - `page` (int)  
  - `size` (int)  
- Response:  
```json
[
  {
    "date": "YYYY-MM-DD",
    "homeTeam": "Team A",
    "awayTeam": "Team B",
    "homeScore": 100,
    "awayScore": 98,
    "otherDetails": "..."
  },
  ...
]
```

### 5. Get Games by Date  
**GET /games/{date}**  
- Description: Retrieve all NBA games for a specified date (`YYYY-MM-DD`).  
- Response:  
```json
[
  {
    "date": "YYYY-MM-DD",
    "homeTeam": "Team A",
    "awayTeam": "Team B",
    "homeScore": 100,
    "awayScore": 98,
    "otherDetails": "..."
  },
  ...
]
```

---

# Mermaid Diagram: User and App Interaction Sequence

```mermaid
sequenceDiagram
    participant User
    participant App
    participant ExternalAPI
    participant EmailService

    User->>App: POST /subscribe { email }
    App-->>User: Subscription confirmation

    Note over App: Daily scheduled trigger or manual trigger

    App->>App: POST /games/fetch { date? }
    App->>ExternalAPI: GET NBA scores for date
    ExternalAPI-->>App: NBA scores data
    App->>App: Store games data locally
    App->>EmailService: Send daily summary emails to subscribers
    EmailService-->>App: Email send status
    App-->>User: Fetch and notify success

    User->>App: GET /subscribers
    App-->>User: List of subscriber emails

    User->>App: GET /games/all?page=&size=
    App-->>User: List of all games

    User->>App: GET /games/{date}
    App-->>User: List of games for the date
```