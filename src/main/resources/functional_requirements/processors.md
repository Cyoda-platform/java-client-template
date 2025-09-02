# Processor Requirements

## Overview

This document defines the detailed requirements for all processors in the Cyoda OMS Backend system. Processors implement business logic for workflow transitions and entity transformations.

## Processor Definitions

### 1. CartRecalculateTotalsProcessor

**Entity**: Cart
**Transition**: ADD_ITEM, UPDATE_ITEM, ACTIVATE_CART
**Purpose**: Recalculates cart totals when items are added, updated, or removed

**Input Data**: 
- Cart entity with updated lines array
- Individual line items with sku, name, price, qty

**Expected Output**: 
- Updated Cart entity with recalculated totalItems and grandTotal
- No state transition (stays in current state)

**Pseudocode**:
```
PROCESS CartRecalculateTotals:
  INPUT: cart entity
  
  SET totalItems = 0
  SET grandTotal = 0.0
  
  FOR EACH line IN cart.lines:
    IF line.qty > 0:
      SET totalItems = totalItems + line.qty
      SET lineTotal = line.price * line.qty
      SET grandTotal = grandTotal + lineTotal
    ELSE:
      REMOVE line FROM cart.lines
  
  SET cart.totalItems = totalItems
  SET cart.grandTotal = grandTotal
  SET cart.updatedAt = current timestamp
  
  RETURN updated cart entity
```

**Other Entity Updates**: None
**Transition Name**: null (no state change)

---

### 2. PaymentCreateDummyProcessor

**Entity**: Payment
**Transition**: START_DUMMY_PAYMENT
**Purpose**: Creates a dummy payment record and initializes payment processing

**Input Data**:
- Payment entity with cartId and amount
- Cart entity reference for validation

**Expected Output**:
- Payment entity with provider set to "DUMMY"
- Payment moved to INITIATED state

**Pseudocode**:
```
PROCESS PaymentCreateDummy:
  INPUT: payment entity
  
  VALIDATE payment.cartId is not null
  VALIDATE payment.amount > 0
  
  SET payment.provider = "DUMMY"
  SET payment.createdAt = current timestamp
  SET payment.updatedAt = current timestamp
  
  LOG "Dummy payment created for cart: " + payment.cartId + ", amount: " + payment.amount
  
  RETURN payment entity
```

**Other Entity Updates**: None
**Transition Name**: null (state managed by workflow)

---

### 3. PaymentAutoMarkPaidProcessor

**Entity**: Payment
**Transition**: AUTO_MARK_PAID
**Purpose**: Automatically marks dummy payment as paid after ~3 seconds delay

**Input Data**:
- Payment entity in INITIATED state

**Expected Output**:
- Payment entity marked as paid
- Payment moved to PAID state

**Pseudocode**:
```
PROCESS PaymentAutoMarkPaid:
  INPUT: payment entity
  
  VALIDATE payment.provider = "DUMMY"
  VALIDATE entity.meta.state = "INITIATED"
  
  WAIT 3 seconds (simulate processing time)
  
  SET payment.updatedAt = current timestamp
  
  LOG "Dummy payment auto-approved: " + payment.paymentId
  
  RETURN payment entity
```

**Other Entity Updates**: None
**Transition Name**: null (state managed by workflow)

---

### 4. OrderCreateFromPaidProcessor

**Entity**: Order
**Transition**: CREATE_ORDER_FROM_PAID
**Purpose**: Creates order from paid cart, decrements product stock, creates shipment

**Input Data**:
- Order entity with basic information
- Cart entity (via cartId reference)
- Payment entity (via paymentId reference)

**Expected Output**:
- Complete Order entity with snapshotted cart data
- Updated Product entities with decremented quantityAvailable
- New Shipment entity created

