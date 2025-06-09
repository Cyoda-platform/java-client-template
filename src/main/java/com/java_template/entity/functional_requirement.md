# Confirmed Functional Requirements for Data Fetching Application

## API Endpoints

1. **Create Entity**
   - **Endpoint**: `POST /entities`
   - **Request**:
     ```json
     {
       "apiUrl": "https://external.api/data"
     }
     ```
   - **Response**:
     ```json
     {
       "entityId": "12345",
       "apiUrl": "https://external.api/data",
       "fetchedData": null,
       "fetchedAt": null
     }
     ```
   - **Description**: Creates a new entity with the provided API URL. Automatically triggers data fetching.

2. **Update Entity**
   - **Endpoint**: `POST /entities/{entityId}`
   - **Request**:
     ```json
     {
       "apiUrl": "https://new.api/data"
     }
     ```
   - **Response**:
     ```json
     {
       "entityId": "12345",
       "apiUrl": "https://new.api/data",
       "fetchedData": "...",
       "fetchedAt": "2023-10-01T12:00:00Z"
     }
     ```
   - **Description**: Updates the entity's API URL and automatically triggers data fetching.

3. **Manual Data Fetching**
   - **Endpoint**: `POST /entities/{entityId}/fetch`
   - **Request**: No body required
   - **Response**:
     ```json
     {
       "entityId": "12345",
       "fetchedData": "...",
       "fetchedAt": "2023-10-01T12:00:00Z"
     }
     ```
   - **Description**: Manually triggers data fetching for the specified entity.

4. **Get All Entities**
   - **Endpoint**: `GET /entities`
   - **Response**:
     ```json
     [
       {
         "entityId": "12345",
         "apiUrl": "https://external.api/data",
         "fetchedData": "...",
         "fetchedAt": "2023-10-01T12:00:00Z"
       },
       ...
     ]
     ```
   - **Description**: Retrieves all entities with their details.

5. **Delete Single Entity**
   - **Endpoint**: `DELETE /entities/{entityId}`
   - **Response**: `204 No Content`
   - **Description**: Deletes the specified entity.

6. **Delete All Entities**
   - **Endpoint**: `DELETE /entities`
   - **Response**: `204 No Content`
   - **Description**: Deletes all entities.

## User-App Interaction

```mermaid
sequenceDiagram
    participant User
    participant App
    participant ExternalAPI

    User->>App: POST /entities with apiUrl
    App->>ExternalAPI: Fetch data
    ExternalAPI-->>App: Return data
    App-->>User: Return entity details with fetched data

    User->>App: POST /entities/{entityId}
    App->>ExternalAPI: Fetch new data
    ExternalAPI-->>App: Return new data
    App-->>User: Return updated entity details

    User->>App: POST /entities/{entityId}/fetch
    App->>ExternalAPI: Fetch data
    ExternalAPI-->>App: Return data
    App-->>User: Return entity with updated fetched data

    User->>App: GET /entities
    App-->>User: Return all entities

    User->>App: DELETE /entities/{entityId}
    App-->>User: 204 No Content

    User->>App: DELETE /entities
    App-->>User: 204 No Content
```

```mermaid
journey
    title User Journey for Data Fetching App
    section Create and Fetch Data
      User: 1: Provides API URL
      App: 2: Creates entity and fetches data
    section Update Entity
      User: 1: Updates API URL
      App: 2: Fetches new data
    section Manual Fetch
      User: 1: Triggers fetch
      App: 2: Fetches data from API
    section Entity Management
      User: 1: Views all entities
      App: 2: Returns entity list
      User: 1: Deletes an entity
      App: 2: Confirms deletion
      User: 1: Deletes all entities
      App: 2: Confirms all deletions
```