# Final Functional Requirements for Data Fetching Application

## API Endpoints

### 1. Create Entity
- **Endpoint**: `POST /entities`
- **Description**: Creates a new entity and triggers data fetching from the provided API URL.
- **Request Format**:
  ```json
  {
    "api_url": "string"
  }
  ```
- **Response Format**:
  ```json
  {
    "id": "string",
    "api_url": "string",
    "fetched_data": "object",
    "fetched_at": "string"
  }
  ```

### 2. Update Entity
- **Endpoint**: `POST /entities/{id}`
- **Description**: Updates an existing entity's API URL and triggers data fetching.
- **Request Format**:
  ```json
  {
    "api_url": "string"
  }
  ```
- **Response Format**:
  ```json
  {
    "id": "string",
    "api_url": "string",
    "fetched_data": "object",
    "fetched_at": "string"
  }
  ```

### 3. Manual Data Fetching
- **Endpoint**: `POST /entities/{id}/fetch`
- **Description**: Manually triggers data fetching for a specific entity.
- **Response Format**:
  ```json
  {
    "id": "string",
    "fetched_data": "object",
    "fetched_at": "string"
  }
  ```

### 4. Get All Entities
- **Endpoint**: `GET /entities`
- **Description**: Retrieves all entities.
- **Response Format**:
  ```json
  [
    {
      "id": "string",
      "api_url": "string",
      "fetched_data": "object",
      "fetched_at": "string"
    }
  ]
  ```

### 5. Delete Single Entity
- **Endpoint**: `DELETE /entities/{id}`
- **Description**: Deletes a specific entity.
- **Response Format**:
  ```json
  {
    "message": "Entity deleted successfully."
  }
  ```

### 6. Delete All Entities
- **Endpoint**: `DELETE /entities`
- **Description**: Deletes all entities.
- **Response Format**:
  ```json
  {
    "message": "All entities deleted successfully."
  }
  ```

## Visual Representation of User-App Interaction

### Entity Creation and Data Fetching Sequence

```mermaid
sequenceDiagram
    participant User
    participant App
    participant ExternalAPI

    User->>App: POST /entities (api_url)
    App->>ExternalAPI: Fetch data from API
    ExternalAPI-->>App: Return data
    App-->>User: Return entity with fetched_data and fetched_at
```

### Manual Data Fetching Sequence

```mermaid
sequenceDiagram
    participant User
    participant App
    participant ExternalAPI

    User->>App: POST /entities/{id}/fetch
    App->>ExternalAPI: Fetch data from API
    ExternalAPI-->>App: Return data
    App-->>User: Return entity with updated fetched_data and fetched_at
```

### User Journey

```mermaid
journey
    title User Journey for Data Fetching Application
    section Entity Creation and Data Fetching
      User: Provide API URL: 5: User inputs API URL to create an entity
      App: Store and Fetch Data: 5: App stores URL and fetches data immediately
    section Entity Management
      User: View Entities: 4: User retrieves list of all entities
      User: Update API URL: 4: User updates the API URL of an entity
      App: Automatic Data Fetch: 4: App automatically fetches data from new URL
      User: Trigger Manual Fetch: 3: User manually triggers data fetch
      App: Execute Manual Fetch: 3: App fetches data from stored API URL
      User: Delete Single Entity: 3: User deletes a specific entity
      User: Delete All Entities: 2: User deletes all entities
```

These functional requirements and visual diagrams should provide a comprehensive understanding of the application's functionality and user interactions. If everything looks good, we can proceed further!