**Pseudocode**:
```
PROCESS OrderCreateFromPaid:
  INPUT: order entity, cartId, paymentId
  
  GET cart = findCartById(cartId)
  GET payment = findPaymentById(paymentId)
  
  VALIDATE payment.meta.state = "PAID"
  VALIDATE cart.meta.state = "CHECKING_OUT"
  
  // Generate short ULID for order number
  SET order.orderNumber = generateShortULID()
  
  // Snapshot cart lines to order
  SET order.lines = []
  FOR EACH cartLine IN cart.lines:
    CREATE orderLine:
      SET orderLine.sku = cartLine.sku
      SET orderLine.name = cartLine.name
      SET orderLine.unitPrice = cartLine.price
      SET orderLine.qty = cartLine.qty
      SET orderLine.lineTotal = cartLine.price * cartLine.qty
    ADD orderLine TO order.lines
  
  // Snapshot totals
  SET order.totals.items = cart.totalItems
  SET order.totals.grand = cart.grandTotal
  
  // Snapshot guest contact
  SET order.guestContact = cart.guestContact
  
  SET order.createdAt = current timestamp
  SET order.updatedAt = current timestamp
  
  // Decrement product stock
  FOR EACH orderLine IN order.lines:
    GET product = findProductBySku(orderLine.sku)
    SET product.quantityAvailable = product.quantityAvailable - orderLine.qty
    UPDATE product entity (no transition)
  
  // Create shipment
  CREATE shipment:
    SET shipment.shipmentId = generateUUID()
    SET shipment.orderId = order.orderId
    SET shipment.lines = []
    FOR EACH orderLine IN order.lines:
      CREATE shipmentLine:
        SET shipmentLine.sku = orderLine.sku
        SET shipmentLine.qtyOrdered = orderLine.qty
        SET shipmentLine.qtyPicked = 0
        SET shipmentLine.qtyShipped = 0
      ADD shipmentLine TO shipment.lines
    SET shipment.createdAt = current timestamp
    SET shipment.updatedAt = current timestamp
  
  CREATE shipment entity with transition "CREATE_SHIPMENT"
  
  // Update cart to converted
  UPDATE cart entity with transition "CHECKOUT"
  
  LOG "Order created: " + order.orderNumber + " from cart: " + cartId
  
  RETURN order entity
```

**Other Entity Updates**: 
- Product entities: decrement quantityAvailable (no transition)
- Shipment entity: create new with transition "CREATE_SHIPMENT"
- Cart entity: update with transition "CHECKOUT"

**Transition Name**: null (state managed by workflow)

---

### 5. ShipmentCreateProcessor

**Entity**: Shipment
**Transition**: CREATE_SHIPMENT
**Purpose**: Initializes shipment with picking state

**Input Data**:
- Shipment entity with orderId and lines

**Expected Output**:
- Shipment entity ready for picking

**Pseudocode**:
```
PROCESS ShipmentCreate:
  INPUT: shipment entity
  
  VALIDATE shipment.orderId is not null
  VALIDATE shipment.lines is not empty
  
  FOR EACH line IN shipment.lines:
    VALIDATE line.qtyOrdered > 0
    SET line.qtyPicked = 0
    SET line.qtyShipped = 0
  
  SET shipment.createdAt = current timestamp
  SET shipment.updatedAt = current timestamp
  
  LOG "Shipment created for order: " + shipment.orderId
  
  RETURN shipment entity
```

**Other Entity Updates**: None
**Transition Name**: null (state managed by workflow)

---

### 6. ShipmentUpdateOrderStateProcessor

**Entity**: Shipment
**Transition**: READY_TO_SEND, MARK_SENT, MARK_DELIVERED
**Purpose**: Updates corresponding order state when shipment state changes

**Input Data**:
- Shipment entity with current state
- Order entity reference

**Expected Output**:
- Updated shipment entity
- Order entity with synchronized state

**Pseudocode**:
```
PROCESS ShipmentUpdateOrderState:
  INPUT: shipment entity
  
  GET order = findOrderById(shipment.orderId)
  
  SET shipment.updatedAt = current timestamp
  
  // Determine order transition based on shipment state
  IF shipment.meta.state = "WAITING_TO_SEND":
    UPDATE order entity with transition "READY_TO_SEND"
  ELSE IF shipment.meta.state = "SENT":
    UPDATE order entity with transition "MARK_SENT"
  ELSE IF shipment.meta.state = "DELIVERED":
    UPDATE order entity with transition "MARK_DELIVERED"
  
  LOG "Order state updated to match shipment: " + shipment.shipmentId
  
  RETURN shipment entity
```

**Other Entity Updates**: 
- Order entity: update with corresponding transition

**Transition Name**: null (state managed by workflow)

## Processor Summary

| Processor | Entity | Purpose | Creates Other Entities |
|-----------|--------|---------|----------------------|
| CartRecalculateTotalsProcessor | Cart | Recalculate totals | No |
| PaymentCreateDummyProcessor | Payment | Initialize dummy payment | No |
| PaymentAutoMarkPaidProcessor | Payment | Auto-approve payment | No |
| OrderCreateFromPaidProcessor | Order | Create order from cart | Shipment |
| ShipmentCreateProcessor | Shipment | Initialize shipment | No |
| ShipmentUpdateOrderStateProcessor | Shipment | Sync order state | No |

## Business Rules

1. **Stock Management**: Product stock is decremented immediately when order is created
2. **State Synchronization**: Shipment state changes automatically update order state
3. **Data Snapshots**: Order captures cart and contact data at time of creation
4. **Dummy Payment**: 3-second delay simulates real payment processing
5. **Single Shipment**: One shipment per order for demo simplicity
