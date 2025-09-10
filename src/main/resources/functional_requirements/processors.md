# Purrfect Pets - Processor Requirements

## Overview
Processors implement the business logic for workflow transitions. Each processor handles specific entity transformations and may interact with other entities through EntityService.

## Pet Entity Processors

### 1. PetInitializationProcessor
**Entity**: Pet
**Transition**: none → available
**Input**: New Pet entity
**Description**: Initialize a new pet and set default values

#### Pseudocode:
```
PROCESS pet:
  SET pet.createdAt = current timestamp
  SET pet.updatedAt = current timestamp
  IF pet.status is null THEN
    SET pet.status = "available"
  END IF
  IF pet.vaccinated is null THEN
    SET pet.vaccinated = false
  END IF
  VALIDATE pet data integrity
  LOG "Pet initialized: " + pet.petId
  RETURN updated pet
```

#### Expected Output:
- Pet entity with timestamps and default values set
- Pet state transitions to "available"

### 2. PetReservationProcessor
**Entity**: Pet
**Transition**: available → pending
**Input**: Pet entity with reservation request
**Description**: Reserve a pet when an order is placed

#### Pseudocode:
```
PROCESS pet:
  SET pet.updatedAt = current timestamp
  LOG "Pet reserved: " + pet.petId
  
  // Update related order if orderId is provided in context
  IF context contains orderId THEN
    FIND order by orderId using EntityService
    IF order exists THEN
      UPDATE order status to "placed" (transition: place_order)
    END IF
  END IF
  
  RETURN updated pet
```

#### Expected Output:
- Pet entity with updated timestamp
- Pet state transitions to "pending"
- Related order updated if applicable

### 3. PetSaleProcessor
**Entity**: Pet
**Transition**: pending → sold
**Input**: Pet entity with sale completion data
**Description**: Complete the sale of a pet

#### Pseudocode:
```
PROCESS pet:
  SET pet.updatedAt = current timestamp
  LOG "Pet sold: " + pet.petId
  
  // Update related order to delivered
  IF context contains orderId THEN
    FIND order by orderId using EntityService
    IF order exists THEN
      UPDATE order status to "delivered" (transition: deliver_order)
      SET order.complete = true
      SET order.updatedAt = current timestamp
    END IF
  END IF
  
  RETURN updated pet
```

#### Expected Output:
- Pet entity with updated timestamp
- Pet state transitions to "sold"
- Related order marked as delivered

### 4. PetReservationCancellationProcessor
**Entity**: Pet
**Transition**: pending → available
**Input**: Pet entity with cancellation request
**Description**: Cancel pet reservation and return to available status

#### Pseudocode:
```
PROCESS pet:
  SET pet.updatedAt = current timestamp
  LOG "Pet reservation cancelled: " + pet.petId
  
  // Cancel related order
  IF context contains orderId THEN
    FIND order by orderId using EntityService
    IF order exists AND order.status = "placed" THEN
      UPDATE order status to "none" (transition: cancel_order)
      SET order.updatedAt = current timestamp
    END IF
  END IF
  
  RETURN updated pet
```

#### Expected Output:
- Pet entity with updated timestamp
- Pet state transitions to "available"
- Related order cancelled if applicable

### 5. PetReturnProcessor
**Entity**: Pet
**Transition**: sold → available
**Input**: Pet entity with return request
**Description**: Process pet return (rare case)

#### Pseudocode:
```
PROCESS pet:
  SET pet.updatedAt = current timestamp
  LOG "Pet returned: " + pet.petId
  
  // Note: This is a rare case, typically for returns or exchanges
  // No automatic order updates as this is an exceptional case
  
  RETURN updated pet
```

#### Expected Output:
- Pet entity with updated timestamp
- Pet state transitions to "available"

## Order Entity Processors

### 6. OrderPlacementProcessor
**Entity**: Order
**Transition**: none → placed
**Input**: New Order entity
**Description**: Process a new order placement

#### Pseudocode:
```
PROCESS order:
  SET order.createdAt = current timestamp
  SET order.updatedAt = current timestamp
  SET order.orderDate = current timestamp
  IF order.quantity is null THEN
    SET order.quantity = 1
  END IF
  IF order.complete is null THEN
    SET order.complete = false
  END IF
  
  // Reserve the pet
  FIND pet by order.petId using EntityService
  IF pet exists AND pet.status = "available" THEN
    UPDATE pet status to "pending" (transition: reserve_pet)
    SET pet.updatedAt = current timestamp
  END IF
  
  LOG "Order placed: " + order.orderId
  RETURN updated order
```

#### Expected Output:
- Order entity with timestamps and defaults set
- Order state transitions to "placed"
- Associated pet reserved (pending state)

