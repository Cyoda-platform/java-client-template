# Processor Requirements

## Overview
This document defines the processor requirements for the Product Performance Analysis and Reporting System. Processors implement business logic for entity transitions and handle data transformations, API integrations, and analytics calculations.

## Processor Definitions

### Pet Entity Processors

#### 1. PetDataExtractionProcessor
**Entity**: Pet  
**Transition**: extract_pet_data (none → extracted)  
**Purpose**: Extract pet data from Pet Store API and create Pet entities

**Input**: Empty Pet entity with minimal data  
**Output**: Pet entity populated with API data

**Pseudocode**:
```
BEGIN process()
    CALL Pet Store API /pet/findByStatus with status=['available', 'pending', 'sold']
    FOR each pet in API response:
        CREATE new Pet entity
        SET petId = pet.id
        SET name = pet.name
        SET category = pet.category
        SET photoUrls = pet.photoUrls
        SET tags = pet.tags
        SET price = CALCULATE_PRICE(pet.category, pet.tags)
        SET stockLevel = RANDOM(1, 100) // Simulated stock data
        SET salesVolume = 0
        SET revenue = 0.0
        SET createdAt = CURRENT_TIMESTAMP
        SET updatedAt = CURRENT_TIMESTAMP
        SAVE Pet entity using entityService
    END FOR
    RETURN current entity (unchanged)
END
```

**Other Entity Updates**: Creates new Pet entities via entityService  
**Transition for Other Entities**: extract_pet_data (none → extracted)

#### 2. PetDataValidationProcessor
**Entity**: Pet  
**Transition**: validate_pet_data (extracted → validated)  
**Purpose**: Validate and enrich pet data with additional business logic

**Input**: Pet entity with basic API data  
**Output**: Pet entity with validated and enriched data

**Pseudocode**:
```
BEGIN process()
    VALIDATE current entity data:
        IF petId is null OR name is empty:
            THROW validation error
        END IF
    
    ENRICH entity data:
        IF price is null:
            SET price = CALCULATE_DEFAULT_PRICE(category.name)
        END IF
        
        IF stockLevel < 0:
            SET stockLevel = 0
        END IF
        
        SET updatedAt = CURRENT_TIMESTAMP
    
    RETURN updated current entity
END
```

**Other Entity Updates**: None  
**Transition for Other Entities**: N/A

#### 3. PetPerformanceAnalysisProcessor
**Entity**: Pet  
**Transition**: analyze_performance (active → analyzed)  
**Purpose**: Calculate performance metrics for pet products

**Input**: Pet entity in active state  
**Output**: Pet entity with updated performance metrics

**Pseudocode**:
```
BEGIN process()
    GET all orders for current pet using entityService
    
    CALCULATE performance metrics:
        totalSales = SUM(order.quantity) for all orders
        totalRevenue = SUM(order.totalAmount) for all orders
        lastSaleDate = MAX(order.orderDate) for all orders
        
    UPDATE current entity:
        SET salesVolume = totalSales
        SET revenue = totalRevenue
        SET lastSaleDate = lastSaleDate
        SET updatedAt = CURRENT_TIMESTAMP
    
    RETURN updated current entity
END
```

**Other Entity Updates**: None  
**Transition for Other Entities**: N/A

### Order Entity Processors

#### 4. OrderImportProcessor
**Entity**: Order  
**Transition**: import_order (none → imported)  
**Purpose**: Import order data from Pet Store API

**Input**: Empty Order entity  
**Output**: Order entity populated with API data

**Pseudocode**:
```
BEGIN process()
    CALL Pet Store API /store/inventory to get inventory data
    CALL Pet Store API /store/order/{orderId} for order details
    
    POPULATE current entity:
        SET orderId = API order.id
        SET petId = API order.petId
        SET quantity = API order.quantity
        SET shipDate = API order.shipDate
        SET orderDate = CURRENT_TIMESTAMP
        SET complete = API order.complete
        
    CALCULATE derived fields:
        GET pet entity using petId
        SET unitPrice = pet.price
        SET totalAmount = quantity * unitPrice
        
    SET customerInfo = GENERATE_MOCK_CUSTOMER_DATA()
    
    RETURN updated current entity
END
```

**Other Entity Updates**: None  
**Transition for Other Entities**: N/A

#### 5. OrderValidationProcessor
**Entity**: Order  
**Transition**: validate_order (imported → validated)  
**Purpose**: Validate order data and ensure pet relationship exists

**Input**: Order entity with imported data  
**Output**: Validated Order entity

