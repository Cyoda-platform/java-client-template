# Criteria Requirements

## Overview
This document defines the criteria required for the Weekly Cat Fact Subscription application. Criteria are pure functions that evaluate conditions without side effects to determine workflow transitions.

## CatFact Criteria

### 1. CatFactCampaignExecutedCriterion

**Entity:** CatFact  
**Transition:** SCHEDULED → SENT  
**Purpose:** Check if the associated email campaign has been executed successfully

**Input Data:**
- CatFact entity with catFactId
- Current entity state

**Evaluation Logic:**
```
1. Get the cat fact ID from the entity
2. Query EmailCampaign entities where catFactId matches
3. Check if any campaign is in COMPLETED state
4. Return true if at least one campaign completed successfully
5. Return false otherwise
```

**Return:** Boolean - true if campaign executed, false otherwise

**Side Effects:** None (read-only operation)

---

## EmailCampaign Criteria

### 2. EmailCampaignReadyCriterion

**Entity:** EmailCampaign  
**Transition:** SCHEDULED → EXECUTING  
**Purpose:** Check if the campaign is ready to be executed (scheduled time has arrived)

**Input Data:**
- EmailCampaign entity with scheduledDate
- Current timestamp

**Evaluation Logic:**
```
1. Get the scheduled date from the campaign entity
2. Get the current timestamp
3. Check if current time >= scheduled time
4. Verify that a cat fact is assigned (catFactId is not null)
5. Verify that there are active subscribers available
6. Return true if all conditions are met
7. Return false otherwise
```

**Return:** Boolean - true if ready to execute, false otherwise

**Side Effects:** None (read-only operation)

---

### 3. EmailCampaignSuccessCriterion

**Entity:** EmailCampaign  
**Transition:** EXECUTING → COMPLETED  
**Purpose:** Check if the campaign execution has completed successfully

**Input Data:**
- EmailCampaign entity with delivery statistics
- Associated EmailDelivery entities

**Evaluation Logic:**
```
1. Get the campaign ID from the entity
2. Query all EmailDelivery entities for this campaign
3. Count deliveries in final states (DELIVERED, OPENED, CLICKED, FAILED, BOUNCED)
4. Compare count with totalSubscribers
5. Check if all deliveries are processed (no PENDING or SENT states)
6. Verify that at least 50% of deliveries were successful
7. Return true if campaign completed successfully
8. Return false otherwise
```

**Return:** Boolean - true if campaign completed successfully, false otherwise

**Side Effects:** None (read-only operation)

---

### 4. EmailCampaignFailureCriterion

**Entity:** EmailCampaign  
**Transition:** EXECUTING → FAILED  
**Purpose:** Check if the campaign execution has failed

**Input Data:**
- EmailCampaign entity with delivery statistics
- Associated EmailDelivery entities
- Execution timeout threshold

**Evaluation Logic:**
```
1. Get the campaign execution start time
2. Check if execution has exceeded timeout threshold (e.g., 2 hours)
3. Query EmailDelivery entities for this campaign
4. Count failed deliveries (FAILED, BOUNCED states)
5. Calculate failure rate (failed / total)
6. Check if failure rate exceeds threshold (e.g., 80%)
7. Return true if any failure condition is met
8. Return false otherwise
```

**Return:** Boolean - true if campaign failed, false otherwise

**Side Effects:** None (read-only operation)

---

## EmailDelivery Criteria

### 5. EmailDeliveryFailureCriterion

**Entity:** EmailDelivery  
**Transition:** PENDING → FAILED  
**Purpose:** Check if email delivery attempt has failed

**Input Data:**
- EmailDelivery entity
- Email service response/status
- Retry attempt count

**Evaluation Logic:**
```
1. Check email service response status
2. Verify if email address format is valid
3. Check if maximum retry attempts exceeded (e.g., 3 attempts)
4. Verify if subscriber is still active
5. Check for permanent failure indicators (invalid email, blocked domain)
6. Return true if any failure condition is met
7. Return false if delivery should be retried or is successful
```

**Return:** Boolean - true if delivery failed, false otherwise

**Side Effects:** None (read-only operation)

---

