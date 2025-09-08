# Criteria Specification

## Overview
This document defines all criteria required for the Weekly Cat Fact Subscription application. Criteria are pure functions that evaluate conditions without side effects to determine workflow transitions.

## Subscriber Criteria

### 1. SubscriberValidationCriterion
**Entity**: Subscriber  
**Purpose**: Validate subscriber data and eligibility for activation/reactivation  
**Input**: Subscriber entity  
**Output**: Boolean (true if valid, false otherwise)  

**Validation Logic**:
```
check(subscriber):
    if subscriber.email is null or empty:
        return false
    if subscriber.email format is invalid:
        return false
    if subscriber.unsubscribeToken is null or empty:
        return false
    if subscriber.subscriptionDate is in the future:
        return false
    return true
```

**Use Cases**:
- PENDING → ACTIVE transition
- BOUNCED → ACTIVE transition  
- UNSUBSCRIBED → ACTIVE transition

### 2. EmailBounceCriterion
**Entity**: Subscriber  
**Purpose**: Determine if email delivery failure should trigger bounce state  
**Input**: Subscriber entity with bounce information  
**Output**: Boolean (true if should bounce, false otherwise)  

**Validation Logic**:
```
check(subscriber):
    if bounce type is "hard bounce":
        return true
    if bounce type is "soft bounce" and bounce count > 3:
        return true
    if subscriber.isActive is false:
        return true
    return false
```

**Use Cases**:
- ACTIVE → BOUNCED transition

## CatFact Criteria

### 3. CatFactValidityCriterion
**Entity**: CatFact  
**Purpose**: Validate cat fact content and readiness for use  
**Input**: CatFact entity  
**Output**: Boolean (true if valid, false otherwise)  

**Validation Logic**:
```
check(catFact):
    if catFact.text is null or empty:
        return false
    if catFact.text.length() != catFact.length:
        return false
    if catFact.text.length() < 10 or catFact.text.length() > 500:
        return false
    if catFact.retrievedDate is in the future:
        return false
    if contains inappropriate content:
        return false
    return true
```

**Use Cases**:
- RETRIEVED → READY transition

### 4. CatFactReuseCriterion
**Entity**: CatFact  
**Purpose**: Determine if cat fact can be reused in another campaign  
**Input**: CatFact entity  
**Output**: Boolean (true if can reuse, false otherwise)  

**Validation Logic**:
```
check(catFact):
    if catFact.usageCount >= 5:
        return false
    if catFact.lastUsedDate is within last 30 days:
        return false
    if catFact.text.length() < 50:
        return false
    return true
```

**Use Cases**:
- USED → USED transition (reuse)

### 5. CatFactArchiveCriterion
**Entity**: CatFact  
**Purpose**: Determine if cat fact should be archived  
**Input**: CatFact entity  
**Output**: Boolean (true if should archive, false otherwise)  

**Validation Logic**:
```
check(catFact):
    if catFact.usageCount >= 10:
        return true
    if catFact.lastUsedDate is older than 365 days:
        return true
    if catFact.text contains outdated information:
        return true
    return false
```

**Use Cases**:
- USED → ARCHIVED transition

## EmailCampaign Criteria

### 6. EmailCampaignReadyCriterion
**Entity**: EmailCampaign  
**Purpose**: Validate campaign is ready to be sent  
**Input**: EmailCampaign entity  
**Output**: Boolean (true if ready, false otherwise)  

**Validation Logic**:
```
check(campaign):
    if campaign.scheduledDate is in the future:
        return false
    if campaign.catFactId is null or invalid:
        return false
    if campaign.totalSubscribers <= 0:
        return false
    if campaign.emailSubject is null or empty:
        return false
    if campaign.emailTemplate is null or empty:
        return false
    if referenced CatFact is not in READY or USED state:
        return false
    return true
```

**Use Cases**:
- SCHEDULED → SENDING transition

### 7. EmailCampaignSuccessCriterion
**Entity**: EmailCampaign  
**Purpose**: Determine if campaign sending was successful  
**Input**: EmailCampaign entity  
**Output**: Boolean (true if successful, false otherwise)  

**Validation Logic**:
```
check(campaign):
    if campaign.actualSentDate is null:
        return false
    total_attempts = campaign.successfulDeliveries + campaign.failedDeliveries
    if total_attempts == 0:
        return false
    success_rate = campaign.successfulDeliveries / total_attempts
    if success_rate >= 0.8:
        return true
    return false
```

**Use Cases**:
- SENDING → SENT transition

### 8. EmailCampaignFailureCriterion
**Entity**: EmailCampaign  
**Purpose**: Determine if campaign sending failed  
**Input**: EmailCampaign entity  
**Output**: Boolean (true if failed, false otherwise)  

**Validation Logic**:
```
check(campaign):
    total_attempts = campaign.successfulDeliveries + campaign.failedDeliveries
    if total_attempts == 0:
        return true
    success_rate = campaign.successfulDeliveries / total_attempts
    if success_rate < 0.5:
        return true
    if campaign.failedDeliveries > campaign.totalSubscribers * 0.8:
        return true
    return false
```

**Use Cases**:
- SENDING → FAILED transition

### 9. EmailCampaignRetryCriterion
**Entity**: EmailCampaign  
**Purpose**: Determine if failed campaign can be retried  
**Input**: EmailCampaign entity  
**Output**: Boolean (true if can retry, false otherwise)  

**Validation Logic**:
```
check(campaign):
    if campaign.retryCount >= 3:
        return false
    if campaign.scheduledDate is older than 7 days:
        return false
    if campaign.failedDeliveries == campaign.totalSubscribers:
        return false
    if system resources are insufficient:
        return false
    return true
```

**Use Cases**:
- FAILED → SCHEDULED transition

## Criteria Design Principles

### Pure Functions
- All criteria are stateless and have no side effects
- They only evaluate conditions based on input data
- No database modifications or external API calls
- Deterministic results for same input

### Validation Rules
- Input validation is performed before business logic
- Clear boolean return values with no exceptions
- Comprehensive logging for debugging purposes
- Fail-safe defaults (return false when uncertain)

### Performance Considerations
- Lightweight operations only
- No complex calculations or heavy processing
- Quick evaluation for workflow efficiency
- Minimal memory usage

### Error Handling
- Graceful handling of null or invalid inputs
- Default to false for safety
- Comprehensive logging for troubleshooting
- No exceptions thrown from criteria methods

## Integration with Workflows

### Transition Guards
- Criteria act as guards for state transitions
- Only allow transitions when conditions are met
- Prevent invalid state changes
- Ensure data integrity throughout workflows

### Business Rule Enforcement
- Encode business rules as criteria
- Centralize validation logic
- Maintain consistency across the application
- Enable easy modification of business rules
