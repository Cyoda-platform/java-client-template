# Criteria Requirements

## Overview
This document defines the criteria required for workflow transitions in the London Houses Data Analysis application. Criteria implement conditional logic to determine when transitions should occur.

## DataSource Criteria

### 1. DataSourceErrorResolvedCriterion
**Entity**: DataSource  
**Transition**: resolve_error (error → active)  
**Purpose**: Check if the error condition has been resolved and data source can be reactivated  

**Validation Logic**:
- Verify that the data source URL is accessible
- Ensure error message has been cleared or acknowledged
- Validate that the data source configuration is correct
- Check that network connectivity is available

**Implementation**:
```java
// Pseudocode for criterion evaluation
IF dataSource.url is valid AND 
   dataSource.url is accessible AND
   (dataSource.errorMessage is null OR dataSource.errorMessage is empty)
THEN return SUCCESS
ELSE return FAILURE with reason "Data source error not resolved"
```

## AnalysisJob Criteria

### 2. DataSourceAvailableCriterion
**Entity**: AnalysisJob  
**Transition**: start_analysis (pending → running)  
**Purpose**: Ensure that the required data source has data available for analysis  

**Validation Logic**:
- Check that the referenced data source exists
- Verify that the data source is in "active" state
- Confirm that downloaded data is available in temporary storage
- Validate that the data is not corrupted or empty

**Implementation**:
```java
// Pseudocode for criterion evaluation
dataSource = GET DataSource by analysisJob.dataSourceId
IF dataSource exists AND 
   dataSource.meta.state equals "active" AND
   temporary data exists for dataSource.id AND
   temporary data is not empty
THEN return SUCCESS
ELSE return FAILURE with reason "Data source not available for analysis"
```

## Report Criteria

### 3. SubscribersAvailableCriterion
**Entity**: Report  
**Transition**: start_sending (generated → sending)  
**Purpose**: Check if there are active subscribers to receive the report  

**Validation Logic**:
- Verify that at least one subscriber exists
- Ensure at least one subscriber is in "active" state
- Check that active subscribers have valid email addresses
- Validate that the system can send emails (email service is available)

**Implementation**:
```java
// Pseudocode for criterion evaluation
activeSubscribers = GET all Subscriber entities where meta.state equals "active"
IF activeSubscribers count > 0 AND
   email service is available AND
   all active subscribers have valid email addresses
THEN return SUCCESS
ELSE return FAILURE with reason "No active subscribers available"
```

## Subscriber Criteria

### 4. EmailDeliveryFailureCriterion
**Entity**: Subscriber  
**Transition**: email_bounced (active → bounced)  
**Purpose**: Determine if a subscriber should be marked as bounced due to email delivery failures  

**Validation Logic**:
- Check if email delivery failure count exceeds threshold (3 failures)
- Verify that failures occurred within a reasonable time window
- Ensure that the failures are legitimate delivery failures (not temporary issues)

**Implementation**:
```java
// Pseudocode for criterion evaluation
IF subscriber.emailDeliveryFailures >= 3 AND
   subscriber.lastEmailSent is within last 30 days AND
   failures are permanent delivery failures (not temporary)
THEN return SUCCESS
ELSE return FAILURE with reason "Email delivery failure threshold not met"
```

### 5. ValidEmailCriterion
**Entity**: Subscriber  
**Transition**: subscribe (none → active), resubscribe (inactive → active)  
**Purpose**: Validate that the subscriber has a valid email address  

**Validation Logic**:
- Check email format using standard email regex
- Verify that email domain exists and accepts mail
- Ensure email is not in a blacklist of known invalid domains
- Validate that email is not already registered (for new subscriptions)

**Implementation**:
```java
// Pseudocode for criterion evaluation
IF subscriber.email matches valid email pattern AND
   email domain exists AND
   email domain is not blacklisted AND
   (for new subscriptions: email is not already registered)
THEN return SUCCESS
ELSE return FAILURE with reason "Invalid email address"
```

## System-Level Criteria

### 6. SystemResourcesCriterion
**Entity**: AnalysisJob  
**Transition**: start_analysis (pending → running)  
**Purpose**: Ensure system has sufficient resources to run analysis  

**Validation Logic**:
- Check available memory for data processing
- Verify CPU availability for analysis computations
- Ensure temporary storage has sufficient space
- Validate that no other resource-intensive jobs are running

**Implementation**:
```java
// Pseudocode for criterion evaluation
IF available memory > required memory threshold AND
   CPU usage < 80% AND
   available disk space > data size * 2 AND
   concurrent analysis jobs < maximum allowed
THEN return SUCCESS
ELSE return FAILURE with reason "Insufficient system resources"
```

### 7. DataValidityCriterion
**Entity**: DataSource  
**Transition**: download_completed (downloading → active)  
**Purpose**: Validate that downloaded data meets quality requirements  

**Validation Logic**:
- Check that CSV has expected column headers
- Verify minimum number of data rows
- Validate data types in key columns
- Ensure no critical data corruption

**Implementation**:
```java
// Pseudocode for criterion evaluation
csvData = GET temporary data for dataSource.id
IF csvData has expected columns AND
   csvData row count >= minimum required rows AND
   key columns have valid data types AND
   data integrity checks pass
THEN return SUCCESS
ELSE return FAILURE with reason "Downloaded data failed validation"
```

## Criteria Usage in Workflows

### DataSource Workflow
- `DataSourceErrorResolvedCriterion` - Used in resolve_error transition
- `DataValidityCriterion` - Used in download_completed transition

### AnalysisJob Workflow  
- `DataSourceAvailableCriterion` - Used in start_analysis transition
- `SystemResourcesCriterion` - Used in start_analysis transition

### Report Workflow
- `SubscribersAvailableCriterion` - Used in start_sending transition

### Subscriber Workflow
- `EmailDeliveryFailureCriterion` - Used in email_bounced transition  
- `ValidEmailCriterion` - Used in subscribe and resubscribe transitions

## Implementation Notes

1. **Error Handling**: All criteria should handle exceptions gracefully and return appropriate failure reasons
2. **Performance**: Criteria should be lightweight and fast-executing to avoid workflow delays
3. **Logging**: Important validation failures should be logged for debugging and monitoring
4. **Caching**: Where appropriate, criteria results can be cached to improve performance
5. **Configuration**: Thresholds and validation rules should be configurable through application properties
