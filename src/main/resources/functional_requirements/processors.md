# Purrfect Pets - Processor Requirements

## Overview
This document defines the detailed requirements for all processors in the Purrfect Pets application. Processors handle business logic during workflow transitions.

## Pet Processors

### 1. PetRegistrationProcessor

**Entity:** Pet  
**Transition:** auto_add_pet (none → available)  
**Input:** Pet entity in "none" state  
**Purpose:** Validates and registers a new pet in the system  

#### Expected Input Data:
- Pet entity with all required fields populated
- Pet must have valid petId, name, and photoUrls

#### Business Logic (Pseudocode):
```
process(Pet pet):
    1. Validate pet data completeness
    2. Set default values:
       - createdAt = current timestamp
       - updatedAt = current timestamp
       - vaccinated = false if not specified
    3. Generate unique petId if not provided
    4. Log pet registration event
    5. Return updated pet entity
```

#### Expected Output:
- Pet entity with populated timestamps and defaults
- Entity state will automatically transition to "available"

### 2. PetReservationProcessor

**Entity:** Pet  
**Transition:** reserve_pet (available → pending)  
**Input:** Pet entity in "available" state  
**Purpose:** Reserves a pet for a customer  

#### Expected Input Data:
- Pet entity in "available" state
- Customer information (passed via transition parameters)

#### Business Logic (Pseudocode):
```
process(Pet pet):
    1. Validate pet is still available
    2. Set updatedAt = current timestamp
    3. Log reservation event with customer info
    4. Send notification to customer about reservation
    5. Set reservation expiry (24 hours from now)
    6. Return updated pet entity
```

#### Expected Output:
- Pet entity with updated timestamp
- Entity state will transition to "pending"

### 3. PetSaleProcessor

**Entity:** Pet  
**Transitions:** sell_pet_direct (available → sold), complete_sale (pending → sold)  
**Input:** Pet entity in "available" or "pending" state  
**Purpose:** Processes the sale of a pet  

#### Expected Input Data:
- Pet entity in "available" or "pending" state
- Order information (orderId, customer details)

#### Business Logic (Pseudocode):
```
process(Pet pet):
    1. Validate pet can be sold
    2. Create or update associated Order entity:
       - Set order state to "placed" (transition: create_order)
    3. Update pet timestamps
    4. Log sale event
    5. Send confirmation to customer
    6. Update inventory records
    7. Return updated pet entity
```

#### Expected Output:
- Pet entity with updated timestamp
- New/Updated Order entity with transition "create_order"
- Entity state will transition to "sold"

### 4. PetReservationCancelProcessor

**Entity:** Pet  
**Transition:** cancel_reservation (pending → available)  
**Input:** Pet entity in "pending" state  
**Purpose:** Cancels a pet reservation  

#### Expected Input Data:
- Pet entity in "pending" state
- Cancellation reason

#### Business Logic (Pseudocode):
```
process(Pet pet):
    1. Validate pet is in pending state
    2. Update timestamps
    3. Log cancellation event with reason
    4. Send notification to customer about cancellation
    5. Clear reservation data
    6. Return updated pet entity
```

#### Expected Output:
- Pet entity with cleared reservation data
- Entity state will transition to "available"

### 5. PetReturnProcessor

**Entity:** Pet  
**Transition:** return_pet (sold → available)  
**Input:** Pet entity in "sold" state  
**Purpose:** Processes the return of a sold pet  

#### Expected Input Data:
- Pet entity in "sold" state
- Return reason and condition assessment

#### Business Logic (Pseudocode):
```
process(Pet pet):
    1. Validate return eligibility
    2. Update associated Order entity:
       - Set order state to "returned" (transition: return_order)
    3. Update pet condition and health status
    4. Update timestamps
    5. Log return event
    6. Process refund if applicable
    7. Return updated pet entity
```

#### Expected Output:
- Pet entity ready for re-sale
- Updated Order entity with transition "return_order"
- Entity state will transition to "available"

## Order Processors

### 1. OrderCreationProcessor

