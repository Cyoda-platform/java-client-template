# Entity Requirements

## Overview
This document defines the entities required for the Product Performance Analysis and Reporting System. The system integrates with the Pet Store API to collect product data, analyze performance metrics, and generate automated reports.

## Entity Definitions

### 1. Pet Entity
**Purpose**: Represents pet products from the Pet Store API with performance tracking capabilities.

**Attributes**:
- `petId` (Long): Unique identifier from Pet Store API
- `name` (String): Pet name (required)
- `category` (Category): Pet category information
- `photoUrls` (List<String>): Product image URLs
- `tags` (List<Tag>): Product tags for categorization
- `price` (Double): Current selling price
- `stockLevel` (Integer): Current inventory count
- `salesVolume` (Integer): Total units sold
- `revenue` (Double): Total revenue generated
- `lastSaleDate` (LocalDateTime): Date of last sale
- `createdAt` (LocalDateTime): Record creation timestamp
- `updatedAt` (LocalDateTime): Last update timestamp

**Nested Classes**:
- `Category`: id (Long), name (String)
- `Tag`: id (Long), name (String)

**Relationships**:
- One Pet can have multiple Orders (One-to-Many)
- One Pet can appear in multiple Reports (Many-to-Many)

**Business Rules**:
- Pet ID must be unique and match Pet Store API
- Name is required field
- Stock level cannot be negative
- Sales volume and revenue are calculated fields

**Entity State**: 
The system will use `entity.meta.state` to track pet lifecycle states. The user requirement mentions "status" in the Pet Store API (available/pending/sold), but this will be mapped to our internal state management system rather than being a separate field.

### 2. Order Entity
**Purpose**: Represents purchase orders for pets, tracking sales transactions and performance metrics.

**Attributes**:
- `orderId` (Long): Unique order identifier from Pet Store API
- `petId` (Long): Reference to purchased pet
- `quantity` (Integer): Number of items ordered
- `unitPrice` (Double): Price per unit at time of sale
- `totalAmount` (Double): Total order value (quantity * unitPrice)
- `shipDate` (LocalDateTime): Scheduled shipping date
- `orderDate` (LocalDateTime): Order placement date
- `customerInfo` (CustomerInfo): Customer details
- `complete` (Boolean): Order completion status

**Nested Classes**:
- `CustomerInfo`: name (String), email (String), phone (String)

**Relationships**:
- Many Orders belong to One Pet (Many-to-One)
- One Order can be included in multiple Reports (Many-to-Many)

**Business Rules**:
- Order ID must be unique and match Pet Store API
- Quantity must be positive
- Total amount is calculated field (quantity * unitPrice)
- Ship date cannot be before order date

**Entity State**:
The system will use `entity.meta.state` to track order states. The Pet Store API uses "status" (placed/approved/delivered), which will be mapped to our internal state management.

### 3. Report Entity
**Purpose**: Represents generated performance analysis reports with aggregated metrics and insights.

**Attributes**:
- `reportId` (String): Unique report identifier (UUID)
- `reportType` (String): Type of report (WEEKLY_PERFORMANCE, INVENTORY_ANALYSIS)
- `generationDate` (LocalDateTime): Report creation timestamp
- `reportPeriod` (ReportPeriod): Time period covered by report
- `metrics` (PerformanceMetrics): Aggregated performance data
- `insights` (List<Insight>): Generated business insights
- `filePath` (String): Path to generated report file
- `fileFormat` (String): Report format (PDF, HTML, JSON)
- `fileSize` (Long): Report file size in bytes

**Nested Classes**:
- `ReportPeriod`: startDate (LocalDateTime), endDate (LocalDateTime)
- `PerformanceMetrics`: totalSales (Double), totalRevenue (Double), topSellingPets (List<String>), slowMovingPets (List<String>), inventoryTurnover (Double)
- `Insight`: category (String), description (String), priority (String), actionRequired (Boolean)

**Relationships**:
- One Report can reference multiple Pets (Many-to-Many)
- One Report can reference multiple Orders (Many-to-Many)
- One Report can have multiple EmailNotifications (One-to-Many)

**Business Rules**:
- Report ID must be unique
- Generation date is automatically set
- Report period end date must be after start date
- File path is required for completed reports

**Entity State**:
The system will use `entity.meta.state` to track report generation states (PENDING, GENERATING, COMPLETED, FAILED).

### 4. EmailNotification Entity
**Purpose**: Manages email notifications for report delivery to stakeholders.

**Attributes**:
- `notificationId` (String): Unique notification identifier (UUID)
- `reportId` (String): Reference to associated report
- `recipientEmail` (String): Email address of recipient
- `subject` (String): Email subject line
- `body` (String): Email body content
- `attachmentPath` (String): Path to report attachment
- `scheduledTime` (LocalDateTime): Scheduled send time
- `sentTime` (LocalDateTime): Actual send time
- `deliveryAttempts` (Integer): Number of delivery attempts
- `lastError` (String): Last error message if delivery failed

**Relationships**:
- Many EmailNotifications belong to One Report (Many-to-One)

**Business Rules**:
- Notification ID must be unique
- Recipient email must be valid format
- Scheduled time cannot be in the past
- Maximum 3 delivery attempts allowed
- Report must exist before creating notification

**Entity State**:
The system will use `entity.meta.state` to track notification states (SCHEDULED, SENDING, SENT, FAILED, CANCELLED).

## Entity Relationships Summary

```
Pet (1) ←→ (M) Order
Pet (M) ←→ (M) Report
Order (M) ←→ (M) Report  
Report (1) ←→ (M) EmailNotification
```

## Data Flow
1. Pet data is extracted from Pet Store API and stored as Pet entities
2. Order data is extracted and linked to Pet entities
3. Performance analysis generates Report entities with aggregated metrics
4. EmailNotification entities are created to deliver reports to stakeholders

## Validation Rules
- All entities must implement `CyodaEntity` interface
- Required fields must be validated in `isValid()` method
- Foreign key relationships must be maintained
- Timestamps must be in UTC format
- Email addresses must follow RFC 5322 standard
