```markdown
# Application Requirements

## Overview
Develop a Java 21 Spring Boot application that retrieves the current Bitcoin-to-USD (BTC/USD) and Bitcoin-to-EUR (BTC/EUR) conversion rates, then sends these rates in an email report. The application must expose two HTTP endpoints for initiating the report creation and for retrieving stored reports by ID.

---

## Functional Requirements

### 1. POST `/job`
- **Purpose:** Initiates the report creation workflow.
- **Behavior:**
  - Fetch the latest Bitcoin-to-USD and Bitcoin-to-EUR conversion rates.
  - Store the fetched rates as a "report" entity.
  - Trigger sending an email with the conversion rate report.
- **Response:** Return a unique report ID to the client for later retrieval.

### 2. GET `/report?id={reportId}`
- **Purpose:** Retrieve a previously generated report by its unique ID.
- **Behavior:**
  - Fetch and return the stored report containing BTC/USD and BTC/EUR rates.
- **Response:** JSON containing the stored conversion rates and report metadata.

---

## Business Logic & Technical Details

### Core Design Principles (Cyoda Stack)
- **Entity:** The report is modeled as a core entity with lifecycle state.
- **Workflow:** The report entity’s workflow is triggered by the POST `/job` event.
- **Event-Driven:** The system is event-driven, with the workflow managing fetching rates and sending email.
- **State Machine:** Report lifecycle states include at least `CREATED`, `FETCHED`, `SENT`.
- **Dynamic Workflows:** Workflow dynamically handles fetching, storing, and emailing.

### Data Retrieval
- Use a reliable public API to fetch Bitcoin conversion rates, such as:
  - CoinGecko API: `https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd,eur`
  - or any equivalent BTC price API supporting BTC/USD and BTC/EUR.

### Email Sending
- Send an email report containing the BTC/USD and BTC/EUR conversion rates.
- Email content should include:
  - Timestamp of the rate fetch.
  - BTC/USD rate.
  - BTC/EUR rate.
- Use standard Spring Boot Mail integration or configurable SMTP.

### Persistence
- Store reports (conversion rates + timestamp + unique ID) in a persistent store (e.g., relational DB).
- Enable retrieval by report ID via GET `/report`.

### API Details

#### POST /job
- **Request:** No body required.
- **Response:**
  ```json
  {
    "reportId": "string-uuid"
  }
  ```

#### GET /report?id={reportId}
- **Request:**
  - Query parameter: `id` (string UUID)
- **Response:**
  ```json
  {
    "reportId": "string-uuid",
    "timestamp": "ISO-8601-timestamp",
    "btcUsd": number,
    "btcEur": number,
    "emailSent": boolean
  }
  ```

---

## Summary
- Java 21 Spring Boot application.
- Two endpoints: POST `/job` to trigger workflow, GET `/report` to retrieve stored report.
- Retrieve real-time BTC/USD and BTC/EUR rates from public API.
- Store report entity with state machine-driven workflow.
- Send email with conversion rates after fetching.
- Persist reports for later retrieval.

This design aligns with Cyoda platform principles: event-driven, entity-centric, workflow-enabled system with clear separation of concerns and dynamic workflow orchestration.
```