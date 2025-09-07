# Processors Specification - Purrfect Pets API

## Overview
This document defines the business logic processors for each workflow transition in the Purrfect Pets API. Processors implement the business rules and data transformations required for state transitions.

## Pet Entity Processors

### 1. PetRegistrationProcessor

**Entity:** Pet  
**Transition:** register_pet (none → REGISTERED)  
**Input:** New pet data with owner reference  
**Purpose:** Validates and registers a new pet in the system

**Expected Input Data:**
- Pet basic information (name, species, breed, age, weight, color)
- Owner ID reference
- Optional health notes and photo URL

**Business Logic (Pseudocode):**
```
1. Validate pet data completeness
   - Check required fields: name, species, ownerId
   - Validate age is positive number
   - Validate weight is positive number

2. Verify owner exists and is active
   - Get owner by ownerId using EntityService
   - Check owner state is ACTIVE
   - If owner not active, fail registration

3. Generate unique petId
   - Create business ID in format "PET-{sequence}"
   - Ensure uniqueness across all pets

4. Set registration metadata
   - Set registrationDate to current timestamp
   - Initialize totalPets count for owner

5. Update owner's pet count
   - Increment owner's totalPets field
   - Update owner entity (no transition needed)

6. Return registered pet entity
```

**Expected Output:** Pet entity in REGISTERED state with generated petId

---

### 2. PetActivationProcessor

**Entity:** Pet  
**Transition:** activate_pet (REGISTERED → ACTIVE)  
**Input:** Pet entity in REGISTERED state  
**Purpose:** Activates a registered pet for service eligibility

**Expected Input Data:**
- Pet entity with all required registration data
- Optional updated health notes

**Business Logic (Pseudocode):**
```
1. Validate pet is in REGISTERED state
   - Check current entity state
   - Verify all required fields are present

2. Verify owner is still active
   - Get owner by ownerId using EntityService
   - Ensure owner state is ACTIVE
   - If owner inactive, prevent activation

3. Update pet activation metadata
   - No specific fields to update for activation
   - Pet becomes eligible for service orders

4. Return activated pet entity
```

**Expected Output:** Pet entity ready for ACTIVE state

---

### 3. PetDeactivationProcessor

**Entity:** Pet  
**Transition:** deactivate_pet (ACTIVE → INACTIVE)  
**Input:** Pet entity in ACTIVE state  
**Purpose:** Temporarily deactivates a pet (e.g., moved away)

**Expected Input Data:**
- Pet entity in ACTIVE state
- Optional reason for deactivation

**Business Logic (Pseudocode):**
```
1. Check for active orders
   - Search for PetCareOrders with this petId
   - Find orders in PENDING, CONFIRMED, or IN_PROGRESS states
   - If active orders exist, prevent deactivation

2. Update pet status
   - Pet becomes ineligible for new service orders
   - Existing completed orders remain accessible

3. Return deactivated pet entity
```

**Expected Output:** Pet entity ready for INACTIVE state

---

### 4. PetReactivationProcessor

**Entity:** Pet  
**Transition:** reactivate_pet (INACTIVE → ACTIVE)  
**Input:** Pet entity in INACTIVE state  
**Purpose:** Reactivates an inactive pet

**Expected Input Data:**
- Pet entity in INACTIVE state
- Optional updated information

**Business Logic (Pseudocode):**
```
1. Verify owner is still active
   - Get owner by ownerId using EntityService
   - Ensure owner state is ACTIVE
   - If owner inactive, prevent reactivation

2. Update pet status
   - Pet becomes eligible for service orders again
   - Restore full functionality

3. Return reactivated pet entity
```

**Expected Output:** Pet entity ready for ACTIVE state

---

### 5. PetArchivalProcessor

**Entity:** Pet  
**Transition:** archive_pet (ACTIVE/INACTIVE → ARCHIVED)  
**Input:** Pet entity in ACTIVE or INACTIVE state  
**Purpose:** Permanently archives a pet (e.g., deceased)

**Expected Input Data:**
- Pet entity in ACTIVE or INACTIVE state
- Optional archival reason

**Business Logic (Pseudocode):**
```
1. Cancel any pending orders
   - Search for PetCareOrders with this petId
   - Find orders in PENDING or CONFIRMED states
   - Cancel each order using transition "cancel_order"

2. Update owner's pet count
   - Decrement owner's totalPets field
   - Update owner entity (no transition needed)

3. Archive pet data
   - Pet becomes read-only
   - Historical data preserved for reporting

4. Return archived pet entity
```

**Expected Output:** Pet entity ready for ARCHIVED state

## Owner Entity Processors

