# Purrfect Pets - Criteria Requirements

## Overview
This document defines the detailed requirements for all criteria in the Purrfect Pets application. Criteria are pure functions that evaluate conditions without side effects to determine if workflow transitions should proceed.

## Pet Criteria

### 1. PetAvailabilityCriterion

**Entity:** Pet  
**Transitions:** reserve_pet, sell_pet_direct  
**Purpose:** Checks if a pet is available for reservation or sale  

#### Input Data:
- Pet entity to be evaluated
- Current entity state from entity.meta.state

#### Evaluation Logic:
```
check(Pet pet):
    1. Verify pet.meta.state == "available"
    2. Check pet.isValid() returns true
    3. Verify pet has required fields:
       - petId is not null/empty
       - name is not null/empty
       - photoUrls is not null
    4. Check pet is not already reserved (no pending reservations)
    5. Return true if all conditions met, false otherwise
```

#### Return Value:
- **true:** Pet is available for reservation/sale
- **false:** Pet is not available

### 2. PetReservationValidCriterion

**Entity:** Pet  
**Transition:** complete_sale  
**Purpose:** Validates that a pet reservation is valid for sale completion  

#### Input Data:
- Pet entity in "pending" state
- Reservation details

#### Evaluation Logic:
```
check(Pet pet):
    1. Verify pet.meta.state == "pending"
    2. Check reservation has not expired (within 24 hours)
    3. Verify pet.isValid() returns true
    4. Check associated order exists and is valid
    5. Return true if all conditions met, false otherwise
```

#### Return Value:
- **true:** Reservation is valid for completion
- **false:** Reservation is invalid or expired

### 3. PetReturnEligibilityCriterion

**Entity:** Pet  
**Transition:** return_pet  
**Purpose:** Checks if a sold pet is eligible for return  

#### Input Data:
- Pet entity in "sold" state
- Return request details
- Original sale date

#### Evaluation Logic:
```
check(Pet pet):
    1. Verify pet.meta.state == "sold"
    2. Check return is within allowed timeframe (30 days)
    3. Verify associated order exists and is "delivered"
    4. Check pet health/condition allows return
    5. Validate return reason is acceptable
    6. Return true if all conditions met, false otherwise
```

#### Return Value:
- **true:** Pet is eligible for return
- **false:** Pet return is not allowed

## Order Criteria

### 1. OrderValidationCriterion

**Entity:** Order  
**Transition:** approve_order  
**Purpose:** Validates that an order can be approved  

#### Input Data:
- Order entity in "placed" state
- Customer information
- Payment details

#### Evaluation Logic:
```
check(Order order):
    1. Verify order.meta.state == "placed"
    2. Check order.isValid() returns true
    3. Validate customer exists and is active:
       - Customer ID is valid
       - Customer account is in "active" state
    4. Verify associated pet exists and is "pending"
    5. Check payment information is complete
    6. Validate order total is correct
    7. Check inventory availability
    8. Return true if all conditions met, false otherwise
```

#### Return Value:
- **true:** Order can be approved
- **false:** Order cannot be approved

### 2. OrderReadyForDeliveryCriterion

**Entity:** Order  
**Transition:** deliver_order  
**Purpose:** Checks if an approved order is ready for delivery  

#### Input Data:
- Order entity in "approved" state
- Delivery preparation status
- Shipping information

#### Evaluation Logic:
```
check(Order order):
    1. Verify order.meta.state == "approved"
    2. Check all required delivery information is present:
       - Shipping address is complete
       - Customer contact info is valid
    3. Verify associated pet is ready for delivery
    4. Check payment has been processed successfully
    5. Validate delivery logistics are arranged
    6. Return true if all conditions met, false otherwise
```

#### Return Value:
- **true:** Order is ready for delivery
- **false:** Order is not ready for delivery

### 3. OrderCancellationAllowedCriterion

**Entity:** Order  
**Transition:** cancel_approved_order  
**Purpose:** Determines if an approved order can still be cancelled  

#### Input Data:
- Order entity in "approved" state
- Cancellation request details
- Current processing status

#### Evaluation Logic:
```
check(Order order):
    1. Verify order.meta.state == "approved"
    2. Check order has not been shipped yet
    3. Verify cancellation is within allowed timeframe
    4. Check if pet preparation has not started
    5. Validate cancellation reason is acceptable
    6. Return true if all conditions met, false otherwise
```

#### Return Value:
- **true:** Approved order can be cancelled
- **false:** Approved order cannot be cancelled

### 4. OrderReturnEligibilityCriterion

**Entity:** Order  
**Transition:** return_order  
**Purpose:** Validates if a delivered order is eligible for return  

#### Input Data:
- Order entity in "delivered" state
- Return request details
- Delivery date

