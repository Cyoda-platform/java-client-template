# Purrfect Pets - Processor Requirements

## Overview
This document defines the business logic processors for the Purrfect Pets API. Each processor implements specific business logic for workflow transitions and entity operations.

## Pet Processors

### 1. PetInitializationProcessor

**Entity**: Pet
**Transition**: initialize_pet (none → available)
**Input**: Pet entity with basic data
**Output**: Pet entity ready for sale

**Business Logic**:
```
PROCESS initialize_pet:
  1. Validate pet has required fields (name, photoUrls)
  2. Validate category exists and is active
  3. Validate all tags exist and are active
  4. Set default values if needed
  5. Log pet initialization
  6. Return pet entity
```

### 2. PetReservationProcessor

**Entity**: Pet
**Transition**: reserve_pet (available → pending)
**Input**: Pet entity being reserved
**Output**: Pet entity with reservation data

**Business Logic**:
```
PROCESS reserve_pet:
  1. Get current pet entity
  2. Validate pet is in available state
  3. Find the order that triggered this reservation
  4. Validate order exists and is in placed state
  5. Log pet reservation with order ID
  6. Return pet entity
```

### 3. PetSaleCompletionProcessor

**Entity**: Pet
**Transition**: complete_sale (pending → sold)
**Input**: Pet entity being sold
**Output**: Pet entity marked as sold

**Business Logic**:
```
PROCESS complete_sale:
  1. Get current pet entity
  2. Validate pet is in pending state
  3. Find associated order and validate it's delivered
  4. Update inventory count in store
  5. Log sale completion
  6. Return pet entity
```

### 4. PetReservationCancellationProcessor

**Entity**: Pet
**Transition**: cancel_reservation (pending → available)
**Input**: Pet entity with cancelled reservation
**Output**: Pet entity available again

**Business Logic**:
```
PROCESS cancel_reservation:
  1. Get current pet entity
  2. Validate pet is in pending state
  3. Find associated order and validate it's cancelled
  4. Clear reservation data
  5. Log reservation cancellation
  6. Return pet entity
```

## Order Processors

### 5. OrderPlacementProcessor

**Entity**: Order
**Transition**: place_order (none → placed)
**Input**: Order entity with customer and pet data
**Output**: Order entity with reservation

**Business Logic**:
```
PROCESS place_order:
  1. Validate order has required fields (petId, userId, quantity)
  2. Validate user exists and is active
  3. Validate pet exists and is available
  4. Validate quantity is positive and <= available stock
  5. Reserve pet by transitioning to pending state
  6. Calculate order total and shipping date
  7. Log order placement
  8. Return order entity
```

### 6. OrderApprovalProcessor

**Entity**: Order
**Transition**: approve_order (placed → approved)
**Input**: Order entity to approve
**Output**: Approved order entity

**Business Logic**:
```
PROCESS approve_order:
  1. Get current order entity
  2. Validate order is in placed state
  3. Validate pet is still reserved (pending state)
  4. Validate user account is still active
  5. Validate payment information if required
  6. Update order approval timestamp
  7. Log order approval
  8. Return order entity
```

### 7. OrderDeliveryProcessor

**Entity**: Order
**Transition**: deliver_order (approved → delivered)
**Input**: Order entity being delivered
**Output**: Delivered order entity

**Business Logic**:
```
PROCESS deliver_order:
  1. Get current order entity
  2. Validate order is in approved state
  3. Complete pet sale by transitioning pet to sold state
  4. Update delivery timestamp
  5. Send delivery notification to user
  6. Update store inventory
  7. Log order delivery
  8. Return order entity
```

### 8. OrderCancellationProcessor

**Entity**: Order
**Transition**: cancel_order (placed/approved → cancelled)
**Input**: Order entity being cancelled
**Output**: Cancelled order entity

**Business Logic**:
```
PROCESS cancel_order:
  1. Get current order entity
  2. Validate order is in placed or approved state
  3. Cancel pet reservation by transitioning pet to available state
  4. Process refund if payment was made
  5. Update cancellation timestamp and reason
  6. Send cancellation notification to user
  7. Log order cancellation
  8. Return order entity
```

## User Processors

### 9. UserActivationProcessor

**Entity**: User
**Transition**: activate_user (none → active)
**Input**: User entity with registration data
**Output**: Active user entity

**Business Logic**:
```
PROCESS activate_user:
  1. Validate user has required fields (username, email, password)
  2. Validate username is unique
  3. Validate email format and uniqueness
  4. Encrypt password
  5. Set default user status to active
  6. Set account creation timestamp
  7. Send welcome email
  8. Log user activation
  9. Return user entity
```

### 10. UserDeactivationProcessor

**Entity**: User
**Transition**: deactivate_user (active → inactive)
**Input**: User entity to deactivate
**Output**: Inactive user entity

**Business Logic**:
```
PROCESS deactivate_user:
  1. Get current user entity
  2. Validate user is in active state
  3. Check for pending orders and handle appropriately
  4. Update deactivation timestamp
  5. Send deactivation notification
  6. Log user deactivation
  7. Return user entity
```

### 11. UserReactivationProcessor

**Entity**: User
**Transition**: reactivate_user (inactive → active)
**Input**: User entity to reactivate
**Output**: Active user entity

**Business Logic**:
```
PROCESS reactivate_user:
  1. Get current user entity
  2. Validate user is in inactive state
  3. Validate account is eligible for reactivation
  4. Update reactivation timestamp
  5. Send reactivation notification
  6. Log user reactivation
  7. Return user entity
```

### 12. UserSuspensionProcessor

**Entity**: User
**Transition**: suspend_user (active → suspended)
**Input**: User entity to suspend
**Output**: Suspended user entity

