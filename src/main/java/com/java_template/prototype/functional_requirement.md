### 1. Entity Definitions

```
Mail:
- isHappy: Boolean (indicates if the mail is happy or gloomy)
- mailList: List<String> (list of recipient email addresses)
```

### 2. Process Method Flows

```
processMail() Flow:
1. Initial State: Mail entity is created with isHappy and mailList fields populated
2. Validation (optional): If requested, checkMailIsHappy() or checkMailIsGloomy() criteria validate the isHappy field value
3. Processing:
   - If isHappy == true → sendHappyMail processor sends happy mail content to all recipients in mailList
   - If isHappy == false → sendGloomyMail processor sends gloomy mail content to all recipients in mailList
4. Completion: Mail sending completes, event chain ends (no updates to Mail entity, immutable)
```

### 3. API Endpoints Design

- **POST /mails**  
  - Creates a new Mail entity (triggers `processMail()` event by default if only one processor or explicitly specify processor)  
  - Request body includes `isHappy` and `mailList`  
  - Response: `{ "technicalId": "<generated-id>" }`

- **GET /mails/{technicalId}**  
  - Retrieves stored Mail entity by `technicalId`  
  - Response includes `isHappy` and `mailList`

- **GET /mails** *(optional, only if explicitly requested)*  
  - Retrieves all stored Mail entities or filtered by criteria (e.g., isHappy) if requested

### 4. Request/Response Formats

**POST /mails**  
_Request JSON:_
```json
{
  "isHappy": true,
  "mailList": ["user1@example.com", "user2@example.com"]
}
```

_Response JSON:_
```json
{
  "technicalId": "abc123"
}
```

**GET /mails/{technicalId}**  
_Response JSON:_
```json
{
  "isHappy": true,
  "mailList": ["user1@example.com", "user2@example.com"]
}
```

---

### Mermaid Diagram: Event-Driven Processing Chain

```mermaid
flowchart TD
    A[POST /mails] --> B[Save Mail entity]
    B --> C{Single processor?}
    C -- Yes --> D[processMail()]
    D --> E{isHappy?}
    E -- true --> F[sendHappyMail processor sends happy mail]
    E -- false --> G[sendGloomyMail processor sends gloomy mail]
    F --> H[Mail sending complete]
    G --> H
```