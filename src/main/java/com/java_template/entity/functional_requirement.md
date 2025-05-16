```markdown
# Functional Requirements and API Specification

## API Endpoints

### 1. Create Message (POST `/messages`)
- **Purpose**: Accept a user-provided text message, save it with a server-generated timestamp.
- **Request Body** (JSON):
  ```json
  {
    "text": "string"
  }
  ```
- **Response** (JSON):
  ```json
  {
    "id": "string",
    "text": "string",
    "timestamp": "ISO-8601 string"
  }
  ```

### 2. Get Message by ID (GET `/messages/{id}`)
- **Purpose**: Retrieve a stored message by its unique ID.
- **Response** (JSON):
  ```json
  {
    "id": "string",
    "text": "string",
    "timestamp": "ISO-8601 string"
  }
  ```

### 3. Get All Messages (GET `/messages`)
- **Purpose**: Retrieve all stored messages.
- **Response** (JSON array):
  ```json
  [
    {
      "id": "string",
      "text": "string",
      "timestamp": "ISO-8601 string"
    },
    ...
  ]
  ```

---

## Business Logic Notes
- All external data retrieval or calculations (if any in future) should be handled inside POST endpoints.
- GET endpoints only return already stored results.
- Server generates timestamps when saving messages.

---

## User-App Interaction (Sequence Diagram)

```mermaid
sequenceDiagram
    participant User
    participant App

    User->>App: POST /messages { text }
    App->>App: Generate ID and Timestamp
    App->>App: Save message with ID and Timestamp
    App-->>User: 201 Created { id, text, timestamp }

    User->>App: GET /messages/{id}
    App->>App: Retrieve message by ID
    App-->>User: 200 OK { id, text, timestamp }

    User->>App: GET /messages
    App->>App: Retrieve all messages
    App-->>User: 200 OK [ { id, text, timestamp }, ... ]
```

---

## User-App Interaction (Journey Diagram)

```mermaid
journey
    title User Message Workflow
    section Submit Message
      User: 5: Submits text message
      App: 5: Processes and stores message
    section Retrieve Message
      User: 4: Requests message by ID or all messages
      App: 4: Returns requested message(s)
```
```