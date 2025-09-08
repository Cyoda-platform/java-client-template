# Processors Specification

## Overview
This document defines all processors required for the Weekly Cat Fact Subscription application. Each processor implements specific business logic for entity state transitions.

## Subscriber Processors

### 1. SubscriberRegistrationProcessor
**Entity**: Subscriber  
**Input**: New subscriber data (email, firstName, lastName)  
**Purpose**: Handle new subscriber registration  
**Output**: Subscriber entity in PENDING state  

**Pseudocode**:
```
process(subscriberData):
    validate email format and uniqueness
    generate unique unsubscribe token
    set subscription date to current timestamp
    set isActive to true
    set totalEmailsReceived to 0
    create subscriber entity with PENDING state
    send confirmation email with activation link
    return updated subscriber entity
```

### 2. SubscriberActivationProcessor
**Entity**: Subscriber  
**Input**: Subscriber entity in PENDING state  
**Purpose**: Activate confirmed subscriber  
**Output**: Subscriber entity in ACTIVE state  

**Pseudocode**:
```
process(subscriber):
    verify subscriber is in PENDING state
    update isActive to true
    log activation timestamp
    send welcome email with subscription details
    return subscriber entity (state will transition to ACTIVE)
```

### 3. SubscriberUnsubscribeProcessor
**Entity**: Subscriber  
**Input**: Subscriber entity in ACTIVE state  
**Purpose**: Handle subscriber unsubscription  
**Output**: Subscriber entity in UNSUBSCRIBED state  

**Pseudocode**:
```
process(subscriber):
    verify subscriber is in ACTIVE state
    set isActive to false
    log unsubscribe timestamp
    send unsubscribe confirmation email
    return subscriber entity (state will transition to UNSUBSCRIBED)
```

### 4. SubscriberBounceProcessor
**Entity**: Subscriber  
**Input**: Subscriber entity in ACTIVE state with bounce notification  
**Purpose**: Handle email delivery failures  
**Output**: Subscriber entity in BOUNCED state  

**Pseudocode**:
```
process(subscriber):
    verify subscriber is in ACTIVE state
    increment bounce counter
    log bounce timestamp and reason
    if bounce count exceeds threshold:
        set isActive to false
    return subscriber entity (state will transition to BOUNCED)
```

### 5. SubscriberReactivationProcessor
**Entity**: Subscriber  
**Input**: Subscriber entity in BOUNCED state  
**Purpose**: Reactivate bounced subscriber  
**Output**: Subscriber entity in ACTIVE state  

**Pseudocode**:
```
process(subscriber):
    verify subscriber is in BOUNCED state
    reset bounce counter
    set isActive to true
    log reactivation timestamp
    send reactivation confirmation email
    return subscriber entity (state will transition to ACTIVE)
```

### 6. SubscriberResubscribeProcessor
**Entity**: Subscriber  
**Input**: Subscriber entity in UNSUBSCRIBED state  
**Purpose**: Handle subscriber resubscription  
**Output**: Subscriber entity in ACTIVE state  

**Pseudocode**:
```
process(subscriber):
    verify subscriber is in UNSUBSCRIBED state
    set isActive to true
    update subscription date to current timestamp
    generate new unsubscribe token
    send resubscription confirmation email
    return subscriber entity (state will transition to ACTIVE)
```

## CatFact Processors

### 7. CatFactIngestionProcessor
**Entity**: CatFact  
**Input**: Empty or trigger data  
**Purpose**: Retrieve cat facts from external API  
**Output**: CatFact entity in RETRIEVED state  

**Pseudocode**:
```
process(triggerData):
    call Cat Fact API (https://catfact.ninja/fact)
    extract fact text and length from API response
    generate unique factId
    set retrievedDate to current timestamp
    set source to "catfact.ninja"
    set isUsed to false
    set usageCount to 0
    create CatFact entity
    return cat fact entity (state will transition to RETRIEVED)
```

### 8. CatFactValidationProcessor
**Entity**: CatFact  
**Input**: CatFact entity in RETRIEVED state  
**Purpose**: Validate and prepare cat fact for use  
**Output**: CatFact entity in READY state  

**Pseudocode**:
```
process(catFact):
    verify fact text is not empty
    verify length matches actual text length
    check for inappropriate content (basic filtering)
    verify fact is not duplicate of existing facts
    log validation timestamp
    return cat fact entity (state will transition to READY)
```

### 9. CatFactUsageProcessor
**Entity**: CatFact  
**Input**: CatFact entity in READY state  
**Purpose**: Mark cat fact as used in campaign  
**Output**: CatFact entity in USED state  

**Pseudocode**:
```
process(catFact):
    verify fact is in READY state
    increment usageCount by 1
    set lastUsedDate to current timestamp
    set isUsed to true
    log usage in campaign tracking
    return cat fact entity (state will transition to USED)
```

