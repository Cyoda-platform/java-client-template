# Processors

## Overview
This document defines the processors for the Weekly Cat Fact Subscription application. Each processor implements the business logic for workflow transitions.

## Subscriber Processors

### 1. SubscriberRegistrationProcessor

**Entity**: Subscriber
**Transition**: subscribe (none → pending_verification)
**Input**: Subscriber entity with email, firstName, lastName
**Output**: Subscriber entity with generated unsubscribeToken and subscriptionDate

**Pseudocode**:
```
process(subscriber):
    validate email format
    check if email already exists in system
    if email exists and is active:
        throw error "Email already subscribed"
    
    generate unique unsubscribeToken (UUID)
    set subscriptionDate to current timestamp
    set isActive to true
    set preferences to default values
    
    save subscriber to database
    send verification email with verification link
    
    return updated subscriber
```

### 2. SubscriberVerificationProcessor

**Entity**: Subscriber
**Transition**: verify_email (pending_verification → active)
**Input**: Subscriber entity with verification token
**Output**: Subscriber entity marked as verified

**Pseudocode**:
```
process(subscriber):
    validate verification token
    check if verification is not expired (within 24 hours)
    
    mark subscriber as verified
    update lastModified timestamp
    
    send welcome email with unsubscribe link
    log successful verification
    
    return updated subscriber
```

### 3. SubscriberUnsubscribeProcessor

**Entity**: Subscriber
**Transition**: unsubscribe (active → unsubscribed)
**Input**: Subscriber entity with unsubscribe request
**Output**: Subscriber entity marked as unsubscribed

**Pseudocode**:
```
process(subscriber):
    validate unsubscribe token
    set isActive to false
    set unsubscribeDate to current timestamp
    
    send unsubscribe confirmation email
    log unsubscribe event with reason (if provided)
    
    return updated subscriber
```

### 4. SubscriberSuspensionProcessor

**Entity**: Subscriber
**Transition**: suspend (active → suspended)
**Input**: Subscriber entity with suspension reason
**Output**: Subscriber entity marked as suspended

**Pseudocode**:
```
process(subscriber):
    set suspensionReason (bounce, complaint, etc.)
    set suspensionDate to current timestamp
    increment suspension count
    
    if suspension count > 3:
        transition to unsubscribed (null transition)
    
    log suspension event
    
    return updated subscriber
```

### 5. SubscriberReactivationProcessor

**Entity**: Subscriber
**Transition**: reactivate (suspended → active)
**Input**: Subscriber entity with reactivation request
**Output**: Subscriber entity reactivated

**Pseudocode**:
```
process(subscriber):
    clear suspensionReason
    clear suspensionDate
    set reactivationDate to current timestamp
    
    send reactivation confirmation email
    log reactivation event
    
    return updated subscriber
```

## CatFact Processors

### 6. CatFactRetrievalProcessor

**Entity**: CatFact
**Transition**: retrieve (none → retrieved)
**Input**: Empty CatFact entity
**Output**: CatFact entity with retrieved data

**Pseudocode**:
```
process(catFact):
    call Cat Fact API (https://catfact.ninja/fact)
    if API call fails:
        retry up to 3 times with exponential backoff
        if still fails, throw error
    
    extract fact text from API response
    set source to "catfact.ninja"
    set retrievedDate to current timestamp
    set length to fact text length
    set isUsed to false
    
    check for duplicate facts in database
    if duplicate exists:
        retrieve new fact (recursive call)
    
    return populated catFact
```

### 7. CatFactValidationProcessor

**Entity**: CatFact
**Transition**: validate (retrieved → validated)
**Input**: CatFact entity with retrieved data
**Output**: CatFact entity marked as validated

**Pseudocode**:
```
process(catFact):
    validate fact text is not empty
    validate fact length is between 10 and 500 characters
    check for inappropriate content (profanity filter)
    validate text encoding (UTF-8)
    
    if validation fails:
        mark as invalid and transition to archived (null transition)
        return catFact
    
    set validationDate to current timestamp
    set validationStatus to "passed"
    
    return validated catFact
```

### 8. CatFactApprovalProcessor

**Entity**: CatFact
**Transition**: approve (validated → ready)
**Input**: CatFact entity validated
**Output**: CatFact entity ready for use

**Pseudocode**:
```
process(catFact):
    set approvalDate to current timestamp
    set approvedBy to "system"
    
    add to ready facts pool
    log fact approval
    
    return approved catFact
```

### 9. CatFactUsageProcessor

**Entity**: CatFact
**Transition**: use (ready → used)
**Input**: CatFact entity being used in campaign
**Output**: CatFact entity marked as used

