# Workflows Requirements

## Mail Workflow

### Overview
The Mail workflow manages the lifecycle of mail entities from creation through processing to completion. The workflow determines whether to send happy or gloomy mail based on the entity's `isHappy` attribute and appropriate criteria.

### Workflow Name
`Mail`

### States

| State | Description | Type |
|-------|-------------|------|
| INITIAL | Starting state when mail entity is created | Initial |
| PENDING | Mail is ready for processing | Intermediate |
| HAPPY_PROCESSING | Mail is being processed as happy mail | Intermediate |
| GLOOMY_PROCESSING | Mail is being processed as gloomy mail | Intermediate |
| SENT | Mail has been successfully sent | Final |
| FAILED | Mail processing failed | Final |

### Transitions

| From State | To State | Type | Processor | Criterion | Description |
|------------|----------|------|-----------|-----------|-------------|
| INITIAL | PENDING | Automatic | None | None | Initial transition to start processing |
| PENDING | HAPPY_PROCESSING | Automatic | None | MailIsHappyCriterion | Route to happy processing if mail is happy |
| PENDING | GLOOMY_PROCESSING | Automatic | None | MailIsGloomyCriterion | Route to gloomy processing if mail is gloomy |
| HAPPY_PROCESSING | SENT | Automatic | MailSendHappyMailProcessor | None | Send happy mail and mark as sent |
| HAPPY_PROCESSING | FAILED | Automatic | None | None | Fallback if happy mail processing fails |
| GLOOMY_PROCESSING | SENT | Automatic | MailSendGloomyMailProcessor | None | Send gloomy mail and mark as sent |
| GLOOMY_PROCESSING | FAILED | Automatic | None | None | Fallback if gloomy mail processing fails |
| FAILED | PENDING | Manual | None | None | Retry failed mail processing |

### Workflow State Diagram

```mermaid
stateDiagram-v2
    [*] --> INITIAL
    INITIAL --> PENDING : Automatic
    
    PENDING --> HAPPY_PROCESSING : MailIsHappyCriterion
    PENDING --> GLOOMY_PROCESSING : MailIsGloomyCriterion
    
    HAPPY_PROCESSING --> SENT : MailSendHappyMailProcessor
    HAPPY_PROCESSING --> FAILED : Error
    
    GLOOMY_PROCESSING --> SENT : MailSendGloomyMailProcessor
    GLOOMY_PROCESSING --> FAILED : Error
    
    FAILED --> PENDING : Manual Retry
    
    SENT --> [*]
    FAILED --> [*]
```

### Business Rules
1. The first transition from INITIAL to PENDING is always automatic
2. Mail routing is determined by the `isHappy` attribute using criteria
3. Each processing state has a dedicated processor for sending the appropriate type of mail
4. Failed mails can be manually retried by transitioning back to PENDING
5. The workflow supports both happy and gloomy mail processing paths
