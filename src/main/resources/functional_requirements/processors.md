# Processor Requirements

## Overview

This document defines the detailed requirements for all processors in the Cyoda OMS Backend system. Processors implement the business logic for workflow transitions and are executed when entities move between states.

## Processor Design Principles

1. Each processor implements the `CyodaProcessor` interface
2. Processors are stateless and should be thread-safe
3. Use pseudocode that business analysts can understand
4. Specify input data, processing logic, and expected output
5. Include transition names for entity updates when applicable
6. Handle error cases gracefully

## 1. CartRecalculateTotalsProcessor

**Entity:** Cart
**Transitions:** `create_on_first_add`, `add_item`, `update_item`
**Purpose:** Recalculates cart totals when items are added, updated, or removed

### Input Data
- Cart entity with lines array
- Each line contains: { sku, name, price, qty }

### Processing Logic (Pseudocode)
```
BEGIN CartRecalculateTotalsProcessor.process()
    INPUT: cart entity
    
    INITIALIZE totalItems = 0
    INITIALIZE grandTotal = 0.0
    
    FOR each line in cart.lines:
        IF line.qty <= 0:
            REMOVE line from cart.lines
            CONTINUE
        END IF
        
        SET line.lineTotal = line.price * line.qty
        ADD line.qty to totalItems
        ADD line.lineTotal to grandTotal
    END FOR
    
    SET cart.totalItems = totalItems
    SET cart.grandTotal = grandTotal
    SET cart.updatedAt = current timestamp
    
    RETURN updated cart entity
END
```

### Expected Output
- Updated Cart entity with recalculated totals
- Transition: null (stays in same state)
- Lines with qty <= 0 are removed
- totalItems and grandTotal are updated

### Error Handling
- If cart is null: throw validation error
- If lines array is null: set empty array and zero totals
- If line price is negative: log warning and use 0

## 2. PaymentAutoMarkPaidProcessor

**Entity:** Payment
**Transitions:** `auto_mark_paid`
**Purpose:** Automatically marks payment as PAID after approximately 3 seconds (dummy payment simulation)

### Input Data
- Payment entity in INITIATED state
- Payment amount and cartId

### Processing Logic (Pseudocode)
```
BEGIN PaymentAutoMarkPaidProcessor.process()
    INPUT: payment entity
    
    VALIDATE payment.state == "INITIATED"
    VALIDATE payment.provider == "DUMMY"
    
    WAIT 3 seconds (simulate payment processing)
    
    SET payment.updatedAt = current timestamp
    
    LOG "Payment {paymentId} auto-approved for amount {amount}"
    
    RETURN updated payment entity
END
```

### Expected Output
- Updated Payment entity
- Transition: `auto_mark_paid` (INITIATED → PAID)
- Payment is marked as successfully processed

### Error Handling
- If payment not in INITIATED state: throw invalid state error
- If provider is not DUMMY: throw unsupported provider error
- If amount <= 0: throw invalid amount error

## 3. OrderCreateFromPaidProcessor

**Entity:** Order
**Transitions:** `create_order_from_paid`
**Purpose:** Creates order from paid cart, decrements product stock, and creates shipment

### Input Data
- Order entity (newly created)
- Associated Cart entity (CONVERTED state)
- Associated Payment entity (PAID state)

### Processing Logic (Pseudocode)
```
BEGIN OrderCreateFromPaidProcessor.process()
    INPUT: order entity, cart entity, payment entity
    
    VALIDATE payment.state == "PAID"
    VALIDATE cart.state == "CONVERTED"
    VALIDATE cart.guestContact is not null
    
    // Generate short ULID for order number
    SET order.orderNumber = generateShortULID()
    
    // Snapshot cart data to order
    SET order.lines = []
    SET order.totals.items = 0
    SET order.totals.grand = 0.0
    
    FOR each cartLine in cart.lines:
        CREATE orderLine = {
            sku: cartLine.sku,
            name: cartLine.name,
            unitPrice: cartLine.price,
            qty: cartLine.qty,
            lineTotal: cartLine.price * cartLine.qty
        }
        ADD orderLine to order.lines
        ADD orderLine.qty to order.totals.items
        ADD orderLine.lineTotal to order.totals.grand
        
        // Decrement product stock
        FIND product by sku = cartLine.sku
        IF product exists:
            SET product.quantityAvailable = product.quantityAvailable - cartLine.qty
            UPDATE product entity (transition: null)
        ELSE:
            LOG warning "Product {sku} not found for stock decrement"
        END IF
    END FOR
    
    // Copy guest contact information
    SET order.guestContact = cart.guestContact
    SET order.updatedAt = current timestamp
    
    // Create shipment
    CREATE shipment entity:
        SET shipment.shipmentId = generateUUID()
        SET shipment.orderId = order.orderId
        SET shipment.lines = []
        
        FOR each orderLine in order.lines:
            CREATE shipmentLine = {
                sku: orderLine.sku,
                qtyOrdered: orderLine.qty,
                qtyPicked: 0,
                qtyShipped: 0
            }
            ADD shipmentLine to shipment.lines
        END FOR
        
        SET shipment.createdAt = current timestamp
        SET shipment.updatedAt = current timestamp
    
    SAVE shipment entity (transition: "initialize_shipment")
    
    LOG "Order {orderNumber} created from cart {cartId} with {itemCount} items"
    
    RETURN updated order entity
END
```

