Certainly! Below is your original requirement transformed into user stories with a technical details section preserving all business logic and API details.

---

## User Stories

### User Story 1: Create and send mails  
**As a** user of the mail application,  
**I want** to create a mail entity specifying whether it is happy or gloomy and provide a list of email recipients,  
**so that** the system can process and send the appropriate mail type automatically.

### User Story 2: Happy mail processing  
**As** the system,  
**I want** to process mails marked as happy using the `sendHappyMail` processor,  
**so that** happy mails are sent with the correct content and workflow.

### User Story 3: Gloomy mail processing  
**As** the system,  
**I want** to process mails marked as gloomy using the `sendGloomyMail` processor,  
**so that** gloomy mails are sent with the correct content and workflow.

### User Story 4: Criteria-based mail classification  
**As** the system,  
**I want** to determine if a mail is happy or gloomy based on two criteria,  
**so that** the correct processor is triggered for sending the mail.

---

## Technical Details

### Entity

- **Mail**  
  - `isHappy`: Boolean — indicates if the mail is happy (`true`) or gloomy (`false`).  
  - `mailList`: List<String> — list of email addresses to send the mail to.  
  - `status`: Enum (PENDING, SENT, FAILED) — tracks the lifecycle state of the mail.

### Processors

- **sendHappyMail**: Processes mails where `isHappy == true`.  
- **sendGloomyMail**: Processes mails where `isHappy == false`.

### Criteria

- Two criteria that check the mail entity to determine if it is happy or gloomy, tied to `isHappy` field.

### Event-Driven Processing Flow

- Saving a new `Mail` entity triggers processing events:
  - If saved without specifying a processor and only one exists, `processMail()` is triggered.
  - If specifying a processor, `processMailSendHappyMail()` or `processMailSendGloomyMail()` is triggered accordingly.
- Validation uses `checkMailIsHappy()` and `checkMailIsGloomy()` criteria events.
- Immutable creation of `Mail` entities is favored; no update or delete endpoints unless explicitly requested.

### APIs

- **POST /mail**  
  - Creates a new `Mail` entity.  
  - Input: JSON with fields `isHappy` and `mailList`.  
  - Output: JSON containing only the generated `technicalId`.  
  - Side effect: triggers mail processing events.

- **GET /mail/{technicalId}**  
  - Retrieves the `Mail` entity by its `technicalId` including current status.

- **(Optional) GET /mail?isHappy=true/false**  
  - Retrieves mails filtered by happy or gloomy status (only if explicitly requested).

### Example JSONs

**POST /mail Request**
```json
{
  "isHappy": true,
  "mailList": ["user1@example.com", "user2@example.com"]
}
```

**POST /mail Response**
```json
{
  "technicalId": "123e4567-e89b-12d3-a456-426614174000"
}
```

**GET /mail/{technicalId} Response**
```json
{
  "isHappy": true,
  "mailList": ["user1@example.com", "user2@example.com"],
  "status": "SENT"
}
```

---

If you need me to add update/delete operations or more complex filtering, just let me know!