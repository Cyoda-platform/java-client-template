# Processors

This document defines the processors for the Weekly Cat Fact Subscription application. Each processor implements business logic for workflow transitions.

## Processor Overview

Processors are responsible for executing business logic during workflow transitions. They can modify entity state, interact with external APIs, send emails, and update related entities.

## 1. SubscriberProcessor

**Entity**: Subscriber
**Purpose**: Handles subscriber lifecycle operations including registration, verification, and subscription management.

### Methods

#### subscribe (none → pending)
**Input**: Subscriber entity with email, firstName, lastName, preferences
**Process**:
1. Validate email format and uniqueness
2. Generate verification token
3. Set subscription date to current time
4. Set isActive to false (pending verification)
5. Send verification email with token
6. Log subscription attempt

**Output**: Updated Subscriber entity in "pending" state
**Other Entity Updates**: None
**Transition**: null (state managed by workflow)

#### verify_email (pending → active)
**Input**: Subscriber entity with verification token
**Process**:
1. Validate verification token
2. Check token expiration (24 hours)
3. Set isActive to true
4. Clear verification token
5. Send welcome email
6. Log successful verification

**Output**: Updated Subscriber entity in "active" state
**Other Entity Updates**: None
**Transition**: null

#### pause_subscription (active → inactive)
**Input**: Subscriber entity
**Process**:
1. Set isActive to false
2. Record pause date in preferences
3. Send confirmation email
4. Log subscription pause

**Output**: Updated Subscriber entity in "inactive" state
**Other Entity Updates**: None
**Transition**: null

#### resume_subscription (inactive → active)
**Input**: Subscriber entity
**Process**:
1. Set isActive to true
2. Update preferences with resume date
3. Send confirmation email
4. Log subscription resume

**Output**: Updated Subscriber entity in "active" state
**Other Entity Updates**: None
**Transition**: null

#### unsubscribe (any state → unsubscribed)
**Input**: Subscriber entity with unsubscribe reason (optional)
**Process**:
1. Set isActive to false
2. Record unsubscribe date and reason
3. Send unsubscribe confirmation email
4. Log unsubscription with reason
5. Clean up any pending campaigns for this subscriber

**Output**: Updated Subscriber entity in "unsubscribed" state
**Other Entity Updates**: None
**Transition**: null

## 2. CatFactProcessor

**Entity**: CatFact
**Purpose**: Handles cat fact retrieval, validation, and lifecycle management.

### Methods

