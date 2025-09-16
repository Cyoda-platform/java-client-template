# Purrfect Pets API - Processor Requirements

## Overview
This document defines the detailed requirements for all processors in the Purrfect Pets API application. Each processor implements specific business logic for workflow transitions.

## Pet Processors

### 1. PetCreationProcessor

**Entity**: Pet
**Transition**: create_pet (none → draft)
**Input**: Pet entity data
**Output**: Pet entity with draft state

**Pseudocode**:
```
process(petData):
    1. Validate required fields (name, photoUrls)
    2. Set default values if not provided
    3. Generate unique ID if not provided
    4. Set creation timestamp
    5. Initialize empty tags list if not provided
    6. Set entity state to 'draft'
    7. Save pet entity
    8. Return updated pet entity
```

### 2. PetValidationProcessor

**Entity**: Pet
**Transition**: make_available (draft → available)
**Input**: Pet entity in draft state
**Output**: Pet entity with available state

**Pseudocode**:
```
process(pet):
    1. Validate pet name is not empty
    2. Validate at least one photo URL exists
    3. Validate photo URLs are valid format
    4. Validate category exists if provided
    5. Validate tags exist if provided
    6. Set last modified timestamp
    7. Set entity state to 'available'
    8. Update pet entity
    9. Return updated pet entity
```

### 3. PetReservationProcessor

**Entity**: Pet
**Transition**: reserve_pet (available → pending)
**Input**: Pet entity in available state
**Output**: Pet entity with pending state

**Pseudocode**:
```
process(pet):
    1. Check pet is in available state
    2. Set reservation timestamp
    3. Set entity state to 'pending'
    4. Update pet entity
    5. Log reservation event
    6. Return updated pet entity
```

### 4. PetSaleProcessor

**Entity**: Pet
**Transitions**: sell_pet (pending → sold), direct_sale (available → sold)
**Input**: Pet entity in pending or available state
**Output**: Pet entity with sold state

**Pseudocode**:
```
process(pet):
    1. Validate pet is in pending or available state
    2. Set sale timestamp
    3. Set entity state to 'sold'
    4. Update pet entity
    5. Log sale event
    6. Trigger order completion if order exists (null transition)
    7. Return updated pet entity
```

## Category Processors

### 5. CategoryCreationProcessor

**Entity**: Category
**Transition**: create_category (none → active)
**Input**: Category entity data
**Output**: Category entity with active state

**Pseudocode**:
```
process(categoryData):
    1. Validate category name is not empty
    2. Check category name is unique
    3. Generate unique ID if not provided
    4. Set creation timestamp
    5. Set entity state to 'active'
    6. Save category entity
    7. Return updated category entity
```

## Tag Processors

### 6. TagCreationProcessor

**Entity**: Tag
**Transition**: create_tag (none → active)
**Input**: Tag entity data
**Output**: Tag entity with active state

**Pseudocode**:
```
process(tagData):
    1. Validate tag name is not empty
    2. Check tag name is unique
    3. Generate unique ID if not provided
    4. Set creation timestamp
    5. Set entity state to 'active'
    6. Save tag entity
    7. Return updated tag entity
```

## Order Processors

### 7. OrderCreationProcessor

**Entity**: Order
**Transition**: place_order (none → placed)
**Input**: Order entity data
**Output**: Order entity with placed state

**Pseudocode**:
```
process(orderData):
    1. Validate pet ID exists and pet is available
    2. Validate quantity is positive
    3. Validate user exists if user ID provided
    4. Generate unique order ID if not provided
    5. Set order timestamp
    6. Set default quantity to 1 if not provided
    7. Set complete flag to false
    8. Set entity state to 'placed'
    9. Reserve the pet (trigger pet reservation - reserve_pet transition)
    10. Save order entity
    11. Return updated order entity
```

### 8. OrderApprovalProcessor

**Entity**: Order
**Transition**: approve_order (placed → approved)
**Input**: Order entity in placed state
**Output**: Order entity with approved state

**Pseudocode**:
```
process(order):
    1. Validate order is in placed state
    2. Validate pet is still available/reserved
    3. Validate payment information if required
    4. Set approval timestamp
    5. Set entity state to 'approved'
    6. Update order entity
    7. Log approval event
    8. Return updated order entity
```

