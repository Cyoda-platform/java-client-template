Certainly! Here is your original requirement expressed as user stories along with a technical details section preserving all business logic and necessary operations:

---

## User Stories

- **As a user**, I want to create and send mails that can be categorized as happy or gloomy.
- **As a system**, I want to have a single entity called `Mail` with two fields: `isHappy` (boolean) and `mailList` (a list of recipient email addresses).
- **As a system**, I want two processors: `sendHappyMail` and `sendGloomyMail` that handle sending mails based on whether they are happy or gloomy.
- **As a system**, I want two criteria that define if the mail is happy or gloomy, used to decide which processor to invoke.
- **As a developer**, I want the application written in Java Spring Boot.

---

## Technical Details

### Entity

- **Mail**  
  - `isHappy`: Boolean — indicates if the mail is happy (`true`) or gloomy (`false`)  
  - `mailList`: List<String> — list of recipient email addresses

### Processors

- `sendHappyMail` — processor to send mails marked happy  
- `sendGloomyMail` — processor to send mails marked gloomy

### Criteria

- `checkMailIsHappy` — validates if `isHappy` is `true`  
- `checkMailIsGloomy` — validates if `isHappy` is `false`

### API Endpoints

- **POST /mails**  
  - Purpose: Create a new mail entity and trigger sending process  
  - Request body:
    ```json
    {
      "isHappy": true,
      "mailList": ["email1@example.com", "email2@example.com"]
    }
    ```
  - Response body:
    ```json
    {
      "id": "generated-uuid"
    }
    ```
  - Behavior:  
    - Saving the mail triggers `processMail()` event.
    - If no explicit processor is specified, `processMail()` invokes either `sendHappyMail` or `sendGloomyMail` based on criteria.
    - If user specifies a processor explicitly, `processMailSendHappyMail()` or `processMailSendGloomyMail()` is triggered.

- **GET /mails/{id}**  
  - Purpose: Retrieve mail entity details including status  
  - Response body:
    ```json
    {
      "id": "uuid",
      "isHappy": true,
      "mailList": ["email1@example.com", "email2@example.com"],
      "status": "SENT_HAPPY"  // or SENT_GLOOMY, PENDING, FAILED
    }
    ```

### Business Logic & Operations

- On mail creation (POST /mails), an immutable mail entity is saved with `PENDING` status.
- Cyoda’s event-driven mechanism triggers processing automatically.
- Criteria `checkMailIsHappy` or `checkMailIsGloomy` determine which processor to execute.
- Processor `sendHappyMail` or `sendGloomyMail` sends the mail with appropriate templates/messages.
- After sending, mail status updates to `SENT_HAPPY` or `SENT_GLOOMY`.
- On failure, status updates to `FAILED`.
- No update or delete endpoints are provided to maintain event history immutability.

---

If you want me to generate detailed functional requirements or implementation steps next, please let me know!