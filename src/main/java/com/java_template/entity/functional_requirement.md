```markdown
# Activity Tracker Application - Functional Requirements

## API Endpoints

### 1. POST /api/activities/ingest  
**Description:** Trigger ingestion of user activity data from Fakerest API, process and analyze it.  
**Request:**  
```json
{
  "date": "YYYY-MM-DD"  // Optional, defaults to current date if omitted
}
```  
**Response:**  
```json
{
  "status": "success",
  "message": "Activity data ingested and analyzed for the date YYYY-MM-DD"
}
```

---

### 2. GET /api/reports/daily  
**Description:** Retrieve the generated daily report (summary of user activities).  
**Query Parameters:**  
- `date` (optional, defaults to current date)  
**Response:**  
```json
{
  "date": "YYYY-MM-DD",
  "summary": {
    "totalUsers": 100,
    "totalActivities": 450,
    "patterns": {
      "mostFrequentActivity": "Running",
      "averageActivityPerUser": 4.5
    },
    "anomalies": [
      "User 23 had zero activities",
      "Spike in 'Swimming' activity at 15:00"
    ]
  }
}
```

---

### 3. POST /api/reports/send  
**Description:** Send the daily report to admin email.  
**Request:**  
```json
{
  "date": "YYYY-MM-DD"  // Optional, defaults to current date if omitted
}
```  
**Response:**  
```json
{
  "status": "success",
  "message": "Daily report sent to admin email for the date YYYY-MM-DD"
}
```

---

## Business Logic Notes  
- The ingestion endpoint fetches user activity data from the Fakerest API, processes it to identify patterns (activity frequency, types), and stores results.  
- The reports endpoint retrieves stored summarized data.  
- Sending report endpoint triggers sending email with the report.  
- Daily ingestion is scheduled via internal scheduler triggering the POST `/api/activities/ingest` automatically.  

---

## User-App Interaction Sequence

```mermaid
sequenceDiagram
    participant Scheduler
    participant App
    participant FakerestAPI
    participant EmailService
    participant Admin

    Scheduler->>App: POST /api/activities/ingest (daily)
    App->>FakerestAPI: Fetch user activity data
    FakerestAPI-->>App: Return activity data
    App->>App: Analyze & store activity patterns
    App-->>Scheduler: Acknowledge completion

    Admin->>App: GET /api/reports/daily?date=YYYY-MM-DD
    App-->>Admin: Return daily activity report

    Admin->>App: POST /api/reports/send
    App->>EmailService: Send report email to admin
    EmailService-->>App: Email sent confirmation
    App-->>Admin: Confirm report sent
```

---

## User Journey Diagram

```mermaid
flowchart TD
    User[Admin/User]
    Scheduler[Scheduler]
    App[Activity Tracker App]
    FakerestAPI[Fakerest External API]
    EmailService[Email Service]

    Scheduler -->|Trigger daily ingest| App
    App -->|Fetch activities| FakerestAPI
    FakerestAPI --> App
    App -->|Analyze & Store data| App
    User -->|Request daily report| App
    App --> User
    User -->|Request report email| App
    App --> EmailService
    EmailService --> User
```
```