# Purrfect Pets - Criteria Requirements

## Overview
This document defines the criteria requirements for the Purrfect Pets system. Criteria are pure functions that evaluate conditions without side effects and determine whether workflow transitions should proceed.

## Pet Criteria

### 1. PetPaymentCriterion
**Entity**: Pet  
**Transitions**: complete_sale, direct_sale (pending/available → sold)  
**Purpose**: Check if payment has been confirmed for the pet sale

**Evaluation Logic**:
```
check(pet, context):
    1. Extract payment information from context or pet metadata
    2. Check if payment status is 'confirmed' or 'completed'
    3. Verify payment amount matches pet price
    4. Return true if payment is valid, false otherwise
```

**Input Validation**:
- Pet entity must not be null
- Pet price must be greater than 0
- Context should contain payment information

**Return**: Boolean (true if payment confirmed, false otherwise)

## Order Criteria

### 2. OrderValidationCriterion
**Entity**: Order  
**Transition**: approve_order (placed → approved)  
**Purpose**: Validate order details before approval

**Evaluation Logic**:
```
check(order, context):
    1. Validate order has at least one item
    2. Check if all items have valid petId references
    3. Verify total amount equals sum of (quantity × unitPrice) for all items
    4. Validate shipping address has all required fields (street, city, state, zipCode, country)
    5. Check if order amount is within acceptable limits (e.g., < $10,000)
    6. Return true if all validations pass, false otherwise
```

**Input Validation**:
- Order entity must not be null
- Order items list must not be empty
- Total amount must be greater than 0
- Shipping address must not be null

**Return**: Boolean (true if order is valid, false otherwise)

### 3. OrderDeliveryCriterion
**Entity**: Order  
**Transition**: confirm_delivery (shipped → delivered)  
**Purpose**: Check if order delivery can be confirmed

**Evaluation Logic**:
```
check(order, context):
    1. Check if shipDate is set and is in the past
    2. Verify sufficient time has passed since shipping (e.g., at least 1 day)
    3. Check if delivery confirmation is available in context
    4. Validate delivery address matches shipping address
    5. Return true if delivery can be confirmed, false otherwise
```

**Input Validation**:
- Order entity must not be null
- Order must have a valid shipDate
- Context should contain delivery information

**Return**: Boolean (true if delivery confirmed, false otherwise)

## User Criteria

### 4. UserEmailValidationCriterion
**Entity**: User  
**Transition**: activate_user (none → active)  
**Purpose**: Validate user email format and uniqueness

**Evaluation Logic**:
```
check(user, context):
    1. Validate email format using regex pattern
    2. Check email is not empty or null
    3. Verify email contains '@' and valid domain
    4. Check email length is reasonable (< 255 characters)
    5. Return true if email is valid, false otherwise
```

**Input Validation**:
- User entity must not be null
- User email must not be null

**Return**: Boolean (true if email is valid, false otherwise)

### 5. UserAccountStatusCriterion
**Entity**: User  
**Transition**: suspend_user (active → suspended)  
**Purpose**: Check if user account can be suspended

**Evaluation Logic**:
```
check(user, context):
    1. Check if user has any active orders in 'shipped' state
    2. Verify user is not already in 'suspended' state
    3. Check if suspension reason is provided in context
    4. Validate user has been active for minimum period (e.g., 24 hours)
    5. Return true if user can be suspended, false otherwise
```

**Input Validation**:
- User entity must not be null
- User must be in 'active' state
- Context should contain suspension reason

**Return**: Boolean (true if user can be suspended, false otherwise)

## Category Criteria

### 6. CategoryValidationCriterion
**Entity**: Category  
**Transition**: activate_category (none → active)  
**Purpose**: Validate category before activation

**Evaluation Logic**:
```
check(category, context):
    1. Check if category name is not empty and has reasonable length (< 100 characters)
    2. Validate category name contains only allowed characters (letters, numbers, spaces, hyphens)
    3. Verify category name is unique (if uniqueness check is available in context)
    4. Check if description length is reasonable (< 500 characters)
    5. Return true if category is valid, false otherwise
```

**Input Validation**:
- Category entity must not be null
- Category name must not be null or empty

**Return**: Boolean (true if category is valid, false otherwise)

## Pet Inventory Criteria

### 7. PetAvailabilityCriterion
**Entity**: Pet  
**Transition**: reserve_pet (available → pending)  
**Purpose**: Check if pet is available for reservation

**Evaluation Logic**:
```
check(pet, context):
    1. Verify pet is in 'available' state
    2. Check if pet has valid price (> 0)
    3. Validate pet category is active (if category info available in context)
    4. Check if pet is not already reserved by another process
    5. Return true if pet can be reserved, false otherwise
```

**Input Validation**:
- Pet entity must not be null
- Pet must have valid petId
- Pet price must be greater than 0

**Return**: Boolean (true if pet is available, false otherwise)

## Order Processing Criteria

### 8. OrderCancellationCriterion
**Entity**: Order  
**Transition**: cancel_order, cancel_approved_order (placed/approved → cancelled)  
**Purpose**: Check if order can be cancelled

**Evaluation Logic**:
```
check(order, context):
    1. Verify order is not already in 'shipped' or 'delivered' state
    2. Check if cancellation is within allowed timeframe (e.g., within 24 hours of placement)
    3. Validate cancellation reason is provided in context
    4. Check if order has not been processed for shipping
    5. Return true if order can be cancelled, false otherwise
```

**Input Validation**:
- Order entity must not be null
- Order must be in 'placed' or 'approved' state
- Context should contain cancellation reason

**Return**: Boolean (true if order can be cancelled, false otherwise)

## Implementation Guidelines

### General Rules
1. **Pure Functions**: Criteria must not modify entities or have side effects
2. **Null Safety**: Always check for null inputs and handle gracefully
3. **Context Usage**: Use context parameter for additional validation data
4. **Error Handling**: Return false for invalid inputs rather than throwing exceptions
5. **Performance**: Keep evaluation logic simple and fast
6. **Logging**: Log evaluation results for debugging (but don't modify state)

### Input Validation Pattern
```
check(entity, context):
    1. Validate entity is not null
    2. Validate required entity fields are present
    3. Perform business logic validation
    4. Return boolean result
```

### Context Usage
- Context may contain additional data needed for validation
- Common context data: payment info, delivery confirmations, user permissions
- Always check if context data exists before using it
- Context should not be modified by criteria

### Return Values
- Always return boolean (true/false)
- true = condition met, transition should proceed
- false = condition not met, transition should not proceed
- Never return null or throw exceptions for business logic failures
