# Processor Requirements

## Overview
Processors handle the business logic for mail sending operations. Each processor is responsible for a specific type of mail processing and implements the actual mail delivery functionality.

## MailSendHappyMailProcessor

### Entity
`Mail`

### Purpose
Processes and sends happy mail messages to all recipients in the mail list.

### Expected Input Data
- **Entity**: Mail entity with `isHappy = true` and populated `mailList`
- **Entity State**: HAPPY_PROCESSING
- **Metadata**: Entity metadata including UUID and current state

### Functionality
Sends cheerful, positive email content to all recipients in the mail list.

### Expected Output
- **Entity State**: No direct entity state modification (handled by workflow)
- **Side Effects**: Email messages sent to external mail service
- **Other Entities**: No other entities are created, updated, or deleted
- **Transition**: No transition needed (null transition)

### Pseudocode
```
FUNCTION process(mailEntity):
    BEGIN
        // Extract mail list from entity
        recipients = mailEntity.mailList
        
        // Validate input
        IF recipients is empty OR null THEN
            THROW ValidationException("Mail list cannot be empty")
        END IF
        
        // Prepare happy mail content
        subject = "🌟 Happy Mail - Brighten Your Day!"
        content = generateHappyMailContent()
        
        // Send mail to each recipient
        FOR EACH recipient IN recipients DO
            TRY
                sendEmail(
                    to: recipient,
                    subject: subject,
                    body: content,
                    type: "HAPPY"
                )
                LOG "Happy mail sent successfully to: " + recipient
            CATCH EmailException as e
                LOG "Failed to send happy mail to: " + recipient + " Error: " + e.message
                THROW ProcessingException("Failed to send happy mail to " + recipient)
            END TRY
        END FOR
        
        LOG "All happy mails sent successfully to " + recipients.size() + " recipients"
        
        // Return processed entity (unchanged)
        RETURN mailEntity
    END
    
FUNCTION generateHappyMailContent():
    RETURN "
        Dear Friend,
        
        We hope this message brings a smile to your face! 😊
        
        Here are some happy thoughts to brighten your day:
        - Every day is a new opportunity for joy
        - You are appreciated and valued
        - Good things are coming your way
        
        Have a wonderful day!
        
        Best regards,
        The Happy Mail Team
    "
END
```

## MailSendGloomyMailProcessor

### Entity
`Mail`

### Purpose
Processes and sends gloomy mail messages to all recipients in the mail list.

### Expected Input Data
- **Entity**: Mail entity with `isHappy = false` and populated `mailList`
- **Entity State**: GLOOMY_PROCESSING
- **Metadata**: Entity metadata including UUID and current state

### Functionality
Sends somber, melancholic email content to all recipients in the mail list.

### Expected Output
- **Entity State**: No direct entity state modification (handled by workflow)
- **Side Effects**: Email messages sent to external mail service
- **Other Entities**: No other entities are created, updated, or deleted
- **Transition**: No transition needed (null transition)

### Pseudocode
```
FUNCTION process(mailEntity):
    BEGIN
        // Extract mail list from entity
        recipients = mailEntity.mailList
        
        // Validate input
        IF recipients is empty OR null THEN
            THROW ValidationException("Mail list cannot be empty")
        END IF
        
        // Prepare gloomy mail content
        subject = "🌧️ Reflective Thoughts - A Moment of Contemplation"
        content = generateGloomyMailContent()
        
        // Send mail to each recipient
        FOR EACH recipient IN recipients DO
            TRY
                sendEmail(
                    to: recipient,
                    subject: subject,
                    body: content,
                    type: "GLOOMY"
                )
                LOG "Gloomy mail sent successfully to: " + recipient
            CATCH EmailException as e
                LOG "Failed to send gloomy mail to: " + recipient + " Error: " + e.message
                THROW ProcessingException("Failed to send gloomy mail to " + recipient)
            END TRY
        END FOR
        
        LOG "All gloomy mails sent successfully to " + recipients.size() + " recipients"
        
        // Return processed entity (unchanged)
        RETURN mailEntity
    END
    
FUNCTION generateGloomyMailContent():
    RETURN "
        Dear Recipient,
        
        Sometimes life brings us moments of reflection and contemplation. 🌧️
        
        In these quiet moments, we might ponder:
        - The fleeting nature of time
        - The weight of our daily struggles
        - The complexity of human emotions
        
        Remember that even in darker moments, there is meaning to be found.
        
        With thoughtful regards,
        The Contemplative Mail Team
    "
END
```

## Common Processing Notes

### Error Handling
- Both processors should handle email sending failures gracefully
- Failed email delivery should be logged with recipient details
- Processing exceptions should be thrown to trigger workflow error handling

### Logging Requirements
- Log successful mail delivery for each recipient
- Log failures with detailed error information
- Log summary of total mails sent per processing session

### Email Service Integration
- Processors should integrate with external email service (implementation detail)
- Email format should be HTML-capable for rich content
- Email headers should include appropriate metadata for tracking

### Performance Considerations
- Process recipients sequentially to avoid overwhelming email service
- Consider rate limiting for large recipient lists
- Implement timeout handling for email service calls
