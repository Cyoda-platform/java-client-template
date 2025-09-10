# Purrfect Pets - Criteria Requirements

## Overview
Criteria are pure functions that evaluate conditions without side effects. They determine whether a workflow transition should proceed based on business rules and entity state validation.

## Pet Entity Criteria

### 1. PetAvailabilityCriterion
**Entity**: Pet
**Transition**: available → pending (reserve_pet)
**Purpose**: Verify that a pet is available for reservation

#### Validation Logic:
```
EVALUATE pet:
  IF pet is null THEN
    RETURN FAIL with reason "Pet not found"
  END IF
  
  IF pet.petId is null or empty THEN
    RETURN FAIL with reason "Pet ID is required"
  END IF
  
  IF pet.name is null or empty THEN
    RETURN FAIL with reason "Pet name is required"
  END IF
  
  // Note: pet.status is managed by entity.meta.state, not directly accessible
  // The workflow engine ensures this criterion is only called when pet is in "available" state
  
  IF pet.price is not null AND pet.price <= 0 THEN
    RETURN FAIL with reason "Pet price must be positive"
  END IF
  
  RETURN SUCCESS with reason "Pet is available for reservation"
```

#### Expected Outcome:
- **SUCCESS**: Pet can be reserved
- **FAIL**: Pet cannot be reserved due to validation errors

### 2. PetReturnCriterion
**Entity**: Pet
**Transition**: sold → available (return_pet)
**Purpose**: Validate that a pet can be returned (business rules)

#### Validation Logic:
```
EVALUATE pet:
  IF pet is null THEN
    RETURN FAIL with reason "Pet not found"
  END IF
  
  // Business rule: Only allow returns within 30 days (example)
  IF pet.updatedAt is not null THEN
    days_since_sale = current_date - pet.updatedAt
    IF days_since_sale > 30 THEN
      RETURN FAIL with reason "Return period expired (30 days)"
    END IF
  END IF
  
  // Additional business rules can be added here
  // e.g., health checks, return conditions, etc.
  
  RETURN SUCCESS with reason "Pet return is allowed"
```

#### Expected Outcome:
- **SUCCESS**: Pet can be returned
- **FAIL**: Pet return is not allowed due to business rules

## Order Entity Criteria

### 3. OrderValidationCriterion
**Entity**: Order
**Transition**: placed → approved (approve_order)
**Purpose**: Validate that an order meets approval requirements

#### Validation Logic:
```
EVALUATE order:
  IF order is null THEN
    RETURN FAIL with reason "Order not found"
  END IF
  
  IF order.orderId is null or empty THEN
    RETURN FAIL with reason "Order ID is required"
  END IF
  
  IF order.petId is null or empty THEN
    RETURN FAIL with reason "Pet ID is required"
  END IF
  
  IF order.quantity is null or order.quantity <= 0 THEN
    RETURN FAIL with reason "Order quantity must be positive"
  END IF
  
  IF order.customerInfo is null THEN
    RETURN FAIL with reason "Customer information is required"
  END IF
  
  IF order.customerInfo.firstName is null or empty THEN
    RETURN FAIL with reason "Customer first name is required"
  END IF
  
  IF order.customerInfo.lastName is null or empty THEN
    RETURN FAIL with reason "Customer last name is required"
  END IF
  
  IF order.customerInfo.email is null or empty THEN
    RETURN FAIL with reason "Customer email is required"
  END IF
  
  IF order.customerInfo.email does not match email pattern THEN
    RETURN FAIL with reason "Invalid email format"
  END IF
  
  // Validate that the pet exists and is in pending state
  // Note: This would typically be handled by the workflow engine
  // but we can add additional business validation here
  
  RETURN SUCCESS with reason "Order is valid for approval"
```

#### Expected Outcome:
- **SUCCESS**: Order can be approved
- **FAIL**: Order cannot be approved due to validation errors

### 4. DeliveryReadinessCriterion
**Entity**: Order
**Transition**: approved → delivered (deliver_order)
**Purpose**: Verify that an order is ready for delivery

