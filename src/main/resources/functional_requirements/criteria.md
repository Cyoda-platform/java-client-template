# Purrfect Pets API - Criteria Requirements

## Overview
This document defines the detailed requirements for all criteria in the Purrfect Pets API application. Criteria implement conditional logic for workflow transitions.

## Pet Criteria

### 1. PetValidityCriterion

**Entity**: Pet
**Transition**: make_available (draft → available)
**Purpose**: Validate that a pet meets all requirements to be made available for sale

**Validation Rules**:
1. Pet name must not be null or empty
2. Pet name must be between 1 and 100 characters
3. At least one photo URL must be provided
4. All photo URLs must be valid HTTP/HTTPS URLs
5. If category is provided, it must exist and be active
6. If tags are provided, all tags must exist and be active
7. Pet must not already be in available, pending, or sold state

**Pseudocode**:
```
check(pet):
    1. IF pet is null THEN return FAIL("Pet entity is null")
    2. IF pet.name is null or empty THEN return FAIL("Pet name is required")
    3. IF pet.name length > 100 THEN return FAIL("Pet name too long")
    4. IF pet.photoUrls is null or empty THEN return FAIL("At least one photo URL required")
    5. FOR each photoUrl in pet.photoUrls:
        IF not valid URL format THEN return FAIL("Invalid photo URL format")
    6. IF pet.category is not null:
        IF category does not exist THEN return FAIL("Category does not exist")
        IF category state is not 'active' THEN return FAIL("Category is not active")
    7. IF pet.tags is not null:
        FOR each tag in pet.tags:
            IF tag does not exist THEN return FAIL("Tag does not exist")
            IF tag state is not 'active' THEN return FAIL("Tag is not active")
    8. IF pet.meta.state in ['available', 'pending', 'sold'] THEN return FAIL("Pet is already processed")
    9. return SUCCESS()
```

## Order Criteria

### 2. OrderValidityCriterion

**Entity**: Order
**Transition**: place_order (none → placed)
**Purpose**: Validate that an order can be placed

**Validation Rules**:
1. Pet ID must be provided and pet must exist
2. Pet must be in 'available' state
3. Quantity must be positive integer
4. Quantity must not exceed available stock (assume 1 per pet)
5. If user ID is provided, user must exist and be active

**Pseudocode**:
```
check(order):
    1. IF order is null THEN return FAIL("Order entity is null")
    2. IF order.petId is null THEN return FAIL("Pet ID is required")
    3. pet = getPetById(order.petId)
    4. IF pet is null THEN return FAIL("Pet does not exist")
    5. IF pet.meta.state != 'available' THEN return FAIL("Pet is not available for purchase")
    6. IF order.quantity is null or <= 0 THEN return FAIL("Quantity must be positive")
    7. IF order.quantity > 1 THEN return FAIL("Only one pet per order allowed")
    8. IF order.userId is not null:
        user = getUserById(order.userId)
        IF user is null THEN return FAIL("User does not exist")
        IF user.meta.state != 'active' THEN return FAIL("User account is not active")
    9. return SUCCESS()
```

### 3. OrderApprovalCriterion

**Entity**: Order
**Transition**: approve_order (placed → approved)
**Purpose**: Validate that an order can be approved

**Validation Rules**:
1. Order must be in 'placed' state
2. Pet must still be in 'pending' state (reserved)
3. Payment validation (if payment system exists)
4. User account must still be active (if user exists)

**Pseudocode**:
```
check(order):
    1. IF order is null THEN return FAIL("Order entity is null")
    2. IF order.meta.state != 'placed' THEN return FAIL("Order is not in placed state")
    3. pet = getPetById(order.petId)
    4. IF pet is null THEN return FAIL("Pet no longer exists")
    5. IF pet.meta.state != 'pending' THEN return FAIL("Pet is not reserved")
    6. IF order.userId is not null:
        user = getUserById(order.userId)
        IF user is null THEN return FAIL("User no longer exists")
        IF user.meta.state != 'active' THEN return FAIL("User account is not active")
    7. // Payment validation would go here if payment system exists
    8. return SUCCESS()
```

## User Criteria

### 4. UserValidityCriterion

**Entity**: User
**Transition**: register_user (none → registered)
**Purpose**: Validate that a user can be registered

**Validation Rules**:
1. Username must be provided and unique
2. Email must be provided, valid format, and unique
3. Password must meet security requirements
4. Phone number must be valid format if provided
5. First name and last name should be reasonable length

