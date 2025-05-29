```markdown
# Functional Requirements for Filtered Pet Search Application

## API Endpoints

### 1. POST /api/pets/search  
**Description:**  
Fetch pet details from external API based on search parameters, perform transformation, and store results for retrieval.

**Request Body:**  
```json
{
  "species": "string",      // optional, e.g. "dog"
  "status": "string",       // optional, e.g. "available"
  "categoryId": "integer"   // optional
}
```

**Response:**  
```json
{
  "searchId": "string",     // unique ID to retrieve results
  "message": "Search initiated"
}
```

---

### 2. GET /api/pets/results/{searchId}  
**Description:**  
Retrieve the transformed pet list for a given search ID.

**Response:**  
```json
{
  "pets": [
    {
      "Name": "string",
      "Species": "string",
      "CategoryId": "integer",
      "Availability": "string",
      "OtherAttributes": "..."
    }
  ],
  "notification": "string"  // e.g. "No pets found" or empty string
}
```

---

## Business Logic

- The POST /search endpoint triggers:
  - Validation of input parameters.
  - Calling the external PetStore API with given filters.
  - Transformation of received data (e.g., renaming "petName" to "Name", adding availability status).
  - Persisting transformed results with a generated `searchId`.
- The GET /results endpoint fetches the stored transformed pet list by `searchId`.
- If no pets found, a notification message is included in the response.

---

## User-App Interaction Sequence

```mermaid
sequenceDiagram
    participant User
    participant AppBackend
    participant ExternalPetAPI

    User->>AppBackend: POST /api/pets/search {species, status, categoryId}
    AppBackend->>ExternalPetAPI: Request pet data with filters
    ExternalPetAPI-->>AppBackend: Return pet data
    AppBackend->>AppBackend: Transform and store data with searchId
    AppBackend-->>User: Return {searchId}

    User->>AppBackend: GET /api/pets/results/{searchId}
    AppBackend-->>User: Return transformed pet list + notification
```

---

## User Interaction Journey

```mermaid
journey
    title Pet Search User Interaction
    section Search
      User inputs filters: 5: User
      App sends search request: 4: AppBackend
      External API returns data: 3: ExternalPetAPI
      App processes & transforms data: 4: AppBackend
      User receives searchId: 5: User
    section Results
      User requests results: 5: User
      App returns transformed pets: 5: AppBackend
      User views pets or notification: 5: User
```
```