**Business Logic**:
```
PROCESS suspend_user:
  1. Get current user entity
  2. Validate user is in active state
  3. Cancel all pending orders
  4. Update suspension timestamp and reason
  5. Send suspension notification
  6. Log user suspension with reason
  7. Return user entity
```

### 13. UserUnsuspensionProcessor

**Entity**: User
**Transition**: unsuspend_user (suspended → active)
**Input**: User entity to unsuspend
**Output**: Active user entity

**Business Logic**:
```
PROCESS unsuspend_user:
  1. Get current user entity
  2. Validate user is in suspended state
  3. Validate suspension period has ended
  4. Validate any required conditions are met
  5. Update unsuspension timestamp
  6. Send unsuspension notification
  7. Log user unsuspension
  8. Return user entity
```

## Category Processors

### 14. CategoryActivationProcessor

**Entity**: Category
**Transition**: activate_category (none → active)
**Input**: Category entity with basic data
**Output**: Active category entity

**Business Logic**:
```
PROCESS activate_category:
  1. Validate category has required fields (name)
  2. Validate category name is unique
  3. Set activation timestamp
  4. Log category activation
  5. Return category entity
```

### 15. CategoryDeactivationProcessor

**Entity**: Category
**Transition**: deactivate_category (active → inactive)
**Input**: Category entity to deactivate
**Output**: Inactive category entity

**Business Logic**:
```
PROCESS deactivate_category:
  1. Get current category entity
  2. Validate category is in active state
  3. Check if any pets are using this category
  4. Update deactivation timestamp
  5. Log category deactivation
  6. Return category entity
```

### 16. CategoryReactivationProcessor

**Entity**: Category
**Transition**: reactivate_category (inactive → active)
**Input**: Category entity to reactivate
**Output**: Active category entity

**Business Logic**:
```
PROCESS reactivate_category:
  1. Get current category entity
  2. Validate category is in inactive state
  3. Update reactivation timestamp
  4. Log category reactivation
  5. Return category entity
```

## Tag Processors

### 17. TagActivationProcessor

**Entity**: Tag
**Transition**: activate_tag (none → active)
**Input**: Tag entity with basic data
**Output**: Active tag entity

**Business Logic**:
```
PROCESS activate_tag:
  1. Validate tag has required fields (name)
  2. Validate tag name is unique
  3. Set activation timestamp
  4. Log tag activation
  5. Return tag entity
```

### 18. TagDeactivationProcessor

**Entity**: Tag
**Transition**: deactivate_tag (active → inactive)
**Input**: Tag entity to deactivate
**Output**: Inactive tag entity

**Business Logic**:
```
PROCESS deactivate_tag:
  1. Get current tag entity
  2. Validate tag is in active state
  3. Update deactivation timestamp
  4. Log tag deactivation
  5. Return tag entity
```

### 19. TagReactivationProcessor

**Entity**: Tag
**Transition**: reactivate_tag (inactive → active)
**Input**: Tag entity to reactivate
**Output**: Active tag entity

**Business Logic**:
```
PROCESS reactivate_tag:
  1. Get current tag entity
  2. Validate tag is in inactive state
  3. Update reactivation timestamp
  4. Log tag reactivation
  5. Return tag entity
```

## Store Processors

### 20. StoreOpeningProcessor

**Entity**: Store
**Transition**: open_store (none → open)
**Input**: Store entity with basic data
**Output**: Open store entity

**Business Logic**:
```
PROCESS open_store:
  1. Validate store has required fields (name)
  2. Validate store name is unique
  3. Set opening timestamp
  4. Initialize inventory tracking
  5. Log store opening
  6. Return store entity
```

### 21. StoreClosingProcessor

**Entity**: Store
**Transition**: close_store (open → closed)
**Input**: Store entity to close
**Output**: Closed store entity

**Business Logic**:
```
PROCESS close_store:
  1. Get current store entity
  2. Validate store is in open state
  3. Handle pending orders appropriately
  4. Update closing timestamp
  5. Log store closing
  6. Return store entity
```

### 22. StoreReopeningProcessor

**Entity**: Store
**Transition**: reopen_store (closed → open)
**Input**: Store entity to reopen
**Output**: Open store entity

**Business Logic**:
```
PROCESS reopen_store:
  1. Get current store entity
  2. Validate store is in closed state
  3. Update reopening timestamp
  4. Log store reopening
  5. Return store entity
```

### 23. StoreMaintenanceProcessor

**Entity**: Store
**Transition**: start_maintenance (open → maintenance)
**Input**: Store entity entering maintenance
**Output**: Store entity in maintenance

**Business Logic**:
```
PROCESS start_maintenance:
  1. Get current store entity
  2. Validate store is in open state
  3. Pause new order processing
  4. Update maintenance start timestamp
  5. Log maintenance start
  6. Return store entity
```

### 24. StoreMaintenanceEndProcessor

**Entity**: Store
**Transition**: end_maintenance (maintenance → open)
**Input**: Store entity ending maintenance
**Output**: Open store entity

**Business Logic**:
```
PROCESS end_maintenance:
  1. Get current store entity
  2. Validate store is in maintenance state
  3. Resume order processing
  4. Update maintenance end timestamp
  5. Log maintenance end
  6. Return store entity
```

## Processor Implementation Notes

1. **Entity Access**: Use `context.entityResponse().getEntity()` to get the current entity
2. **Metadata Access**: Use `context.entityResponse().getMetadata()` for entity metadata
3. **Other Entity Updates**: Use EntityService to update related entities with transitions
4. **Current Entity Limitation**: Cannot update the current entity's state (managed by workflow)
5. **Error Handling**: Validate all business rules and throw appropriate exceptions
6. **Logging**: Log all significant business events for audit trail
7. **Notifications**: Send appropriate notifications to users when needed
