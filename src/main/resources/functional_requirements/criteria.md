# Purrfect Pets - Criteria Requirements

## Overview
This document defines the criteria for conditional logic in the Purrfect Pets API workflows. Criteria are used to validate conditions before allowing workflow transitions.

## Order Criteria

### 1. OrderApprovalCriterion

**Entity**: Order
**Transition**: approve_order (placed → approved)
**Purpose**: Validates that an order can be approved

**Validation Logic**:
```
EVALUATE order_approval:
  1. Validate order is in 'placed' state
  2. Validate associated pet exists and is in 'pending' state
  3. Validate user exists and is in 'active' state
  4. Validate order quantity is positive
  5. Validate pet is still available for the requested quantity
  6. Validate user has no suspended orders
  7. Return SUCCESS if all validations pass
  8. Return FAILURE with specific reason if any validation fails
```

**Failure Reasons**:
- "Order is not in placed state"
- "Associated pet not found or not in pending state"
- "User account is not active"
- "Invalid order quantity"
- "Pet no longer available"
- "User has suspended orders"

## User Criteria

### 2. UserUnsuspensionCriterion

**Entity**: User
**Transition**: unsuspend_user (suspended → active)
**Purpose**: Validates that a user can be unsuspended

**Validation Logic**:
```
EVALUATE user_unsuspension:
  1. Validate user is in 'suspended' state
  2. Validate suspension period has ended (if time-based)
  3. Validate any required conditions are met
  4. Validate no pending violations exist
  5. Validate user has completed any required actions
  6. Return SUCCESS if all conditions are met
  7. Return FAILURE with specific reason if conditions not met
```

**Failure Reasons**:
- "User is not in suspended state"
- "Suspension period has not ended"
- "Required conditions not met"
- "Pending violations exist"
- "Required actions not completed"

## Category Criteria

### 3. CategoryDeactivationCriterion

**Entity**: Category
**Transition**: deactivate_category (active → inactive)
**Purpose**: Validates that a category can be deactivated

**Validation Logic**:
```
EVALUATE category_deactivation:
  1. Validate category is in 'active' state
  2. Check if any pets are currently using this category
  3. Validate no pets in 'available' or 'pending' state use this category
  4. Return SUCCESS if category can be safely deactivated
  5. Return FAILURE if pets are still using the category
```

**Failure Reasons**:
- "Category is not in active state"
- "Category is being used by active pets"
- "Category has pets in available or pending state"

## Pet Criteria

### 4. PetAvailabilityCriterion

**Entity**: Pet
**Transition**: reserve_pet (available → pending)
**Purpose**: Validates that a pet is available for reservation

**Validation Logic**:
```
EVALUATE pet_availability:
  1. Validate pet is in 'available' state
  2. Validate pet category is in 'active' state
  3. Validate all pet tags are in 'active' state
  4. Validate pet has valid photo URLs
  5. Validate pet data integrity
  6. Return SUCCESS if pet is available for reservation
  7. Return FAILURE with specific reason if not available
```

**Failure Reasons**:
- "Pet is not in available state"
- "Pet category is not active"
- "One or more pet tags are not active"
- "Pet has invalid photo URLs"
- "Pet data integrity issues"

## Store Criteria

### 5. StoreOperationalCriterion

**Entity**: Store
**Transition**: close_store (open → closed)
**Purpose**: Validates that a store can be closed

**Validation Logic**:
```
EVALUATE store_closure:
  1. Validate store is in 'open' state
  2. Check for pending orders that need processing
  3. Validate no critical operations are in progress
  4. Validate all staff have been notified
  5. Return SUCCESS if store can be safely closed
  6. Return FAILURE if closure would disrupt operations
```

**Failure Reasons**:
- "Store is not in open state"
- "Pending orders require processing"
- "Critical operations in progress"
- "Staff notification incomplete"

## General Validation Criteria

### 6. EntityIntegrityCriterion