### 7. OrderApprovalProcessor
**Entity**: Order
**Transition**: placed → approved
**Input**: Order entity with approval data
**Description**: Approve an order for processing

#### Pseudocode:
```
PROCESS order:
  SET order.updatedAt = current timestamp
  
  // Calculate total amount if not set
  IF order.totalAmount is null THEN
    FIND pet by order.petId using EntityService
    IF pet exists AND pet.price is not null THEN
      SET order.totalAmount = pet.price * order.quantity
    END IF
  END IF
  
  // Set expected ship date (e.g., 3 days from now)
  SET order.shipDate = current timestamp + 3 days
  
  LOG "Order approved: " + order.orderId
  RETURN updated order
```

#### Expected Output:
- Order entity with updated timestamp and calculated amounts
- Order state transitions to "approved"

### 8. OrderDeliveryProcessor
**Entity**: Order
**Transition**: approved → delivered
**Input**: Order entity with delivery confirmation
**Description**: Mark order as delivered

#### Pseudocode:
```
PROCESS order:
  SET order.updatedAt = current timestamp
  SET order.complete = true
  
  // Mark pet as sold
  FIND pet by order.petId using EntityService
  IF pet exists AND pet.status = "pending" THEN
    UPDATE pet status to "sold" (transition: complete_sale)
    SET pet.updatedAt = current timestamp
  END IF
  
  LOG "Order delivered: " + order.orderId
  RETURN updated order
```

#### Expected Output:
- Order entity marked as complete
- Order state transitions to "delivered"
- Associated pet marked as sold

### 9. OrderCancellationProcessor
**Entity**: Order
**Transition**: placed → none
**Input**: Order entity with cancellation request
**Description**: Cancel a placed order

#### Pseudocode:
```
PROCESS order:
  SET order.updatedAt = current timestamp
  
  // Release the pet reservation
  FIND pet by order.petId using EntityService
  IF pet exists AND pet.status = "pending" THEN
    UPDATE pet status to "available" (transition: cancel_reservation)
    SET pet.updatedAt = current timestamp
  END IF
  
  LOG "Order cancelled: " + order.orderId
  RETURN updated order
```

#### Expected Output:
- Order entity with updated timestamp
- Order state transitions to "none"
- Associated pet returned to available status

### 10. OrderRejectionProcessor
**Entity**: Order
**Transition**: approved → placed
**Input**: Order entity with rejection reason
**Description**: Reject an approved order back to placed status

#### Pseudocode:
```
PROCESS order:
  SET order.updatedAt = current timestamp
  SET order.shipDate = null
  
  // Add rejection reason to notes if provided
  IF context contains rejectionReason THEN
    SET order.notes = order.notes + " | Rejected: " + rejectionReason
  END IF
  
  LOG "Order rejected: " + order.orderId
  RETURN updated order
```

#### Expected Output:
- Order entity with updated timestamp and notes
- Order state transitions to "placed"

## Category Entity Processors

### 11. CategoryActivationProcessor
**Entity**: Category
**Transition**: none → active
**Input**: New Category entity
**Description**: Activate a new category

#### Pseudocode:
```
PROCESS category:
  SET category.createdAt = current timestamp
  SET category.updatedAt = current timestamp
  IF category.active is null THEN
    SET category.active = true
  END IF
  
  LOG "Category activated: " + category.categoryId
  RETURN updated category
```

#### Expected Output:
- Category entity with timestamps and active status set
- Category state transitions to "active"

## Tag Entity Processors

### 12. TagActivationProcessor
**Entity**: Tag
**Transition**: none → active
**Input**: New Tag entity
**Description**: Activate a new tag

#### Pseudocode:
```
PROCESS tag:
  SET tag.createdAt = current timestamp
  SET tag.updatedAt = current timestamp
  IF tag.active is null THEN
    SET tag.active = true
  END IF
  IF tag.color is null THEN
    SET tag.color = "blue"  // default color
  END IF
  
  LOG "Tag activated: " + tag.tagId
  RETURN updated tag
```

#### Expected Output:
- Tag entity with timestamps, active status, and default color set
- Tag state transitions to "active"

## Important Notes

### EntityService Usage:
- Processors can read the current entity being processed
- Processors can update OTHER entities using EntityService
- Processors CANNOT update the current entity using EntityService
- When updating other entities, specify the transition name or null if no state change needed

### Error Handling:
- All processors should include proper error logging
- Failed operations should be logged with entity IDs for traceability
- Processors should handle null values gracefully

### Performance Considerations:
- Minimize EntityService calls within processors
- Use batch operations when updating multiple entities
- Log important business events for audit trails
