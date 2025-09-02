# Processors

## Mail Processors

### MailSendHappyMailProcessor

#### Description
Processes and sends happy mail messages to the specified recipients in the mail list.

#### Entity
Mail

#### Expected Input Data
- Mail entity with:
  - isHappy: true
  - mailList: List of valid email addresses

#### Business Logic (Pseudocode)
```
process(Mail mail):
    1. Validate input mail entity
        - Check if mail is not null
        - Verify isHappy is true
        - Validate mailList is not empty
        - Validate all email addresses in mailList are valid format
    
    2. Prepare happy mail content
        - Set mail subject to "Happy Mail! 😊"
        - Set mail body to cheerful, positive message
        - Add happy emojis and positive language
        - Set mail priority to normal
    
    3. Send mail to recipients
        - For each email address in mailList:
            - Send happy mail with prepared content
            - Log successful sending
            - Handle any sending errors gracefully
    
    4. Update mail entity
        - Set sending timestamp
        - Mark as successfully sent
        - Log completion
    
    5. Return updated mail entity
```

#### Expected Output
- Updated Mail entity with sending timestamp
- Entity state transitions to "happy_sent"
- No other entity updates required (null transition)

#### Error Handling
- Invalid email addresses: Log warning and skip invalid addresses
- Mail service unavailable: Retry with exponential backoff
- Complete failure: Transition to "failed" state

---

### MailSendGloomyMailProcessor

#### Description
Processes and sends gloomy mail messages to the specified recipients in the mail list.

#### Entity
Mail

#### Expected Input Data
- Mail entity with:
  - isHappy: false
  - mailList: List of valid email addresses

#### Business Logic (Pseudocode)
```
process(Mail mail):
    1. Validate input mail entity
        - Check if mail is not null
        - Verify isHappy is false
        - Validate mailList is not empty
        - Validate all email addresses in mailList are valid format
    
    2. Prepare gloomy mail content
        - Set mail subject to "Thoughtful Message"
        - Set mail body to empathetic, supportive message
        - Use gentle, understanding language
        - Set mail priority to normal
    
    3. Send mail to recipients
        - For each email address in mailList:
            - Send gloomy mail with prepared content
            - Log successful sending
            - Handle any sending errors gracefully
    
    4. Update mail entity
        - Set sending timestamp
        - Mark as successfully sent
        - Log completion
    
    5. Return updated mail entity
```

#### Expected Output
- Updated Mail entity with sending timestamp
- Entity state transitions to "gloomy_sent"
- No other entity updates required (null transition)

#### Error Handling
- Invalid email addresses: Log warning and skip invalid addresses
- Mail service unavailable: Retry with exponential backoff
- Complete failure: Transition to "failed" state

---

## Processor Summary

| Processor Name | Entity | Input Condition | Output State | Transition |
|----------------|--------|-----------------|--------------|------------|
| MailSendHappyMailProcessor | Mail | isHappy = true | happy_sent | null |
| MailSendGloomyMailProcessor | Mail | isHappy = false | gloomy_sent | null |

## Common Processing Notes
- Both processors validate email addresses using standard email regex
- Both processors implement retry logic for transient failures
- Both processors log all activities for audit purposes
- Both processors handle partial failures gracefully (some emails sent, others failed)
- Both processors update the mail entity with sending metadata
