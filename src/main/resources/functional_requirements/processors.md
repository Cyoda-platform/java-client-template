# Purrfect Pets - Processor Requirements

## Overview
This document defines the processor requirements for handling business logic during workflow transitions in the Purrfect Pets system.

## Pet Processors

### 1. PetReservationProcessor
**Entity**: Pet
**Transition**: reserve_pet (available → pending)
**Input**: Pet entity in available state
**Purpose**: Reserve a pet for potential purchase

**Pseudocode**:
```
process(pet):
    validate pet is in available state
    set pet.updatedAt to current timestamp
    log reservation event
    return updated pet entity
```

**Expected Output**: Pet entity with updated timestamp, state managed by workflow

### 2. PetReleaseProcessor
**Entity**: Pet
**Transition**: release_reservation (pending → available)
**Input**: Pet entity in pending state
**Purpose**: Release pet reservation and make it available again

**Pseudocode**:
```
process(pet):
    validate pet is in pending state
    set pet.updatedAt to current timestamp
    log release event
    return updated pet entity
```

**Expected Output**: Pet entity with updated timestamp, state managed by workflow

### 3. PetSaleProcessor
**Entity**: Pet
**Transition**: sell_pet (pending → sold)
**Input**: Pet entity in pending state
**Purpose**: Complete pet sale and mark as sold

**Pseudocode**:
```
process(pet):
    validate pet is in pending state
    set pet.updatedAt to current timestamp
    log sale completion event
    return updated pet entity
```

**Expected Output**: Pet entity with updated timestamp, state managed by workflow

### 4. PetUnavailableProcessor
**Entity**: Pet
**Transition**: mark_unavailable (available → unavailable)
**Input**: Pet entity in available state
**Purpose**: Mark pet as temporarily unavailable

**Pseudocode**:
```
process(pet):
    validate pet is in available state
    set pet.updatedAt to current timestamp
    log unavailable event
    return updated pet entity
```

**Expected Output**: Pet entity with updated timestamp, state managed by workflow

### 5. PetAvailableProcessor
**Entity**: Pet
**Transition**: mark_available (unavailable → available)
**Input**: Pet entity in unavailable state
**Purpose**: Mark pet as available for purchase

**Pseudocode**:
```
process(pet):
    validate pet is in unavailable state
    set pet.updatedAt to current timestamp
    log available event
    return updated pet entity
```

**Expected Output**: Pet entity with updated timestamp, state managed by workflow

## Category Processors

### 6. CategoryDeactivationProcessor
**Entity**: Category
**Transition**: deactivate_category (active → inactive)
**Input**: Category entity in active state
**Purpose**: Deactivate category and hide from listings

**Pseudocode**:
```
process(category):
    validate category is in active state
    set category.updatedAt to current timestamp
    log deactivation event
    return updated category entity
```

**Expected Output**: Category entity with updated timestamp, state managed by workflow

### 7. CategoryActivationProcessor
**Entity**: Category
**Transition**: reactivate_category (inactive → active), activate_user (none → active)
**Input**: Category entity in inactive or none state
**Purpose**: Activate category for display

**Pseudocode**:
```
process(category):
    validate category is in inactive or none state
    set category.updatedAt to current timestamp
    if category.active is null, set to true
    log activation event
    return updated category entity
```

**Expected Output**: Category entity with updated timestamp and active flag, state managed by workflow

## Order Processors

### 8. OrderPlacementProcessor
**Entity**: Order
**Transition**: place_order (none → placed)
**Input**: Order entity in none state
**Purpose**: Process order placement and validate order data

**Pseudocode**:
```
process(order):
    validate order data completeness
    calculate total amount from order items
    set order.orderDate to current timestamp
    set order.createdAt to current timestamp
    for each orderItem in order.orderItems:
        get pet by petId using entityService
        if pet exists and pet.meta.state == "available":
            update pet to pending state using entityService with transition "reserve_pet"
        else:
            throw error "Pet not available"
    log order placement event
    return updated order entity
```

**Expected Output**: Order entity with calculated totals and timestamps, referenced pets updated to pending state