### 10. CatFactReuseProcessor
**Entity**: CatFact  
**Input**: CatFact entity in USED state  
**Purpose**: Handle reuse of cat fact in new campaign  
**Output**: CatFact entity remains in USED state  

**Pseudocode**:
```
process(catFact):
    verify fact is in USED state
    increment usageCount by 1
    update lastUsedDate to current timestamp
    log reuse in campaign tracking
    return cat fact entity (state remains USED)
```

### 11. CatFactArchiveProcessor
**Entity**: CatFact
**Input**: CatFact entity in USED state
**Purpose**: Archive overused cat facts
**Output**: CatFact entity in ARCHIVED state

**Pseudocode**:
```
process(catFact):
    verify fact is in USED state
    verify usage count exceeds archive threshold
    log archive timestamp
    mark fact as archived in system
    return cat fact entity (state will transition to ARCHIVED)
```

## EmailCampaign Processors

### 12. EmailCampaignScheduleProcessor
**Entity**: EmailCampaign
**Input**: Campaign scheduling data (catFactId, scheduledDate)
**Purpose**: Create and schedule new email campaign
**Output**: EmailCampaign entity in PENDING state

**Pseudocode**:
```
process(campaignData):
    generate unique campaignId
    verify catFactId references valid CatFact
    set scheduledDate from input
    generate campaign name with date
    count active subscribers for totalSubscribers
    set all delivery counters to 0
    create email subject and template
    create EmailCampaign entity
    return campaign entity (state will transition to PENDING)
```

### 13. EmailCampaignSendProcessor
**Entity**: EmailCampaign
**Input**: EmailCampaign entity in PENDING state
**Purpose**: Execute email campaign sending
**Output**: EmailCampaign entity in SENT state
**Other Entity Updates**: Updates Subscriber entities (null transition)

**Pseudocode**:
```
process(campaign):
    verify campaign is in PENDING state
    retrieve cat fact using catFactId
    get all active subscribers
    set actualSentDate to current timestamp
    for each active subscriber:
        prepare personalized email content
        attempt to send email
        if successful:
            increment successfulDeliveries
            update subscriber.lastEmailSent
            increment subscriber.totalEmailsReceived
        else:
            increment failedDeliveries
            trigger SubscriberBounceProcessor if needed
    return campaign entity (state will transition to SENT)
```

### 14. EmailCampaignCompleteProcessor
**Entity**: EmailCampaign
**Input**: EmailCampaign entity in SENT state
**Purpose**: Complete successful email campaign
**Output**: EmailCampaign entity in DELIVERED state
**Other Entity Updates**: Updates CatFact entity (CatFactUsageProcessor transition)

**Pseudocode**:
```
process(campaign):
    verify campaign is in SENT state
    finalize delivery statistics
    calculate success rate
    log campaign completion
    trigger CatFactUsageProcessor for used cat fact
    generate campaign report
    return campaign entity (state will transition to DELIVERED)
```

### 15. EmailCampaignFailProcessor
**Entity**: EmailCampaign
**Input**: EmailCampaign entity in SENT state
**Purpose**: Handle failed email campaign
**Output**: EmailCampaign entity in FAILED state

**Pseudocode**:
```
process(campaign):
    verify campaign is in SENT state
    log failure reason and timestamp
    calculate partial delivery statistics
    notify administrators of failure
    generate failure report
    return campaign entity (state will transition to FAILED)
```

### 16. EmailCampaignCancelProcessor
**Entity**: EmailCampaign
**Input**: EmailCampaign entity in PENDING state
**Purpose**: Cancel scheduled email campaign
**Output**: EmailCampaign entity in CANCELLED state

**Pseudocode**:
```
process(campaign):
    verify campaign is in PENDING state
    log cancellation reason and timestamp
    notify administrators of cancellation
    return campaign entity (state will transition to CANCELLED)
```

### 17. EmailCampaignRetryProcessor
**Entity**: EmailCampaign
**Input**: EmailCampaign entity in FAILED state
**Purpose**: Retry failed email campaign
**Output**: EmailCampaign entity in PENDING state

**Pseudocode**:
```
process(campaign):
    verify campaign is in FAILED state
    reset delivery counters
    update scheduledDate to new retry time
    log retry attempt
    return campaign entity (state will transition to PENDING)
```

## Cross-Entity Processor Interactions

### Weekly Automation Flow
1. **CatFactIngestionProcessor** runs weekly to fetch new facts
2. **EmailCampaignScheduleProcessor** creates weekly campaigns
3. **EmailCampaignSendProcessor** executes campaigns and updates subscribers
4. **EmailCampaignCompleteProcessor** finalizes campaigns and updates cat facts

### Error Handling
- All processors include comprehensive error logging
- Failed operations trigger appropriate error state transitions
- Retry mechanisms are built into critical processors
- Administrative notifications for system failures