### 6. EmailDeliverySuccessCriterion

**Entity:** EmailDelivery  
**Transition:** SENT → DELIVERED  
**Purpose:** Check if email was successfully delivered to recipient's inbox

**Input Data:**
- EmailDelivery entity
- Email service delivery confirmation
- Delivery status from email provider

**Evaluation Logic:**
```
1. Check email service delivery confirmation status
2. Verify delivery timestamp is present and valid
3. Check that no bounce or failure notifications received
4. Verify delivery status indicates successful inbox delivery
5. Confirm delivery time is after sent time
6. Return true if delivery confirmed successful
7. Return false otherwise
```

**Return:** Boolean - true if delivered successfully, false otherwise

**Side Effects:** None (read-only operation)

---

### 7. EmailDeliveryBounceCriterion

**Entity:** EmailDelivery  
**Transition:** SENT → BOUNCED  
**Purpose:** Check if email bounced back from recipient's email server

**Input Data:**
- EmailDelivery entity
- Email service bounce notification
- Bounce reason/type

**Evaluation Logic:**
```
1. Check for bounce notification from email service
2. Verify bounce reason indicates permanent or temporary failure
3. Check bounce timestamp is after sent timestamp
4. Validate bounce notification authenticity
5. Determine if bounce is hard (permanent) or soft (temporary)
6. Return true if valid bounce detected
7. Return false otherwise
```

**Return:** Boolean - true if email bounced, false otherwise

**Side Effects:** None (read-only operation)

---

## Validation Criteria (Optional - for additional validation)

### 8. SubscriberEmailValidCriterion

**Entity:** Subscriber  
**Purpose:** Validate subscriber email format and domain

**Input Data:**
- Subscriber entity with email address

**Evaluation Logic:**
```
1. Check email format using regex pattern
2. Verify email domain exists and accepts mail
3. Check against blocked domain list
4. Validate email length constraints
5. Check for common typos in popular domains
6. Return true if email is valid
7. Return false otherwise
```

**Return:** Boolean - true if email valid, false otherwise

**Side Effects:** None (read-only operation)

---

### 9. CatFactContentValidCriterion

**Entity:** CatFact  
**Purpose:** Validate cat fact content quality and appropriateness

**Input Data:**
- CatFact entity with fact text

**Evaluation Logic:**
```
1. Check fact text is not empty or null
2. Verify minimum and maximum length constraints
3. Check for inappropriate content (basic filtering)
4. Validate text encoding and special characters
5. Check for duplicate content against existing facts
6. Return true if content is valid
7. Return false otherwise
```

**Return:** Boolean - true if content valid, false otherwise

**Side Effects:** None (read-only operation)

---

### 10. EmailCampaignScheduleValidCriterion

**Entity:** EmailCampaign  
**Purpose:** Validate campaign scheduling constraints

**Input Data:**
- EmailCampaign entity with scheduled date
- Current timestamp

**Evaluation Logic:**
```
1. Check scheduled date is in the future
2. Verify scheduled date is not too far in advance (e.g., max 30 days)
3. Check for scheduling conflicts with other campaigns
4. Validate scheduled time is during acceptable hours
5. Verify day of week is appropriate for email campaigns
6. Return true if schedule is valid
7. Return false otherwise
```

**Return:** Boolean - true if schedule valid, false otherwise

**Side Effects:** None (read-only operation)

## Criteria Usage Notes

1. **Pure Functions**: All criteria must be pure functions without side effects
2. **Read-Only**: Criteria can only read data, never modify entities
3. **Performance**: Keep evaluation logic efficient as criteria may be called frequently
4. **Error Handling**: Criteria should handle null/invalid inputs gracefully
5. **Logging**: Log evaluation results for debugging but avoid excessive logging
6. **Dependencies**: Criteria can query other entities but should not depend on external services
7. **Timeout**: Evaluation should complete quickly (under 1 second)

## Integration with Workflows

- Criteria are evaluated automatically by the workflow engine
- Multiple criteria can be combined using logical operators (AND, OR)
- Criteria results determine which transition path to take
- Failed criteria evaluation prevents transition and may trigger error handling
- Criteria evaluation is logged for audit and debugging purposes