**Pseudocode**:
```
BEGIN process()
    VALIDATE current entity:
        IF orderId is null OR petId is null:
            THROW validation error
        END IF
        
        IF quantity <= 0:
            THROW validation error
        END IF
    
    VERIFY pet relationship:
        GET pet entity using petId from entityService
        IF pet not found:
            THROW relationship error
        END IF
    
    RETURN current entity (unchanged)
END
```

**Other Entity Updates**: None  
**Transition for Other Entities**: N/A

#### 6. OrderProcessingProcessor
**Entity**: Order  
**Transition**: process_order (validated → processed)  
**Purpose**: Process order for analytics and update pet performance

**Input**: Validated Order entity  
**Output**: Processed Order entity

**Pseudocode**:
```
BEGIN process()
    UPDATE pet performance:
        GET pet entity using petId from entityService
        UPDATE pet entity:
            INCREMENT salesVolume by current order quantity
            INCREMENT revenue by current order totalAmount
            SET lastSaleDate = current order orderDate
            SET updatedAt = CURRENT_TIMESTAMP
        SAVE updated pet entity using entityService
    
    RETURN current entity (unchanged)
END
```

**Other Entity Updates**: Updates Pet entity via entityService  
**Transition for Other Entities**: null (no state transition for Pet)

#### 7. OrderCompletionProcessor
**Entity**: Order  
**Transition**: complete_order (processed → completed)  
**Purpose**: Mark order as completed and finalize processing

**Input**: Processed Order entity  
**Output**: Completed Order entity

**Pseudocode**:
```
BEGIN process()
    SET complete = true
    SET updatedAt = CURRENT_TIMESTAMP
    
    LOG order completion for audit trail
    
    RETURN updated current entity
END
```

**Other Entity Updates**: None  
**Transition for Other Entities**: N/A

### Report Entity Processors

#### 8. ReportSchedulingProcessor
**Entity**: Report  
**Transition**: schedule_report (none → scheduled)  
**Purpose**: Schedule weekly report generation

**Input**: Empty Report entity  
**Output**: Scheduled Report entity

**Pseudocode**:
```
BEGIN process()
    CALCULATE report period:
        endDate = CURRENT_DATE
        startDate = endDate - 7 days
    
    POPULATE current entity:
        SET reportId = GENERATE_UUID()
        SET reportType = "WEEKLY_PERFORMANCE"
        SET generationDate = CURRENT_TIMESTAMP
        SET reportPeriod.startDate = startDate
        SET reportPeriod.endDate = endDate
        SET fileFormat = "PDF"
    
    RETURN updated current entity
END
```

**Other Entity Updates**: None  
**Transition for Other Entities**: N/A

#### 9. ReportGenerationProcessor
**Entity**: Report  
**Transition**: start_generation (scheduled → generating)  
**Purpose**: Generate performance analysis report

**Input**: Scheduled Report entity  
**Output**: Report entity with generated content

**Pseudocode**:
```
BEGIN process()
    GET all pets from entityService
    GET all orders within report period from entityService
    
    CALCULATE metrics:
        totalSales = SUM(order.quantity) for period orders
        totalRevenue = SUM(order.totalAmount) for period orders
        topSellingPets = TOP 5 pets by salesVolume
        slowMovingPets = pets with salesVolume < THRESHOLD
        inventoryTurnover = totalSales / AVERAGE(pet.stockLevel)
    
    GENERATE insights:
        FOR each metric:
            CREATE insight based on performance thresholds
            ADD to insights list
        END FOR
    
    CREATE report file:
        filePath = GENERATE_REPORT_FILE(metrics, insights)
        fileSize = GET_FILE_SIZE(filePath)
    
    UPDATE current entity:
        SET metrics = calculated metrics
        SET insights = generated insights
        SET filePath = filePath
        SET fileSize = fileSize
    
    RETURN updated current entity
END
```

**Other Entity Updates**: None  
**Transition for Other Entities**: N/A

#### 10. ReportCompletionProcessor
**Entity**: Report  
**Transition**: complete_generation (generating → generated)  
**Purpose**: Finalize report generation and prepare for distribution

**Input**: Report entity with generated content  
**Output**: Completed Report entity

**Pseudocode**:
```
BEGIN process()
    VALIDATE report file exists and is readable
    
    UPDATE current entity:
        SET generationDate = CURRENT_TIMESTAMP
    
    CREATE email notification:
        CREATE new EmailNotification entity
        SET reportId = current entity reportId
        SET recipientEmail = "victoria.sagdieva@cyoda.com"
        SET subject = "Weekly Performance Report - " + generationDate
        SET body = GENERATE_EMAIL_BODY(current entity)
        SET attachmentPath = current entity filePath
        SET scheduledTime = CURRENT_TIMESTAMP + 5 minutes
        SAVE EmailNotification using entityService
    
    RETURN current entity (unchanged)
END
```

