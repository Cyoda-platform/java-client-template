# Criterion Requirements

## Overview
This document defines the detailed requirements for all criteria in the Product Performance Analysis and Reporting System. Criteria implement conditional logic for workflow transitions and validation rules.

## DataExtraction Entity Criteria

### 1. ExtractionSuccessCriterion
**Entity**: DataExtraction  
**Transition**: in_progress → completed  
**Purpose**: Validates that data extraction completed successfully

**Validation Logic**:
- Check that totalProductsExtracted > 0
- Verify that dataQualityScore >= 0.7 (70% quality threshold)
- Ensure no critical errors occurred during extraction
- Validate that endTime is set and after startTime
- Confirm API responses were received within timeout limits

**Return**: 
- Success if all validation checks pass
- Failure with specific reason if any check fails

### 2. ExtractionFailureCriterion
**Entity**: DataExtraction  
**Transition**: in_progress → failed  
**Purpose**: Identifies when data extraction has failed

**Validation Logic**:
- Check for API connection timeouts
- Verify if authentication failed
- Detect if data quality score < 0.7
- Identify if no products were extracted
- Check for critical system errors

**Return**:
- Success if failure conditions are met (triggers failure transition)
- Failure if extraction should continue

### 3. RetryEligibilityCriterion
**Entity**: DataExtraction  
**Transition**: failed → scheduled  
**Purpose**: Determines if failed extraction can be retried

**Validation Logic**:
- Check that retryCount < maxRetries (default: 3)
- Verify error type is retryable (not authentication or configuration errors)
- Ensure minimum time has passed since last attempt (exponential backoff)
- Validate that system resources are available for retry

**Return**:
- Success if retry is eligible
- Failure if retry should not be attempted

## Report Entity Criteria

### 4. EmailFailureCriterion
**Entity**: Report  
**Transition**: generated → email_failed  
**Purpose**: Identifies email sending failures

**Validation Logic**:
- Check for SMTP server connection errors
- Verify recipient email address validity
- Detect attachment size limits exceeded
- Identify authentication failures with email server
- Check for network connectivity issues

**Return**:
- Success if email failure conditions are met
- Failure if email sending should continue

### 5. EmailRetryEligibilityCriterion
**Entity**: Report  
**Transition**: email_failed → generated  
**Purpose**: Determines if failed email can be retried

**Validation Logic**:
- Check that email retry count < maxEmailRetries (default: 3)
- Verify error is temporary (not permanent bounce or invalid address)
- Ensure minimum time has passed since last email attempt
- Validate that email server is accessible

**Return**:
- Success if email retry is eligible
- Failure if retry should not be attempted

## EmailNotification Entity Criteria

### 6. DeliveryConfirmationCriterion
**Entity**: EmailNotification  
**Transition**: sent → delivered  
**Purpose**: Confirms email delivery to recipient

**Validation Logic**:
- Check for delivery status notification from email server
- Verify delivery confirmation timestamp is valid
- Ensure delivery status indicates successful delivery
- Validate that no bounce messages were received

**Return**:
- Success if delivery is confirmed
- Failure if delivery cannot be confirmed

### 7. EmailSendFailureCriterion
**Entity**: EmailNotification  
**Transition**: sending → failed  
**Purpose**: Identifies email sending failures at notification level

**Validation Logic**:
- Check for SMTP protocol errors
- Verify server response codes indicate failure
- Detect timeout during email transmission
- Identify invalid recipient address format
- Check for attachment processing errors

**Return**:
- Success if send failure conditions are met
- Failure if sending should continue

### 8. DeliveryFailureCriterion
**Entity**: EmailNotification  
**Transition**: sent → failed  
**Purpose**: Identifies email delivery failures

**Validation Logic**:
- Check for bounce messages received
- Verify delivery failure notifications
- Detect recipient mailbox full conditions
- Identify permanent delivery failures (invalid domain, user not found)
- Check for spam filter rejections

**Return**:
- Success if delivery failure conditions are met
- Failure if delivery status is still pending

## Product Entity Criteria

