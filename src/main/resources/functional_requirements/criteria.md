# Criteria

## MailIsHappyCriterion

### Description
Determines whether a mail entity should be processed as happy mail by evaluating the `isHappy` field. This criterion is used in the workflow to route mail to the appropriate happy mail processing path.

### Entity
- **Belongs to**: Mail entity
- **Used in workflow transition**: `process_happy_mail`

### Evaluation Logic
The criterion checks if the mail entity has the `isHappy` field set to `true`.

### Input Data
- **Mail entity** with attributes:
  - `isHappy`: Boolean
  - `mailList`: List<String>

### Expected Behavior
- **Returns true**: When `isHappy` field is explicitly set to `true`
- **Returns false**: When `isHappy` field is `false`, `null`, or undefined

### Business Rules
1. **Explicit Happy Flag**: Only mails explicitly marked as happy (isHappy = true) should be processed by the happy mail processor
2. **Null Safety**: Handle cases where isHappy field might be null or undefined
3. **Type Safety**: Ensure the field is actually a boolean value

### Pseudocode
```
FUNCTION check(mailEntity):
    IF mailEntity is null:
        RETURN false
    
    IF mailEntity.isHappy is null:
        RETURN false
    
    IF mailEntity.isHappy is not boolean type:
        RETURN false
    
    RETURN mailEntity.isHappy == true
```

### Usage in Workflow
- **Transition**: `process_happy_mail`
- **From State**: `pending`
- **To State**: `processing_happy`
- **Purpose**: Ensures only happy mails are processed by the MailSendHappyMailProcessor

---

## MailIsGloomyCriterion

### Description
Determines whether a mail entity should be processed as gloomy mail by evaluating the `isHappy` field. This criterion is used in the workflow to route mail to the appropriate gloomy mail processing path.

### Entity
- **Belongs to**: Mail entity
- **Used in workflow transition**: `process_gloomy_mail`

### Evaluation Logic
The criterion checks if the mail entity has the `isHappy` field set to `false`.

### Input Data
- **Mail entity** with attributes:
  - `isHappy`: Boolean
  - `mailList`: List<String>

### Expected Behavior
- **Returns true**: When `isHappy` field is explicitly set to `false`
- **Returns false**: When `isHappy` field is `true`, `null`, or undefined

### Business Rules
1. **Explicit Gloomy Flag**: Only mails explicitly marked as gloomy (isHappy = false) should be processed by the gloomy mail processor
2. **Null Safety**: Handle cases where isHappy field might be null or undefined
3. **Type Safety**: Ensure the field is actually a boolean value
4. **Mutual Exclusivity**: A mail cannot be both happy and gloomy

### Pseudocode
```
FUNCTION check(mailEntity):
    IF mailEntity is null:
        RETURN false
    
    IF mailEntity.isHappy is null:
        RETURN false
    
    IF mailEntity.isHappy is not boolean type:
        RETURN false
    
    RETURN mailEntity.isHappy == false
```

### Usage in Workflow
- **Transition**: `process_gloomy_mail`
- **From State**: `pending`
- **To State**: `processing_gloomy`
- **Purpose**: Ensures only gloomy mails are processed by the MailSendGloomyMailProcessor

---

## Criteria Interaction

### Mutual Exclusivity
The two criteria are designed to be mutually exclusive:
- If `MailIsHappyCriterion` returns `true`, then `MailIsGloomyCriterion` will return `false`
- If `MailIsGloomyCriterion` returns `true`, then `MailIsHappyCriterion` will return `false`
- If both return `false`, the mail entity has invalid or missing `isHappy` field

### Error Handling
If neither criterion evaluates to `true` (e.g., when `isHappy` is null or invalid):
- The mail will remain in `pending` state
- No automatic transition will occur
- Manual intervention may be required to correct the mail entity data

### Validation Rules
Both criteria implement the same validation logic:
1. **Null Entity Check**: Verify the mail entity exists
2. **Null Field Check**: Verify the isHappy field is not null
3. **Type Check**: Verify the isHappy field is a boolean
4. **Value Check**: Check the specific boolean value (true for happy, false for gloomy)

### Notes
- These criteria are simple and focused on a single field evaluation
- They provide clear routing logic for the mail processing workflow
- Error cases are handled gracefully by returning false
- The criteria ensure data integrity before processing begins