### 9. OrderApprovalProcessor
**Entity**: Order
**Transition**: approve_order (placed → approved)
**Input**: Order entity in placed state
**Purpose**: Process order approval

**Pseudocode**:
```
process(order):
    validate order is in placed state
    validate all referenced pets are still in pending state
    set order.updatedAt to current timestamp
    log approval event
    return updated order entity
```

**Expected Output**: Order entity with updated timestamp, state managed by workflow

### 10. OrderPreparationProcessor
**Entity**: Order
**Transition**: start_preparation (approved → preparing)
**Input**: Order entity in approved state
**Purpose**: Start order preparation process

**Pseudocode**:
```
process(order):
    validate order is in approved state
    set order.updatedAt to current timestamp
    log preparation start event
    return updated order entity
```

**Expected Output**: Order entity with updated timestamp, state managed by workflow

### 11. OrderShippingProcessor
**Entity**: Order
**Transition**: ship_order (preparing → shipped)
**Input**: Order entity in preparing state
**Purpose**: Process order shipment

**Pseudocode**:
```
process(order):
    validate order is in preparing state
    set order.shipDate to current timestamp
    set order.updatedAt to current timestamp
    for each orderItem in order.orderItems:
        get pet by petId using entityService
        if pet exists and pet.meta.state == "pending":
            update pet to sold state using entityService with transition "sell_pet"
    log shipment event
    return updated order entity
```

**Expected Output**: Order entity with ship date, referenced pets updated to sold state

### 12. OrderDeliveryProcessor
**Entity**: Order
**Transition**: deliver_order (shipped → delivered)
**Input**: Order entity in shipped state
**Purpose**: Mark order as delivered

**Pseudocode**:
```
process(order):
    validate order is in shipped state
    set order.updatedAt to current timestamp
    log delivery event
    return updated order entity
```

**Expected Output**: Order entity with updated timestamp, state managed by workflow

### 13. OrderCancellationProcessor
**Entity**: Order
**Transition**: cancel_order (placed → cancelled), cancel_approved_order (approved → cancelled)
**Input**: Order entity in placed or approved state
**Purpose**: Cancel order and release reserved pets

**Pseudocode**:
```
process(order):
    validate order is in placed or approved state
    set order.updatedAt to current timestamp
    for each orderItem in order.orderItems:
        get pet by petId using entityService
        if pet exists and pet.meta.state == "pending":
            update pet to available state using entityService with transition "release_reservation"
    log cancellation event
    return updated order entity
```

**Expected Output**: Order entity with updated timestamp, referenced pets released back to available state

## User Processors

### 14. UserActivationProcessor
**Entity**: User
**Transition**: activate_user (none → active), reactivate_user (inactive → active), unsuspend_user (suspended → active)
**Input**: User entity in none, inactive, or suspended state
**Purpose**: Activate user account

**Pseudocode**:
```
process(user):
    validate user is in none, inactive, or suspended state
    if user.registrationDate is null, set to current timestamp
    set user.updatedAt to current timestamp
    log activation event
    return updated user entity
```

**Expected Output**: User entity with updated timestamps, state managed by workflow

### 15. UserDeactivationProcessor
**Entity**: User
**Transition**: deactivate_user (active → inactive)
**Input**: User entity in active state
**Purpose**: Deactivate user account

**Pseudocode**:
```
process(user):
    validate user is in active state
    set user.updatedAt to current timestamp
    log deactivation event
    return updated user entity
```

**Expected Output**: User entity with updated timestamp, state managed by workflow

### 16. UserSuspensionProcessor
**Entity**: User
**Transition**: suspend_user (active → suspended)
**Input**: User entity in active state
**Purpose**: Suspend user account

**Pseudocode**:
```
process(user):
    validate user is in active state
    set user.updatedAt to current timestamp
    log suspension event
    return updated user entity
```

**Expected Output**: User entity with updated timestamp, state managed by workflow

## Notes
- All processors implement CyodaProcessor interface
- Processors can read current entity state but cannot update it directly (state managed by workflow)
- Processors can update other entities using EntityService with appropriate transition names
- All processors include logging for audit trail
- Validation ensures entity is in correct state before processing
- Timestamps are updated to track entity modifications
