```markdown
# Functional Requirements and API Specification for Weekly Cat Fact Subscription

## API Endpoints

### 1. User Subscription

- **Endpoint:** `POST /api/subscribers`
- **Description:** User signs up for weekly cat fact emails.
- **Request:**
  ```json
  {
    "email": "user@example.com",
    "name": "Optional User Name"
  }
  ```
- **Response:**
  ```json
  {
    "subscriberId": "uuid",
    "message": "Subscription successful"
  }
  ```

### 2. Retrieve Subscriber List (Reporting)

- **Endpoint:** `GET /api/subscribers`
- **Description:** Retrieve list of all subscribers (for admin/reporting).
- **Response:**
  ```json
  [
    {
      "subscriberId": "uuid",
      "email": "user@example.com",
      "name": "User Name",
      "subscribedAt": "2024-05-01T12:00:00Z"
    }
  ]
  ```

### 3. Trigger Weekly Cat Fact Retrieval and Email Send-Out

- **Endpoint:** `POST /api/facts/send-weekly`
- **Description:** Internal endpoint to fetch a new cat fact from external API and send it to all subscribers.
- **Request:** No body required.
- **Response:**
  ```json
  {
    "factId": "uuid",
    "factText": "Cats have five toes on their front paws.",
    "sentToSubscribers": 100
  }
  ```

### 4. Retrieve Reporting Metrics

- **Endpoint:** `GET /api/reporting/metrics`
- **Description:** Retrieve reporting data such as subscriber count and interaction stats.
- **Response:**
  ```json
  {
    "totalSubscribers": 100,
    "emailsSent": 52,
    "averageOpenRate": 0.42,
    "averageClickRate": 0.15
  }
  ```

---

## Mermaid Sequence Diagram: User Subscription and Weekly Email Flow

```mermaid
sequenceDiagram
    participant User
    participant Backend
    participant CatFactAPI
    participant EmailService

    User->>Backend: POST /api/subscribers {email, name}
    Backend-->>User: 200 OK (Subscription successful)

    Note over Backend,CatFactAPI: Scheduled weekly task triggers:
    Backend->>CatFactAPI: POST /facts/random
    CatFactAPI-->>Backend: Cat fact JSON

    Backend->>EmailService: Send email with cat fact to subscribers
    EmailService-->>Backend: Email send confirmation

    Backend->>Backend: Update reporting metrics
```

---

## Mermaid Journey Diagram: Weekly Cat Fact Subscription User Journey

```mermaid
journey
    title Weekly Cat Fact Subscription Journey
    section Subscription
      User visits signup page: 5: User
      User submits email: 5: User
      Backend confirms subscription: 5: Backend
    section Weekly Fact Delivery
      Scheduled job runs weekly: 5: Backend
      Fetch cat fact from API: 5: Backend
      Send emails to subscribers: 5: EmailService
      Track interactions (opens/clicks): 3: Backend
    section Reporting
      Admin requests subscriber list or metrics: 4: Admin
      Backend returns reporting data: 4: Backend
```
```