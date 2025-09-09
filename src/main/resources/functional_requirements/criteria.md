# Criteria Requirements

## Overview
This document defines the criteria requirements for the Product Performance Analysis and Reporting System. Criteria are pure functions that evaluate conditions without side effects to determine if transitions should proceed.

## Criteria Definitions

### Pet Entity Criteria

#### 1. PetDataValidityCriterion
**Entity**: Pet  
**Transition**: validate_pet_data (extracted → validated)  
**Purpose**: Check if extracted pet data is valid for processing

**Evaluation Logic**:
```
BEGIN check()
    IF entity.petId is null:
        RETURN false
    END IF
    
    IF entity.name is null OR entity.name is empty:
        RETURN false
    END IF
    
    IF entity.category is null:
        RETURN false
    END IF
    
    IF entity.price is not null AND entity.price < 0:
        RETURN false
    END IF
    
    RETURN true
END
```

**Description**: Validates that essential pet data fields are present and valid before proceeding to validation processing.

#### 2. PetAnalysisReadyCriterion
**Entity**: Pet  
**Transition**: analyze_performance (active → analyzed)  
**Purpose**: Check if pet has sufficient data for performance analysis

**Evaluation Logic**:
```
BEGIN check()
    IF entity.createdAt is null:
        RETURN false
    END IF
    
    // Check if pet has been active for at least 24 hours
    timeSinceCreation = CURRENT_TIMESTAMP - entity.createdAt
    IF timeSinceCreation < 24 hours:
        RETURN false
    END IF
    
    // Check if there are any orders for this pet
    orderCount = COUNT orders where petId = entity.petId
    IF orderCount > 0:
        RETURN true
    END IF
    
    // Allow analysis if pet has been active for more than 7 days even without orders
    IF timeSinceCreation > 7 days:
        RETURN true
    END IF
    
    RETURN false
END
```

**Description**: Ensures pet has sufficient activity time or order history before performance analysis.

#### 3. PetArchivalCriterion
**Entity**: Pet  
**Transition**: archive_pet (analyzed → archived)  
**Purpose**: Check if pet should be archived based on inactivity

**Evaluation Logic**:
```
BEGIN check()
    IF entity.lastSaleDate is null:
        // No sales recorded, check creation date
        timeSinceCreation = CURRENT_TIMESTAMP - entity.createdAt
        RETURN timeSinceCreation > 90 days
    END IF
    
    // Check time since last sale
    timeSinceLastSale = CURRENT_TIMESTAMP - entity.lastSaleDate
    IF timeSinceLastSale > 60 days:
        RETURN true
    END IF
    
    // Check if sales volume is very low
    IF entity.salesVolume < 5 AND timeSinceLastSale > 30 days:
        RETURN true
    END IF
    
    RETURN false
END
```

**Description**: Determines if pet should be archived based on sales inactivity and low performance.

### Order Entity Criteria

#### 4. OrderValidityCriterion
**Entity**: Order  
**Transition**: validate_order (imported → validated)  
**Purpose**: Check if imported order data is valid

**Evaluation Logic**:
```
BEGIN check()
    IF entity.orderId is null:
        RETURN false
    END IF
    
    IF entity.petId is null:
        RETURN false
    END IF
    
    IF entity.quantity is null OR entity.quantity <= 0:
        RETURN false
    END IF
    
    IF entity.unitPrice is null OR entity.unitPrice < 0:
        RETURN false
    END IF
    
    IF entity.totalAmount is null OR entity.totalAmount != (entity.quantity * entity.unitPrice):
        RETURN false
    END IF
    
    IF entity.orderDate is null:
        RETURN false
    END IF
    
    // Check if order date is not in the future
    IF entity.orderDate > CURRENT_TIMESTAMP:
        RETURN false
    END IF
    
    RETURN true
END
```

**Description**: Validates order data integrity and business rules before processing.

### Report Entity Criteria

#### 5. ReportGenerationReadyCriterion
**Entity**: Report  
**Transition**: start_generation (scheduled → generating)  
**Purpose**: Check if system is ready to generate report

**Evaluation Logic**:
```
BEGIN check()
    IF entity.reportPeriod is null:
        RETURN false
    END IF
    
    IF entity.reportPeriod.startDate is null OR entity.reportPeriod.endDate is null:
        RETURN false
    END IF
    
    IF entity.reportPeriod.endDate <= entity.reportPeriod.startDate:
        RETURN false
    END IF
    
    // Check if there is sufficient data for the report period
    orderCount = COUNT orders where orderDate BETWEEN entity.reportPeriod.startDate AND entity.reportPeriod.endDate
    IF orderCount < 1:
        RETURN false
    END IF
    
    // Check system resources (simplified check)
    currentTime = CURRENT_TIMESTAMP
    IF currentTime.hour BETWEEN 2 AND 6:
        // Prefer report generation during low-traffic hours
        RETURN true
    END IF
    
    // Allow generation during business hours if urgent
    IF entity.reportType = "URGENT":
        RETURN true
    END IF
    
    RETURN currentTime.hour BETWEEN 2 AND 6
END
```