### 9. OrderShippingProcessor

**Entity**: Order
**Transition**: ship_order (approved → shipped)
**Input**: Order entity in approved state
**Output**: Order entity with shipped state

**Pseudocode**:
```
process(order):
    1. Validate order is in approved state
    2. Generate shipping tracking number
    3. Set ship date to current timestamp
    4. Set entity state to 'shipped'
    5. Update order entity
    6. Send shipping notification
    7. Return updated order entity
```

### 10. OrderDeliveryProcessor

**Entity**: Order
**Transition**: deliver_order (shipped → delivered)
**Input**: Order entity in shipped state
**Output**: Order entity with delivered state

**Pseudocode**:
```
process(order):
    1. Validate order is in shipped state
    2. Set delivery timestamp
    3. Set complete flag to true
    4. Set entity state to 'delivered'
    5. Update order entity
    6. Complete pet sale (trigger pet sale - sell_pet transition)
    7. Send delivery confirmation
    8. Return updated order entity
```

### 11. OrderCancellationProcessor

**Entity**: Order
**Transitions**: cancel_order (placed → cancelled), cancel_approved_order (approved → cancelled)
**Input**: Order entity in placed or approved state
**Output**: Order entity with cancelled state

**Pseudocode**:
```
process(order):
    1. Validate order is in placed or approved state
    2. Set cancellation timestamp
    3. Set entity state to 'cancelled'
    4. Update order entity
    5. Release pet reservation (trigger pet availability - cancel_reservation transition)
    6. Process refund if payment was made
    7. Send cancellation notification
    8. Return updated order entity
```

## User Processors

### 12. UserRegistrationProcessor

**Entity**: User
**Transition**: register_user (none → registered)
**Input**: User entity data
**Output**: User entity with registered state

**Pseudocode**:
```
process(userData):
    1. Validate required fields (username, email, password)
    2. Check username is unique
    3. Check email is unique
    4. Encrypt password
    5. Generate unique user ID if not provided
    6. Set registration timestamp
    7. Set default user status to 0
    8. Set entity state to 'registered'
    9. Save user entity
    10. Send welcome email
    11. Return updated user entity
```

### 13. UserActivationProcessor

**Entity**: User
**Transition**: activate_user (registered → active)
**Input**: User entity in registered state
**Output**: User entity with active state

**Pseudocode**:
```
process(user):
    1. Validate user is in registered state
    2. Set activation timestamp
    3. Set user status to 1 (active)
    4. Set entity state to 'active'
    5. Update user entity
    6. Send activation confirmation email
    7. Return updated user entity
```

### 14. UserSuspensionProcessor

**Entity**: User
**Transition**: suspend_user (active → suspended)
**Input**: User entity in active state
**Output**: User entity with suspended state

**Pseudocode**:
```
process(user):
    1. Validate user is in active state
    2. Set suspension timestamp
    3. Set user status to -1 (suspended)
    4. Set entity state to 'suspended'
    5. Update user entity
    6. Cancel active orders if any (trigger order cancellation)
    7. Send suspension notification email
    8. Return updated user entity
```

### 15. UserDeletionProcessor

**Entity**: User
**Transitions**: delete_user (active → deleted), delete_suspended_user (suspended → deleted)
**Input**: User entity in active or suspended state
**Output**: User entity with deleted state

**Pseudocode**:
```
process(user):
    1. Validate user is in active or suspended state
    2. Set deletion timestamp
    3. Anonymize personal data (keep ID for referential integrity)
    4. Set user status to -2 (deleted)
    5. Set entity state to 'deleted'
    6. Update user entity
    7. Cancel all active orders (trigger order cancellation)
    8. Send account deletion confirmation
    9. Return updated user entity
```

## Processor Notes

1. **Error Handling**: All processors should handle errors gracefully and provide meaningful error messages.
2. **Logging**: Important events should be logged for audit purposes.
3. **Validation**: Input validation should be thorough and consistent.
4. **Timestamps**: All processors should update relevant timestamps.
5. **Cross-Entity Updates**: Some processors trigger transitions in other entities (noted as transition names or null transitions).
