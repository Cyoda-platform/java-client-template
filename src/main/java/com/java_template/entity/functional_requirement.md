```markdown
# Functional Requirements for User Details Retrieval Application

## API Endpoints

### 1. POST /users/fetch
- **Purpose:** Accept a user ID, call the external ReqRes API to retrieve user details, and store/cache the result internally.
- **Request:**
  ```json
  {
    "userId": 2
  }
  ```
- **Response (Success - 200 OK):**
  ```json
  {
    "message": "User data fetched successfully",
    "userId": 2
  }
  ```
- **Response (Error - 400 Bad Request):**
  ```json
  {
    "error": "Invalid user ID"
  }
  ```
- **Response (Error - 404 Not Found):**
  ```json
  {
    "error": "User not found"
  }
  ```

### 2. GET /users/{userId}
- **Purpose:** Retrieve the stored user details for the given user ID.
- **Response (Success - 200 OK):**
  ```json
  {
    "id": 2,
    "email": "janet.weaver@reqres.in",
    "first_name": "Janet",
    "last_name": "Weaver",
    "avatar": "https://reqres.in/img/faces/2-image.jpg"
  }
  ```
- **Response (Error - 404 Not Found):**
  ```json
  {
    "error": "User data not found. Please fetch first."
  }
  ```

---

## User-App Interaction Sequence Diagram

```mermaid
sequenceDiagram
    participant Client
    participant App
    participant ReqResAPI

    Client->>App: POST /users/fetch { userId }
    App->>ReqResAPI: GET /users/{userId}
    ReqResAPI-->>App: User data or 404
    alt User found
        App-->>Client: 200 OK {message}
    else User not found
        App-->>Client: 404 Not Found {error}
    end

    Client->>App: GET /users/{userId}
    alt User data cached
        App-->>Client: 200 OK {user data}
    else No cached data
        App-->>Client: 404 Not Found {error}
    end
```

---

## User Journey Diagram

```mermaid
journey
    title User fetching and retrieval journey
    section Fetch User Data
      User submits userId: 5: Client
      Application calls ReqRes API: 4: App
      API returns user data or error: 4: ReqResAPI
      Application responds with status: 5: App
    section Retrieve User Data
      User requests stored user info: 5: Client
      Application returns cached user data or error: 4: App
```
```