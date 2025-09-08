# Processors Requirements

## Overview
This document defines the processors required for the Weekly Cat Fact Subscription application. Each processor implements specific business logic for entity state transitions.

## Subscriber Processors

### 1. SubscriberRegistrationProcessor

**Entity:** Subscriber  
**Transition:** INITIAL → PENDING

**Input Data:** 
- Email address (required)
- First name (optional)
- Last name (optional)
- Preferences (optional)

**Business Logic:**
```
1. Validate email format and uniqueness
2. Set subscription date to current timestamp
3. Set isActive to true
4. Generate unique subscriber ID
5. Initialize preferences if provided
6. Save subscriber entity
7. Send welcome/confirmation email (optional)
```

**Expected Output:** Subscriber entity in PENDING state with all required fields populated.

**Other Entity Updates:** None

---

### 2. SubscriberActivationProcessor

**Entity:** Subscriber  
**Transition:** PENDING → ACTIVE

**Input Data:** 
- Subscriber ID
- Confirmation token (optional)

**Business Logic:**
```
1. Validate subscriber exists and is in PENDING state
2. Verify confirmation token if provided
3. Update subscriber status to active
4. Log activation timestamp
5. Send activation confirmation email
```

**Expected Output:** Subscriber entity in ACTIVE state.

**Other Entity Updates:** None

---

### 3. SubscriberDeactivationProcessor

**Entity:** Subscriber  
**Transition:** ACTIVE → INACTIVE

**Input Data:** 
- Subscriber ID
- Reason for deactivation (optional)

**Business Logic:**
```
1. Validate subscriber exists and is in ACTIVE state
2. Set isActive to false
3. Log deactivation reason and timestamp
4. Send deactivation notification email
```

**Expected Output:** Subscriber entity in INACTIVE state with isActive = false.

**Other Entity Updates:** None

---

### 4. SubscriberReactivationProcessor

**Entity:** Subscriber  
**Transition:** INACTIVE → ACTIVE

**Input Data:** 
- Subscriber ID

**Business Logic:**
```
1. Validate subscriber exists and is in INACTIVE state
2. Set isActive to true
3. Log reactivation timestamp
4. Send reactivation confirmation email
```

**Expected Output:** Subscriber entity in ACTIVE state with isActive = true.

**Other Entity Updates:** None

---

### 5. SubscriberUnsubscribeProcessor

**Entity:** Subscriber  
**Transition:** PENDING/ACTIVE/INACTIVE → UNSUBSCRIBED

**Input Data:** 
- Subscriber ID
- Unsubscribe reason (optional)

**Business Logic:**
```
1. Validate subscriber exists
2. Set isActive to false
3. Log unsubscribe reason and timestamp
4. Send unsubscribe confirmation email
5. Remove from future email campaigns
```

**Expected Output:** Subscriber entity in UNSUBSCRIBED state with isActive = false.

**Other Entity Updates:** None

## CatFact Processors

### 6. CatFactRetrievalProcessor

**Entity:** CatFact  
**Transition:** INITIAL → RETRIEVED

**Input Data:** None (triggered by scheduled job)

**Business Logic:**
```
1. Call Cat Fact API (https://catfact.ninja/fact)
2. Parse API response to extract fact text
3. Calculate fact length
4. Set retrieved date to current timestamp
5. Set source to "catfact.ninja"
6. Set isUsed to false
7. Generate unique cat fact ID
8. Save cat fact entity
```

**Expected Output:** CatFact entity in RETRIEVED state with fact content from API.

**Other Entity Updates:** None

---

### 7. CatFactSchedulingProcessor

**Entity:** CatFact  
**Transition:** RETRIEVED → SCHEDULED

**Input Data:** 
- CatFact ID
- Scheduled date for campaign

**Business Logic:**
```
1. Validate cat fact exists and is in RETRIEVED state
2. Set scheduled date for the fact
3. Mark fact as scheduled for use
4. Log scheduling timestamp
```

**Expected Output:** CatFact entity in SCHEDULED state with scheduledDate set.

**Other Entity Updates:** None

---

### 8. CatFactArchivalProcessor

**Entity:** CatFact  
**Transition:** SENT → ARCHIVED

**Input Data:** 
- CatFact ID

**Business Logic:**
```
1. Validate cat fact exists and is in SENT state
2. Set isUsed to true
3. Log archival timestamp
4. Clean up any temporary data
```

**Expected Output:** CatFact entity in ARCHIVED state with isUsed = true.

**Other Entity Updates:** None

## EmailCampaign Processors

### 9. EmailCampaignCreationProcessor

**Entity:** EmailCampaign  
**Transition:** INITIAL → CREATED

**Input Data:** 
- Campaign name
- Scheduled date
- Campaign type (default: "WEEKLY")

**Business Logic:**
```
1. Generate unique campaign ID
2. Set campaign name and type
3. Set scheduled date
4. Initialize counters (totalSubscribers, successfulDeliveries, failedDeliveries) to 0
5. Save campaign entity
```

**Expected Output:** EmailCampaign entity in CREATED state.

**Other Entity Updates:** None

---

### 10. EmailCampaignSchedulingProcessor

**Entity:** EmailCampaign  
**Transition:** CREATED → SCHEDULED

**Input Data:** 
- Campaign ID
- CatFact ID to use

**Business Logic:**
```
1. Validate campaign exists and is in CREATED state
2. Validate cat fact exists and is available (RETRIEVED state)
3. Assign cat fact to campaign
4. Count active subscribers for totalSubscribers
5. Validate scheduled date is in future
```

**Expected Output:** EmailCampaign entity in SCHEDULED state with catFactId assigned.