### 9. ProductValidationCriterion
**Entity**: Product  
**Purpose**: Validates product data quality and completeness

**Validation Logic**:
- Check that petId is not null and > 0
- Verify name is not null or empty
- Validate that stockLevel >= 0
- Ensure salesVolume >= 0 if set
- Check that revenue >= 0 if calculated
- Verify extractionDate is not null and not in future

**Return**:
- Success if all product data is valid
- Failure with specific validation error details

### 10. PerformanceAnalysisReadyCriterion
**Entity**: Product  
**Purpose**: Determines if product is ready for performance analysis

**Validation Logic**:
- Check that product is in "extracted" state
- Verify all required fields are populated
- Ensure extractionDate is recent (within last 24 hours)
- Validate that product has valid category information
- Check that stock level data is available

**Return**:
- Success if product is ready for analysis
- Failure if prerequisites are not met

## System-Level Criteria

### 11. DataQualityCriterion
**Purpose**: Validates overall data quality across entities

**Validation Logic**:
- Check data completeness percentage across all products
- Verify data consistency between related entities
- Validate that required fields are populated
- Ensure data freshness (extraction within acceptable timeframe)
- Check for duplicate or conflicting data

**Return**:
- Success if data quality meets minimum standards
- Failure with quality assessment details

### 12. SystemResourceCriterion
**Purpose**: Validates system resources are available for processing

**Validation Logic**:
- Check available memory for large data processing
- Verify disk space for report generation and storage
- Ensure network connectivity to external APIs
- Validate that required services are running
- Check system load and performance metrics

**Return**:
- Success if system resources are adequate
- Failure if resource constraints exist

## Business Rule Criteria

### 13. BusinessHoursCriterion
**Purpose**: Validates operations occur during appropriate business hours

**Validation Logic**:
- Check current time against configured business hours
- Verify day of week for scheduled operations
- Ensure operations don't conflict with maintenance windows
- Validate timezone considerations for global operations

**Return**:
- Success if current time is within business hours
- Failure if operation should be delayed

### 14. ReportingPeriodCriterion
**Purpose**: Validates reporting period and frequency requirements

**Validation Logic**:
- Check that sufficient time has passed since last report
- Verify reporting period aligns with business requirements
- Ensure minimum data collection period has elapsed
- Validate that reporting frequency matches configuration

**Return**:
- Success if reporting period requirements are met
- Failure if report generation should be delayed

## Error Handling Criteria

### 15. CriticalErrorCriterion
**Purpose**: Identifies critical system errors requiring immediate attention

**Validation Logic**:
- Check for database connectivity issues
- Verify external API availability
- Detect configuration errors
- Identify security-related failures
- Check for data corruption indicators

**Return**:
- Success if critical errors are detected (triggers error handling)
- Failure if system is operating normally

### 16. RecoverableErrorCriterion
**Purpose**: Distinguishes between recoverable and permanent errors

**Validation Logic**:
- Analyze error type and category
- Check error frequency and patterns
- Verify if error conditions are temporary
- Assess impact on system functionality
- Determine if automatic recovery is possible

**Return**:
- Success if error is recoverable (enables retry logic)
- Failure if error requires manual intervention

## Configuration and Thresholds

### Default Thresholds
- **Data Quality Minimum**: 70%
- **Maximum Retries**: 3 attempts
- **Email Retry Limit**: 3 attempts
- **Performance Score Threshold**: 0.3 (below = underperforming)
- **Stock Level Threshold**: 10 units (below = needs restocking)
- **Extraction Timeout**: 300 seconds (5 minutes)
- **Email Timeout**: 60 seconds

### Retry Policies
- **Exponential Backoff**: 2^attempt_number minutes
- **Maximum Backoff**: 60 minutes
- **Jitter**: ±25% random variation to prevent thundering herd

### Business Hours
- **Default Hours**: Monday-Friday, 9:00 AM - 5:00 PM UTC
- **Maintenance Window**: Sunday 2:00 AM - 4:00 AM UTC
- **Holiday Schedule**: Configurable per region