**Entity:** Order  
**Transition:** create_order (none → placed)  
**Input:** Order entity in "none" state  
**Purpose:** Creates and validates a new order  

#### Expected Input Data:
- Order entity with customer info, pet ID, and quantity
- Valid customer ID and pet ID

#### Business Logic (Pseudocode):
```
process(Order order):
    1. Validate customer exists and is active
    2. Validate pet exists and is available
    3. Calculate total amount based on pet price and quantity
    4. Set timestamps (createdAt, updatedAt)
    5. Generate unique orderId if not provided
    6. Update associated Pet entity:
       - Set pet state to "pending" (transition: reserve_pet)
    7. Log order creation event
    8. Send order confirmation to customer
    9. Return updated order entity
```

#### Expected Output:
- Order entity with calculated totals and timestamps
- Updated Pet entity with transition "reserve_pet"
- Entity state will transition to "placed"

### 2. OrderApprovalProcessor

**Entity:** Order  
**Transition:** approve_order (placed → approved)  
**Input:** Order entity in "placed" state  
**Purpose:** Approves an order for processing  

#### Expected Input Data:
- Order entity in "placed" state
- Staff approval information

#### Business Logic (Pseudocode):
```
process(Order order):
    1. Validate order details and payment
    2. Check inventory availability
    3. Update timestamps
    4. Log approval event with staff info
    5. Send approval notification to customer
    6. Schedule delivery preparation
    7. Return updated order entity
```

#### Expected Output:
- Order entity with approval information
- Entity state will transition to "approved"

### 3. OrderDeliveryProcessor

**Entity:** Order  
**Transition:** deliver_order (approved → delivered)  
**Input:** Order entity in "approved" state  
**Purpose:** Processes order delivery  

#### Expected Input Data:
- Order entity in "approved" state
- Delivery confirmation details

#### Business Logic (Pseudocode):
```
process(Order order):
    1. Validate delivery readiness
    2. Update associated Pet entity:
       - Set pet state to "sold" (transition: complete_sale)
    3. Set delivery timestamp
    4. Update order completion status
    5. Log delivery event
    6. Send delivery confirmation to customer
    7. Generate delivery receipt
    8. Return updated order entity
```

#### Expected Output:
- Order entity marked as delivered
- Updated Pet entity with transition "complete_sale"
- Entity state will transition to "delivered"

### 4. OrderCancellationProcessor

**Entity:** Order  
**Transitions:** cancel_order (placed → cancelled), cancel_approved_order (approved → cancelled)  
**Input:** Order entity in "placed" or "approved" state  
**Purpose:** Cancels an order  

#### Expected Input Data:
- Order entity in "placed" or "approved" state
- Cancellation reason

#### Business Logic (Pseudocode):
```
process(Order order):
    1. Validate cancellation is allowed
    2. Update associated Pet entity:
       - Set pet state to "available" (transition: cancel_reservation)
    3. Process refund if payment was made
    4. Update timestamps
    5. Log cancellation event with reason
    6. Send cancellation notification to customer
    7. Return updated order entity
```

#### Expected Output:
- Order entity marked as cancelled
- Updated Pet entity with transition "cancel_reservation"
- Entity state will transition to "cancelled"

### 5. OrderReturnProcessor

**Entity:** Order  
**Transition:** return_order (delivered → returned)  
**Input:** Order entity in "delivered" state  
**Purpose:** Processes order return  

#### Expected Input Data:
- Order entity in "delivered" state
- Return reason and condition

#### Business Logic (Pseudocode):
```
process(Order order):
    1. Validate return eligibility and timeframe
    2. Update associated Pet entity:
       - Set pet state to "available" (transition: return_pet)
    3. Process return refund
    4. Update timestamps
    5. Log return event
    6. Send return confirmation to customer
    7. Return updated order entity
```

#### Expected Output:
- Order entity marked as returned
- Updated Pet entity with transition "return_pet"
- Entity state will transition to "returned"

## User Processors

### 1. UserRegistrationProcessor

**Entity:** User  
**Transition:** register_user (none → registered)  
**Input:** User entity in "none" state  
**Purpose:** Registers a new user in the system  

