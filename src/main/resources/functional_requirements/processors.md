# Purrfect Pets - Processor Requirements

## Overview
This document defines the processor requirements for the Purrfect Pets system. Processors handle business logic during workflow transitions and can interact with other entities.

## Pet Processors

### 1. PetAvailabilityProcessor
**Entity**: Pet  
**Transition**: make_available (none → available)  
**Input**: Pet entity in 'none' state  
**Purpose**: Initialize pet for availability and perform validation

**Pseudocode**:
```
process(pet):
    1. Validate pet has all required fields (name, categoryId, price)
    2. Set default values if missing:
       - createdAt = current timestamp
       - vaccinated = false if not specified
    3. Validate category exists using entityService
    4. If category not found, log error and return pet unchanged
    5. Set updatedAt = current timestamp
    6. Return updated pet entity
```

**Other Entity Updates**: None

### 2. PetReservationProcessor
**Entity**: Pet  
**Transition**: reserve_pet (available → pending)  
**Input**: Pet entity in 'available' state  
**Purpose**: Reserve pet and update reservation timestamp

**Pseudocode**:
```
process(pet):
    1. Set updatedAt = current timestamp
    2. Log reservation action with pet ID and timestamp
    3. Return updated pet entity
```

**Other Entity Updates**: None

### 3. PetSaleProcessor
**Entity**: Pet  
**Transition**: complete_sale, direct_sale (pending/available → sold)  
**Input**: Pet entity in 'pending' or 'available' state  
**Purpose**: Complete pet sale and update sale information

**Pseudocode**:
```
process(pet):
    1. Set updatedAt = current timestamp
    2. Log sale completion with pet ID and timestamp
    3. Return updated pet entity
```

**Other Entity Updates**: None

## Category Processors

### 4. CategoryActivationProcessor
**Entity**: Category  
**Transition**: activate_category (none → active)  
**Input**: Category entity in 'none' state  
**Purpose**: Activate category and set default values

**Pseudocode**:
```
process(category):
    1. Validate category has required fields (name)
    2. Set default values:
       - createdAt = current timestamp if not set
       - description = "No description available" if empty
    3. Set updatedAt = current timestamp
    4. Return updated category entity
```

**Other Entity Updates**: None

## Order Processors

### 5. OrderPlacementProcessor
**Entity**: Order  
**Transition**: place_order (none → placed)  
**Input**: Order entity in 'none' state  
**Purpose**: Place order and reserve associated pets

**Pseudocode**:
```
process(order):
    1. Validate order has required fields (userId, items, totalAmount, shippingAddress)
    2. For each item in order.items:
       a. Find pet by petId using entityService
       b. If pet not found, log error and continue
       c. If pet state is 'available', update pet to 'pending' state using transition 'reserve_pet'
       d. If pet state is not 'available', log warning
    3. Set orderDate = current timestamp if not set
    4. Set updatedAt = current timestamp
    5. Return updated order entity
```

**Other Entity Updates**: Pet entities (transition to 'pending' state via 'reserve_pet')

### 6. OrderApprovalProcessor
**Entity**: Order  
**Transition**: approve_order (placed → approved)  
**Input**: Order entity in 'placed' state  
**Purpose**: Approve order after validation

**Pseudocode**:
```
process(order):
    1. Validate user exists using entityService
    2. If user not found or not in 'active' state, log error and return order unchanged
    3. Validate all pets in order items are in 'pending' state
    4. Calculate and verify total amount matches sum of item prices
    5. Set updatedAt = current timestamp
    6. Return updated order entity
```

**Other Entity Updates**: None

### 7. OrderCancellationProcessor
**Entity**: Order  
**Transition**: cancel_order, cancel_approved_order (placed/approved → cancelled)  
**Input**: Order entity in 'placed' or 'approved' state  
**Purpose**: Cancel order and release reserved pets

**Pseudocode**:
```
process(order):
    1. For each item in order.items:
       a. Find pet by petId using entityService
       b. If pet found and state is 'pending', update pet to 'available' state using transition 'cancel_reservation'
    2. Set updatedAt = current timestamp
    3. Return updated order entity
```

**Other Entity Updates**: Pet entities (transition to 'available' state via 'cancel_reservation')

### 8. OrderShippingProcessor
**Entity**: Order  
**Transition**: ship_order (approved → shipped)  
**Input**: Order entity in 'approved' state  
**Purpose**: Process order shipping

**Pseudocode**:
```
process(order):
    1. Set shipDate = current timestamp
    2. Set updatedAt = current timestamp
    3. Log shipping information with order ID and shipping address
    4. Return updated order entity
```

**Other Entity Updates**: None

### 9. OrderDeliveryProcessor
**Entity**: Order  
**Transition**: confirm_delivery (shipped → delivered)  
**Input**: Order entity in 'shipped' state  
**Purpose**: Confirm delivery and complete pet sales

**Pseudocode**:
```
process(order):
    1. For each item in order.items:
       a. Find pet by petId using entityService
       b. If pet found and state is 'pending', update pet to 'sold' state using transition 'complete_sale'
    2. Set updatedAt = current timestamp
    3. Log delivery confirmation with order ID and timestamp
    4. Return updated order entity
```

**Other Entity Updates**: Pet entities (transition to 'sold' state via 'complete_sale')

## User Processors

### 10. UserActivationProcessor
**Entity**: User  
**Transition**: activate_user (none → active)  
**Input**: User entity in 'none' state  
**Purpose**: Activate user account and set defaults

**Pseudocode**:
```
process(user):
    1. Validate user has required fields (username, firstName, lastName, email)
    2. Validate email format is correct
    3. Set default values:
       - createdAt = current timestamp if not set
       - preferences.newsletter = true if not specified
       - preferences.notifications = true if not specified
    4. Set updatedAt = current timestamp
    5. Return updated user entity
```

**Other Entity Updates**: None

### 11. UserSuspensionProcessor
**Entity**: User  
**Transition**: suspend_user (active → suspended)  
**Input**: User entity in 'active' state  
**Purpose**: Suspend user account and cancel active orders

**Pseudocode**:
```
process(user):
    1. Find all orders for this user in 'placed' or 'approved' states using entityService
    2. For each active order:
       a. Update order to 'cancelled' state using transition 'cancel_order' or 'cancel_approved_order'
    3. Set updatedAt = current timestamp
    4. Log suspension action with user ID and timestamp
    5. Return updated user entity
```

**Other Entity Updates**: Order entities (transition to 'cancelled' state via appropriate transitions)

### 12. UserUnsuspensionProcessor
**Entity**: User  
**Transition**: unsuspend_user (suspended → active)  
**Input**: User entity in 'suspended' state  
**Purpose**: Remove suspension from user account

**Pseudocode**:
```
process(user):
    1. Set updatedAt = current timestamp
    2. Log unsuspension action with user ID and timestamp
    3. Return updated user entity
```

**Other Entity Updates**: None

## Implementation Notes

1. **Error Handling**: All processors should handle errors gracefully and log appropriate messages
2. **EntityService Usage**: Processors can read current entity, update OTHER entities, but cannot update current entity with entityService
3. **Transition Names**: When updating other entities, use specific transition names as defined in workflows.md
4. **Validation**: Processors should validate data before processing and return unchanged entity if validation fails
5. **Logging**: All processors should log their actions for audit purposes
6. **Timestamps**: Always update `updatedAt` timestamp when modifying entities
7. **Null Checks**: Always check for null values before processing entity fields
