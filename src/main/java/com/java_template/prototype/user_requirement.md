```markdown
# Requirement: Java Service to Save Hacker News Items in JSON Format

## Overview
Build a Java service using **Java 21 Spring Boot** that allows storing and retrieving Hacker News (HN) items in JSON format, as per the Firebase HN API structure.

---

## Functional Requirements

### 1. POST Endpoint: Save Hacker News Item
- **Endpoint:** `/items`
- **Method:** POST
- **Request Body:** The raw JSON of the Hacker News item, exactly as received from the Firebase HN API.
- **Validations:**
  - The JSON **must** contain the mandatory fields:
    - `"type"` (string)
    - `"id"` (integer or string as per HN API)
  - If either field is missing, respond with HTTP **400 Bad Request** and an appropriate error message.
- **Behavior:**
  - Save the entire JSON object as-is.
  - Return the `"id"` field in the response to confirm successful storage.
- **Response:**
  - **Success:** HTTP 201 Created with the saved `"id"` in the response body.
  - **Failure:** HTTP 400 Bad Request if validation fails.

### 2. GET Endpoint: Retrieve Hacker News Item by ID
- **Endpoint:** `/items/{id}`
- **Method:** GET
- **Behavior:**
  - Retrieve the stored JSON object for the given `id`.
  - If the item with the given `id` does not exist, respond with HTTP **404 Not Found**.
- **Response:**
  - **Success:** HTTP 200 OK with the full JSON object as stored.
  - **Failure:** HTTP 404 Not Found if the `id` is not found.

---

## Technical Details

- **Programming Language:** Java 21
- **Framework:** Spring Boot
- **Data Storage:** 
  - In-memory storage (e.g., `ConcurrentHashMap<Integer, String>`) or any simple persistent storage for JSON items keyed by their `"id"`.
  - Storage must preserve the JSON format with no modification.
- **JSON Handling:** Use Jackson or equivalent for JSON parsing and validation.
- **API Contracts:**
  - POST accepts raw JSON body representing the Hacker News item.
  - GET returns raw JSON body representing the stored Hacker News item.
- **Error Handling:**
  - Validate incoming JSON for mandatory fields before saving.
  - Return clear error messages for missing fields.
  - Return 404 if `id` not found on GET request.

---

## Example Request/Response

### POST /items
**Request Body:**
```json
{
  "id": 8863,
  "type": "story",
  "by": "dhouston",
  "time": 1175714200,
  "text": "My YC app: Dropbox - Throw away your USB drive",
  "url": "http://www.getdropbox.com/u/2/screencast.html"
}
```

**Response:**
- Status: 201 Created
- Body:
```json
{
  "id": 8863
}
```

---

### GET /items/8863

**Response:**
- Status: 200 OK
- Body:
```json
{
  "id": 8863,
  "type": "story",
  "by": "dhouston",
  "time": 1175714200,
  "text": "My YC app: Dropbox - Throw away your USB drive",
  "url": "http://www.getdropbox.com/u/2/screencast.html"
}
```

---

### GET /items/999999 (non-existent)

**Response:**
- Status: 404 Not Found
- Body:
```json
{
  "error": "Item with id 999999 not found."
}
```

---

## Summary

- Java 21 Spring Boot REST service
- POST `/items` to save HN item JSON (validates `type` and `id` fields)
- GET `/items/{id}` to retrieve stored item or 404 if not found
- Store full JSON as-is
- Proper HTTP status codes and error handling

---

If you want, I can also provide a full implementation example based on these requirements.
```