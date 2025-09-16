# Criteria

## Overview
This document defines the criteria for the Weekly Cat Fact Subscription application. Criteria implement conditional logic that determines whether workflow transitions should occur.

## Subscriber Criteria

### 1. SubscriberSuspensionCriterion

**Entity**: Subscriber
**Transition**: suspend (active → suspended)
**Purpose**: Determine if a subscriber should be suspended due to delivery issues

**Validation Logic**:
```
check(subscriber):
    if subscriber.bounceCount >= 3:
        return true (suspend due to hard bounces)
    
    if subscriber.complaintCount >= 1:
        return true (suspend due to spam complaints)
    
    if subscriber.lastDeliveryDate < (currentDate - 30 days):
        and subscriber.deliveryAttempts >= 5:
        return true (suspend due to persistent delivery failures)
    
    return false (do not suspend)
```

**Failure Reasons**:
- "Multiple hard bounces detected"
- "Spam complaint received"
- "Persistent delivery failures"

### 2. SubscriberVerificationExpirationCriterion

**Entity**: Subscriber
**Transition**: expire_verification (pending_verification → unsubscribed)
**Purpose**: Check if email verification has expired

**Validation Logic**:
```
check(subscriber):
    verificationAge = currentTime - subscriber.subscriptionDate
    
    if verificationAge > 24 hours:
        return true (verification expired)
    
    return false (verification still valid)
```

**Failure Reasons**:
- "Email verification expired after 24 hours"

## CatFact Criteria

### 3. CatFactValidationCriterion

**Entity**: CatFact
**Transition**: validate (retrieved → validated)
**Purpose**: Validate cat fact content quality and appropriateness

**Validation Logic**:
```
check(catFact):
    if catFact.factText == null or catFact.factText.trim().isEmpty():
        return false (empty fact text)
    
    if catFact.length < 10:
        return false (fact too short)
    
    if catFact.length > 500:
        return false (fact too long)
    
    if containsProfanity(catFact.factText):
        return false (inappropriate content)
    
    if containsInvalidCharacters(catFact.factText):
        return false (invalid characters)
    
    if isDuplicate(catFact.factText):
        return false (duplicate fact)
    
    return true (fact is valid)
```

**Failure Reasons**:
- "Fact text is empty"
- "Fact text too short (minimum 10 characters)"
- "Fact text too long (maximum 500 characters)"
- "Inappropriate content detected"
- "Invalid characters in fact text"
- "Duplicate fact already exists"

### 4. CatFactRejectionCriterion

**Entity**: CatFact
**Transition**: reject (validated → archived)
**Purpose**: Determine if a validated fact should be rejected for business reasons

**Validation Logic**:
```
check(catFact):
    if catFact.category == "medical" and not isVerifiedMedicalFact(catFact):
        return true (reject unverified medical claims)
    
    if containsControversialContent(catFact.factText):
        return true (reject controversial content)
    
    if catFact.source == "unreliable_source":
        return true (reject from unreliable sources)
    
    return false (do not reject)
```

**Failure Reasons**:
- "Unverified medical claim"
- "Controversial content detected"
- "Unreliable source"

## EmailCampaign Criteria

### 5. EmailCampaignPreparationCriterion

**Entity**: EmailCampaign
**Transition**: prepare (scheduled → preparing)
**Purpose**: Check if campaign is ready to be prepared

**Validation Logic**:
```
check(emailCampaign):
    if currentTime < emailCampaign.scheduledDate:
        return false (not yet time to prepare)
    
    if emailCampaign.catFactId == null:
        return false (no cat fact assigned)
    
    catFact = getCatFactById(emailCampaign.catFactId)
    if catFact.state != "ready":
        return false (cat fact not ready)
    
    activeSubscriberCount = getActiveSubscriberCount()
    if activeSubscriberCount == 0:
        return false (no active subscribers)
    
    return true (ready to prepare)
```

**Failure Reasons**:
- "Scheduled time not yet reached"
- "No cat fact assigned to campaign"
- "Cat fact is not in ready state"
- "No active subscribers found"

### 6. EmailCampaignFailureCriterion