#### fetch_from_api (none → retrieved)
**Input**: Empty CatFact entity or trigger event
**Process**:
1. Call Cat Fact API (https://catfact.ninja/fact)
2. Parse JSON response
3. Extract fact text and length
4. Generate unique ID for the fact
5. Set retrievedDate to current time
6. Set usageCount to 0
7. Set source to "catfact.ninja"

**Output**: New CatFact entity in "retrieved" state
**Other Entity Updates**: None
**Transition**: null

#### validate_content (retrieved → validated)
**Input**: CatFact entity with raw content
**Process**:
1. Check fact text is not empty
2. Validate text length (10-500 characters)
3. Remove excessive whitespace
4. Check for inappropriate content (basic filter)
5. Verify fact is not duplicate of existing facts
6. Set validation timestamp

**Output**: Updated CatFact entity in "validated" state
**Other Entity Updates**: None
**Transition**: null

#### approve_for_use (validated → ready)
**Input**: Validated CatFact entity
**Process**:
1. Mark fact as ready for campaigns
2. Add to available facts pool
3. Log fact approval
4. Update fact metadata

**Output**: Updated CatFact entity in "ready" state
**Other Entity Updates**: None
**Transition**: null

#### archive_fact (ready → archived)
**Input**: CatFact entity
**Process**:
1. Check usage count and last used date
2. Mark fact as archived
3. Remove from active pool
4. Log archival reason
5. Update statistics

**Output**: Updated CatFact entity in "archived" state
**Other Entity Updates**: None
**Transition**: null

#### reject_fact (validated → archived)
**Input**: CatFact entity with rejection reason
**Process**:
1. Mark fact as rejected
2. Log rejection reason
3. Archive immediately
4. Update quality metrics

**Output**: Updated CatFact entity in "archived" state
**Other Entity Updates**: None
**Transition**: null

## 3. EmailCampaignProcessor

**Entity**: EmailCampaign
**Purpose**: Handles email campaign creation, scheduling, and execution.

### Methods

#### create_campaign (none → draft)
**Input**: EmailCampaign entity with basic info
**Process**:
1. Generate unique campaign ID
2. Set campaign name with current week
3. Select available cat fact from ready pool
4. Set scheduled date to next Monday 9 AM
5. Count active subscribers
6. Generate email subject line
7. Select email template

**Output**: New EmailCampaign entity in "draft" state
**Other Entity Updates**: Update selected CatFact usage count
**Transition**: null

#### schedule_campaign (draft → scheduled)
**Input**: EmailCampaign entity
**Process**:
1. Validate scheduled date is in future
2. Confirm cat fact is still available
3. Get final subscriber count
4. Prepare email content
5. Queue campaign for sending
6. Log campaign scheduling

**Output**: Updated EmailCampaign entity in "scheduled" state
**Other Entity Updates**: None
**Transition**: null

#### start_sending (scheduled → sending)
**Input**: EmailCampaign entity
**Process**:
1. Get list of active subscribers
2. Initialize send counters
3. Begin batch email sending
4. Update campaign status
5. Log sending start

**Output**: Updated EmailCampaign entity in "sending" state
**Other Entity Updates**: None
**Transition**: null

#### finish_sending (sending → completed)
**Input**: EmailCampaign entity with send results
**Process**:
1. Finalize send statistics
2. Set actual sent date
3. Update cat fact last used date
4. Generate campaign report
5. Log campaign completion

**Output**: Updated EmailCampaign entity in "completed" state
**Other Entity Updates**: Update CatFact lastUsedDate and usageCount
**Transition**: null

#### handle_failure (sending → failed)
**Input**: EmailCampaign entity with error details
**Process**:
1. Log failure details
2. Calculate partial send statistics
3. Determine failure cause
4. Set failure metadata
5. Notify administrators

**Output**: Updated EmailCampaign entity in "failed" state
**Other Entity Updates**: None
**Transition**: null

#### retry_campaign (failed → scheduled)
**Input**: Failed EmailCampaign entity
**Process**:
1. Reset send counters
2. Update scheduled date
3. Validate retry conditions
4. Log retry attempt
5. Prepare for new send

**Output**: Updated EmailCampaign entity in "scheduled" state
**Other Entity Updates**: None
**Transition**: null

#### cancel_campaign (draft → failed)
**Input**: EmailCampaign entity
**Process**:
1. Mark campaign as cancelled
2. Release reserved cat fact
3. Log cancellation reason
4. Clean up resources

**Output**: Updated EmailCampaign entity in "failed" state
**Other Entity Updates**: Reset CatFact reservation if applicable
**Transition**: null

## 4. InteractionProcessor

**Entity**: Interaction
**Purpose**: Handles tracking and processing of user interactions with cat facts.

### Methods

#### record_interaction (none → recorded)
**Input**: Interaction entity with basic data
**Process**:
1. Validate interaction data
2. Set interaction timestamp
3. Generate unique interaction ID
4. Store metadata (user agent, IP, etc.)
5. Log interaction event

**Output**: New Interaction entity in "recorded" state
**Other Entity Updates**: None
**Transition**: null

#### process_for_reporting (recorded → processed)
**Input**: Interaction entity
**Process**:
1. Validate interaction references
2. Update reporting metrics
3. Calculate engagement scores
4. Update subscriber engagement history
5. Generate analytics data

**Output**: Updated Interaction entity in "processed" state
**Other Entity Updates**: Update Subscriber engagement metrics
**Transition**: null