**Other Entity Updates:** 
- Update CatFact to SCHEDULED state (transition: CatFactSchedulingProcessor)

---

### 11. EmailCampaignExecutionProcessor

**Entity:** EmailCampaign  
**Transition:** SCHEDULED → EXECUTING

**Input Data:** 
- Campaign ID

**Business Logic:**
```
1. Validate campaign is ready for execution
2. Get all active subscribers
3. Update totalSubscribers count
4. Set execution start timestamp
5. Create EmailDelivery entities for each active subscriber
6. Initialize delivery counters
```

**Expected Output:** EmailCampaign entity in EXECUTING state with executedDate set.

**Other Entity Updates:** 
- Create multiple EmailDelivery entities (transition: EmailDeliveryCreationProcessor)

---

### 12. EmailCampaignCompletionProcessor

**Entity:** EmailCampaign  
**Transition:** EXECUTING → COMPLETED

**Input Data:** 
- Campaign ID

**Business Logic:**
```
1. Validate all email deliveries are processed
2. Calculate final delivery statistics
3. Update success and failure counters
4. Log completion timestamp
5. Generate campaign report
```

**Expected Output:** EmailCampaign entity in COMPLETED state with final statistics.

**Other Entity Updates:** 
- Update CatFact to SENT state (transition: null - automatic based on criterion)

---

### 13. EmailCampaignFailureProcessor

**Entity:** EmailCampaign  
**Transition:** EXECUTING → FAILED

**Input Data:** 
- Campaign ID
- Failure reason

**Business Logic:**
```
1. Log failure reason and timestamp
2. Calculate partial delivery statistics
3. Update failure counters
4. Send failure notification to administrators
5. Clean up incomplete deliveries
```

**Expected Output:** EmailCampaign entity in FAILED state with error details.

**Other Entity Updates:** None

---

### 14. EmailCampaignCancellationProcessor

**Entity:** EmailCampaign  
**Transition:** CREATED/SCHEDULED → CANCELLED

**Input Data:** 
- Campaign ID
- Cancellation reason

**Business Logic:**
```
1. Validate campaign can be cancelled
2. Log cancellation reason and timestamp
3. Release assigned cat fact if any
4. Clean up any prepared deliveries
5. Send cancellation notification
```

**Expected Output:** EmailCampaign entity in CANCELLED state.

**Other Entity Updates:** 
- Update CatFact back to RETRIEVED state if it was scheduled (transition: null - manual reset)

## EmailDelivery Processors

### 15. EmailDeliveryCreationProcessor

**Entity:** EmailDelivery  
**Transition:** INITIAL → PENDING

**Input Data:** 
- Campaign ID
- Subscriber ID

**Business Logic:**
```
1. Validate campaign and subscriber exist
2. Get subscriber email address
3. Generate unique delivery ID
4. Set delivery status to PENDING
5. Initialize timestamps
6. Save delivery entity
```

**Expected Output:** EmailDelivery entity in PENDING state.

**Other Entity Updates:** None

---

### 16. EmailDeliverySendProcessor

**Entity:** EmailDelivery  
**Transition:** PENDING → SENT

**Input Data:** 
- Delivery ID

**Business Logic:**
```
1. Get campaign and cat fact details
2. Get subscriber information
3. Compose email content with cat fact
4. Send email via email service provider
5. Set sent timestamp
6. Update delivery status
```

**Expected Output:** EmailDelivery entity in SENT state with sentDate set.

**Other Entity Updates:** None

---

### 17. EmailDeliveryFailureProcessor

**Entity:** EmailDelivery  
**Transition:** PENDING → FAILED

**Input Data:** 
- Delivery ID
- Error message

**Business Logic:**
```
1. Log failure reason and timestamp
2. Set error message
3. Update delivery status to FAILED
4. Increment campaign failure counter
5. Send failure notification if critical
```

**Expected Output:** EmailDelivery entity in FAILED state with error details.

**Other Entity Updates:** 
- Update EmailCampaign failedDeliveries counter (no transition)

---

### 18. EmailDeliveryBounceProcessor

**Entity:** EmailDelivery  
**Transition:** SENT → BOUNCED

**Input Data:** 
- Delivery ID
- Bounce reason

**Business Logic:**
```
1. Log bounce reason and timestamp
2. Set error message with bounce details
3. Update delivery status to BOUNCED
4. Mark subscriber email as potentially invalid
5. Increment campaign failure counter
```

**Expected Output:** EmailDelivery entity in BOUNCED state.

**Other Entity Updates:** 
- Update EmailCampaign failedDeliveries counter (no transition)

---

### 19. EmailDeliveryOpenProcessor

**Entity:** EmailDelivery  
**Transition:** DELIVERED → OPENED

**Input Data:** 
- Delivery ID
- Open timestamp

**Business Logic:**
```
1. Validate delivery was successfully delivered
2. Set opened timestamp
3. Update delivery status to OPENED
4. Log open event for analytics
5. Update campaign engagement metrics
```

**Expected Output:** EmailDelivery entity in OPENED state with openedDate set.

**Other Entity Updates:** None

---

### 20. EmailDeliveryClickProcessor

**Entity:** EmailDelivery  
**Transition:** OPENED → CLICKED

**Input Data:** 
- Delivery ID
- Click timestamp
- Clicked URL

**Business Logic:**
```
1. Validate delivery was opened
2. Set clicked timestamp
3. Update delivery status to CLICKED
4. Log click event with URL for analytics
5. Update campaign engagement metrics
```

**Expected Output:** EmailDelivery entity in CLICKED state with clickedDate set.

**Other Entity Updates:** None