### Expected Output
- Updated Order entity with snapshotted cart data
- Transition: `create_order_from_paid` (WAITING_TO_FULFILL → PICKING)
- Product entities updated with decremented stock (transition: null)
- New Shipment entity created (transition: "initialize_shipment")

### Error Handling
- If payment not PAID: throw invalid payment state error
- If cart not CONVERTED: throw invalid cart state error
- If guest contact missing: throw missing contact error
- If product not found for stock decrement: log warning and continue
- If insufficient stock: log warning but allow negative stock (oversell)

## 4. ShipmentUpdateOrderStateProcessor

**Entity:** Shipment
**Transitions:** `ready_to_send`, `mark_sent`, `mark_delivered`
**Purpose:** Synchronizes order state with shipment state changes

### Input Data
- Shipment entity with updated state
- Associated Order entity

### Processing Logic (Pseudocode)
```
BEGIN ShipmentUpdateOrderStateProcessor.process()
    INPUT: shipment entity
    
    FIND order by orderId = shipment.orderId
    VALIDATE order exists
    
    // Map shipment state to order state
    SWITCH shipment.meta.state:
        CASE "PICKING":
            SET targetOrderState = "PICKING"
            SET orderTransition = "ready_to_send"
        CASE "WAITING_TO_SEND":
            SET targetOrderState = "WAITING_TO_SEND"
            SET orderTransition = "ready_to_send"
        CASE "SENT":
            SET targetOrderState = "SENT"
            SET orderTransition = "mark_sent"
        CASE "DELIVERED":
            SET targetOrderState = "DELIVERED"
            SET orderTransition = "mark_delivered"
        DEFAULT:
            LOG warning "Unknown shipment state: {shipment.meta.state}"
            RETURN shipment entity unchanged
    END SWITCH
    
    // Update order state if different
    IF order.meta.state != targetOrderState:
        SET order.updatedAt = current timestamp
        UPDATE order entity (transition: orderTransition)
        LOG "Order {orderId} state updated to {targetOrderState} following shipment"
    END IF
    
    SET shipment.updatedAt = current timestamp
    
    RETURN updated shipment entity
END
```

### Expected Output
- Updated Shipment entity
- Transition: varies based on current transition (`ready_to_send`, `mark_sent`, `mark_delivered`)
- Order entity updated to match shipment state (with appropriate transition)

### Error Handling
- If order not found: throw missing order error
- If shipment state is unknown: log warning and continue
- If order update fails: log error but continue with shipment update

## Processor Implementation Notes

### Naming Conventions
- All processor names use PascalCase starting with entity name
- Example: `CartRecalculateTotalsProcessor`, `PaymentAutoMarkPaidProcessor`

### Error Handling Strategy
- Log errors but don't fail the entire workflow unless critical
- Use appropriate error codes and messages
- Validate input data before processing
- Handle missing related entities gracefully

### Performance Considerations
- Keep processing synchronous where possible for demo simplicity
- Use batch operations for multiple entity updates
- Cache frequently accessed data (e.g., product lookups)
- Log processing times for monitoring

### Transaction Boundaries
- Each processor runs in its own transaction context
- Related entity updates should be atomic
- Use appropriate isolation levels for concurrent access

### Audit and Logging
- Log all significant business events
- Include entity IDs and transition names in log messages
- Track processing times and error rates
- Use structured logging for better searchability
