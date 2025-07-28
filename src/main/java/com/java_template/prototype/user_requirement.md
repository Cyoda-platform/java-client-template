```markdown
# Application Requirements: Bitcoin Conversion Rate Reporting Service

## Overview
Develop an application that retrieves current Bitcoin-to-USD (BTC/USD) and Bitcoin-to-EUR (BTC/EUR) conversion rates, sends these rates via an email report, and manages report storage and retrieval through REST API endpoints.

---

## Functional Requirements

### 1. Endpoints

#### POST `/job`
- **Purpose:** Initiates the creation of a Bitcoin conversion rate report.
- **Process:**
  - Fetch the latest BTC/USD and BTC/EUR conversion rates from reliable APIs.
  - Create and store a "report" entity containing the retrieved conversion rates and metadata (e.g., timestamp, report ID).
  - Trigger the sending of an email containing the conversion rates report to predefined recipients.
- **Response:**
  - Return a confirmation with the generated report ID and status of the job initiation.

#### GET `/report?id={reportId}`
- **Purpose:** Retrieve a previously generated report by its unique identifier.
- **Process:**
  - Look up the stored report using the provided report ID.
  - Return the stored conversion rate information (BTC/USD and BTC/EUR) along with the report metadata.
- **Response:**
  - JSON containing the report details including BTC/USD rate, BTC/EUR rate, timestamp, and report ID.
  - Proper error handling if the report ID does not exist.

---

## Business Logic and Technical Details

- **Conversion Rate Retrieval:**
  - Use reliable external APIs for fetching real-time Bitcoin conversion rates (e.g., CoinGecko, CoinAPI, or similar).
  - Retrieve both BTC/USD and BTC/EUR rates in a single atomic operation to ensure consistency.

- **Report Entity:**
  - Represents the stored report with fields such as:
    - `reportId` (unique identifier)
    - `btcUsdRate` (decimal)
    - `btcEurRate` (decimal)
    - `timestamp` (datetime of rate retrieval)
  - Stored in a persistent storage (database or durable store).

- **Email Sending:**
  - The email content includes the BTC/USD and BTC/EUR rates and the timestamp of retrieval.
  - Use a reliable email service provider API (e.g., SMTP, SendGrid, Amazon SES).
  - Ensure asynchronous or event-driven sending to avoid blocking the `/job` request.

- **Workflow & Event-Driven Architecture (Cyoda Design Values):**
  - Model the report as a core "entity" with an attached workflow.
  - The workflow is triggered by the POST `/job` event.
  - Workflow steps:
    1. Fetch BTC conversion rates.
    2. Store report entity.
    3. Trigger email sending event.
  - State machine ensures reliable transitions and error handling.
  - Design the system to be extensible with potential Trino integration or dynamic workflows for complex reporting/logging.

- **Error Handling:**
  - Handle API failures gracefully with retries or fallbacks.
  - Return meaningful HTTP error codes and messages for client errors or server failures.

---

## Summary

| Endpoint        | Method | Functionality                                               |
|-----------------|--------|-------------------------------------------------------------|
| `/job`          | POST   | Initiate report creation, fetch BTC/USD & BTC/EUR rates, store report, send email. |
| `/report?id=...`| GET    | Retrieve stored report by ID with BTC conversion rates and metadata.  |

---

This specification preserves all business logic and technical details necessary for implementation, including the use of event-driven workflows, entities, and external API integrations for rate fetching and email sending.
```