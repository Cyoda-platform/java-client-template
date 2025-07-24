Certainly! Here is your original requirement reformulated as user stories, along with a technical details section preserving all business logic and necessary operations:

---

## User Stories

**User Story 1:**  
_As a system user, I want to create a mail entity with a list of recipients so that the system knows who to send mails to._

**User Story 2:**  
_As a system user, I want the system to automatically evaluate the mail content against 22 criteria to determine if it is happy or gloomy._

**User Story 3:**  
_As a system user, I want the system to send mails through two different processors: one for happy mails (`sendHappyMail`) and one for gloomy mails (`sendGloomyMail`), based on the criteria evaluation._

---

## Technical Details

### Entities

- **Mail**  
  - `mailList`: List of email recipients (List<String>)  
  - `isHappy`: Boolean flag indicating if the mail is happy or gloomy (true = happy, false = gloomy)  

### Processors

- `sendHappyMail`: Processes and sends mails classified as happy.  
- `sendGloomyMail`: Processes and sends mails classified as gloomy.

### Criteria

- 22 distinct criteria methods (e.g., `checkEntityCriteria1()`, ..., `checkEntityCriteria22()`) define whether a mail is happy or gloomy. Each criterion is a separate validation method.

### Business Logic

- When a new Mail entity is created (immutable creation), the system automatically triggers evaluation of all 22 criteria.  
- If the mail satisfies the criteria for being happy, `sendHappyMail` processor is triggered to send the mail.  
- Otherwise, `sendGloomyMail` processor is triggered.  
- The field `isHappy` is set accordingly based on criteria evaluation.  

### API Endpoints

- **POST /mails**  
  - Description: Create a new Mail entity.  
  - Request body:  
    ```json
    {
      "mailList": ["recipient1@example.com", "recipient2@example.com"]
    }
    ```  
  - Behavior:  
    - Triggers criteria validations automatically.  
    - Triggers appropriate mail sending processor based on criteria result.  
  - Response body:  
    ```json
    {
      "id": "unique-mail-id",
      "mailList": ["recipient1@example.com", "recipient2@example.com"],
      "isHappy": true,
      "status": "SENT_HAPPY"
    }
    ```  

- **GET /mails/{id}**  
  - Description: Retrieve mail entity details including recipients, happy/gloomy status, and sending status.  
  - Response body: same as above.

### Operations

- Immutable creation only for Mail entities; no update/delete endpoints unless explicitly requested later.  
- Automatic event-driven invocation of criteria checks upon Mail creation.  
- Automatic processor invocation (`sendHappyMail` or `sendGloomyMail`) based on criteria evaluation.  

---

Please let me know if you want me to proceed with more detailed specification or implementation!