# Purrfect Pets - Criteria Requirements

## Overview
This document defines the criteria for the Purrfect Pets application. Criteria implement conditional logic to determine whether specific transitions should be allowed or specific conditions are met.

## Pet Criteria

### 1. PetAvailabilityCriterion

**Entity**: Pet  
**Operation Name**: `PetAvailabilityCriterion`  
**Purpose**: Check if a pet is available for reservation

**Validation Logic**:
- Pet must exist
- Pet must be in 'available' state
- Pet must have valid category (active)
- Pet must have at least one photo URL

**Return**: 
- Success if pet is available for reservation
- Failure with specific reason if any condition fails

### 2. PetReservationValidCriterion

**Entity**: Pet  
**Operation Name**: `PetReservationValidCriterion`  
**Purpose**: Validate conditions for pet reservation

**Validation Logic**:
- Pet must be in 'available' state
- Pet must not have any pending orders
- Pet category must be active
- All pet tags must be active

**Return**:
- Success if reservation is valid
- Failure with specific reason if any condition fails

### 3. PetValidationCriterion

**Entity**: Pet  
**Operation Name**: `PetValidationCriterion`  
**Purpose**: Validate pet data for creation and updates

**Validation Logic**:
- Pet name must not be empty (1-100 characters)
- At least one photo URL must be provided
- Photo URLs must be valid format
- If category provided, must exist and be active
- If tags provided, all must exist and be active

**Return**:
- Success if all validations pass
- Failure with specific validation error

## Order Criteria

### 4. OrderValidationCriterion

**Entity**: Order  
**Operation Name**: `OrderValidationCriterion`  
**Purpose**: Validate order data for creation

**Validation Logic**:
- Pet ID must be provided and valid
- Pet must exist and be available
- User ID must be provided and valid
- User must exist and be active
- Quantity must be positive (> 0)
- Quantity must not exceed available stock (if applicable)

**Return**:
- Success if order is valid
- Failure with specific validation error

### 5. PetAvailableForOrderCriterion

**Entity**: Order  
**Operation Name**: `PetAvailableForOrderCriterion`  
**Purpose**: Check if the pet in the order is available

**Validation Logic**:
- Pet referenced in order must exist
- Pet must be in 'available' state
- Pet must not have conflicting reservations
- Pet category must be active

**Return**:
- Success if pet is available for ordering
- Failure if pet is not available

### 6. OrderApprovalCriterion

**Entity**: Order  
**Operation Name**: `OrderApprovalCriterion`  
**Purpose**: Check if order can be approved

**Validation Logic**:
- Order must be in 'placed' state
- Associated pet must be in 'pending' state
- User must still be active
- No payment issues (if payment system integrated)

**Return**:
- Success if order can be approved
- Failure with specific reason

## User Criteria

### 7. UserValidationCriterion

**Entity**: User  
**Operation Name**: `UserValidationCriterion`  
**Purpose**: Validate user data for registration

**Validation Logic**:
- Username must be unique (3-50 characters)
- Username must contain only alphanumeric characters and underscores
- Email must be unique and valid format
- Password must meet security requirements (min 8 characters, mixed case, numbers)
- Phone number must be valid format (if provided)
- First name and last name must not be empty (if provided)

**Return**:
- Success if user data is valid
- Failure with specific validation error

### 8. UserPermissionCriterion

**Entity**: User  
**Operation Name**: `UserPermissionCriterion`  
**Purpose**: Check user permissions for specific actions

**Validation Logic**:
- User must exist and be active
- User must not be suspended
- User must have appropriate permissions for the requested action
- Account must not be locked or restricted

**Return**:
- Success if user has required permissions
- Failure if user lacks permissions

### 9. UserDeactivationCriterion

**Entity**: User  
**Operation Name**: `UserDeactivationCriterion`  
**Purpose**: Check if user can be deactivated

**Validation Logic**:
- User must be in 'active' state
- User must not have pending orders
- User must not have outstanding obligations
- Administrative permissions for deactivation

**Return**:
- Success if user can be deactivated
- Failure with specific reason

## Category Criteria

### 10. CategoryValidationCriterion

**Entity**: Category  
**Operation Name**: `CategoryValidationCriterion`  
**Purpose**: Validate category data

**Validation Logic**:
- Category name must be unique
- Category name must not be empty (1-50 characters)
- Category name must contain only letters, numbers, and spaces
- No special characters except hyphens and underscores

**Return**:
- Success if category data is valid
- Failure with validation error

### 11. CategoryDeactivationCriterion

**Entity**: Category  
**Operation Name**: `CategoryDeactivationCriterion`  
**Purpose**: Check if category can be deactivated

**Validation Logic**:
- Category must be in 'active' state
- No pets should be actively using this category in 'available' or 'pending' states
- Administrative permissions for deactivation

**Return**:
- Success if category can be deactivated
- Failure if category is in use

## Tag Criteria

### 12. TagValidationCriterion

**Entity**: Tag  
**Operation Name**: `TagValidationCriterion`  
**Purpose**: Validate tag data

**Validation Logic**:
- Tag name must be unique
- Tag name must not be empty (1-30 characters)
- Tag name must contain only letters, numbers, and spaces
- No special characters except hyphens and underscores

**Return**:
- Success if tag data is valid
- Failure with validation error

### 13. TagDeactivationCriterion

**Entity**: Tag  
**Operation Name**: `TagDeactivationCriterion`  
**Purpose**: Check if tag can be deactivated

**Validation Logic**:
- Tag must be in 'active' state
- No pets should be actively using this tag in 'available' or 'pending' states
- Administrative permissions for deactivation

**Return**:
- Success if tag can be deactivated
- Failure if tag is in use

## Store Criteria

### 14. StoreValidationCriterion

**Entity**: Store  
**Operation Name**: `StoreValidationCriterion`  
**Purpose**: Validate store data

**Validation Logic**:
- Store name must not be empty (1-100 characters)
- Email must be valid format (if provided)
- Phone must be valid format (if provided)
- Address must be reasonable length (if provided)

**Return**:
- Success if store data is valid
- Failure with validation error

## Criteria Implementation Guidelines

### Common Validation Patterns

1. **Null/Empty Checks**: Always validate required fields are not null or empty
2. **Format Validation**: Use regex patterns for email, phone, URL validation
3. **Length Validation**: Enforce minimum and maximum length constraints
4. **Uniqueness Checks**: Verify unique constraints using EntityService
5. **State Validation**: Check entity states before allowing transitions
6. **Relationship Validation**: Verify related entities exist and are in valid states

### Error Handling

1. **Specific Error Messages**: Provide clear, actionable error messages
2. **Error Categories**: Use appropriate EvaluationOutcome failure types:
   - `structuralFailure()` for missing required data
   - `businessRuleFailure()` for business logic violations
   - `dataQualityFailure()` for format/validation issues

### Performance Considerations

1. **Efficient Queries**: Use EntityService.getById() for single entity lookups
2. **Batch Validation**: Group related validations to minimize database calls
3. **Early Exit**: Return failure immediately when critical validations fail
4. **Caching**: Cache frequently accessed reference data

### Security Considerations

1. **Input Sanitization**: Validate and sanitize all input data
2. **Permission Checks**: Verify user permissions for sensitive operations
3. **Rate Limiting**: Consider rate limiting for validation-heavy operations
4. **Audit Logging**: Log validation failures for security monitoring
