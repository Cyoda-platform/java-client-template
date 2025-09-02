# Criteria

This document defines the criteria for the Weekly Cat Fact Subscription application. Criteria implement conditional logic for workflow transitions.

## Criteria Overview

Criteria are used to evaluate conditions before allowing workflow transitions. They perform validation checks, business rule enforcement, and conditional logic to ensure data integrity and proper workflow execution.

## 1. SubscriberCriterion

**Entity**: Subscriber
**Purpose**: Validates subscriber-related conditions and business rules.

### Methods

#### validateVerificationToken (pending → active)
**Purpose**: Validates email verification token before activating subscription
**Input**: Subscriber entity with verification token
**Logic**:
1. Check if verification token exists and is not empty
2. Validate token format (UUID or similar)
3. Check token expiration (must be within 24 hours)
4. Verify token matches the one sent to subscriber
5. Ensure subscriber is in "pending" state

**Return**: 
- Success: Token is valid and not expired
- Failure: Token is invalid, expired, or subscriber not in pending state

#### validateEmailUniqueness (none → pending)
**Purpose**: Ensures email address is unique in the system
**Input**: Subscriber entity with email
**Logic**:
1. Check if email format is valid
2. Search existing subscribers for same email
3. Exclude unsubscribed users (allow re-subscription)
4. Validate email domain is not blacklisted

**Return**:
- Success: Email is unique and valid
- Failure: Email already exists or is invalid

## 2. CatFactCriterion

**Entity**: CatFact
**Purpose**: Validates cat fact content and business rules.

### Methods

#### validateContentQuality (retrieved → validated)
**Purpose**: Ensures cat fact meets quality standards
**Input**: CatFact entity with raw content
**Logic**:
1. Check fact text length is between 10-500 characters
2. Validate text contains no inappropriate content
3. Ensure text is readable (not garbled or encoded)
4. Check for basic grammar and structure
5. Verify fact is actually about cats

**Return**:
- Success: Content meets quality standards
- Failure: Content fails quality checks

#### checkDuplicateFact (validated → ready)
**Purpose**: Prevents duplicate facts from being approved
**Input**: CatFact entity
**Logic**:
1. Compare fact text with existing facts
2. Use fuzzy matching for similar content (80% similarity threshold)
3. Check for exact duplicates
4. Allow facts if last usage was over 6 months ago

**Return**:
- Success: Fact is unique or sufficiently old
- Failure: Fact is duplicate or too similar to recent fact

#### validateUsageCount (ready → archived)
**Purpose**: Determines if fact should be archived based on usage
**Input**: CatFact entity
**Logic**:
1. Check if usage count exceeds threshold (5 times)
2. Verify last used date is older than 3 months
3. Consider fact quality score
4. Check if fact pool has sufficient alternatives

**Return**:
- Success: Fact should be archived
- Failure: Fact can still be used

## 3. EmailCampaignCriterion

**Entity**: EmailCampaign
**Purpose**: Validates campaign scheduling and execution conditions.

### Methods

#### validateScheduleTime (draft → scheduled)
**Purpose**: Ensures campaign is scheduled at appropriate time
**Input**: EmailCampaign entity with scheduled date
**Logic**:
1. Check scheduled date is in the future (at least 1 hour)
2. Validate scheduled time is during business hours (8 AM - 6 PM)
3. Ensure scheduled day is Monday-Friday
4. Check no other campaign is scheduled within 6 hours
5. Verify sufficient lead time for preparation

**Return**:
- Success: Schedule time is valid
- Failure: Schedule conflicts or inappropriate timing

#### checkTimeToSend (scheduled → sending)
**Purpose**: Determines if it's time to start sending campaign
**Input**: EmailCampaign entity
**Logic**:
1. Check current time matches scheduled time (within 5 minutes)
2. Verify system resources are available
3. Ensure email service is operational
4. Check subscriber count is above minimum (at least 1)
5. Validate cat fact is still available

