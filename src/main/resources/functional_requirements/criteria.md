# Criteria Requirements for Purrfect Pets API

## Overview
Criteria implement conditional logic for workflow transitions. They validate entities and determine whether transitions should proceed based on business rules and data quality checks.

## Pet Criteria

### 1. PetValidationCriterion
**Entity**: Pet  
**Transition**: approve_pet (pending_review → available)  
**Purpose**: Validate pet meets all requirements for approval  

**Validation Logic**:
```
check(pet):
    return success if all of the following are true:
        - pet name is not null and not empty
        - pet name length is between 1 and 100 characters
        - pet has at least one photo URL
        - all photo URLs are valid URL format
        - if price is provided, it must be positive
        - if birth date is provided, it must not be in the future
        - if weight is provided, it must be positive
        - pet has a valid category
        - category is active
        - all tags are active (if any tags assigned)
        - pet description is not longer than 1000 characters
    return failure with specific reason if any validation fails
```

**Error Messages**:
- "Pet name is required and must be 1-100 characters"
- "Pet must have at least one photo URL"
- "Invalid photo URL format: {url}"
- "Pet price must be positive"
- "Pet birth date cannot be in the future"
- "Pet weight must be positive"
- "Pet category is required and must be active"
- "All pet tags must be active"
- "Pet description cannot exceed 1000 characters"

## Order Criteria

### 1. OrderValidationCriterion
**Entity**: Order  
**Transition**: confirm_order (pending → confirmed)  
**Purpose**: Validate order can be confirmed  

**Validation Logic**:
```
check(order):
    return success if all of the following are true:
        - customer name is not null and not empty
        - customer name length is between 1 and 100 characters
        - customer email is valid email format
        - customer phone is valid phone format
        - customer address is not null and not empty
        - pet ID references an existing pet
        - referenced pet is in 'reserved' state
        - pet is reserved for this specific order
        - quantity is positive integer
        - total amount is positive
        - total amount matches pet price * quantity
        - payment method is specified
        - order date is not in the future
    return failure with specific reason if any validation fails
```

**Error Messages**:
- "Customer name is required and must be 1-100 characters"
- "Customer email is required and must be valid format"
- "Customer phone is required and must be valid format"
- "Customer address is required"
- "Order must reference an existing pet"
- "Referenced pet must be reserved"
- "Pet is not reserved for this order"
- "Order quantity must be positive"
- "Order total amount must be positive"
- "Total amount does not match pet price"
- "Payment method is required"
- "Order date cannot be in the future"

### 2. OrderRefundEligibilityCriterion
**Entity**: Order  
**Transition**: refund_order (delivered → refunded)  
**Purpose**: Validate order is eligible for refund  

**Validation Logic**:
```
check(order):
    return success if all of the following are true:
        - order is in 'delivered' state
        - delivery date is within refund period (30 days default)
        - payment status is 'paid' or 'confirmed'
        - order has not been previously refunded
        - refund reason is provided
        - pet return conditions are met (if applicable)
    return failure with specific reason if any validation fails
```

**Error Messages**:
- "Order must be delivered to be eligible for refund"
- "Refund period has expired (30 days from delivery)"
- "Payment must be confirmed for refund"
- "Order has already been refunded"
- "Refund reason is required"
- "Pet return conditions not met"

## Category Criteria

### 1. CategoryUniquenessValidationCriterion
**Entity**: Category  
**Transition**: activate_category (none → active)  
**Purpose**: Validate category name is unique  

**Validation Logic**:
```
check(category):
    return success if:
        - category name is not null and not empty
        - category name is unique across all active categories
        - category name length is between 1 and 50 characters
        - category description does not exceed 500 characters
    return failure with specific reason if validation fails
```

**Error Messages**:
- "Category name is required and must be 1-50 characters"
- "Category name must be unique"
- "Category description cannot exceed 500 characters"

### 2. CategoryDeletionValidationCriterion
**Entity**: Category  
**Transition**: deactivate_category (active → inactive)  
**Purpose**: Validate category can be safely deactivated  

**Validation Logic**:
```
check(category):
    return success if:
        - category is not used by any pets in 'available' or 'reserved' state
        - or alternative category is specified for reassignment
    return failure if category is actively used without reassignment plan
```

