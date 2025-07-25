```markdown
# Application Requirement Specification

## Overview
Develop a Java 21 Spring Boot application that retrieves the current Bitcoin-to-USD (BTC/USD) and Bitcoin-to-EUR (BTC/EUR) conversion rates, sends these rates in an email report, and provides endpoints to initiate report creation and retrieve stored reports.

---

## Functional Requirements

### Endpoints

1. **POST /job**

- Purpose: Initiates the report creation process.
- Actions:
  - Fetch the latest BTC/USD and BTC/EUR conversion rates.
  - Store the fetched conversion rates as a "report" entity.
  - Trigger an email to send the conversion rate report.
- Response: Should confirm the initiation of the job and provide the report ID for tracking.

2. **GET /report/{id}**

- Purpose: Retrieve a previously generated report.
- Input: `id` - The unique identifier of the stored report.
- Actions:
  - Look up the stored report by its ID.
  - Return the stored conversion rate information (BTC/USD and BTC/EUR) as it was at the time of report creation.
- Response: The conversion rates and any relevant metadata about the report.

---

## Technical Details

### Application Stack

- Language: Java 21
- Framework: Spring Boot

### Core Design Principles (Cyoda Stack)

- Model the report as an **Entity** in the system.
- Attach a **Workflow** to the entity that triggers on the event of job creation (`POST /job`).
- Use a **State Machine** to manage the lifecycle of the report entity (e.g., CREATED → FETCHED → EMAILED → STORED).
- Design the fetching of conversion rates and sending of emails as asynchronous event-driven steps within the workflow.
- Use dynamic workflows and possible Trino integration if needed for data querying or analytics (optional, based on complexity).

### External Integrations

- **BTC Conversion Rates API**: Use a reliable public API to fetch real-time BTC/USD and BTC/EUR rates. Example APIs:
  - CoinGecko API: `https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd,eur`
  - Alternatively, other trusted APIs like CoinMarketCap, CryptoCompare, etc.
- **Email Service**:
  - Use JavaMailSender or an external transactional email service API (e.g., SendGrid, Amazon SES) to send the email report.
  - Email content should include the BTC/USD and BTC/EUR conversion rates retrieved.

### Data Persistence

- Store reports in a database with at least the following fields:
  - Unique Report ID
  - Timestamp of report generation
  - BTC/USD rate (decimal)
  - BTC/EUR rate (decimal)
  - Status of report (e.g., CREATED, EMAILED)
  - Any additional metadata (e.g., email sent timestamp)

---

## Summary

- Implement two REST endpoints:  
  - `POST /job`  
  - `GET /report/{id}`  
- On `POST /job`:  
  - Trigger workflow to fetch BTC/USD and BTC/EUR rates.  
  - Store report entity with fetched data and status.  
  - Send the email report asynchronously.  
- On `GET /report/{id}`:  
  - Retrieve and return the stored report data with exact BTC rates from creation time.  
- Follow Cyoda design values by architecting the system as event-driven, entity-centric with workflows and state machines.
```