```markdown
# Egg Alarm App - Final Functional Requirements and API Design

## Functional Requirements
- User selects egg type: soft-boiled, medium-boiled, or hard-boiled.
- Fixed timer durations per egg type:
  - Soft-boiled: 4 minutes
  - Medium-boiled: 7 minutes
  - Hard-boiled: 10 minutes
- User starts an alarm for the selected egg type.
- User can retrieve the current status of the alarm.
- Only one alarm can run at a time.
- Alarm triggers a sound notification when the timer ends.

---

## API Endpoints

### 1. Start Alarm (POST `/api/alarm/start`)
- **Description:** Starts an egg alarm based on the selected egg type.
- **Request:**
```json
{
  "eggType": "soft" | "medium" | "hard"
}
```
- **Response:**
```json
{
  "alarmId": "string",
  "eggType": "soft" | "medium" | "hard",
  "durationSeconds": 240,
  "startTime": "2024-06-01T12:00:00Z",
  "status": "running"
}
```

### 2. Get Alarm Status (GET `/api/alarm/{alarmId}/status`)
- **Description:** Retrieves the current status of the alarm.
- **Response:**
```json
{
  "alarmId": "string",
  "eggType": "soft" | "medium" | "hard",
  "durationSeconds": 240,
  "startTime": "2024-06-01T12:00:00Z",
  "elapsedSeconds": 120,
  "status": "running" | "completed"
}
```

---

## User-App Interaction Sequence Diagram

```mermaid
sequenceDiagram
    participant User
    participant App
    User->>App: POST /api/alarm/start {eggType}
    App->>App: Determine fixed duration by eggType
    App-->>User: Respond with alarmId and duration
    loop Alarm running
        User->>App: GET /api/alarm/{alarmId}/status
        App-->>User: Return current alarm status
    end
    App->>User: Notify alarm completion (sound)
```

---

## User Journey Diagram

```mermaid
journey
    title Egg Alarm User Journey
    section Select Egg Type
      User selects egg type: 5: User
      App confirms selection: 5: App
    section Start Alarm
      User starts alarm: 5: User
      App starts timer: 5: App
    section Monitor Alarm
      User checks status: 3: User
      App returns status: 3: App
    section Alarm Completion
      App notifies alarm done: 5: App
      User acknowledges alarm: 5: User
```
```