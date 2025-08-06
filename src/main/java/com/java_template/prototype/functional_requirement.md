### 1. Entity Definitions
```
Workflow:  (Orchestration entity)
- name: String (Name of the workflow, e.g., "Pet Adoption Process")
- description: String (Optional description of the workflow)
- status: String (Current status of the workflow instance, e.g., "PENDING", "COMPLETED")
- createdAt: String (Timestamp of creation)
- petId: String (Reference to Pet entity involved in this workflow)

Pet:  (Business domain entity)
- name: String (Name of the pet)
- category: String (Pet category, e.g., "cat", "dog")
- photoUrls: String (URL(s) to pet photos)
- tags: String (Comma-separated tags or descriptors)
- status: String (Pet availability status, e.g., "available", "pending", "sold")

AdoptionRequest:  (Business domain entity)
- petId: String (Reference to Pet)
- adopterName: String (Name of person requesting adoption)
- adopterContact: String (Contact info of adopter)
- requestDate: String (Date when request was made)
- status: String (Request status, e.g., "PENDING", "APPROVED", "REJECTED")
```

---

### 2. Process Method Flows

```
processWorkflow() Flow:
1. Initial State: Workflow created with status "PENDING"
2. Validation: Check pet availability and adoption request validity
3. Processing: Trigger adoption steps (notify adopter, update pet status)
4. Completion: Mark workflow status as "COMPLETED" or "FAILED"
5. Notification: Send confirmation to adopter and update pet status in system

processPet() Flow:
1. Initial State: Pet entity created with provided details and "available" status
2. Validation: Check mandatory fields (name, category)
3. Processing: Save pet info to data store
4. Completion: Pet is available for adoption or sale

processAdoptionRequest() Flow:
1. Initial State: AdoptionRequest created with status "PENDING"
2. Validation: Confirm pet exists and is "available"
3. Processing: Initiate Workflow creation for adoption process
4. Completion: AdoptionRequest status updated based on workflow outcome
5. Notification: Inform adopter of status (approved/rejected)
```

---

### 3. API Endpoints Design

| HTTP Method | Path                         | Purpose                                        | Request Body Example                         | Response Example              |
|-------------|------------------------------|------------------------------------------------|----------------------------------------------|-------------------------------|
| POST        | /workflow                    | Create a new adoption workflow                  | `{ "name": "Adopt Fluffy", "description": "...", "petId": "abc123" }` | `{ "technicalId": "wf001" }`  |
| GET         | /workflow/{technicalId}      | Get workflow details by technicalId             |                                              | Full Workflow JSON             |
| POST        | /pet                         | Add a new pet (triggers processPet event)       | `{ "name": "Fluffy", "category": "cat", "photoUrls": "...", "tags": "cute,furry", "status": "available" }` | `{ "technicalId": "pet007" }` |
| GET         | /pet/{technicalId}           | Get pet details by technicalId                   |                                              | Full Pet JSON                 |
| POST        | /adoption-request            | Create an adoption request (triggers processAdoptionRequest) | `{ "petId": "pet007", "adopterName": "Alice", "adopterContact": "...", "requestDate": "2024-06-01", "status": "PENDING" }` | `{ "technicalId": "ar123" }`  |
| GET         | /adoption-request/{technicalId} | Get adoption request by technicalId           |                                              | Full AdoptionRequest JSON     |

- No PUT, PATCH, DELETE endpoints will be created (immutable creation principle).
- GET by non-technicalId fields (e.g., get pets by status) is optional and only added if explicitly requested.

---

### 4. Request/Response JSON Examples

**POST /pet Request**
```json
{
  "name": "Fluffy",
  "category": "cat",
  "photoUrls": "http://example.com/photos/fluffy.jpg",
  "tags": "cute,furry",
  "status": "available"
}
```

**POST /pet Response**
```json
{
  "technicalId": "pet007"
}
```

**GET /pet/{technicalId} Response**
```json
{
  "name": "Fluffy",
  "category": "cat",
  "photoUrls": "http://example.com/photos/fluffy.jpg",
  "tags": "cute,furry",
  "status": "available",
  "createdAt": "2024-06-01T12:00:00Z"
}
```

---

### 5. Mermaid Diagram: Event-driven Processing Chain

```mermaid
flowchart TD
    A[POST /pet] --> B[Save Pet entity]
    B --> C[processPet()]
    C --> D[Validate Pet fields]
    D --> E[Persist Pet data]
    E --> F[Pet is now available]

    G[POST /adoption-request] --> H[Save AdoptionRequest entity]
    H --> I[processAdoptionRequest()]
    I --> J[Validate Pet availability]
    J --> K[Create Workflow entity]
    K --> L[processWorkflow()]
    L --> M[Manage adoption steps]
    M --> N[Update AdoptionRequest status]
    N --> O[Notify adopter & update Pet status]
```
