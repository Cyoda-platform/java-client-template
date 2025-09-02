# Criteria

## Mail Criteria

### MailIsHappyCriterion

#### Description
Determines whether a mail should be processed as a happy mail by checking the `isHappy` field.

#### Entity
Mail

#### Purpose
Used in the workflow transition from "pending" to "happy_sent" state to ensure only happy mails are processed by the MailSendHappyMailProcessor.

#### Logic
```
check(Mail mail):
    1. Validate mail entity exists
        - Return false if mail is null
    
    2. Check isHappy field
        - Return true if mail.isHappy == true
        - Return false if mail.isHappy == false or null
    
    3. Log evaluation result for audit
```

#### Expected Input
- Mail entity with isHappy field

#### Expected Output
- Boolean: true if mail is happy, false otherwise

#### Usage in Workflow
- Transition: send_happy_mail (pending → happy_sent)
- When this criterion returns true, the MailSendHappyMailProcessor will be executed

---

### MailIsGloomyCriterion

#### Description
Determines whether a mail should be processed as a gloomy mail by checking the `isHappy` field.

#### Entity
Mail

#### Purpose
Used in the workflow transition from "pending" to "gloomy_sent" state to ensure only gloomy mails are processed by the MailSendGloomyMailProcessor.

#### Logic
```
check(Mail mail):
    1. Validate mail entity exists
        - Return false if mail is null
    
    2. Check isHappy field
        - Return true if mail.isHappy == false
        - Return false if mail.isHappy == true or null
    
    3. Log evaluation result for audit
```

#### Expected Input
- Mail entity with isHappy field

#### Expected Output
- Boolean: true if mail is gloomy (not happy), false otherwise

#### Usage in Workflow
- Transition: send_gloomy_mail (pending → gloomy_sent)
- When this criterion returns true, the MailSendGloomyMailProcessor will be executed

---

## Criteria Summary

| Criterion Name | Entity | Condition | Returns True When | Used in Transition |
|----------------|--------|-----------|-------------------|-------------------|
| MailIsHappyCriterion | Mail | isHappy == true | Mail is happy | send_happy_mail |
| MailIsGloomyCriterion | Mail | isHappy == false | Mail is gloomy | send_gloomy_mail |

## Implementation Notes

### Mutual Exclusivity
- These criteria are mutually exclusive - a mail cannot be both happy and gloomy
- If isHappy is true, only MailIsHappyCriterion will return true
- If isHappy is false, only MailIsGloomyCriterion will return true
- If isHappy is null, both criteria will return false (error condition)

### Error Handling
- Both criteria handle null mail entities gracefully
- Both criteria handle null isHappy values by returning false
- Logging is implemented for audit trail and debugging

### Validation Rules
- Simple boolean field validation
- No complex business logic required
- Fast evaluation for workflow performance

### Workflow Integration
- These criteria ensure the correct processor is selected based on mail type
- They prevent incorrect routing of happy/gloomy mails
- They provide clear decision points in the workflow state machine
