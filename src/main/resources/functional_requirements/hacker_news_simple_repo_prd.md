# Product Requirements Document (PRD)

## Hacker News Simple Repo (Cyoda Example Application)

---

## 1. Purpose & Goals

This document defines a **minimal but representative example application** built using the **Cyoda Application Platform**.

The goal is to demonstrate how Cyoda enables:

- Entity-centric persistence
- Deterministic validation
- Workflow-driven enrichment
- Auditable state transitions
- Simple REST-based access

The application stores **Hacker News items** exactly as returned by the Firebase Hacker News API, with minimal backend logic and a deliberately simple frontend.

This is an **educational reference implementation**, not a full Hacker News client.

---

## 2. Target Audience

- Backend engineers evaluating Cyoda
- Solution architects exploring event-driven systems
- Developers using the Cyoda AI Assistant to scaffold applications

---

## 3. Scope

### In Scope

- Java-based backend service
- Storage of Hacker News items as raw JSON
- Validation and enrichment via Cyoda workflows
- REST APIs for ingesting and retrieving items

### Out of Scope

- Authentication and authorization
- UI styling or frontend complexity
- Crawling or polling Hacker News directly
- Search, ranking, or recommendation features

---

## 4. Functional Requirements

### 4.1 Ingest Hacker News Item

**Description**

The service accepts a Hacker News item in the same JSON structure as returned by the Firebase Hacker News API.

**Requirements**

- Input must be valid JSON
- Fields `id` and `type` must be present
- The item must be stored without structural modification
- The service must enrich the item with an `importTimestamp`

**Workflow Characteristics**

- Validation is implemented as workflow guards
- Enrichment is performed as part of a deterministic workflow transition

---

### 4.2 Retrieve Hacker News Item by ID

**Description**

Retrieve a previously stored Hacker News item using its `id`.

**Requirements**

- Lookup by item ID
- Return the original JSON, including `importTimestamp`
- No additional wrapping or transformation

---

## 5. Entity Model

### 5.1 Entity: `HackerNewsItem`

**Attributes**

| Field | Type | Description |
|------|------|-------------|
| `id` | number | Hacker News item ID |
| `type` | string | Item type (story, comment, job, etc.) |
| `rawJson` | JSON | Original Firebase API payload |
| `importTimestamp` | timestamp | Server-side ingestion time |

The entity stores the **raw JSON payload verbatim**, with enrichment added by workflow.

---

## 6. Workflow Design

The workflow illustrates Cyoda’s strengths: validation, enrichment, and auditable transitions.

### 6.1 States

| State | Description |
|------|-------------|
| `RECEIVED` | Item submitted but not yet validated |
| `VALIDATED` | Required fields confirmed |
| `STORED` | Item persisted and retrievable |
| `REJECTED` | Validation failed |

---

### 6.2 Transitions

#### `submitItem`

- **From:** `RECEIVED`
- **To:** `VALIDATED` or `REJECTED`
- **Guards:**
  - `id` exists
  - `type` exists
- **On Reject:**
  - Record rejection reason as metadata

#### `enrichAndStore`

- **From:** `VALIDATED`
- **To:** `STORED`
- **Actions:**
  - Set `importTimestamp = now()`
  - Persist entity state

---

## 7. Backend API

### 7.1 POST `/hn/items`

**Description**

Submit a Hacker News item for storage.

**Request Body**

- Raw JSON matching the Firebase Hacker News API format

**Responses**

- `201 Created` – Item accepted and stored
- `400 Bad Request` – Missing required fields (`id`, `type`)

---

### 7.2 GET `/hn/items/{id}`

**Description**

Retrieve a Hacker News item by ID.

**Responses**

- `200 OK` – Original JSON payload
- `404 Not Found` – Item does not exist

---

## 8. Non-Functional Requirements

- Deterministic workflow execution
- Full auditability of state transitions
- Idempotent handling of repeated submissions
- Low operational and deployment complexity

---

## 9. Frontend (Minimal by Design)

The frontend exists purely for demonstration purposes.

**Features**

- Text area to paste raw JSON
- Submit button
- Input field to retrieve item by ID
- Raw JSON display

No client-side validation or formatting is required.

---

## 10. Optional Extensions

These are not part of the core requirements but demonstrate extensibility:

- Add a `DUPLICATE` state for existing IDs
- Track item source (`manual`, `api`, `batch`)
- Introduce moderation or filtering workflows
- Time-based archival or cleanup workflows

---

## 11. Success Criteria

- The example is understandable within a short reading time
- Core Cyoda concepts are visible without additional explanation
- The application can be scaffolded and extended using the Cyoda AI Assistant

---

**End of Document**

