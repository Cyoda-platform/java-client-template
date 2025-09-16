# Processors Requirements

## EggAlarmCreationProcessor

### Entity
EggAlarm

### Purpose
Initialize a new egg alarm with the appropriate cooking time based on the selected egg type.

### Expected Input Data
- EggAlarm entity with:
  - eggType (SOFT_BOILED, MEDIUM_BOILED, or HARD_BOILED)
  - id
  - createdAt

### Business Logic (Pseudocode)
```
PROCESS EggAlarmCreationProcessor:
  INPUT: EggAlarm entity with eggType
  
  VALIDATE input entity:
    - Ensure eggType is valid (SOFT_BOILED, MEDIUM_BOILED, HARD_BOILED)
    - Ensure id is not null/empty
    - Ensure createdAt is set
  
  SET cookingTimeMinutes based on eggType:
    - IF eggType = "SOFT_BOILED" THEN cookingTimeMinutes = 4
    - IF eggType = "MEDIUM_BOILED" THEN cookingTimeMinutes = 6  
    - IF eggType = "HARD_BOILED" THEN cookingTimeMinutes = 8
  
  RETURN updated EggAlarm entity with cookingTimeMinutes set
```

### Expected Output
- Modified EggAlarm entity with cookingTimeMinutes populated
- Entity transitions to CREATED state

### Other Entity Updates
None - this processor only modifies the current EggAlarm entity.

---

## EggAlarmStartProcessor

### Entity
EggAlarm

### Purpose
Start the egg cooking timer by recording the start time.

### Expected Input Data
- EggAlarm entity in CREATED state with cookingTimeMinutes set

### Business Logic (Pseudocode)
```
PROCESS EggAlarmStartProcessor:
  INPUT: EggAlarm entity in CREATED state
  
  VALIDATE input entity:
    - Ensure entity is in CREATED state
    - Ensure cookingTimeMinutes is set and positive
  
  SET startedAt = current timestamp
  
  LOG "Egg alarm started for " + eggType + " cooking for " + cookingTimeMinutes + " minutes"
  
  RETURN updated EggAlarm entity with startedAt set
```

### Expected Output
- Modified EggAlarm entity with startedAt timestamp
- Entity transitions to ACTIVE state

### Other Entity Updates
None - this processor only modifies the current EggAlarm entity.

---

## EggAlarmCompletionProcessor

### Entity
EggAlarm

### Purpose
Complete the egg alarm when the cooking time has elapsed.

### Expected Input Data
- EggAlarm entity in ACTIVE state with startedAt and cookingTimeMinutes set

### Business Logic (Pseudocode)
```
PROCESS EggAlarmCompletionProcessor:
  INPUT: EggAlarm entity in ACTIVE state
  
  VALIDATE input entity:
    - Ensure entity is in ACTIVE state
    - Ensure startedAt is not null
    - Ensure cookingTimeMinutes is set
  
  SET completedAt = current timestamp
  
  CALCULATE actualCookingTime = completedAt - startedAt (in minutes)
  
  LOG "Egg alarm completed for " + eggType + " after " + actualCookingTime + " minutes"
  
  TRIGGER notification/alert that egg is ready
  
  RETURN updated EggAlarm entity with completedAt set
```

### Expected Output
- Modified EggAlarm entity with completedAt timestamp
- Entity transitions to COMPLETED state
- Notification/alert triggered

### Other Entity Updates
None - this processor only modifies the current EggAlarm entity.

---

## EggAlarmCancellationProcessor

### Entity
EggAlarm

### Purpose
Cancel an egg alarm either before it starts (CREATED state) or while it's running (ACTIVE state).

### Expected Input Data
- EggAlarm entity in either CREATED or ACTIVE state

### Business Logic (Pseudocode)
```
PROCESS EggAlarmCancellationProcessor:
  INPUT: EggAlarm entity in CREATED or ACTIVE state
  
  VALIDATE input entity:
    - Ensure entity is in CREATED or ACTIVE state
  
  IF entity is in ACTIVE state:
    CALCULATE partialCookingTime = current timestamp - startedAt (in minutes)
    LOG "Egg alarm cancelled for " + eggType + " after " + partialCookingTime + " minutes of cooking"
  ELSE:
    LOG "Egg alarm cancelled for " + eggType + " before starting"
  
  CLEAR any active timers or notifications
  
  RETURN entity (state will be managed by workflow to CANCELLED)
```

### Expected Output
- Entity transitions to CANCELLED state
- Any active timers or notifications are cleared
- Appropriate logging based on current state

### Other Entity Updates
None - this processor only modifies the current EggAlarm entity.
