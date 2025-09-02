# Processors

## MailSendHappyMailProcessor

### Description
Processes and sends happy mail messages to the specified recipients. This processor handles the business logic for formatting, preparing, and delivering happy-themed email content.

### Entity
- **Belongs to**: Mail entity
- **Workflow transition**: `process_happy_mail`

### Input Data
- **Mail entity** with the following attributes:
  - `isHappy`: Boolean (should be true)
  - `mailList`: List<String> (email addresses)
  - Current state: `pending`

### Business Logic
The processor performs the following operations:
1. Validates the mail entity and recipient list
2. Formats the email content with happy themes
3. Sends the email to all recipients in the mailList
4. Updates processing status based on success/failure

### Expected Output
- **On Success**: 
  - Entity state transitions to `processing_happy`
  - Triggers automatic transition to `sent` state
  - No other entity updates required
- **On Failure**: 
  - Entity state transitions to `processing_happy`
  - Triggers automatic transition to `failed` state
  - No other entity updates required

### Transition Information
- **Success transition**: `happy_mail_sent` (automatic)
- **Failure transition**: `happy_mail_failed` (automatic)

### Pseudocode
```
FUNCTION process():
    // Extract and validate mail entity
    mail = extractMailEntity(request)
    
    IF mail is null:
        RETURN error("Mail entity not found")
    
    IF mail.isHappy is not true:
        RETURN error("Mail is not marked as happy")
    
    IF mail.mailList is empty:
        RETURN error("No recipients specified")
    
    // Prepare happy mail content
    subject = "🌟 Happy Mail - Brighten Your Day! 🌟"
    content = generateHappyContent()
    
    // Send mail to all recipients
    successCount = 0
    failureCount = 0
    
    FOR EACH recipient IN mail.mailList:
        IF isValidEmail(recipient):
            result = sendEmail(recipient, subject, content)
            IF result.success:
                successCount++
            ELSE:
                failureCount++
                LOG error("Failed to send to " + recipient)
        ELSE:
            failureCount++
            LOG error("Invalid email address: " + recipient)
    
    // Determine overall result
    IF successCount > 0 AND failureCount == 0:
        LOG info("Happy mail sent successfully to all recipients")
        RETURN success(mail)
    ELSE IF successCount > 0:
        LOG warning("Happy mail partially sent: " + successCount + " success, " + failureCount + " failed")
        RETURN success(mail)  // Partial success still counts as success
    ELSE:
        LOG error("Failed to send happy mail to any recipient")
        RETURN failure("No recipients received the happy mail")

FUNCTION generateHappyContent():
    RETURN "Dear Friend,\n\nWe hope this message brings a smile to your face! 😊\n\nHave a wonderful and joyful day ahead!\n\nWith warm regards,\nThe Happy Mail Team"
```

---

## MailSendGloomyMailProcessor

### Description
Processes and sends gloomy mail messages to the specified recipients. This processor handles the business logic for formatting, preparing, and delivering gloomy-themed email content with appropriate sensitivity.

### Entity
- **Belongs to**: Mail entity
- **Workflow transition**: `process_gloomy_mail`

### Input Data
- **Mail entity** with the following attributes:
  - `isHappy`: Boolean (should be false)
  - `mailList`: List<String> (email addresses)
  - Current state: `pending`

### Business Logic
The processor performs the following operations:
1. Validates the mail entity and recipient list
2. Formats the email content with gloomy themes (with sensitivity)
3. Sends the email to all recipients in the mailList
4. Updates processing status based on success/failure

### Expected Output
- **On Success**: 
  - Entity state transitions to `processing_gloomy`
  - Triggers automatic transition to `sent` state
  - No other entity updates required
- **On Failure**: 
  - Entity state transitions to `processing_gloomy`
  - Triggers automatic transition to `failed` state
  - No other entity updates required

### Transition Information
- **Success transition**: `gloomy_mail_sent` (automatic)
- **Failure transition**: `gloomy_mail_failed` (automatic)

### Pseudocode
```
FUNCTION process():
    // Extract and validate mail entity
    mail = extractMailEntity(request)
    
    IF mail is null:
        RETURN error("Mail entity not found")
    
    IF mail.isHappy is not false:
        RETURN error("Mail is not marked as gloomy")
    
    IF mail.mailList is empty:
        RETURN error("No recipients specified")
    
    // Prepare gloomy mail content
    subject = "💙 Thoughtful Message - We're Here for You"
    content = generateGloomyContent()
    
    // Send mail to all recipients
    successCount = 0
    failureCount = 0
    
    FOR EACH recipient IN mail.mailList:
        IF isValidEmail(recipient):
            result = sendEmail(recipient, subject, content)
            IF result.success:
                successCount++
            ELSE:
                failureCount++
                LOG error("Failed to send to " + recipient)
        ELSE:
            failureCount++
            LOG error("Invalid email address: " + recipient)
    
    // Determine overall result
    IF successCount > 0 AND failureCount == 0:
        LOG info("Gloomy mail sent successfully to all recipients")
        RETURN success(mail)
    ELSE IF successCount > 0:
        LOG warning("Gloomy mail partially sent: " + successCount + " success, " + failureCount + " failed")
        RETURN success(mail)  // Partial success still counts as success
    ELSE:
        LOG error("Failed to send gloomy mail to any recipient")
        RETURN failure("No recipients received the gloomy mail")

FUNCTION generateGloomyContent():
    RETURN "Dear Friend,\n\nWe understand that not every day is bright, and that's okay.\n\nRemember that difficult times don't last, but resilient people do. You're not alone.\n\nTake care of yourself,\nThe Thoughtful Mail Team"
```

### Notes
- Both processors handle partial success scenarios (some emails sent, some failed) as overall success
- Email validation is performed for each recipient
- Appropriate logging is implemented for monitoring and debugging
- Content is generated with sensitivity for the gloomy theme
- Processors do not directly modify entity state - this is handled by workflow transitions
