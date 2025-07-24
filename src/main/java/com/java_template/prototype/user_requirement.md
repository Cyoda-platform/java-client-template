```markdown
# Requirements for Digest Request Event Processing System

## Overview
The system must support the processing of **digest request events**. The processing involves three main stages: event registration, data retrieval from an external API, and email dispatch. The system should be implemented in **Java 21 Spring Boot** following Cyoda design principles (event-driven, entity workflows).

---

## Detailed Requirements

### 1. Event Registration
- The system must accept incoming **digest request events**.
- Each event includes:
  - User's **email address**.
  - Relevant **metadata** associated with the digest request.
- The event must be **logged** for audit and tracking purposes.
- This event triggers the workflow for processing.

### 2. Data Retrieval
- Upon event registration, the system must retrieve required data from an **external API**.
- The external API base URL:  
  `https://petstore.swagger.io/`
- The specific API endpoint(s) and parameters to call should be:
  - Determined dynamically based on either:
    - Contents of the digest request event, or
    - System-defined default configuration.
- The system must handle:
  - Constructing the correct API requests.
  - Parsing and processing the API responses to extract relevant data for the digest.

### 3. Email Dispatch
- After retrieving and processing the data, the system must compile it into a digest format.
- Supported digest formats may include:
  - Plain text
  - HTML formatted email
  - Attachments (if applicable)
- The digest must be sent via email to the **email address provided in the original digest request event**.
- Email dispatch must ensure:
  - Proper formatting of the email content.
  - Reliable delivery (e.g., retries or failure handling as needed).

---

## Technical & Architectural Notes

- Implement using **Java 21 Spring Boot**.
- Architect the system as an **event-driven workflow** based on the **Cyoda stack**:
  - Core abstraction: an **Entity** with an associated **workflow**.
  - Workflow triggered by incoming **digest request events**.
- Integration with external API must be robust:
  - Use appropriate HTTP client (e.g., WebClient or RestTemplate).
  - Handle errors and timeouts gracefully.
- Email sending can leverage Spring Boot's mail utilities or integrate with external SMTP/email services.
- Event logging should be persistent and queryable for audit purposes.
- Consider using asynchronous processing for scalability and responsiveness.

---

## Summary

| Step             | Description                                                                                           |
|------------------|---------------------------------------------------------------------------------------------------|
| Event Registration | Accept and log digest request events with email and metadata.                                      |
| Data Retrieval     | Fetch data from `https://petstore.swagger.io/` API; endpoints and params based on event or defaults. |
| Email Dispatch     | Compile data into digest format and send email to the user’s address specified in the event.       |

This specification preserves all business logic and technical details to implement a robust, event-driven digest processing system in Java Spring Boot.
```