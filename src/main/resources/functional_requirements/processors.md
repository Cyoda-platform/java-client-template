# Processor Requirements

## Overview
This document defines the detailed requirements for all processors in the Cyoda OMS Backend system. Processors implement business logic during workflow transitions and handle entity transformations.

**Critical Limitations:**
- Processors cannot update the current entity being processed via EntityService
- Processors can read the current entity and update OTHER entities
- Use ProcessorSerializer for type-safe entity processing

## 1. CartRecalculateTotalsProcessor

**Processor Name:** CartRecalculateTotalsProcessor
**Entity:** Cart
**Package:** `com.java_template.application.processor`
**Transitions:** ACTIVATE_CART, ADD_ITEM, UPDATE_ITEM, REMOVE_ITEM

### Purpose
Recalculates cart totals (totalItems and grandTotal) whenever cart lines are modified.

### Input Data
- Current Cart entity with updated lines
- Cart lines with sku, name, price, qty

### Expected Output
- Modified Cart entity with updated totalItems and grandTotal
- No other entities are updated
- Transition: null (stays in same state)

### Pseudocode
```
FUNCTION process(cart):
    totalItems = 0
    grandTotal = 0.00
    
    FOR each line in cart.lines:
        lineTotal = line.price * line.qty
        totalItems = totalItems + line.qty
        grandTotal = grandTotal + lineTotal
    END FOR
    
    cart.totalItems = totalItems
    cart.grandTotal = grandTotal
    cart.updatedAt = currentTimestamp()
    
    RETURN cart
END FUNCTION
```

### Validation Rules
- All cart lines must have qty > 0
- All cart lines must have price >= 0
- Cart must have at least one line when calculating totals

## 2. PaymentCreateDummyPaymentProcessor

**Processor Name:** PaymentCreateDummyPaymentProcessor
**Entity:** Payment
**Package:** `com.java_template.application.processor`
**Transitions:** START_DUMMY_PAYMENT

### Purpose
Creates a dummy payment record in INITIATED state and schedules automatic payment approval.

### Input Data
- Payment entity with paymentId, cartId, amount
- Provider is always "DUMMY"

### Expected Output
- Payment entity in INITIATED state
- No other entities are updated
- Transition: null (automatic transition to INITIATED state)

### Pseudocode
```
FUNCTION process(payment):
    payment.provider = "DUMMY"
    payment.createdAt = currentTimestamp()
    payment.updatedAt = currentTimestamp()
    
    // Schedule auto-payment after 3 seconds
    scheduleDelayedTransition(payment.paymentId, "AUTO_MARK_PAID", 3000)
    
    RETURN payment
END FUNCTION
```

### Validation Rules
- Payment amount must be > 0
- CartId must reference an existing cart
- PaymentId must be unique

## 3. PaymentAutoMarkPaidProcessor

**Processor Name:** PaymentAutoMarkPaidProcessor
**Entity:** Payment
**Package:** `com.java_template.application.processor`
**Transitions:** AUTO_MARK_PAID

### Purpose
Automatically marks a dummy payment as PAID after a 3-second delay, simulating payment processing.

### Input Data
- Payment entity in INITIATED state

### Expected Output
- Payment entity marked as processed
- No other entities are updated
- Transition: null (automatic transition to PAID state)

### Pseudocode
```
FUNCTION process(payment):
    // Simulate payment processing delay
    IF payment.provider == "DUMMY":
        payment.updatedAt = currentTimestamp()
        // Payment state will be automatically updated to PAID by workflow
    END IF
    
    RETURN payment
END FUNCTION
```

### Validation Rules
- Payment must be in INITIATED state
- Payment provider must be "DUMMY"

## 4. OrderCreateFromPaidProcessor

**Processor Name:** OrderCreateFromPaidProcessor
**Entity:** Order
**Package:** `com.java_template.application.processor`
**Transitions:** CREATE_ORDER_FROM_PAID

### Purpose
Creates an order from a paid cart and payment, decrements product stock, and creates a shipment.

### Input Data
- Order entity with orderId, orderNumber
- Associated Cart entity (via cartId)
- Associated Payment entity (via paymentId)