#### Validation Logic:
```
EVALUATE order:
  IF order is null THEN
    RETURN FAIL with reason "Order not found"
  END IF
  
  IF order.shippingAddress is null THEN
    RETURN FAIL with reason "Shipping address is required for delivery"
  END IF
  
  IF order.shippingAddress.street is null or empty THEN
    RETURN FAIL with reason "Street address is required"
  END IF
  
  IF order.shippingAddress.city is null or empty THEN
    RETURN FAIL with reason "City is required"
  END IF
  
  IF order.shippingAddress.zipCode is null or empty THEN
    RETURN FAIL with reason "ZIP code is required"
  END IF
  
  IF order.shippingAddress.country is null or empty THEN
    RETURN FAIL with reason "Country is required"
  END IF
  
  // Check if ship date has passed (if set)
  IF order.shipDate is not null AND current_date < order.shipDate THEN
    RETURN FAIL with reason "Order is not ready for delivery yet"
  END IF
  
  // Ensure total amount is calculated
  IF order.totalAmount is null or order.totalAmount <= 0 THEN
    RETURN FAIL with reason "Order total amount must be calculated"
  END IF
  
  RETURN SUCCESS with reason "Order is ready for delivery"
```

#### Expected Outcome:
- **SUCCESS**: Order can be delivered
- **FAIL**: Order is not ready for delivery

### 5. OrderCompletionCriterion
**Entity**: Pet (used in pet workflow)
**Transition**: pending → sold (complete_sale)
**Purpose**: Verify that the associated order is ready for completion

#### Validation Logic:
```
EVALUATE pet:
  IF pet is null THEN
    RETURN FAIL with reason "Pet not found"
  END IF
  
  // This criterion would typically receive order context
  // or would need to look up the order associated with this pet
  // For simplicity, we'll assume basic pet validation
  
  IF pet.petId is null or empty THEN
    RETURN FAIL with reason "Pet ID is required"
  END IF
  
  // Additional business rules for completing a sale
  IF pet.vaccinated is not null AND pet.vaccinated = false THEN
    RETURN FAIL with reason "Pet must be vaccinated before sale completion"
  END IF
  
  // Could add more complex validation here, such as:
  // - Health certificate requirements
  // - Age restrictions
  // - Special handling requirements
  
  RETURN SUCCESS with reason "Pet sale can be completed"
```

#### Expected Outcome:
- **SUCCESS**: Pet sale can be completed
- **FAIL**: Pet sale cannot be completed due to business rules

## Category Entity Criteria

### 6. CategoryValidationCriterion (Optional)
**Entity**: Category
**Purpose**: Basic category validation (if needed for complex workflows)

#### Validation Logic:
```
EVALUATE category:
  IF category is null THEN
    RETURN FAIL with reason "Category not found"
  END IF
  
  IF category.categoryId is null or empty THEN
    RETURN FAIL with reason "Category ID is required"
  END IF
  
  IF category.name is null or empty THEN
    RETURN FAIL with reason "Category name is required"
  END IF
  
  RETURN SUCCESS with reason "Category is valid"
```

## Tag Entity Criteria

### 7. TagValidationCriterion (Optional)
**Entity**: Tag
**Purpose**: Basic tag validation (if needed for complex workflows)

#### Validation Logic:
```
EVALUATE tag:
  IF tag is null THEN
    RETURN FAIL with reason "Tag not found"
  END IF
  
  IF tag.tagId is null or empty THEN
    RETURN FAIL with reason "Tag ID is required"
  END IF
  
  IF tag.name is null or empty THEN
    RETURN FAIL with reason "Tag name is required"
  END IF
  
  RETURN SUCCESS with reason "Tag is valid"
```

## Criteria Design Principles

### Pure Functions:
- Criteria must NOT modify entities or have side effects
- They should only evaluate conditions and return SUCCESS or FAIL
- No EntityService calls for updates (read-only operations are acceptable)

### Error Handling:
- Always provide meaningful error messages for business users
- Use appropriate StandardEvalReasonCategories:
  - `VALIDATION_ERROR` for data validation failures
  - `BUSINESS_RULE_VIOLATION` for business logic violations
  - `INSUFFICIENT_DATA` for missing required information

### Performance:
- Keep criteria evaluation fast and lightweight
- Avoid complex calculations or external API calls
- Cache frequently used validation results if needed

### Reason Categories:
- Use `StandardEvalReasonCategories.VALIDATION_ERROR` for basic validation
- Use `StandardEvalReasonCategories.BUSINESS_RULE_VIOLATION` for business logic
- Use `StandardEvalReasonCategories.INSUFFICIENT_DATA` for missing data
- Use `StandardEvalReasonCategories.SYSTEM_ERROR` for technical issues

### Context Usage:
- Criteria can access entity data and workflow context
- Use context to pass additional parameters when needed
- Keep context usage minimal and well-documented

This criteria design ensures robust validation while maintaining the pure function principle required by the Cyoda framework.
