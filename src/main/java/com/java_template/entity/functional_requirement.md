# Purrfect Pets API Functional Requirements

## API Endpoints

### 1. POST /pets/fetch  
Fetch pet data from the external Petstore API and process it (e.g., filtering, enrichment, or calculations).  
**Request:**  
```json
{
  "filter": {
    "type": "cat" | "dog" | "all",
    "status": "available" | "pending" | "sold" | null
  }
}
```
**Response:**  
```json
{
  "processedPets": [
    {
      "id": "string",
      "name": "string",
      "type": "cat" | "dog",
      "status": "available" | "pending" | "sold",
      "age": "number",
      "funFact": "string"
    }
  ]
}
```

### 2. GET /pets  
Retrieve the last fetched and processed pet data stored in the application.  
**Response:**  
```json
{
  "pets": [
    {
      "id": "string",
      "name": "string",
      "type": "cat" | "dog",
      "status": "available" | "pending" | "sold",
      "age": "number",
      "funFact": "string"
    }
  ]
}
```

### 3. POST /pets/adopt  
Register an adoption event for a pet and update its status.  
**Request:**  
```json
{
  "petId": "string",
  "adopterName": "string",
  "adoptionDate": "YYYY-MM-DD"
}
```
**Response:**  
```json
{
  "success": true,
  "message": "Pet adoption recorded successfully"
}
```

### 4. GET /pets/{id}  
Retrieve details of a specific pet by ID.  
**Response:**  
```json
{
  "id": "string",
  "name": "string",
  "type": "cat" | "dog",
  "status": "available" | "pending" | "sold",
  "age": "number",
  "funFact": "string"
}
```

---

## Mermaid Sequence Diagram: User Interaction with Purrfect Pets API

```mermaid
sequenceDiagram
    participant User
    participant PurrfectPetsAPI
    participant PetstoreAPI

    User->>PurrfectPetsAPI: POST /pets/fetch (filter)
    PurrfectPetsAPI->>PetstoreAPI: Fetch pet data
    PetstoreAPI-->>PurrfectPetsAPI: Pet data response
    PurrfectPetsAPI-->>User: Processed pet data response

    User->>PurrfectPetsAPI: GET /pets
    PurrfectPetsAPI-->>User: Return last processed pet data

    User->>PurrfectPetsAPI: POST /pets/adopt (adoption info)
    PurrfectPetsAPI-->>User: Adoption confirmation

    User->>PurrfectPetsAPI: GET /pets/{id}
    PurrfectPetsAPI-->>User: Pet details response
```

---

## Mermaid Journey Diagram: User Journey in Purrfect Pets App

```mermaid
journey
    title User Journey in Purrfect Pets API
    section Fetch & View Pets
      User requests pet data: 5: User
      System fetches from Petstore API: 4: PurrfectPetsAPI
      User views pet list: 5: User
    section Adopt a Pet
      User submits adoption request: 4: User
      System processes and confirms adoption: 5: PurrfectPetsAPI
      User views updated pet details: 5: User
```