**Entity**: EmailCampaign
**Transition**: fail (sending → failed)
**Purpose**: Determine if a campaign should be marked as failed

**Validation Logic**:
```
check(emailCampaign):
    totalEmails = emailCampaign.totalSubscribers
    failedEmails = emailCampaign.failedDeliveries
    
    if totalEmails == 0:
        return true (no emails to send)
    
    failureRate = failedEmails / totalEmails
    
    if failureRate >= 0.5:
        return true (failure rate too high - 50% or more failed)
    
    if emailCampaign.sendDuration > 2 hours:
        return true (sending took too long)
    
    if hasSystemError(emailCampaign):
        return true (system error occurred)
    
    return false (campaign not failed)
```

**Failure Reasons**:
- "No emails to send"
- "High failure rate (50% or more failed)"
- "Sending duration exceeded 2 hours"
- "System error during sending"

## Utility Criteria

### 7. SystemHealthCriterion

**Purpose**: General system health check for various transitions
**Used by**: Multiple workflows as needed

**Validation Logic**:
```
check(entity):
    if isMaintenanceMode():
        return false (system in maintenance)
    
    if isDatabaseConnectionHealthy():
        return false (database connection issues)
    
    if isEmailServiceHealthy():
        return false (email service unavailable)
    
    if isExternalApiHealthy("catfact.ninja"):
        return false (cat fact API unavailable)
    
    return true (system healthy)
```

**Failure Reasons**:
- "System in maintenance mode"
- "Database connection issues"
- "Email service unavailable"
- "External API unavailable"

### 8. BusinessHoursCriterion

**Purpose**: Check if current time is within business hours for certain operations
**Used by**: Email sending and other time-sensitive operations

**Validation Logic**:
```
check(entity):
    currentHour = getCurrentHour()
    currentDay = getCurrentDayOfWeek()
    
    if currentDay == SATURDAY or currentDay == SUNDAY:
        return false (weekend - no business operations)
    
    if currentHour < 9 or currentHour > 17:
        return false (outside business hours 9 AM - 5 PM)
    
    return true (within business hours)
```

**Failure Reasons**:
- "Weekend - no business operations"
- "Outside business hours (9 AM - 5 PM)"

## Rate Limiting Criteria

### 9. EmailRateLimitCriterion

**Purpose**: Ensure email sending doesn't exceed rate limits
**Used by**: Email campaign sending

**Validation Logic**:
```
check(emailCampaign):
    emailsSentInLastHour = getEmailsSentInLastHour()
    emailsSentInLastDay = getEmailsSentInLastDay()
    
    if emailsSentInLastHour >= 1000:
        return false (hourly rate limit exceeded)
    
    if emailsSentInLastDay >= 10000:
        return false (daily rate limit exceeded)
    
    return true (within rate limits)
```

**Failure Reasons**:
- "Hourly email rate limit exceeded (1000/hour)"
- "Daily email rate limit exceeded (10000/day)"

### 10. ApiRateLimitCriterion

**Purpose**: Ensure API calls don't exceed rate limits
**Used by**: Cat fact retrieval

**Validation Logic**:
```
check(catFact):
    apiCallsInLastMinute = getApiCallsInLastMinute("catfact.ninja")
    apiCallsInLastHour = getApiCallsInLastHour("catfact.ninja")
    
    if apiCallsInLastMinute >= 10:
        return false (per-minute rate limit exceeded)
    
    if apiCallsInLastHour >= 100:
        return false (hourly rate limit exceeded)
    
    return true (within API rate limits)
```

**Failure Reasons**:
- "API rate limit exceeded (10 calls/minute)"
- "API rate limit exceeded (100 calls/hour)"

## Notes

1. **Error Handling**: All criteria should handle exceptions gracefully and return false (do not proceed) if validation cannot be completed.

2. **Logging**: Criteria should log their decisions, especially when returning false, to aid in debugging and monitoring.

3. **Performance**: Criteria should be lightweight and fast, as they may be evaluated frequently.

4. **Caching**: Consider caching results for expensive validations that don't change frequently (e.g., system health checks).

5. **Configuration**: Rate limits and thresholds should be configurable through application properties.