### 6. OwnerRegistrationProcessor

**Entity:** Owner  
**Transition:** register_owner (none → PENDING)  
**Input:** New owner registration data  
**Purpose:** Creates a new owner account pending verification

**Expected Input Data:**
- Owner personal information (firstName, lastName, email, phoneNumber)
- Address information (address, city, zipCode)
- Optional emergency contact and preferred vet

**Business Logic (Pseudocode):**
```
1. Validate owner data completeness
   - Check required fields: firstName, lastName, email, phoneNumber
   - Validate email format
   - Validate phone number format

2. Check for duplicate email
   - Search existing owners by email
   - If email exists, prevent duplicate registration

3. Generate unique ownerId
   - Create business ID in format "OWNER-{sequence}"
   - Ensure uniqueness across all owners

4. Set registration metadata
   - Set registrationDate to current timestamp
   - Initialize totalPets to 0

5. Return pending owner entity
```

**Expected Output:** Owner entity in PENDING state with generated ownerId

---

### 7. OwnerVerificationProcessor

**Entity:** Owner  
**Transition:** verify_owner (PENDING → ACTIVE)  
**Input:** Owner entity in PENDING state  
**Purpose:** Activates owner account after verification

**Expected Input Data:**
- Owner entity with complete registration data
- Verification status from external system

**Business Logic (Pseudocode):**
```
1. Validate verification criteria
   - Check email verification status
   - Validate contact information completeness
   - Ensure no duplicate accounts

2. Activate owner account
   - Owner becomes eligible to register pets
   - Owner can place service orders

3. Return verified owner entity
```

**Expected Output:** Owner entity ready for ACTIVE state

---

### 8. OwnerSuspensionProcessor

**Entity:** Owner  
**Transition:** suspend_owner (ACTIVE → SUSPENDED)  
**Input:** Owner entity in ACTIVE state  
**Purpose:** Temporarily suspends owner account

**Expected Input Data:**
- Owner entity in ACTIVE state
- Suspension reason

**Business Logic (Pseudocode):**
```
1. Cancel pending orders
   - Search for PetCareOrders with this ownerId
   - Find orders in PENDING state
   - Cancel each pending order using transition "cancel_order"

2. Deactivate associated pets
   - Get all pets owned by this owner
   - For each pet in ACTIVE state, transition to INACTIVE
   - Use transition "deactivate_pet"

3. Suspend account access
   - Owner cannot place new orders
   - Existing data remains accessible

4. Return suspended owner entity
```

**Expected Output:** Owner entity ready for SUSPENDED state

---

### 9. OwnerReactivationProcessor

**Entity:** Owner  
**Transition:** reactivate_owner (SUSPENDED → ACTIVE)  
**Input:** Owner entity in SUSPENDED state  
**Purpose:** Reactivates a suspended owner account

**Expected Input Data:**
- Owner entity in SUSPENDED state
- Reactivation approval

**Business Logic (Pseudocode):**
```
1. Validate reactivation eligibility
   - Check suspension reason resolution
   - Verify account information is current

2. Reactivate account access
   - Owner can place new orders
   - Owner can register new pets

3. Optionally reactivate pets
   - Pets remain in their current states
   - Owner can manually reactivate pets as needed

4. Return reactivated owner entity
```

**Expected Output:** Owner entity ready for ACTIVE state

---

### 10. OwnerClosureProcessor

**Entity:** Owner  
**Transition:** close_owner_account (ACTIVE/SUSPENDED → CLOSED)  
**Input:** Owner entity in ACTIVE or SUSPENDED state  
**Purpose:** Permanently closes owner account

**Expected Input Data:**
- Owner entity in ACTIVE or SUSPENDED state
- Closure reason

**Business Logic (Pseudocode):**
```
1. Archive all owned pets
   - Get all pets owned by this owner
   - For each pet not already ARCHIVED, transition to ARCHIVED
   - Use transition "archive_pet"

2. Cancel all pending orders
   - Search for PetCareOrders with this ownerId
   - Cancel orders in PENDING or CONFIRMED states
   - Use transition "cancel_order"

3. Close account permanently
   - Account becomes read-only
   - Historical data preserved for compliance

4. Return closed owner entity
```

**Expected Output:** Owner entity ready for CLOSED state

## PetCareOrder Entity Processors

### 11. OrderCreationProcessor

**Entity:** PetCareOrder
**Transition:** create_order (none → PENDING)
**Input:** New order data with pet and owner references
**Purpose:** Creates a new pet care service order

**Expected Input Data:**
- Pet ID and Owner ID references
- Service type and description
- Scheduled date and duration
- Cost and payment method
- Special instructions

