```markdown
# Functional Requirements for User Details Retrieval Application

## API Endpoints

### 1. POST /api/user/retrieve
- **Purpose:** Trigger retrieval of user details from the external ReqRes API using provided user ID.
- **Request Body:**
  ```json
  {
    "userId": 2
  }
  ```
- **Behavior:**
  - Validate the userId (must be positive integer).
  - Call ReqRes API `GET /users/{id}`.
  - Store or cache the retrieved user details internally for later retrieval.
  - Handle errors for invalid userId or user not found.
- **Response:**
  - Success (HTTP 200):
    ```json
    {
      "status": "success",
      "message": "User data retrieved and stored"
    }
    ```
  - Failure (HTTP 400 or 404):
    ```json
    {
      "status": "error",
      "message": "User not found"
    }
    ```

---

### 2. GET /api/user/{userId}
- **Purpose:** Retrieve the stored user details previously fetched.
- **Request Parameters:**
  - `userId` (path param): The user ID whose details are requested.
- **Response:**
  - Success (HTTP 200):
    ```json
    {
      "id": 2,
      "email": "janet.weaver@reqres.in",
      "first_name": "Janet",
      "last_name": "Weaver",
      "avatar": "https://reqres.in/img/faces/2-image.jpg"
    }
    ```
  - Failure (HTTP 404):
    ```json
    {
      "status": "error",
      "message": "User data not found. Please retrieve it first."
    }
    ```

---

## User-App Interaction Sequence Diagram

```mermaid
sequenceDiagram
    participant Client
    participant App
    participant ReqResAPI

    Client->>App: POST /api/user/retrieve { userId }
    App->>App: Validate userId
    App->>ReqResAPI: GET /users/{userId}
    ReqResAPI-->>App: User data or 404
    alt User found
        App->>App: Store user data
        App-->>Client: 200 { status: success, message }
    else User not found
        App-->>Client: 404 { status: error, message }
    end

    Client->>App: GET /api/user/{userId}
    App->>App: Lookup stored user data
    alt User data found
        App-->>Client: 200 { user details }
    else User data missing
        App-->>Client: 404 { status: error, message }
    end
```
```