**Other Entity Updates**: Creates EmailNotification entity via entityService  
**Transition for Other Entities**: schedule_notification (none → scheduled)

#### 11. ReportDistributionProcessor
**Entity**: Report  
**Transition**: distribute_report (reviewed → distributed)  
**Purpose**: Distribute report to stakeholders

**Input**: Reviewed Report entity  
**Output**: Distributed Report entity

**Pseudocode**:
```
BEGIN process()
    GET all email notifications for current report from entityService
    
    FOR each notification:
        TRIGGER notification sending by updating notification state
        UPDATE notification using entityService with transition "send_notification"
    END FOR
    
    RETURN current entity (unchanged)
END
```

**Other Entity Updates**: Updates EmailNotification entities via entityService  
**Transition for Other Entities**: send_notification (scheduled → sending)

### EmailNotification Entity Processors

#### 12. NotificationSchedulingProcessor
**Entity**: EmailNotification  
**Transition**: schedule_notification (none → scheduled)  
**Purpose**: Schedule email notification for delivery

**Input**: EmailNotification entity with basic data  
**Output**: Scheduled EmailNotification entity

**Pseudocode**:
```
BEGIN process()
    SET notificationId = GENERATE_UUID()
    SET deliveryAttempts = 0
    SET lastError = null
    
    VALIDATE email format and report existence
    
    RETURN updated current entity
END
```

**Other Entity Updates**: None  
**Transition for Other Entities**: N/A

#### 13. EmailSendingProcessor
**Entity**: EmailNotification  
**Transition**: send_notification (scheduled → sending)  
**Purpose**: Send email notification with report attachment

**Input**: Scheduled EmailNotification entity  
**Output**: EmailNotification entity with sending status

**Pseudocode**:
```
BEGIN process()
    INCREMENT deliveryAttempts
    
    TRY:
        SEND email with:
            to = recipientEmail
            subject = subject
            body = body
            attachment = attachmentPath
        
        SET sentTime = CURRENT_TIMESTAMP
        CLEAR lastError
        
    CATCH email sending error:
        SET lastError = error message
        THROW error for transition to failed state
    
    RETURN updated current entity
END
```

**Other Entity Updates**: None  
**Transition for Other Entities**: N/A

#### 14. DeliveryConfirmationProcessor
**Entity**: EmailNotification  
**Transition**: confirm_delivery (sending → sent)  
**Purpose**: Confirm successful email delivery

**Input**: EmailNotification entity in sending state  
**Output**: Confirmed EmailNotification entity

**Pseudocode**:
```
BEGIN process()
    LOG successful delivery for audit
    
    RETURN current entity (unchanged)
END
```

**Other Entity Updates**: None  
**Transition for Other Entities**: N/A

#### 15. DeliveryFailureProcessor
**Entity**: EmailNotification  
**Transition**: handle_failure (sending → failed)  
**Purpose**: Handle email delivery failure

**Input**: EmailNotification entity with delivery failure  
**Output**: Failed EmailNotification entity

**Pseudocode**:
```
BEGIN process()
    LOG delivery failure with error details
    
    IF deliveryAttempts >= 3:
        LOG permanent failure
        SEND alert to system administrators
    END IF
    
    RETURN current entity (unchanged)
END
```

**Other Entity Updates**: None  
**Transition for Other Entities**: N/A

#### 16. RetryProcessor
**Entity**: EmailNotification  
**Transition**: retry_sending (failed → scheduled)  
**Purpose**: Retry failed email notification

**Input**: Failed EmailNotification entity  
**Output**: Rescheduled EmailNotification entity

**Pseudocode**:
```
BEGIN process()
    CALCULATE retry delay based on attempt number:
        delay = deliveryAttempts * 30 minutes
    
    SET scheduledTime = CURRENT_TIMESTAMP + delay
    CLEAR lastError
    
    RETURN updated current entity
END
```

**Other Entity Updates**: None  
**Transition for Other Entities**: N/A

## Processor Implementation Notes

### Error Handling
- All processors must handle exceptions gracefully
- Failed processors should log errors for debugging
- Critical failures should trigger alerts

### Performance Considerations
- Use batch processing for large datasets
- Implement caching for frequently accessed data
- Optimize API calls to minimize external dependencies

### Security
- Validate all input data
- Sanitize data before external API calls
- Secure email credentials and API keys