**Error Messages**:
- "Category is used by active pets and cannot be deactivated"
- "Specify alternative category for pet reassignment"

## Tag Criteria

### 1. TagUniquenessValidationCriterion
**Entity**: Tag  
**Transition**: activate_tag (none → active)  
**Purpose**: Validate tag name is unique  

**Validation Logic**:
```
check(tag):
    return success if:
        - tag name is not null and not empty
        - tag name is unique across all active tags
        - tag name length is between 1 and 30 characters
        - if color is provided, it is valid hex color format
        - tag description does not exceed 200 characters
    return failure with specific reason if validation fails
```

**Error Messages**:
- "Tag name is required and must be 1-30 characters"
- "Tag name must be unique"
- "Tag color must be valid hex format (e.g., #FF0000)"
- "Tag description cannot exceed 200 characters"

## Business Rule Criteria

### 1. PetAvailabilityValidationCriterion
**Entity**: Pet  
**Transition**: reserve_pet (available → reserved)  
**Purpose**: Validate pet is available for reservation  

**Validation Logic**:
```
check(pet):
    return success if:
        - pet is in 'available' state
        - pet is not already reserved by another customer
        - pet category is active
        - all pet tags are active
        - pet has complete required information
    return failure with specific reason if validation fails
```

**Error Messages**:
- "Pet is not available for reservation"
- "Pet is already reserved by another customer"
- "Pet category is inactive"
- "Pet has inactive tags"
- "Pet information is incomplete"

### 2. CustomerValidationCriterion
**Entity**: Order  
**Transition**: create_order (none → pending)  
**Purpose**: Validate customer information for order creation  

**Validation Logic**:
```
check(order):
    return success if:
        - customer email is unique for active orders (no duplicate pending orders)
        - customer information is complete and valid
        - customer is not blacklisted
        - customer age is appropriate (if age verification required)
    return failure with specific reason if validation fails
```

**Error Messages**:
- "Customer already has a pending order"
- "Customer information is incomplete"
- "Customer is not eligible for orders"
- "Age verification required"

## Data Quality Criteria

### 1. PhotoUrlValidationCriterion
**Entity**: Pet  
**Transition**: submit_for_review (draft → pending_review)  
**Purpose**: Validate pet photo URLs are accessible and valid  

**Validation Logic**:
```
check(pet):
    return success if:
        - at least one photo URL is provided
        - all photo URLs are valid URL format
        - all photo URLs are accessible (HTTP 200 response)
        - photo URLs point to valid image formats (jpg, png, gif, webp)
        - image file sizes are within limits (max 5MB per image)
    return failure with specific reason if validation fails
```

**Error Messages**:
- "At least one photo URL is required"
- "Invalid URL format: {url}"
- "Photo URL is not accessible: {url}"
- "Invalid image format: {url}"
- "Image file size exceeds limit: {url}"

### 2. ContactInformationValidationCriterion
**Entity**: Order  
**Transition**: Multiple transitions  
**Purpose**: Validate customer contact information format  

**Validation Logic**:
```
check(order):
    return success if:
        - email format matches standard email regex pattern
        - phone number format is valid for the region
        - address contains required components (street, city, postal code)
        - postal code format is valid for the country
    return failure with specific reason if validation fails
```

**Error Messages**:
- "Invalid email format"
- "Invalid phone number format"
- "Address is incomplete"
- "Invalid postal code format"

## Criteria Implementation Guidelines

### Validation Patterns
- Use EvaluationOutcome.success() for successful validations
- Use EvaluationOutcome.Fail.businessRuleFailure() for business rule violations
- Use EvaluationOutcome.Fail.dataQualityFailure() for data quality issues
- Use EvaluationOutcome.Fail.structuralFailure() for structural problems

### Error Handling
- Provide specific, actionable error messages
- Include relevant context in error messages
- Use appropriate failure categories
- Chain multiple validations using .and() for comprehensive checks

### Performance Considerations
- Minimize external API calls during validation
- Cache validation results where appropriate
- Use efficient validation algorithms
- Implement timeout handling for external validations

### Integration Requirements
- Criteria should be stateless and thread-safe
- Support both synchronous and asynchronous validation
- Integrate with logging and monitoring systems
- Provide clear audit trails for validation decisions