#### Evaluation Logic:
```
check(Order order):
    1. Verify order.meta.state == "delivered"
    2. Check return is within allowed timeframe (30 days from delivery)
    3. Verify return reason is valid
    4. Check associated pet condition allows return
    5. Validate customer return history (not excessive returns)
    6. Return true if all conditions met, false otherwise
```

#### Return Value:
- **true:** Order is eligible for return
- **false:** Order return is not allowed

## User Criteria

### 1. UserVerificationCriterion

**Entity:** User  
**Transition:** activate_user  
**Purpose:** Validates that a user can be activated  

#### Input Data:
- User entity in "registered" state
- Verification token or admin approval
- Registration details

#### Evaluation Logic:
```
check(User user):
    1. Verify user.meta.state == "registered"
    2. Check user.isValid() returns true
    3. Validate verification token if provided:
       - Token is not expired
       - Token matches user record
    4. OR validate admin approval privileges
    5. Check email address is verified
    6. Verify no duplicate active accounts with same email
    7. Return true if all conditions met, false otherwise
```

#### Return Value:
- **true:** User can be activated
- **false:** User cannot be activated

### 2. UserSuspensionCriterion

**Entity:** User  
**Transition:** suspend_user  
**Purpose:** Determines if a user account should be suspended  

#### Input Data:
- User entity in "active" state
- Suspension reason
- User activity history

#### Evaluation Logic:
```
check(User user):
    1. Verify user.meta.state == "active"
    2. Validate suspension reason is legitimate:
       - Policy violation
       - Fraudulent activity
       - Administrative decision
    3. Check user is not already under investigation
    4. Verify suspension authority (admin privileges)
    5. Return true if all conditions met, false otherwise
```

#### Return Value:
- **true:** User should be suspended
- **false:** User suspension is not warranted

### 3. UserReactivationCriterion

**Entity:** User  
**Transition:** reactivate_user  
**Purpose:** Validates that a suspended user can be reactivated  

#### Input Data:
- User entity in "suspended" state
- Reactivation request
- Suspension history

#### Evaluation Logic:
```
check(User user):
    1. Verify user.meta.state == "suspended"
    2. Check suspension period has been served
    3. Validate reactivation conditions are met:
       - Required actions completed
       - No pending violations
    4. Verify reactivation authority (admin approval)
    5. Check account information is still valid
    6. Return true if all conditions met, false otherwise
```

#### Return Value:
- **true:** User can be reactivated
- **false:** User cannot be reactivated

### 4. UserReactivationEligibilityCriterion

**Entity:** User  
**Transition:** reactivate_inactive  
**Purpose:** Determines if an inactive user is eligible for reactivation  

#### Input Data:
- User entity in "inactive" state
- Reactivation request
- Account history

#### Evaluation Logic:
```
check(User user):
    1. Verify user.meta.state == "inactive"
    2. Check account was not terminated for serious violations
    3. Validate user identity for reactivation request
    4. Check email address is still valid and accessible
    5. Verify no security concerns with reactivation
    6. Validate account information can be restored
    7. Return true if all conditions met, false otherwise
```

#### Return Value:
- **true:** Inactive user is eligible for reactivation
- **false:** Inactive user cannot be reactivated

## Criteria Implementation Notes

### Design Principles
1. **Pure Functions:** Criteria must not modify any data or have side effects
2. **Deterministic:** Same input should always produce the same output
3. **Fast Execution:** Criteria should execute quickly to avoid workflow delays
4. **Clear Logic:** Evaluation logic should be straightforward and well-documented

### Common Patterns
1. **State Validation:** Always verify the entity is in the expected state
2. **Entity Validation:** Call entity.isValid() to ensure data integrity
3. **Business Rules:** Apply specific business logic for the domain
4. **Security Checks:** Validate permissions and authorization where needed
5. **Data Consistency:** Ensure related entities are in consistent states

### Error Handling
1. **Graceful Degradation:** Return false for invalid or missing data
2. **Logging:** Log evaluation results for debugging and audit purposes
3. **Exception Safety:** Handle exceptions gracefully and return false
4. **Null Safety:** Check for null values before evaluation

### Performance Considerations
1. **Minimal Database Access:** Avoid database queries in criteria when possible
2. **Caching:** Use cached data where appropriate
3. **Early Exit:** Return false as soon as a condition fails
4. **Efficient Algorithms:** Use efficient logic for complex evaluations

### Testing Requirements
1. **Unit Tests:** Each criterion should have comprehensive unit tests
2. **Edge Cases:** Test boundary conditions and edge cases
3. **Performance Tests:** Ensure criteria execute within acceptable time limits
4. **Integration Tests:** Test criteria within workflow context