### Expected Output
- Order entity with snapshotted cart data
- Updated Product entities with decremented quantityAvailable
- New Shipment entity created
- Transition: null (automatic transition to WAITING_TO_FULFILL state)

### Pseudocode
```
FUNCTION process(order):
    // Get associated cart and payment
    cart = entityService.getEntity("Cart", order.cartId)
    payment = entityService.getEntity("Payment", order.paymentId)
    
    // Validate preconditions
    IF payment.state != "PAID":
        THROW "Payment must be in PAID state"
    END IF
    
    IF cart.state != "CONVERTED":
        THROW "Cart must be in CONVERTED state"
    END IF
    
    // Snapshot cart data into order
    order.lines = []
    FOR each cartLine in cart.lines:
        orderLine = {
            sku: cartLine.sku,
            name: cartLine.name,
            unitPrice: cartLine.price,
            qty: cartLine.qty,
            lineTotal: cartLine.price * cartLine.qty
        }
        order.lines.add(orderLine)
    END FOR
    
    order.totals = {
        items: cart.grandTotal,
        grand: cart.grandTotal
    }
    
    order.guestContact = cart.guestContact
    order.createdAt = currentTimestamp()
    order.updatedAt = currentTimestamp()
    
    // Decrement product stock
    FOR each orderLine in order.lines:
        product = entityService.getEntity("Product", orderLine.sku)
        product.quantityAvailable = product.quantityAvailable - orderLine.qty
        entityService.updateEntity(product, null) // No transition
    END FOR
    
    // Create shipment
    shipment = {
        shipmentId: generateUniqueId(),
        orderId: order.orderId,
        lines: [],
        createdAt: currentTimestamp(),
        updatedAt: currentTimestamp()
    }
    
    FOR each orderLine in order.lines:
        shipmentLine = {
            sku: orderLine.sku,
            qtyOrdered: orderLine.qty,
            qtyPicked: 0,
            qtyShipped: 0
        }
        shipment.lines.add(shipmentLine)
    END FOR
    
    entityService.createEntity(shipment, "CREATE_SHIPMENT")
    
    RETURN order
END FUNCTION
```

### Validation Rules
- Payment must be in PAID state
- Cart must be in CONVERTED state
- Cart must have guest contact with required address fields
- All products must have sufficient quantityAvailable
- Order lines must match cart lines exactly

### Error Handling
- If insufficient stock: throw exception with details
- If payment not paid: throw exception
- If cart not converted: throw exception

## Common Processor Patterns

### Entity Service Usage
```
// Read current entity (allowed)
currentEntity = getCurrentEntity()

// Read other entities (allowed)
otherEntity = entityService.getEntity("EntityType", entityId)

// Update other entities (allowed)
entityService.updateEntity(otherEntity, "TRANSITION_NAME")

// Create new entities (allowed)
entityService.createEntity(newEntity, "TRANSITION_NAME")

// Update current entity via EntityService (NOT ALLOWED)
// entityService.updateEntity(currentEntity, "TRANSITION") // FORBIDDEN
```

### ProcessorSerializer Usage
```
// Use ProcessorSerializer for type-safe processing
ProcessorSerializer<EntityType> serializer = new ProcessorSerializer<>(EntityType.class);
EntityType entity = serializer.deserialize(entityData);
// Process entity
String result = serializer.serialize(entity);
```

### Timestamp Management
All processors should:
- Set `createdAt` when creating new entities
- Update `updatedAt` when modifying entities
- Use `currentTimestamp()` for consistency

### Error Handling
Processors should:
- Validate input data and throw meaningful exceptions
- Check entity states before processing
- Ensure data consistency across entity updates
- Log important business events

### Transaction Considerations
- All entity updates within a processor should be atomic
- If any update fails, the entire transaction should rollback
- Use appropriate exception handling for rollback scenarios

## Implementation Guidelines

1. **Naming Convention:** ProcessorName must start with entity name (e.g., CartRecalculateTotalsProcessor)
2. **Package Location:** `com.java_template.application.processor`
3. **Interface Implementation:** Must implement `CyodaProcessor` interface
4. **Spring Discovery:** Use `@Component` annotation
5. **Type Safety:** Use ProcessorSerializer for entity processing
6. **State Management:** Cannot modify current entity state directly
7. **Side Effects:** Can update other entities, create new entities, call external services
