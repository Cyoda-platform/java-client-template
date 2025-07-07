# Functional Requirements and API Design for Weekly Cat Fact Subscription

## API Endpoints

### 1. User Sign-Up  
**POST** `/api/subscribers`  
- **Description:** Register a new subscriber with their email (and optionally name).  
- **Request:**  
```json
{
  "email": "user@example.com",
  "name": "John Doe"   // optional
}
```  
- **Response:**  
```json
{
  "subscriberId": "uuid",
  "message": "Subscription successful"
}
```

### 2. Trigger Weekly Cat Fact Fetch and Email Send-Out  
**POST** `/api/facts/send-weekly`  
- **Description:** Trigger data ingestion from Cat Fact API and send the fact email to all subscribers.  
- **Request:**  
```json
{}
```  
- **Response:**  
```json
{
  "status": "success",
  "sentCount": 123
}
```

### 3. Retrieve Subscriber Count  
**GET** `/api/report/subscribers/count`  
- **Description:** Get the total number of subscribers.  
- **Response:**  
```json
{
  "totalSubscribers": 1234
}
```

### 4. Retrieve Interaction Report  
**GET** `/api/report/interactions`  
- **Description:** Get aggregated interaction data (e.g., email opens/clicks).  
- **Response:**  
```json
{
  "interactions": {
    "emailOpens": 1000,
    "linkClicks": 250
  }
}
```

---

## User-App Interaction Sequence Diagram

```mermaid
sequenceDiagram
    participant User
    participant App
    participant CatFactAPI
    participant EmailService

    User->>App: POST /api/subscribers (email, name)
    App-->>User: 200 OK (subscription confirmation)
    
    Note over App: Weekly scheduled trigger (or manual trigger)
    App->>CatFactAPI: POST /facts/random (fetch new cat fact)
    CatFactAPI-->>App: 200 OK (cat fact data)
    
    App->>App: Save cat fact, prepare emails
    App->>EmailService: Send email to all subscribers
    EmailService-->>App: Delivery status
    
    User->>App: GET /api/report/subscribers/count
    App-->>User: Total subscribers
    
    User->>App: GET /api/report/interactions
    App-->>User: Interaction metrics (opens, clicks)
```

---

This design follows RESTful rules:  
- All external data retrieval and business logic are done in POST endpoints.  
- GET endpoints are used for retrieving application state and reports only.