**Description**: Ensures optimal conditions for report generation including data availability and system resources.

#### 6. ReportArchivalCriterion
**Entity**: Report  
**Transition**: archive_report (distributed → archived)  
**Purpose**: Check if report should be archived

**Evaluation Logic**:
```
BEGIN check()
    IF entity.generationDate is null:
        RETURN false
    END IF
    
    // Archive reports older than 30 days
    timeSinceGeneration = CURRENT_TIMESTAMP - entity.generationDate
    IF timeSinceGeneration > 30 days:
        RETURN true
    END IF
    
    // Archive if all email notifications are completed
    pendingNotifications = COUNT emailNotifications where reportId = entity.reportId AND state NOT IN ['sent', 'failed', 'cancelled']
    IF pendingNotifications = 0 AND timeSinceGeneration > 7 days:
        RETURN true
    END IF
    
    RETURN false
END
```

**Description**: Determines when reports can be safely archived based on age and notification status.

### EmailNotification Entity Criteria

#### 7. EmailSendingReadyCriterion
**Entity**: EmailNotification  
**Transition**: send_notification (scheduled → sending)  
**Purpose**: Check if email notification is ready to be sent

**Evaluation Logic**:
```
BEGIN check()
    IF entity.scheduledTime is null:
        RETURN false
    END IF
    
    IF entity.scheduledTime > CURRENT_TIMESTAMP:
        RETURN false
    END IF
    
    IF entity.recipientEmail is null OR entity.recipientEmail is empty:
        RETURN false
    END IF
    
    // Validate email format
    IF NOT MATCHES_EMAIL_PATTERN(entity.recipientEmail):
        RETURN false
    END IF
    
    IF entity.attachmentPath is not null:
        // Check if attachment file exists and is readable
        IF NOT FILE_EXISTS(entity.attachmentPath):
            RETURN false
        END IF
    END IF
    
    // Check if we're within business hours for email sending
    currentTime = CURRENT_TIMESTAMP
    IF currentTime.hour < 6 OR currentTime.hour > 22:
        RETURN false
    END IF
    
    RETURN true
END
```

**Description**: Validates email notification readiness including timing, recipient, and attachment availability.

#### 8. DeliveryFailureCriterion
**Entity**: EmailNotification  
**Transition**: handle_failure (sending → failed)  
**Purpose**: Check if email delivery has failed

**Evaluation Logic**:
```
BEGIN check()
    IF entity.lastError is null OR entity.lastError is empty:
        RETURN false
    END IF
    
    // Check for specific failure conditions
    IF entity.lastError CONTAINS "invalid email":
        RETURN true
    END IF
    
    IF entity.lastError CONTAINS "attachment not found":
        RETURN true
    END IF
    
    IF entity.lastError CONTAINS "smtp error":
        RETURN true
    END IF
    
    IF entity.lastError CONTAINS "timeout":
        RETURN true
    END IF
    
    // Check if sending has been in progress too long
    IF entity.sentTime is null:
        timeSinceSending = CURRENT_TIMESTAMP - entity.updatedAt
        IF timeSinceSending > 10 minutes:
            RETURN true
        END IF
    END IF
    
    RETURN false
END
```

**Description**: Identifies various failure conditions for email delivery.

#### 9. RetryEligibilityCriterion
**Entity**: EmailNotification  
**Transition**: retry_sending (failed → scheduled)  
**Purpose**: Check if failed notification is eligible for retry

**Evaluation Logic**:
```
BEGIN check()
    IF entity.deliveryAttempts is null:
        RETURN false
    END IF
    
    // Maximum 3 retry attempts
    IF entity.deliveryAttempts >= 3:
        RETURN false
    END IF
    
    // Don't retry for permanent failures
    IF entity.lastError CONTAINS "invalid email":
        RETURN false
    END IF
    
    IF entity.lastError CONTAINS "recipient not found":
        RETURN false
    END IF
    
    IF entity.lastError CONTAINS "attachment not found":
        RETURN false
    END IF
    
    // Allow retry for temporary failures
    IF entity.lastError CONTAINS "timeout":
        RETURN true
    END IF
    
    IF entity.lastError CONTAINS "smtp error":
        RETURN true
    END IF
    
    IF entity.lastError CONTAINS "server unavailable":
        RETURN true
    END IF
    
    // Default to retry for unknown errors (up to attempt limit)
    RETURN true
END
```

**Description**: Determines if a failed email notification should be retried based on error type and attempt count.

## Criteria Implementation Notes

### Pure Function Requirements
- Criteria must not modify entities or have side effects
- All evaluations must be deterministic given the same input
- No external API calls or database modifications allowed

### Performance Considerations
- Keep evaluation logic simple and fast
- Avoid complex calculations or loops
- Cache frequently used values when possible

### Error Handling
- Return false for any unexpected conditions
- Log evaluation errors for debugging
- Fail safely to prevent workflow blocking

### Testing
- All criteria must be unit testable
- Test both positive and negative cases
- Verify edge cases and boundary conditions
