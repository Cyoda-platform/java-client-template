# Purrfect Pets API - Functional Requirements

## API Endpoints

### 1. List Pets  
- **Method:** GET  
- **Path:** `/pets`  
- **Description:** Retrieve the list of pets stored in the application (cached or user-added).  
- **Request:** No body  
- **Response:**  
```json
[
  {
    "id": "string",
    "name": "string",
    "type": "string",
    "age": "integer",
    "status": "available" | "adopted"
  },
  ...
]
```

---

### 2. Search Pets  
- **Method:** POST  
- **Path:** `/pets/search`  
- **Description:** Search and filter pets using criteria, invoking external Petstore API data and combining with local data if needed.  
- **Request:**  
```json
{
  "type": "string",         // optional
  "status": "string",       // optional
  "name": "string"          // optional, partial match
}
```  
- **Response:** Same as **List Pets** response format.

---

### 3. Add New Pet  
- **Method:** POST  
- **Path:** `/pets`  
- **Description:** Add a new pet to the local application data.  
- **Request:**  
```json
{
  "name": "string",
  "type": "string",
  "age": "integer",
  "status": "available"
}
```  
- **Response:**  
```json
{
  "id": "string",  // generated pet id
  "message": "Pet added successfully"
}
```

---

### 4. Update Pet Info  
- **Method:** POST  
- **Path:** `/pets/{id}`  
- **Description:** Update pet details such as name, type, age, or status.  
- **Request:**  
```json
{
  "name": "string",      // optional
  "type": "string",      // optional
  "age": "integer",      // optional
  "status": "string"     // optional
}
```  
- **Response:**  
```json
{
  "id": "string",
  "message": "Pet updated successfully"
}
```

---

### 5. Adopt Pet  
- **Method:** POST  
- **Path:** `/pets/{id}/adopt`  
- **Description:** Mark a pet as adopted, changing its status.  
- **Request:** No body  
- **Response:**  
```json
{
  "id": "string",
  "message": "Pet adopted successfully"
}
```

---

## Visual Representation: User-App Interaction

```mermaid
sequenceDiagram
    participant User
    participant PurrfectPetsAPI
    participant PetstoreAPI

    User->>PurrfectPetsAPI: GET /pets
    PurrfectPetsAPI-->>User: List of pets (local data)

    User->>PurrfectPetsAPI: POST /pets/search {criteria}
    PurrfectPetsAPI->>PetstoreAPI: Query external Petstore API with criteria
    PetstoreAPI-->>PurrfectPetsAPI: External pet data
    PurrfectPetsAPI-->>User: Combined search results

    User->>PurrfectPetsAPI: POST /pets {new pet data}
    PurrfectPetsAPI-->>User: Confirmation with new pet id

    User->>PurrfectPetsAPI: POST /pets/{id} {update data}
    PurrfectPetsAPI-->>User: Update confirmation

    User->>PurrfectPetsAPI: POST /pets/{id}/adopt
    PurrfectPetsAPI-->>User: Adoption confirmation
```

```mermaid
journey
    title User Journey in Purrfect Pets App
    section Browsing Pets
      User opens app: 5: User
      Views list of pets: 5: User
    section Searching Pets
      User inputs search criteria: 4: User
      System searches external Petstore API + local data: 5: PurrfectPetsAPI
      Shows combined results: 5: User
    section Managing Pets
      User adds new pet: 3: User
      System confirms addition: 5: PurrfectPetsAPI
      User updates pet info: 3: User
      System confirms update: 5: PurrfectPetsAPI
      User adopts pet: 4: User
      System confirms adoption: 5: PurrfectPetsAPI
```