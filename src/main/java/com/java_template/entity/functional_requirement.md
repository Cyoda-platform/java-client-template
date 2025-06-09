# Egg Alarm App - Final Functional Requirements

## API Endpoints

### 1. Create Alarm
- **Endpoint**: `/api/alarm`
- **Method**: `POST`
- **Description**: Sets an alarm for the selected type of egg.
- **Request Format**:
  ```json
  {
    "eggType": "soft-boiled" // options: soft-boiled, medium-boiled, hard-boiled
  }
  ```
- **Response Format**:
  ```json
  {
    "message": "Alarm set for soft-boiled eggs",
    "time": 300 // time in seconds
  }
  ```

### 2. Get Alarm Status
- **Endpoint**: `/api/alarm/status`
- **Method**: `GET`
- **Description**: Retrieves the current status of the alarm.
- **Response Format**:
  ```json
  {
    "status": "active", // options: active, completed
    "remainingTime": 120 // time in seconds
  }
  ```

### 3. Cancel Alarm
- **Endpoint**: `/api/alarm/cancel`
- **Method**: `POST`
- **Description**: Cancels the active egg alarm.
- **Response Format**:
  ```json
  {
    "message": "Alarm cancelled"
  }
  ```

## User-App Interaction

```mermaid
journey
  title User sets an Egg Alarm
  section Setting Alarm
    User selects egg type: 5: User
    User clicks "Set Alarm": 5: User
    System sets the alarm: 5: App
    System returns alarm time: 5: App
  section Alarm Countdown
    System counts down time: 5: App
    User checks alarm status: 4: User
    System returns remaining time: 4: App
  section Alarm Notification
    System triggers alarm notification: 5: App
    User acknowledges notification: 5: User
```

```mermaid
sequenceDiagram
  participant U as User
  participant A as Egg Alarm App
  U->>A: Selects egg type and sets alarm
  A->>A: Stores alarm details
  A-->>U: Returns confirmation and time
  A->>A: Counts down the time
  U->>A: Requests alarm status
  A-->>U: Returns remaining time
  A->>U: Sends alarm notification
  U->>A: Acknowledges notification
```