# Criteria Requirements

## Overview
Criteria implement conditional logic for workflow transitions. They validate business rules and determine whether transitions should proceed.

## Pet Criteria

### 1. PetValidationCriterion
**Entity**: Pet  
**Transition**: make_available (pending → available)  
**Purpose**: Validate pet is ready to be made available for sale  

**Validation Rules**:
- Pet name must be present and not empty
- Pet must have at least one valid photo URL
- All photo URLs must be accessible and valid format
- If category is specified, it must exist and be active
- If tags are specified, all tags must exist and be active
- Pet must not already be in sold state

**Pseudocode**:
```
validate(pet):
    if pet.name is null or empty:
        return FAIL("Pet name is required")
    
    if pet.photoUrls is empty:
        return FAIL("Pet must have at least one photo")
    
    for each photoUrl in pet.photoUrls:
        if not isValidUrl(photoUrl):
            return FAIL("Invalid photo URL: " + photoUrl)
    
    if pet.category exists:
        if not categoryExists(pet.category.id):
            return FAIL("Category does not exist")
        if not categoryIsActive(pet.category.id):
            return FAIL("Category is not active")
    
    if pet.tags exists:
        for each tag in pet.tags:
            if not tagExists(tag.id):
                return FAIL("Tag does not exist: " + tag.name)
            if not tagIsActive(tag.id):
                return FAIL("Tag is not active: " + tag.name)
    
    return SUCCESS()
```

## Category Criteria

### 2. CategoryUsageCriterion
**Entity**: Category  
**Transition**: deactivate_category (active → inactive)  
**Purpose**: Ensure category can be safely deactivated  

**Validation Rules**:
- No pets in available or sold state should reference this category
- Category must currently be in active state

**Pseudocode**:
```
validate(category):
    if category.state != "active":
        return FAIL("Category is not in active state")
    
    activePetsCount = countPetsUsingCategory(category.id, ["available", "sold"])
    
    if activePetsCount > 0:
        return FAIL("Cannot deactivate category: " + activePetsCount + " active pets are using it")
    
    return SUCCESS()
```

## Tag Criteria

### 3. TagUsageCriterion
**Entity**: Tag  
**Transition**: deactivate_tag (active → inactive)  
**Purpose**: Ensure tag can be safely deactivated  

**Validation Rules**:
- No pets in available or sold state should reference this tag
- Tag must currently be in active state

**Pseudocode**:
```
validate(tag):
    if tag.state != "active":
        return FAIL("Tag is not in active state")
    
    activePetsCount = countPetsUsingTag(tag.id, ["available", "sold"])
    
    if activePetsCount > 0:
        return FAIL("Cannot deactivate tag: " + activePetsCount + " active pets are using it")
    
    return SUCCESS()
```

## Order Criteria

### 4. OrderValidationCriterion
**Entity**: Order  
**Transition**: approve_order (placed → approved)  
**Purpose**: Validate order can be approved  

**Validation Rules**:
- Order must be in placed state
- Referenced pet must exist and be in sold state
- Order quantity must be positive
- Ship date (if specified) must be in the future
- Pet must not have been sold to another order

**Pseudocode**:
```
validate(order):
    if order.state != "placed":
        return FAIL("Order is not in placed state")
    
    pet = findPetById(order.petId)
    if pet is null:
        return FAIL("Referenced pet does not exist")
    
    if pet.state != "sold":
        return FAIL("Pet is not in sold state")
    
    if order.quantity <= 0:
        return FAIL("Order quantity must be positive")
    
    if order.shipDate exists and order.shipDate <= currentDate():
        return FAIL("Ship date must be in the future")
    
    // Check if pet was sold to this specific order
    if not isPetSoldToThisOrder(pet.id, order.id):
        return FAIL("Pet was sold to a different order")
    
    return SUCCESS()
```

## Business Rule Criteria

### 5. PetAvailabilityCriterion
**Purpose**: General criterion to check if a pet is available for purchase  
**Used by**: External validation before order placement  

**Validation Rules**:
- Pet must exist
- Pet must be in available state
- Pet must have valid category and tags
- Pet must have accessible photos

**Pseudocode**:
```
validate(petId):
    pet = findPetById(petId)
    if pet is null:
        return FAIL("Pet not found")
    
    if pet.state != "available":
        return FAIL("Pet is not available for purchase")
    
    if pet.category exists and not categoryIsActive(pet.category.id):
        return FAIL("Pet category is not active")
    
    for each tag in pet.tags:
        if not tagIsActive(tag.id):
            return FAIL("Pet has inactive tag: " + tag.name)
    
    return SUCCESS()
```

### 6. OrderCompletionCriterion
**Purpose**: Validate if an order can be marked as delivered  
**Used by**: Order delivery validation  

**Validation Rules**:
- Order must be in approved state
- Ship date must have passed
- All required delivery information must be present

**Pseudocode**:
```
validate(order):
    if order.state != "approved":
        return FAIL("Order is not approved")
    
    if order.shipDate exists and order.shipDate > currentDate():
        return FAIL("Ship date has not yet arrived")
    
    if order.deliveryAddress is null or empty:
        return FAIL("Delivery address is required")
    
    return SUCCESS()
```

## Data Quality Criteria

### 7. PetDataQualityCriterion
**Purpose**: Validate pet data quality standards  
**Used by**: Data quality checks during pet updates  

**Validation Rules**:
- Pet name should follow naming conventions
- Photo URLs should be accessible
- Category and tags should be appropriate

**Pseudocode**:
```
validate(pet):
    if pet.name.length < 2 or pet.name.length > 100:
        return FAIL("Pet name must be between 2 and 100 characters")
    
    if containsInappropriateContent(pet.name):
        return FAIL("Pet name contains inappropriate content")
    
    for each photoUrl in pet.photoUrls:
        if not isUrlAccessible(photoUrl):
            return FAIL("Photo URL is not accessible: " + photoUrl)
    
    return SUCCESS()
```

## Criterion Implementation Guidelines

### Return Values
- **SUCCESS()**: Criterion passes, transition can proceed
- **FAIL(message)**: Criterion fails with specific reason
- Use descriptive error messages for business users

### Performance Considerations
- Keep database queries minimal
- Cache frequently accessed reference data
- Use efficient lookup methods for entity existence checks

### Error Categories
- **Structural Failures**: Missing required data, invalid formats
- **Business Rule Failures**: Violates business logic
- **Data Quality Failures**: Data doesn't meet quality standards
- **State Consistency Failures**: Entity states are inconsistent

### Testing Requirements
- Test all validation paths (success and failure cases)
- Test edge cases and boundary conditions
- Verify error messages are user-friendly
- Test concurrent access scenarios
