# Criteria Requirements

## Overview
Criteria are pure functions that evaluate conditions to determine workflow transitions. They do not modify entities or have side effects, only evaluate boolean conditions.

## MailIsHappyCriterion

### Entity
Mail

### Purpose
Determines if a mail entity should be routed to happy mail processing based on the `isHappy` attribute.

### Expected Input Data
- Mail entity with `isHappy` attribute
- Entity must be in `PENDING` state

### Evaluation Logic (Pseudocode)
```
FUNCTION check(mailEntity):
    // Simple boolean check
    IF mailEntity.isHappy == true THEN
        RETURN true
    ELSE
        RETURN false
    END IF
END FUNCTION
```

### Expected Output
- Returns `true` if the mail should be processed as happy mail
- Returns `false` if the mail should not be processed as happy mail

### Usage
- Used in workflow transition from `PENDING` to `HAPPY_PROCESSING`
- Enables automatic routing based on mail content type

### Validation
- No side effects or entity modifications
- Pure function that only reads entity data

## MailIsGloomyCriterion

### Entity
Mail

### Purpose
Determines if a mail entity should be routed to gloomy mail processing based on the `isHappy` attribute.

### Expected Input Data
- Mail entity with `isHappy` attribute
- Entity must be in `PENDING` state

### Evaluation Logic (Pseudocode)
```
FUNCTION check(mailEntity):
    // Simple boolean check - opposite of happy
    IF mailEntity.isHappy == false THEN
        RETURN true
    ELSE
        RETURN false
    END IF
END FUNCTION
```

### Expected Output
- Returns `true` if the mail should be processed as gloomy mail
- Returns `false` if the mail should not be processed as gloomy mail

### Usage
- Used in workflow transition from `PENDING` to `GLOOMY_PROCESSING`
- Enables automatic routing based on mail content type

### Validation
- No side effects or entity modifications
- Pure function that only reads entity data

## Business Rules
1. Both criteria are mutually exclusive - a mail cannot be both happy and gloomy
2. The criteria ensure proper routing in the workflow based on the `isHappy` attribute
3. These criteria are simple boolean evaluations without complex logic
4. No external dependencies or side effects are allowed in criteria evaluation
