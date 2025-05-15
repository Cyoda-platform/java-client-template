```markdown
# Purrfect Pets API - Functional Requirements

## API Endpoints

### 1. POST /pets/import  
**Description:** Import or refresh pet data from the external Petstore API.  
**Request Body:**  
```json
{
  "source": "petstore",
  "filter": {
    "type": "string (optional)",
    "status": "string (optional)"
  }
}
```  
**Response:**  
```json
{
  "importedCount": "integer",
  "message": "string"
}
```

### 2. GET /pets  
**Description:** Retrieve a list of pets stored in the application. Supports filtering by type and status via query parameters.  
**Query Parameters (optional):**  
- type (string)  
- status (string)  
**Response:**  
```json
[
  {
    "id": "string",
    "name": "string",
    "type": "string",
    "age": "integer",
    "status": "string"
  }
]
```

### 3. GET /pets/{id}  
**Description:** Retrieve details for a specific pet by ID.  
**Response:**  
```json
{
  "id": "string",
  "name": "string",
  "type": "string",
  "age": "integer",
  "status": "string",
  "description": "string"
}
```

### 4. POST /pets  
**Description:** Add a new pet to the application.  
**Request Body:**  
```json
{
  "name": "string",
  "type": "string",
  "age": "integer",
  "status": "string",
  "description": "string (optional)"
}
```  
**Response:**  
```json
{
  "id": "string",
  "message": "Pet added successfully"
}
```

### 5. POST /pets/{id}/update-status  
**Description:** Update pet adoption status or other mutable fields.  
**Request Body:**  
```json
{
  "status": "string"
}
```  
**Response:**  
```json
{
  "id": "string",
  "newStatus": "string",
  "message": "Status updated successfully"
}
```

---

## User-App Interaction Sequence

```mermaid
sequenceDiagram
  participant User
  participant PurrfectPetsAPI
  participant ExternalPetstoreAPI

  User->>PurrfectPetsAPI: POST /pets/import {filter?}
  PurrfectPetsAPI->>ExternalPetstoreAPI: Fetch pet data
  ExternalPetstoreAPI-->>PurrfectPetsAPI: Pet data response
  PurrfectPetsAPI-->>User: Import result

  User->>PurrfectPetsAPI: GET /pets?type=&status=
  PurrfectPetsAPI-->>User: List of pets

  User->>PurrfectPetsAPI: POST /pets {new pet data}
  PurrfectPetsAPI-->>User: Confirmation with pet id

  User->>PurrfectPetsAPI: POST /pets/{id}/update-status {status}
  PurrfectPetsAPI-->>User: Status update confirmation
```

```mermaid
journey
  title Purrfect Pets User Journey
  section Import Data
    User: 5: Requests import of pet data
    System: 5: Retrieves and stores data from external source
    User: 4: Receives import confirmation

  section Browsing Pets
    User: 5: Requests pet list with filters
    System: 5: Returns filtered pet list
    User: 5: Views pet details

  section Managing Pets
    User: 5: Adds new pet
    System: 5: Confirms addition
    User: 4: Updates pet status
    System: 4: Confirms status update
```
```