#### Expected Input Data:
- User entity with required fields (username, email, password, names)

#### Business Logic (Pseudocode):
```
process(User user):
    1. Validate user data completeness and format
    2. Check username and email uniqueness
    3. Encrypt password
    4. Set default values:
       - registrationDate = current timestamp
       - isActive = false
    5. Generate unique userId if not provided
    6. Send welcome email with verification link
    7. Log registration event
    8. Return updated user entity
```

#### Expected Output:
- User entity with encrypted password and timestamps
- Entity state will transition to "registered"

### 2. UserActivationProcessor

**Entity:** User  
**Transition:** activate_user (registered → active)  
**Input:** User entity in "registered" state  
**Purpose:** Activates a registered user account  

#### Expected Input Data:
- User entity in "registered" state
- Verification token or admin approval

#### Business Logic (Pseudocode):
```
process(User user):
    1. Validate verification token or admin privileges
    2. Set isActive = true
    3. Set lastLoginDate = current timestamp
    4. Update user preferences if provided
    5. Log activation event
    6. Send activation confirmation email
    7. Return updated user entity
```

#### Expected Output:
- User entity marked as active
- Entity state will transition to "active"

### 3. UserSuspensionProcessor

**Entity:** User  
**Transition:** suspend_user (active → suspended)  
**Input:** User entity in "active" state  
**Purpose:** Suspends a user account  

#### Expected Input Data:
- User entity in "active" state
- Suspension reason and duration

#### Business Logic (Pseudocode):
```
process(User user):
    1. Validate suspension reason
    2. Set suspension details and expiry date
    3. Cancel all "placed" orders for this user:
       - Update Order entities with transition "cancel_order"
    4. Log suspension event
    5. Send suspension notification to user
    6. Return updated user entity
```

#### Expected Output:
- User entity with suspension details
- Cancelled Order entities with transition "cancel_order"
- Entity state will transition to "suspended"

### 4. UserDeactivationProcessor

**Entity:** User  
**Transitions:** deactivate_unverified (registered → inactive), deactivate_user (active → inactive), deactivate_suspended (suspended → inactive)  
**Input:** User entity in "registered", "active", or "suspended" state  
**Purpose:** Deactivates a user account  

#### Expected Input Data:
- User entity in valid state for deactivation
- Deactivation reason

#### Business Logic (Pseudocode):
```
process(User user):
    1. Set isActive = false
    2. Cancel all "placed" orders for this user:
       - Update Order entities with transition "cancel_order"
    3. Clear sensitive data if required
    4. Update timestamps
    5. Log deactivation event
    6. Send deactivation confirmation if user-requested
    7. Return updated user entity
```

#### Expected Output:
- User entity marked as inactive
- Cancelled Order entities with transition "cancel_order"
- Entity state will transition to "inactive"

### 5. UserReactivationProcessor

**Entity:** User  
**Transitions:** reactivate_user (suspended → active), reactivate_inactive (inactive → active)  
**Input:** User entity in "suspended" or "inactive" state  
**Purpose:** Reactivates a user account  

#### Expected Input Data:
- User entity in "suspended" or "inactive" state
- Reactivation request details

#### Business Logic (Pseudocode):
```
process(User user):
    1. Validate reactivation eligibility
    2. Set isActive = true
    3. Clear suspension details if applicable
    4. Set lastLoginDate = current timestamp
    5. Log reactivation event
    6. Send reactivation confirmation email
    7. Return updated user entity
```

#### Expected Output:
- User entity marked as active
- Entity state will transition to "active"

## Processor Implementation Notes

1. **Error Handling:** All processors should handle validation errors gracefully and return meaningful error messages
2. **Logging:** All processors should log significant events for audit trails
3. **Notifications:** Processors should send appropriate notifications to users when required
4. **Cross-Entity Updates:** When processors update other entities, they specify the transition name for proper workflow management
5. **Idempotency:** Processors should be designed to handle repeated calls safely
6. **Performance:** Processors should be efficient and avoid unnecessary database calls