**Pseudocode**:
```
check(user):
    1. IF user is null THEN return FAIL("User entity is null")
    2. IF user.username is null or empty THEN return FAIL("Username is required")
    3. IF user.username length < 3 or > 50 THEN return FAIL("Username must be 3-50 characters")
    4. IF usernameExists(user.username) THEN return FAIL("Username already exists")
    5. IF user.email is null or empty THEN return FAIL("Email is required")
    6. IF not validEmailFormat(user.email) THEN return FAIL("Invalid email format")
    7. IF emailExists(user.email) THEN return FAIL("Email already exists")
    8. IF user.password is null or empty THEN return FAIL("Password is required")
    9. IF user.password length < 8 THEN return FAIL("Password must be at least 8 characters")
    10. IF not containsUppercase(user.password) THEN return FAIL("Password must contain uppercase letter")
    11. IF not containsLowercase(user.password) THEN return FAIL("Password must contain lowercase letter")
    12. IF not containsDigit(user.password) THEN return FAIL("Password must contain digit")
    13. IF user.phone is not null and not validPhoneFormat(user.phone) THEN return FAIL("Invalid phone format")
    14. IF user.firstName is not null and user.firstName length > 50 THEN return FAIL("First name too long")
    15. IF user.lastName is not null and user.lastName length > 50 THEN return FAIL("Last name too long")
    16. return SUCCESS()
```

## Category Criteria

### 5. CategoryValidityCriterion

**Entity**: Category
**Transition**: create_category (none → active)
**Purpose**: Validate that a category can be created

**Validation Rules**:
1. Category name must be provided and unique
2. Category name must be reasonable length
3. Category name should not contain special characters

**Pseudocode**:
```
check(category):
    1. IF category is null THEN return FAIL("Category entity is null")
    2. IF category.name is null or empty THEN return FAIL("Category name is required")
    3. IF category.name length < 2 or > 50 THEN return FAIL("Category name must be 2-50 characters")
    4. IF categoryNameExists(category.name) THEN return FAIL("Category name already exists")
    5. IF containsSpecialCharacters(category.name) THEN return FAIL("Category name contains invalid characters")
    6. return SUCCESS()
```

## Tag Criteria

### 6. TagValidityCriterion

**Entity**: Tag
**Transition**: create_tag (none → active)
**Purpose**: Validate that a tag can be created

**Validation Rules**:
1. Tag name must be provided and unique
2. Tag name must be reasonable length
3. Tag name should be lowercase and contain only letters, numbers, and hyphens

**Pseudocode**:
```
check(tag):
    1. IF tag is null THEN return FAIL("Tag entity is null")
    2. IF tag.name is null or empty THEN return FAIL("Tag name is required")
    3. IF tag.name length < 2 or > 30 THEN return FAIL("Tag name must be 2-30 characters")
    4. IF tagNameExists(tag.name) THEN return FAIL("Tag name already exists")
    5. IF not isLowercase(tag.name) THEN return FAIL("Tag name must be lowercase")
    6. IF not matchesPattern(tag.name, "^[a-z0-9-]+$") THEN return FAIL("Tag name contains invalid characters")
    7. return SUCCESS()
```

## General Criteria

### 7. EntityExistsCriterion

**Purpose**: Generic criterion to check if an entity exists by ID
**Used by**: Various transitions that reference other entities

**Pseudocode**:
```
check(entityType, entityId):
    1. IF entityId is null THEN return FAIL("Entity ID is required")
    2. entity = getEntityById(entityType, entityId)
    3. IF entity is null THEN return FAIL("Entity does not exist")
    4. return SUCCESS()
```

### 8. EntityStateCriterion

**Purpose**: Generic criterion to check if an entity is in a specific state
**Used by**: Various transitions that depend on entity states

**Pseudocode**:
```
check(entity, expectedState):
    1. IF entity is null THEN return FAIL("Entity is null")
    2. IF entity.meta.state != expectedState THEN return FAIL("Entity is not in expected state")
    3. return SUCCESS()
```

## Criteria Notes

1. **Error Messages**: All criteria should provide clear, specific error messages.
2. **Performance**: Criteria should be efficient as they may be called frequently.
3. **Consistency**: Similar validation rules should be consistent across different criteria.
4. **Security**: Criteria should not expose sensitive information in error messages.
5. **Reusability**: Common validation logic should be extracted into reusable criteria.
6. **Database Queries**: Criteria should minimize database queries for performance.
7. **Null Safety**: All criteria should handle null inputs gracefully.