**Entity**: All entities
**Purpose**: General entity data integrity validation

**Validation Logic**:
```
EVALUATE entity_integrity:
  1. Validate all required fields are present
  2. Validate field data types and formats
  3. Validate foreign key references exist
  4. Validate business rule constraints
  5. Validate data consistency
  6. Return SUCCESS if entity data is valid
  7. Return FAILURE with specific validation errors
```

**Failure Reasons**:
- "Required field missing: {fieldName}"
- "Invalid data type for field: {fieldName}"
- "Referenced entity not found: {entityType}#{entityId}"
- "Business rule violation: {ruleName}"
- "Data consistency error: {description}"

### 7. PermissionCriterion

**Entity**: All entities
**Purpose**: User permission validation for operations

**Validation Logic**:
```
EVALUATE user_permissions:
  1. Validate user is authenticated
  2. Validate user has required role for operation
  3. Validate user has permission for specific entity
  4. Validate operation is allowed in current context
  5. Return SUCCESS if user has required permissions
  6. Return FAILURE with permission error
```

**Failure Reasons**:
- "User not authenticated"
- "Insufficient role for operation"
- "No permission for this entity"
- "Operation not allowed in current context"

## State Validation Criteria

### 8. StateTransitionCriterion

**Entity**: All entities
**Purpose**: Validates state transition is allowed

**Validation Logic**:
```
EVALUATE state_transition:
  1. Validate current entity state
  2. Validate target state is reachable from current state
  3. Validate transition is defined in workflow
  4. Validate no conflicting operations are in progress
  5. Return SUCCESS if transition is allowed
  6. Return FAILURE with transition error
```

**Failure Reasons**:
- "Invalid current state: {currentState}"
- "Target state not reachable: {targetState}"
- "Transition not defined in workflow"
- "Conflicting operation in progress"

## Business Rule Criteria

### 9. InventoryCriterion

**Entity**: Pet, Order
**Purpose**: Validates inventory availability

**Validation Logic**:
```
EVALUATE inventory_availability:
  1. Validate requested quantity is available
  2. Validate pet is not already reserved
  3. Validate store has sufficient inventory
  4. Validate no inventory conflicts exist
  5. Return SUCCESS if inventory is available
  6. Return FAILURE with inventory error
```

**Failure Reasons**:
- "Insufficient inventory for requested quantity"
- "Pet is already reserved"
- "Store inventory insufficient"
- "Inventory conflict detected"

### 10. TimingCriterion

**Entity**: Order, User
**Purpose**: Validates timing constraints

**Validation Logic**:
```
EVALUATE timing_constraints:
  1. Validate operation is within allowed time window
  2. Validate no time-based restrictions apply
  3. Validate scheduling constraints are met
  4. Validate deadline requirements
  5. Return SUCCESS if timing is valid
  6. Return FAILURE with timing error
```

**Failure Reasons**:
- "Operation outside allowed time window"
- "Time-based restriction applies"
- "Scheduling constraint violation"
- "Deadline requirement not met"

## Criteria Implementation Notes

1. **Return Types**: Use EvaluationOutcome.success() or EvaluationOutcome.Fail.* methods
2. **Chaining**: Use .and() and .or() for logical combinations
3. **Categories**: Use appropriate failure categories (structuralFailure, businessRuleFailure, dataQualityFailure)
4. **Entity Access**: Use context.entityWithMetadata() to access entity and metadata
5. **Related Entities**: Use EntityService to check related entities when needed
6. **Performance**: Keep criteria lightweight and focused on specific validations
7. **Error Messages**: Provide clear, actionable error messages
8. **Logging**: Log criteria evaluation results for debugging and audit

## Criteria Usage in Workflows

Criteria are used in workflow transitions to ensure:
- Data integrity before state changes
- Business rule compliance
- User permission validation
- System consistency
- Operational safety

Each criterion should be focused on a specific aspect of validation and return clear success/failure outcomes with descriptive reasons for failures.
