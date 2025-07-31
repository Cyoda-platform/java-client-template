```markdown
# Application Requirements: Happy Mail Sender

## Overview
Build an application that sends mails categorized as happy or gloomy. The system revolves around a single core entity called `Mail`. The application should determine the emotional tone of each mail and process sending accordingly.

---

## Core Entity

### Entity: `Mail`
- **Fields:**
  - `isHappy` (Boolean)  
    - Indicates if the mail is happy (`true`) or gloomy (`false`).
  - `mailList` (List of email addresses)  
    - The list of recipients for the mail.

---

## Processors

Two processors must be implemented to handle sending mails based on their emotional tone:

1. **Processor: `sendHappyMail`**  
   - Responsible for sending mails where `isHappy == true`.
   - Triggered when the criteria for happy mail is met.

2. **Processor: `sendGloomyMail`**  
   - Responsible for sending mails where `isHappy == false`.
   - Triggered when the criteria for gloomy mail is met.

---

## Criteria

Two criteria must be defined to determine the mail type (happy or gloomy):

- **Happy Mail Criteria:**  
  - If the mail content or context matches the condition(s) defined as "happy" (business logic to be implemented).
  - When criteria met, set `isHappy = true`.

- **Gloomy Mail Criteria:**  
  - If the mail content or context matches the condition(s) defined as "gloomy" (business logic to be implemented).
  - When criteria met, set `isHappy = false`.

*Note:* The exact business logic for these criteria should be clearly defined and encapsulated in the system to automatically classify mails.

---

## Additional Notes

- The system should be designed using an event-driven architecture where the entity `Mail` undergoes state changes triggered by events, activating workflows that run the processors.
- Ensure separation of concerns: criteria evaluation, mail entity state management, and mail sending are distinct components.
- Integration with an email sending API/service is implied (e.g., SMTP, external mail service APIs).
- The solution should be extensible to allow additional mail types or processors in the future.

---

This specification preserves all business logic and technical details as requested.  
```