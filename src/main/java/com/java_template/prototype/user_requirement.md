```markdown
# Weekly Cat Fact Subscription Application - Requirements

## Overview
Develop an application that sends subscribers a new cat fact every week using the Cat Fact API. The system must handle data ingestion, user subscription management, email publishing, and reporting, with a weekly scheduled data ingestion and email send-out.

---

## Functional Requirements

### 1. Data Ingestion
- Retrieve a new cat fact **once a week** from the [Cat Fact API](https://catfact.ninja/#/Facts/getRandomFact).
- Data ingestion must be **scheduled** to run weekly, triggering the rest of the workflow.

### 2. User Interaction
- Provide functionality for users to **sign up** to receive weekly cat fact emails.
- Collect and store subscriber information, minimally including email addresses.
- Validate user input and handle duplicate subscriptions gracefully.

### 3. Publishing
- Send the retrieved cat fact via **email** to all subscribers once per week.
- Email content should include the cat fact text and possibly additional branding or footer information.
- Ensure email sending is reliable, handling failures and retries as needed.

### 4. Reporting
- Track the number of current subscribers.
- Track user interactions with the cat fact emails, including:
  - Email open rates.
  - Clicks or other engagement metrics (if applicable).
- Provide a summary report or dashboard for administrators showing:
  - Total subscribers.
  - Engagement statistics per sent cat fact.

---

## Technical Details

- **API Used:**  
  - Cat Fact API endpoint: `GET https://catfact.ninja/fact`  
  - Example response:  
    ```json
    {
      "fact": "Cats have five toes on their front paws, but only four on the back paws.",
      "length": 65
    }
    ```
  
- **Scheduling:**  
  - Data ingestion and email sending must be scheduled to run **once per week**.
  - This schedule triggers the retrieval of a new cat fact and the subsequent email dispatch.

- **Email Sending:**  
  - Use a reliable email service (SMTP or third-party APIs like SendGrid, Amazon SES, etc.).
  - Emails must be sent to all registered subscribers.

- **Data Storage:**  
  - Store subscriber data securely.
  - Store historical cat facts sent and interaction metrics for reporting.

- **Architecture Considerations (Cyoda Stack):**  
  - Model subscriber and cat fact as **entities**.
  - Use **state machines** and **dynamic workflows** triggered by scheduled events.
  - Integrate with **Trino** for querying reporting data if applicable.
  - The weekly scheduled event triggers the workflow:
    - Fetch cat fact → Send emails → Update reporting stats.

---

## Summary

| Feature               | Description                                           |
|-----------------------|-------------------------------------------------------|
| Data Ingestion        | Retrieve new cat fact weekly from Cat Fact API        |
| User Interaction      | Allow users to sign up for weekly cat fact emails     |
| Publishing            | Send cat fact email weekly to all subscribers         |
| Reporting             | Track number of subscribers and engagement metrics    |
| Scheduling            | Weekly scheduled job triggers ingestion and email send|

---

This specification ensures all business logic and key technical details are captured for the Weekly Cat Fact Subscription application.
```