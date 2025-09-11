# Criteria Requirements

## Overview
Criteria are pure functions that evaluate conditions to determine workflow transitions. They must not have side effects and should only evaluate the current state of the entity to make routing decisions.

## MailIsHappyCriterion

### Entity
`Mail`

### Purpose
Determines if a mail entity should be processed as a happy mail by evaluating the `isHappy` attribute.

### Input Data
- **Entity**: Mail entity with `isHappy` and `mailList` attributes
- **Entity State**: PENDING
- **Metadata**: Entity metadata including UUID and current state

### Evaluation Logic
Checks if the mail entity's `isHappy` attribute is set to `true`.

### Expected Output
- **Boolean**: `true` if the mail should be processed as happy mail, `false` otherwise

### Validation Rules
1. The `isHappy` attribute must not be null
2. The entity must be in PENDING state
3. The `mailList` must not be empty (basic validation)

### Pseudocode
```
FUNCTION check(mailEntity):
    BEGIN
        // Validate entity state
        IF mailEntity.meta.state != "PENDING" THEN
            RETURN false
        END IF
        
        // Validate required attributes
        IF mailEntity.isHappy IS NULL THEN
            LOG "Warning: isHappy attribute is null for mail entity " + mailEntity.meta.uuid
            RETURN false
        END IF
        
        IF mailEntity.mailList IS NULL OR mailEntity.mailList.isEmpty() THEN
            LOG "Warning: mailList is empty for mail entity " + mailEntity.meta.uuid
            RETURN false
        END IF
        
        // Main evaluation logic
        result = mailEntity.isHappy == true
        
        LOG "MailIsHappyCriterion evaluation for entity " + mailEntity.meta.uuid + ": " + result
        
        RETURN result
    END
```

### Business Rules
- Only evaluates to `true` when `isHappy` is explicitly set to `true`
- Returns `false` for any null, undefined, or false values
- Performs basic validation to ensure entity is in correct state for evaluation

## MailIsGloomyCriterion

### Entity
`Mail`

### Purpose
Determines if a mail entity should be processed as a gloomy mail by evaluating the `isHappy` attribute.

### Input Data
- **Entity**: Mail entity with `isHappy` and `mailList` attributes
- **Entity State**: PENDING
- **Metadata**: Entity metadata including UUID and current state

### Evaluation Logic
Checks if the mail entity's `isHappy` attribute is set to `false`.

### Expected Output
- **Boolean**: `true` if the mail should be processed as gloomy mail, `false` otherwise

### Validation Rules
1. The `isHappy` attribute must not be null
2. The entity must be in PENDING state
3. The `mailList` must not be empty (basic validation)

### Pseudocode
```
FUNCTION check(mailEntity):
    BEGIN
        // Validate entity state
        IF mailEntity.meta.state != "PENDING" THEN
            RETURN false
        END IF
        
        // Validate required attributes
        IF mailEntity.isHappy IS NULL THEN
            LOG "Warning: isHappy attribute is null for mail entity " + mailEntity.meta.uuid
            RETURN false
        END IF
        
        IF mailEntity.mailList IS NULL OR mailEntity.mailList.isEmpty() THEN
            LOG "Warning: mailList is empty for mail entity " + mailEntity.meta.uuid
            RETURN false
        END IF
        
        // Main evaluation logic
        result = mailEntity.isHappy == false
        
        LOG "MailIsGloomyCriterion evaluation for entity " + mailEntity.meta.uuid + ": " + result
        
        RETURN result
    END
```

### Business Rules
- Only evaluates to `true` when `isHappy` is explicitly set to `false`
- Returns `false` for any null, undefined, or true values
- Performs basic validation to ensure entity is in correct state for evaluation

## Criteria Design Principles

### Mutual Exclusivity
The two criteria are designed to be mutually exclusive:
- `MailIsHappyCriterion` returns `true` only when `isHappy == true`
- `MailIsGloomyCriterion` returns `true` only when `isHappy == false`
- Both return `false` when `isHappy` is null or entity is invalid

### State Validation
Both criteria validate that:
1. The entity is in the correct state (PENDING) for evaluation
2. Required attributes are present and valid
3. Basic business rules are satisfied

### Error Handling
- Criteria should never throw exceptions
- Invalid conditions should return `false` and log warnings
- Null or missing attributes should be handled gracefully

### Logging Requirements
- Log the evaluation result for each criterion check
- Log warnings for invalid entity states or missing attributes
- Include entity UUID in all log messages for traceability

### Performance Considerations
- Criteria should be lightweight and fast
- No external service calls or heavy computations
- Simple boolean logic based on entity attributes only

### Workflow Integration
These criteria work together in the workflow:
1. From PENDING state, both criteria are evaluated
2. If `MailIsHappyCriterion` returns `true` → transition to HAPPY_PROCESSING
3. If `MailIsGloomyCriterion` returns `true` → transition to GLOOMY_PROCESSING
4. If both return `false` → entity remains in PENDING state (error condition)