**Return**:
- Success: Ready to start sending
- Failure: Not time to send or conditions not met

#### validateAllEmailsProcessed (sending → completed)
**Purpose**: Confirms all emails have been processed
**Input**: EmailCampaign entity with send statistics
**Logic**:
1. Check that (successfulSends + failedSends) equals totalSubscribers
2. Verify no emails are still in queue
3. Ensure sending process has completed
4. Validate send statistics are consistent

**Return**:
- Success: All emails processed
- Failure: Emails still pending or statistics inconsistent

#### detectCriticalFailure (sending → failed)
**Purpose**: Identifies critical failures during sending
**Input**: EmailCampaign entity with error information
**Logic**:
1. Check failure rate exceeds 50% of total subscribers
2. Detect email service outages
3. Identify authentication or configuration errors
4. Check for rate limiting or quota exceeded
5. Validate system resource availability

**Return**:
- Success: Critical failure detected
- Failure: Failures are within acceptable limits

#### validateRetryConditions (failed → scheduled)
**Purpose**: Ensures campaign can be safely retried
**Input**: Failed EmailCampaign entity
**Logic**:
1. Check retry count is below maximum (3 attempts)
2. Verify sufficient time has passed since failure (at least 1 hour)
3. Ensure failure cause has been addressed
4. Validate email service is operational
5. Check subscriber list is still valid

**Return**:
- Success: Campaign can be retried
- Failure: Retry conditions not met

## 4. InteractionCriterion

**Entity**: Interaction
**Purpose**: Validates interaction data and processing conditions.

### Methods

#### validateInteractionData (recorded → processed)
**Purpose**: Ensures interaction data is valid for processing
**Input**: Interaction entity
**Logic**:
1. Verify subscriber ID exists and is valid
2. Check cat fact ID exists in system
3. Validate campaign ID matches existing campaign
4. Ensure interaction type is from allowed list
5. Check interaction date is not in future
6. Validate metadata format and content

**Return**:
- Success: Interaction data is valid
- Failure: Data validation failed

#### checkProcessingEligibility (recorded → processed)
**Purpose**: Determines if interaction should be processed for reporting
**Input**: Interaction entity
**Logic**:
1. Check interaction is not duplicate
2. Verify interaction occurred within campaign timeframe
3. Ensure subscriber was active when interaction occurred
4. Validate interaction type matches campaign type
5. Check processing hasn't already been completed

**Return**:
- Success: Interaction eligible for processing
- Failure: Interaction should not be processed

## Business Rule Criteria

### Global Business Rules

#### validateBusinessHours
**Purpose**: Ensures operations occur during appropriate business hours
**Logic**:
1. Check current time is between 6 AM - 10 PM in system timezone
2. Validate day is Monday-Saturday (no Sunday operations)
3. Consider holiday calendar for special restrictions

#### validateSystemResources
**Purpose**: Ensures system has sufficient resources for operations
**Logic**:
1. Check database connection pool availability
2. Verify email service quota and rate limits
3. Validate memory and CPU usage within limits
4. Ensure external API availability (Cat Fact API)

#### validateDataIntegrity
**Purpose**: Ensures data consistency across entities
**Logic**:
1. Verify foreign key relationships exist
2. Check data format consistency
3. Validate required fields are populated
4. Ensure business logic constraints are met

## Criteria Usage in Workflows

### Automatic Transitions
- Most criteria are used in automatic transitions to ensure data quality
- Failed criteria prevent transition and may trigger error handling
- Criteria results are logged for debugging and monitoring

### Manual Transitions
- Some criteria validate user permissions and business rules
- Manual transitions may bypass certain criteria with proper authorization
- Audit trails are maintained for manual overrides

### Error Handling
- Failed criteria generate specific error messages
- Retry logic may be implemented for transient failures
- Critical failures trigger alerts and notifications
