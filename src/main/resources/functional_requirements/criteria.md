# Criteria Requirements

## EggAlarmTimerCriterion

### Entity
EggAlarm

### Purpose
Check if the cooking time has elapsed for an active egg alarm to determine if it should automatically transition to COMPLETED state.

### Expected Input Data
- EggAlarm entity in ACTIVE state with:
  - startedAt timestamp
  - cookingTimeMinutes value
  - Current system timestamp

### Evaluation Logic (Pseudocode)
```
CHECK EggAlarmTimerCriterion:
  INPUT: EggAlarm entity in ACTIVE state
  
  VALIDATE input entity:
    - Ensure entity is in ACTIVE state
    - Ensure startedAt is not null
    - Ensure cookingTimeMinutes is positive
  
  CALCULATE elapsedTimeMinutes = (current timestamp - startedAt) in minutes
  
  IF elapsedTimeMinutes >= cookingTimeMinutes:
    RETURN true (timer has expired, ready to complete)
  ELSE:
    RETURN false (still cooking, not ready to complete)
```

### Return Value
- **true**: Cooking time has elapsed, alarm should complete
- **false**: Still cooking, alarm should remain active

### Side Effects
None - this is a pure function that only evaluates the condition without modifying any data.

### Business Rules
1. Only evaluates entities in ACTIVE state
2. Compares elapsed time against the required cooking time
3. Uses precise timestamp calculations for accuracy
4. No side effects or entity modifications
5. Supports automatic transition triggering when condition is met

### Error Handling
- If startedAt is null, return false
- If cookingTimeMinutes is not positive, return false  
- If entity is not in ACTIVE state, return false
- Handle any timestamp calculation errors gracefully

### Usage Context
This criterion is used in the workflow transition from ACTIVE to COMPLETED state to automatically complete the alarm when the cooking time has elapsed. It works in conjunction with EggAlarmCompletionProcessor to provide seamless automatic completion of egg alarms.
