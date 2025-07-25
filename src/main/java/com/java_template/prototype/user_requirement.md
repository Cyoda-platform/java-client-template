```markdown
# Requirement Specification: Digest Request Event Processing System

## Overview
The system must support the processing of digest request events, handling the entire flow from event registration through data retrieval to email dispatch.

## Functional Requirements

### 1. Event Registration
- The system must accept incoming digest request events.
- Each event includes:
  - User's email address.
  - Relevant metadata associated with the request.
- Events must be logged/persisted for traceability and auditing.

### 2. Data Retrieval
- Upon receiving a digest request event, the system must fetch required data from an external API.
- The external API base URL: `https://petstore.swagger.io/`
- The exact endpoint(s) and parameters:
  - Should be determined based on the contents of the digest request event.
  - If not specified in the event, system defaults must be used.
- The data retrieval must handle:
  - Proper API request formation.
  - Response parsing and error handling.
  
### 3. Email Dispatch
- The system must compile the retrieved data into a digest format.
- Supported digest formats:
  - Plain text.
  - HTML.
  - Attachment (if applicable).
- The compiled digest must be sent to the email address specified in the original digest request.
- Email dispatch must:
  - Ensure reliable sending.
  - Handle failures and retries if necessary.

## Technical Details
- External API: Swagger Petstore API at `https://petstore.swagger.io/`
- Event contents:
  - Must include user's email.
  - Metadata to guide API call parameters or fallback to defaults.
- Email sending:
  - Should support MIME types for plain text and HTML emails.
  - Attachments if used should be properly encoded.

## Additional Notes
- The system should be designed as event-driven to process incoming digest request events asynchronously.
- Logging and error handling must be implemented at each step for robustness.
- Consider security implications such as validating email addresses and sanitizing data retrieved from external APIs.
```
