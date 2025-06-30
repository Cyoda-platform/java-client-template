# Functional Requirements for Weather Data Fetching Application

## API Endpoints

### 1. POST /weather/fetch
- **Description:** Fetch weather data from Open-Meteo API based on provided location and parameters.
- **Request Body (JSON):**
  ```json
  {
    "latitude": 52.52,
    "longitude": 13.405,
    "parameters": ["temperature_2m", "precipitation"],
    "start_date": "2024-06-01",
    "end_date": "2024-06-02"
  }
  ```
- **Response Body (JSON):**
  ```json
  {
    "requestId": "uuid-generated-id",
    "status": "success",
    "fetchedAt": "2024-06-01T12:00:00Z"
  }
  ```
- **Behavior:**  
  - Validates input data.
  - Calls Open-Meteo API with given parameters.
  - Stores or caches fetched data in the app.
  - Returns a request ID for later retrieval.

---

### 2. GET /weather/result/{requestId}
- **Description:** Retrieve previously fetched weather data by request ID.
- **Path Parameter:**  
  - `requestId` (string) - ID returned by the POST /weather/fetch endpoint.
- **Response Body (JSON):**
  ```json
  {
    "requestId": "uuid-generated-id",
    "latitude": 52.52,
    "longitude": 13.405,
    "parameters": ["temperature_2m", "precipitation"],
    "data": {
      "temperature_2m": [20.1, 21.3, 19.8],
      "precipitation": [0.0, 0.2, 0.0]
    },
    "fetchedAt": "2024-06-01T12:00:00Z"
  }
  ```
- **Behavior:**  
  - Returns stored weather data for the given request ID.
  - Returns HTTP 404 if no data found.

---

# Mermaid Sequence Diagram: User-App Interaction

```mermaid
sequenceDiagram
    participant User
    participant App
    participant OpenMeteoAPI

    User->>App: POST /weather/fetch {lat, lon, params, dates}
    App->>OpenMeteoAPI: Request weather data
    OpenMeteoAPI-->>App: Respond with weather data
    App-->>User: 200 OK {requestId, status}

    User->>App: GET /weather/result/{requestId}
    App-->>User: 200 OK {weather data}
```

---

# Mermaid Journey Diagram: Request Lifecycle

```mermaid
journey
    title Weather Data Fetch Request Lifecycle
    section User Interaction
      User submits fetch request: 5: User, App
      User retrieves results by requestId: 5: User, App
    section Backend Processing
      Validate input data: 4: App
      Call external API (Open-Meteo): 4: App, OpenMeteoAPI
      Store fetched data: 4: App
      Serve stored data on GET request: 4: App
```