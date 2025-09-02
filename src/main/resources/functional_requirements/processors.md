# Processors Requirements

## Overview
Processors implement the business logic for workflow transitions. Each processor handles specific entity operations and state changes.

## Pet Processors

### 1. PetCreationProcessor
**Entity**: Pet  
**Transition**: create_pet (none → pending)  
**Input**: Pet entity with basic information  
**Output**: Pet entity with validated data and pending state  

**Pseudocode**:
```
process(pet):
    validate pet.name is not empty
    validate pet.photoUrls has at least one URL
    validate all photoUrls are valid URL format
    if pet.category exists:
        validate category exists in system
    if pet.tags exist:
        validate all tags exist in system
    set pet.createdAt to current timestamp
    set pet.updatedAt to current timestamp
    return pet
```

### 2. PetAvailabilityProcessor
**Entity**: Pet  
**Transition**: make_available (pending → available)  
**Input**: Pet entity in pending state  
**Output**: Pet entity ready for sale  

**Pseudocode**:
```
process(pet):
    validate pet has valid category
    validate pet has at least one photo
    validate pet name meets business standards
    set pet.updatedAt to current timestamp
    log "Pet {pet.id} is now available for sale"
    return pet
```

### 3. PetSaleProcessor
**Entity**: Pet  
**Transition**: sell_pet (available → sold)  
**Input**: Pet entity and order information  
**Output**: Pet entity marked as sold  
**Other Entity Updates**: Update Order entity to approved state (approve_order transition)

**Pseudocode**:
```
process(pet, orderInfo):
    validate pet is in available state
    validate order exists and is valid
    set pet.soldDate to current timestamp
    set pet.updatedAt to current timestamp
    update order status using approve_order transition
    log "Pet {pet.id} sold to order {orderInfo.orderId}"
    return pet
```

### 4. PetReturnProcessor
**Entity**: Pet  
**Transition**: return_pet (sold → available)  
**Input**: Pet entity in sold state  
**Output**: Pet entity returned to available status  
**Other Entity Updates**: Update related Order entity to cancelled (cancel_order transition)

**Pseudocode**:
```
process(pet):
    validate pet is in sold state
    find related order for this pet
    if order exists and not delivered:
        update order using cancel_order transition
    clear pet.soldDate
    set pet.updatedAt to current timestamp
    log "Pet {pet.id} returned to available status"
    return pet
```

## Category Processors

### 5. CategoryCreationProcessor
**Entity**: Category  
**Transition**: create_category (none → active)  
**Input**: Category entity with name  
**Output**: Category entity ready for use  

**Pseudocode**:
```
process(category):
    validate category.name is not empty
    validate category.name is unique in system
    trim and normalize category.name
    set category.createdAt to current timestamp
    set category.updatedAt to current timestamp
    log "Category {category.name} created"
    return category
```

### 6. CategoryDeactivationProcessor
**Entity**: Category  
**Transition**: deactivate_category (active → inactive)  
**Input**: Category entity in active state  
**Output**: Category entity marked as inactive  

**Pseudocode**:
```
process(category):
    validate no active pets are using this category
    set category.deactivatedAt to current timestamp
    set category.updatedAt to current timestamp
    log "Category {category.name} deactivated"
    return category
```

## Tag Processors

### 7. TagCreationProcessor
**Entity**: Tag  
**Transition**: create_tag (none → active)  
**Input**: Tag entity with name  
**Output**: Tag entity ready for use  

**Pseudocode**:
```
process(tag):
    validate tag.name is not empty
    validate tag.name is unique in system
    trim and normalize tag.name
    set tag.createdAt to current timestamp
    set tag.updatedAt to current timestamp
    log "Tag {tag.name} created"
    return tag
```

### 8. TagDeactivationProcessor
**Entity**: Tag  
**Transition**: deactivate_tag (active → inactive)  
**Input**: Tag entity in active state  
**Output**: Tag entity marked as inactive  

**Pseudocode**:
```
process(tag):
    validate no active pets are using this tag
    set tag.deactivatedAt to current timestamp
    set tag.updatedAt to current timestamp
    log "Tag {tag.name} deactivated"
    return tag
```

## Order Processors

### 9. OrderPlacementProcessor
**Entity**: Order  
**Transition**: place_order (none → placed)  
**Input**: Order entity with petId and customer info  
**Output**: Order entity with placed status  
**Other Entity Updates**: Update Pet entity to sold state (sell_pet transition)

**Pseudocode**:
```
process(order):
    validate order.petId references existing pet
    validate pet is in available state
    validate order.quantity is positive
    if order.shipDate exists:
        validate shipDate is in future
    set order.placedAt to current timestamp
    set order.updatedAt to current timestamp
    update pet using sell_pet transition
    log "Order {order.id} placed for pet {order.petId}"
    return order
```

### 10. OrderApprovalProcessor
**Entity**: Order  
**Transition**: approve_order (placed → approved)  
**Input**: Order entity in placed state  
**Output**: Order entity with approved status  

**Pseudocode**:
```
process(order):
    validate order is in placed state
    validate referenced pet is still in sold state
    validate payment information if required
    set order.approvedAt to current timestamp
    set order.updatedAt to current timestamp
    log "Order {order.id} approved"
    return order
```

### 11. OrderDeliveryProcessor
**Entity**: Order  
**Transition**: deliver_order (approved → delivered)  
**Input**: Order entity in approved state  
**Output**: Order entity with delivered status  

**Pseudocode**:
```
process(order):
    validate order is in approved state
    validate delivery information
    set order.deliveredAt to current timestamp
    set order.complete to true
    set order.updatedAt to current timestamp
    log "Order {order.id} delivered"
    return order
```

### 12. OrderCancellationProcessor
**Entity**: Order  
**Transition**: cancel_order (placed → none)  
**Input**: Order entity in placed state  
**Output**: Order entity cancelled  
**Other Entity Updates**: Update Pet entity back to available (return_pet transition)

**Pseudocode**:
```
process(order):
    validate order is in placed state
    find referenced pet
    if pet exists and is in sold state:
        update pet using return_pet transition
    set order.cancelledAt to current timestamp
    set order.updatedAt to current timestamp
    log "Order {order.id} cancelled"
    return order
```

## Common Processing Rules

### Error Handling
- All processors should validate input data
- Log all significant state changes
- Handle concurrent access gracefully
- Provide meaningful error messages

### Timestamp Management
- Always update entity.updatedAt timestamp
- Set specific timestamps for business events (soldDate, deliveredAt, etc.)
- Use system timezone for all timestamps

### Cross-Entity Updates
- When updating related entities, use their specific transition names
- Maintain referential integrity across entities
- Handle cascading updates properly

### Logging Requirements
- Log all successful state transitions
- Include entity IDs and relevant business data
- Use appropriate log levels (INFO for normal operations, WARN for business rule violations)
