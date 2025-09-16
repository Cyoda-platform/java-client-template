# Purrfect Pets API - Criteria Requirements

## Overview
This document defines the detailed requirements for all criteria in the Purrfect Pets API application. Criteria implement conditional logic to determine whether workflow transitions should be allowed.

## Pet Criteria

### 1. PetAvailabilityCriterion
**Used in Transitions**: reserve_pet, sell_pet_direct
**Purpose**: Verify that a pet is available for reservation or sale

**Validation Logic**:
- Pet entity exists in the system
- Pet current state is 'available'
- Pet is not already reserved by another customer
- Pet has valid and complete information (name, photos)
- If pet has a category, the category must be active
- If pet has tags, all tags must be active

**Return**: 
- SUCCESS if pet is available for transaction
- FAILURE with specific reason if any condition fails

### 2. PetReturnEligibilityCriterion
**Used in Transitions**: return_pet
**Purpose**: Verify that a pet return is eligible and allowed

**Validation Logic**:
- Pet entity exists and is in 'sold' state
- Return is within allowed timeframe (e.g., 30 days from sale)
- Pet was not marked as non-returnable at time of sale
- Customer has valid return authorization
- Pet condition allows for return (if applicable)

**Return**:
- SUCCESS if return is eligible
- FAILURE with specific reason if return is not allowed

## Order Criteria

### 3. OrderValidationCriterion
**Used in Transitions**: approve_order
**Purpose**: Validate that an order can be approved for processing

**Validation Logic**:
- Order entity exists and is in 'placed' state
- Referenced pet exists and is available
- Customer exists and account is in good standing
- Order quantity is valid and available
- Payment information is valid (if required)
- Shipping address is complete and valid
- Order total is calculated correctly

**Return**:
- SUCCESS if order can be approved
- FAILURE with specific validation error

### 4. OrderDeliveryCriterion
**Used in Transitions**: deliver_order
**Purpose**: Verify that an order is ready for delivery

**Validation Logic**:
- Order entity exists and is in 'approved' state
- Pet is prepared and ready for delivery
- Shipping address is confirmed and accessible
- Delivery date is valid and not in the past
- All required documentation is complete
- Customer is available to receive delivery

**Return**:
- SUCCESS if order is ready for delivery
- FAILURE with specific delivery issue

### 5. OrderCancellationCriterion
**Used in Transitions**: cancel_approved_order
**Purpose**: Determine if an approved order can still be cancelled

**Validation Logic**:
- Order entity exists and is in 'approved' state
- Order has not yet been shipped or delivered
- Cancellation is within allowed timeframe
- Pet has not been customized or modified for this order
- No special circumstances prevent cancellation

**Return**:
- SUCCESS if cancellation is allowed
- FAILURE if cancellation is not permitted

### 6. OrderReturnEligibilityCriterion
**Used in Transitions**: return_order
**Purpose**: Verify that an order return is eligible

**Validation Logic**:
- Order entity exists and is in 'delivered' state
- Return is within allowed timeframe (e.g., 30 days)
- Pet is in returnable condition
- Customer has valid return reason
- Original payment method allows refunds
- Return policy conditions are met

**Return**:
- SUCCESS if return is eligible
- FAILURE with specific return restriction

## User Criteria

### 7. UserValidationCriterion
**Used in Transitions**: activate_user
**Purpose**: Validate that a user registration can be activated

**Validation Logic**:
- User entity exists and is in 'registered' state
- Email verification has been completed
- Username is still unique (not taken by another user)
- Account information is complete and valid
- No security flags or restrictions on the account
- Terms of service acceptance is recorded

**Return**:
- SUCCESS if user can be activated
- FAILURE with specific validation issue

### 8. UserSuspensionCriterion
**Used in Transitions**: suspend_user
**Purpose**: Determine if a user account should be suspended

**Validation Logic**:
- User entity exists and is in 'active' state
- Valid suspension reason is provided
- User has violated terms of service or policies
- Suspension is proportionate to the violation
- User has been notified of the issue (if required)
- Administrative approval for suspension exists

**Return**:
- SUCCESS if suspension is warranted
- FAILURE if suspension is not justified

### 9. UserReactivationCriterion
**Used in Transitions**: reactivate_user
**Purpose**: Verify that a suspended user can be reactivated

**Validation Logic**:
- User entity exists and is in 'suspended' state
- Suspension period has been completed
- User has addressed the issues that led to suspension
- No outstanding violations or restrictions
- Administrative approval for reactivation exists
- User account information is still valid

**Return**:
- SUCCESS if reactivation is allowed
- FAILURE if user should remain suspended

## Category Criteria

### 10. CategoryDeactivationCriterion
**Used in Transitions**: deactivate_category
**Purpose**: Determine if a category can be safely deactivated

**Validation Logic**:
- Category entity exists and is in 'active' state
- No pets are currently assigned to this category, OR
- All pets in this category can be reassigned or handled appropriately
- Category is not a required system category
- Deactivation will not break referential integrity

**Return**:
- SUCCESS if category can be deactivated
- FAILURE if deactivation would cause issues

## Tag Criteria

### 11. TagDeactivationCriterion
**Used in Transitions**: deactivate_tag
**Purpose**: Determine if a tag can be safely deactivated

**Validation Logic**:
- Tag entity exists and is in 'active' state
- Tag is not a required system tag
- Deactivation will not affect critical pet categorization
- Tag removal from existing pets is acceptable

**Return**:
- SUCCESS if tag can be deactivated
- FAILURE if deactivation would cause issues

## Criteria Implementation Guidelines

### General Principles
1. **Keep criteria simple**: Each criterion should focus on a single validation concern
2. **Clear failure reasons**: Always provide specific, actionable failure messages
3. **Performance considerations**: Criteria should execute quickly as they may be called frequently
4. **Data consistency**: Ensure criteria check current state of related entities
5. **Security validation**: Include appropriate security checks where relevant

### Error Categories
- **STRUCTURAL_FAILURE**: Entity doesn't exist or has invalid structure
- **BUSINESS_RULE_FAILURE**: Business logic prevents the transition
- **DATA_QUALITY_FAILURE**: Data quality issues prevent the transition
- **VALIDATION_FAILURE**: General validation errors

### Return Values
All criteria must return:
- **EvaluationOutcome.success()** when validation passes
- **EvaluationOutcome.Fail.{category}(reason)** when validation fails with specific category and reason

### Testing Requirements
Each criterion should be thoroughly tested with:
- Valid scenarios that should pass
- Invalid scenarios that should fail with appropriate reasons
- Edge cases and boundary conditions
- Performance under load