**Pseudocode**:
```
process(catFact):
    set isUsed to true
    set firstUsedDate to current timestamp
    increment usageCount
    
    log fact usage with campaign reference
    
    return updated catFact
```

### 10. CatFactArchiveProcessor

**Entity**: CatFact
**Transition**: archive (used → archived)
**Input**: CatFact entity to archive
**Output**: CatFact entity archived

**Pseudocode**:
```
process(catFact):
    set archiveDate to current timestamp
    set archiveReason (overused, outdated, etc.)
    
    remove from active facts pool
    log archival event
    
    return archived catFact
```

## EmailCampaign Processors

### 11. EmailCampaignScheduleProcessor

**Entity**: EmailCampaign
**Transition**: schedule (none → scheduled)
**Input**: EmailCampaign entity with basic info
**Output**: EmailCampaign entity scheduled

**Pseudocode**:
```
process(emailCampaign):
    validate campaign name is unique
    set scheduledDate to next weekly send time
    set campaignName to "Weekly Cat Facts - [date]"
    
    get random ready CatFact
    if no ready facts available:
        trigger CatFact retrieval workflow
        wait for ready fact or timeout
    
    set catFactId to selected fact
    count active subscribers
    set totalSubscribers count
    
    return scheduled campaign
```

### 12. EmailCampaignPreparationProcessor

**Entity**: EmailCampaign
**Transition**: prepare (scheduled → preparing)
**Input**: EmailCampaign entity scheduled
**Output**: EmailCampaign entity prepared

**Pseudocode**:
```
process(emailCampaign):
    get CatFact by catFactId
    get list of active subscribers
    
    prepare email template with cat fact content
    generate personalized emails for each subscriber
    validate email content and recipients
    
    update totalSubscribers with current count
    set preparationDate to current timestamp
    
    return prepared campaign
```

### 13. EmailCampaignSendProcessor

**Entity**: EmailCampaign
**Transition**: start_sending (preparing → sending)
**Input**: EmailCampaign entity prepared
**Output**: EmailCampaign entity being sent

**Pseudocode**:
```
process(emailCampaign):
    set sendStartTime to current timestamp
    initialize successfulDeliveries to 0
    initialize failedDeliveries to 0
    
    for each active subscriber:
        try:
            send personalized email
            increment successfulDeliveries
            log successful delivery
        catch email error:
            increment failedDeliveries
            log failed delivery with reason
            if subscriber email bounced:
                trigger subscriber suspension (null transition)
    
    transition CatFact to used state (null transition)
    
    return sending campaign
```

### 14. EmailCampaignCompletionProcessor

**Entity**: EmailCampaign
**Transition**: complete_sending (sending → sent)
**Input**: EmailCampaign entity being sent
**Output**: EmailCampaign entity sent

**Pseudocode**:
```
process(emailCampaign):
    set sentDate to current timestamp
    calculate send duration
    calculate delivery success rate
    
    log campaign completion statistics
    
    return completed campaign
```

### 15. EmailCampaignFinalizationProcessor

**Entity**: EmailCampaign
**Transition**: finalize (sent → completed)
**Input**: EmailCampaign entity sent
**Output**: EmailCampaign entity finalized

**Pseudocode**:
```
process(emailCampaign):
    wait for initial analytics (opens, clicks) - 1 hour delay
    
    collect open tracking data
    collect click tracking data
    collect unsubscribe data from this campaign
    
    set openCount, clickCount, unsubscribeCount
    calculate engagement metrics
    
    generate campaign report
    store analytics data
    
    return finalized campaign
```

### 16. EmailCampaignCancellationProcessor

**Entity**: EmailCampaign
**Transition**: cancel (scheduled → cancelled)
**Input**: EmailCampaign entity to cancel
**Output**: EmailCampaign entity cancelled

**Pseudocode**:
```
process(emailCampaign):
    set cancellationDate to current timestamp
    set cancellationReason
    
    release reserved CatFact back to ready state (null transition)
    log cancellation event
    
    return cancelled campaign
```

### 17. EmailCampaignRetryProcessor

**Entity**: EmailCampaign
**Transition**: retry (failed → preparing)
**Input**: EmailCampaign entity failed
**Output**: EmailCampaign entity ready for retry

**Pseudocode**:
```
process(emailCampaign):
    increment retryCount
    if retryCount > 3:
        transition to cancelled (null transition)
        return campaign
    
    reset delivery counters
    set retryDate to current timestamp
    
    analyze failure reasons
    exclude problematic subscribers if needed
    
    return campaign ready for retry
```