**Business Logic (Pseudocode):**
```
1. Validate order data completeness
   - Check required fields: petId, ownerId, serviceType, scheduledDate
   - Validate cost is positive number
   - Validate scheduledDate is in future

2. Verify pet and owner eligibility
   - Get pet by petId using EntityService
   - Check pet state is ACTIVE
   - Get owner by ownerId using EntityService
   - Check owner state is ACTIVE
   - Verify pet belongs to the specified owner

3. Generate unique orderId
   - Create business ID in format "ORDER-{sequence}"
   - Ensure uniqueness across all orders

4. Set order metadata
   - Set orderDate to current timestamp
   - Initialize order in PENDING state

5. Return created order entity
```

**Expected Output:** PetCareOrder entity in PENDING state with generated orderId

---

### 12. OrderConfirmationProcessor

**Entity:** PetCareOrder
**Transition:** confirm_order (PENDING → CONFIRMED)
**Input:** PetCareOrder entity in PENDING state
**Purpose:** Confirms order after validation and scheduling

**Expected Input Data:**
- Order entity with all required data
- Optional assigned veterinarian name
- Confirmed scheduling details

**Business Logic (Pseudocode):**
```
1. Validate order details
   - Verify pet and owner are still active
   - Check service availability for scheduled date
   - Validate payment method

2. Assign service provider
   - Set veterinarianName if veterinary service
   - Confirm resource availability

3. Lock in scheduling
   - Order becomes committed
   - Resources are reserved

4. Return confirmed order entity
```

**Expected Output:** PetCareOrder entity ready for CONFIRMED state

---

### 13. ServiceStartProcessor

**Entity:** PetCareOrder
**Transition:** start_service (CONFIRMED → IN_PROGRESS)
**Input:** PetCareOrder entity in CONFIRMED state
**Purpose:** Marks the beginning of service delivery

**Expected Input Data:**
- Order entity in CONFIRMED state
- Service start confirmation

**Business Logic (Pseudocode):**
```
1. Verify service readiness
   - Check scheduled date has arrived
   - Confirm service provider availability
   - Verify pet is present for service

2. Begin service tracking
   - Service is now actively being provided
   - Progress can be monitored

3. Return in-progress order entity
```

**Expected Output:** PetCareOrder entity ready for IN_PROGRESS state

---

### 14. ServiceCompletionProcessor

**Entity:** PetCareOrder
**Transition:** complete_service (IN_PROGRESS → COMPLETED)
**Input:** PetCareOrder entity in IN_PROGRESS state
**Purpose:** Marks successful completion of service

**Expected Input Data:**
- Order entity in IN_PROGRESS state
- Service completion details
- Optional customer rating and notes

**Business Logic (Pseudocode):**
```
1. Finalize service details
   - Set completionDate to current timestamp
   - Record any service notes
   - Capture customer rating if provided

2. Update pet health records
   - If veterinary service, update pet's lastCheckupDate
   - Add any health notes to pet record
   - Update pet entity (no transition needed)

3. Process payment
   - Confirm payment completion
   - Generate service receipt

4. Return completed order entity
```

**Expected Output:** PetCareOrder entity ready for COMPLETED state

---

### 15. OrderCancellationProcessor

**Entity:** PetCareOrder
**Transition:** cancel_order (PENDING/CONFIRMED → CANCELLED)
**Input:** PetCareOrder entity in PENDING or CONFIRMED state
**Purpose:** Cancels an order before service completion

**Expected Input Data:**
- Order entity in PENDING or CONFIRMED state
- Cancellation reason
- Refund information if applicable

**Business Logic (Pseudocode):**
```
1. Validate cancellation eligibility
   - Check order is not yet IN_PROGRESS
   - Verify cancellation policy compliance

2. Release reserved resources
   - Free up scheduled service slots
   - Release assigned service providers

3. Process refunds if applicable
   - Calculate refund amount based on cancellation timing
   - Process payment reversal if needed

4. Record cancellation details
   - Set cancellation reason in notes
   - Preserve order history for analysis

5. Return cancelled order entity
```

**Expected Output:** PetCareOrder entity ready for CANCELLED state

## Processor Implementation Notes

1. **EntityService Usage**: Processors use EntityService to interact with other entities
2. **State Validation**: Always verify entity states before processing
3. **Error Handling**: Processors should validate inputs and handle business rule violations
4. **Data Integrity**: Maintain referential integrity between related entities
5. **Audit Trail**: Preserve historical data for compliance and analysis
6. **Performance**: Use technical UUIDs for EntityService operations when available
