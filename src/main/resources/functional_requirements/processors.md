# Processor Requirements

## Overview
This document defines the detailed requirements for all processors in the Cyoda OMS Backend system. Processors implement business logic for workflow transitions and are executed when entities move between states.

**Important Notes**:
- Processors can read current entity data but cannot update the current entity being processed
- Processors can interact with OTHER entities using EntityService
- Use entity getters/setters only, no reflection
- Prefer synchronous execution for demo simplicity

## Processor Definitions

### 1. Cart Processors

#### CartRecalculateTotalsProcessor

**Entity**: Cart  
**Processor Name**: CartRecalculateTotalsProcessor  
**Transitions**: CREATE_ON_FIRST_ADD, ADD_ITEM, UPDATE_ITEM, REMOVE_ITEM  
**Description**: Recalculates cart totals when items are added, updated, or removed.

**Input Data**: Cart entity with updated lines  
**Expected Output**: Cart entity with recalculated totals (cannot modify current entity state)

**Pseudocode**:
```
PROCESS CartRecalculateTotalsProcessor:
  INPUT: cart entity with lines
  
  INITIALIZE totalItems = 0
  INITIALIZE grandTotal = 0.0
  
  FOR each line in cart.lines:
    VALIDATE line.qty > 0
    VALIDATE line.price >= 0
    
    totalItems = totalItems + line.qty
    grandTotal = grandTotal + (line.price * line.qty)
  END FOR
  
  SET cart.totalItems = totalItems
  SET cart.grandTotal = grandTotal
  SET cart.updatedAt = current timestamp
  
  RETURN cart entity (system will handle state management)
END PROCESS
```

---

### 2. Payment Processors

#### PaymentCreateDummyPaymentProcessor

**Entity**: Payment  
**Processor Name**: PaymentCreateDummyPaymentProcessor  
**Transitions**: START_DUMMY_PAYMENT  
**Description**: Creates a dummy payment in INITIATED state.

**Input Data**: Payment entity with cartId and amount  
**Expected Output**: Payment entity ready for processing

**Pseudocode**:
```
PROCESS PaymentCreateDummyPaymentProcessor:
  INPUT: payment entity with cartId, amount
  
  VALIDATE payment.cartId is not null
  VALIDATE payment.amount > 0
  
  SET payment.provider = "DUMMY"
  SET payment.createdAt = current timestamp
  SET payment.updatedAt = current timestamp
  
  LOG "Dummy payment created for cart: " + payment.cartId
  
  RETURN payment entity
END PROCESS
```

#### PaymentAutoMarkPaidAfter3sProcessor

**Entity**: Payment  
**Processor Name**: PaymentAutoMarkPaidAfter3sProcessor  
**Transitions**: AUTO_MARK_PAID  
**Description**: Automatically marks payment as paid after ~3 seconds delay.

**Input Data**: Payment entity in INITIATED state  
**Expected Output**: Payment entity ready to transition to PAID state

**Pseudocode**:
```
PROCESS PaymentAutoMarkPaidAfter3sProcessor:
  INPUT: payment entity in INITIATED state
  
  VALIDATE payment.provider = "DUMMY"
  VALIDATE current state = "INITIATED"
  
  SLEEP for 3 seconds (simulate processing time)
  
  SET payment.updatedAt = current timestamp
  
  LOG "Payment auto-approved: " + payment.paymentId
  
  RETURN payment entity (system will transition to PAID state)
END PROCESS
```

---

### 3. Order Processors

#### OrderCreateOrderFromPaidProcessor

**Entity**: Order  
**Processor Name**: OrderCreateOrderFromPaidProcessor  
**Transitions**: CREATE_ORDER_FROM_PAID  
**Description**: Creates order from paid cart, decrements stock, creates shipment.

**Input Data**: Order entity with basic information  
**Expected Output**: Order entity with complete data, other entities updated

**Pseudocode**:
```
PROCESS OrderCreateOrderFromPaidProcessor:
  INPUT: order entity with orderId, cartId, paymentId
  
  // Get cart data
  cart = EntityService.findByBusinessId("Cart", order.cartId, "cartId")
  VALIDATE cart.state = "CHECKING_OUT"
  
  // Get payment data
  payment = EntityService.findByBusinessId("Payment", order.paymentId, "paymentId")
  VALIDATE payment.state = "PAID"
  VALIDATE payment.amount = cart.grandTotal
  
  // Generate order number (short ULID)
  SET order.orderNumber = generateShortULID()
  
  // Copy cart lines to order lines
  INITIALIZE order.lines = []
  FOR each cartLine in cart.lines:
    orderLine = {
      sku: cartLine.sku,
      name: cartLine.name,
      unitPrice: cartLine.price,
      qty: cartLine.qty,
      lineTotal: cartLine.price * cartLine.qty
    }
    ADD orderLine to order.lines
  END FOR
  
  // Copy totals
  SET order.totals = {
    items: cart.totalItems,
    grand: cart.grandTotal
  }
  
  // Copy guest contact (required for order)
  SET order.guestContact = cart.guestContact
  VALIDATE order.guestContact.name is not null
  VALIDATE order.guestContact.address is complete
  
  // Decrement product stock
  FOR each line in order.lines:
    product = EntityService.findByBusinessId("Product", line.sku, "sku")
    VALIDATE product.quantityAvailable >= line.qty
    
    product.quantityAvailable = product.quantityAvailable - line.qty
    EntityService.update(product.technicalId, product, null) // No transition
  END FOR
  
  // Create shipment
  shipment = {
    shipmentId: generateUUID(),
    orderId: order.orderId,
    lines: [],
    createdAt: current timestamp,
    updatedAt: current timestamp
  }
  
  FOR each orderLine in order.lines:
    shipmentLine = {
      sku: orderLine.sku,
      qtyOrdered: orderLine.qty,
      qtyPicked: 0,
      qtyShipped: 0
    }
    ADD shipmentLine to shipment.lines
  END FOR
  
  EntityService.save(shipment) // Will trigger CREATE_SHIPMENT transition
  
  // Update cart to CONVERTED
  EntityService.update(cart.technicalId, cart, "CHECKOUT")
  
  SET order.createdAt = current timestamp
  SET order.updatedAt = current timestamp
  
  LOG "Order created: " + order.orderNumber
  
  RETURN order entity
END PROCESS
```

#### OrderReadyToSendProcessor

**Entity**: Order  
**Processor Name**: OrderReadyToSendProcessor  
**Transitions**: READY_TO_SEND  
**Description**: Updates shipment when order is ready to send.

**Input Data**: Order entity  
**Expected Output**: Order entity, shipment updated

**Pseudocode**:
```
PROCESS OrderReadyToSendProcessor:
  INPUT: order entity
  
  // Get shipment
  shipment = EntityService.findByBusinessId("Shipment", order.orderId, "orderId")
  
  // Update shipment quantities (mark as picked)
  FOR each line in shipment.lines:
    line.qtyPicked = line.qtyOrdered
  END FOR
  
  SET shipment.updatedAt = current timestamp
  EntityService.update(shipment.technicalId, shipment, "READY_TO_SEND")
  
  SET order.updatedAt = current timestamp
  
  RETURN order entity
END PROCESS
```

#### OrderMarkSentProcessor

**Entity**: Order  
**Processor Name**: OrderMarkSentProcessor  
**Transitions**: MARK_SENT  
**Description**: Updates shipment when order is sent.

**Input Data**: Order entity  
**Expected Output**: Order entity, shipment updated

**Pseudocode**:
```
PROCESS OrderMarkSentProcessor:
  INPUT: order entity
  
  // Get shipment
  shipment = EntityService.findByBusinessId("Shipment", order.orderId, "orderId")
  
  // Update shipment quantities (mark as shipped)
  FOR each line in shipment.lines:
    line.qtyShipped = line.qtyPicked
  END FOR
  
  SET shipment.updatedAt = current timestamp
  EntityService.update(shipment.technicalId, shipment, "MARK_SENT")
  
  SET order.updatedAt = current timestamp
  
  RETURN order entity
END PROCESS
```

#### OrderMarkDeliveredProcessor

**Entity**: Order  
**Processor Name**: OrderMarkDeliveredProcessor  
**Transitions**: MARK_DELIVERED  
**Description**: Updates shipment when order is delivered.

**Input Data**: Order entity  
**Expected Output**: Order entity, shipment updated

**Pseudocode**:
```
PROCESS OrderMarkDeliveredProcessor:
  INPUT: order entity
  
  // Get shipment
  shipment = EntityService.findByBusinessId("Shipment", order.orderId, "orderId")
  
  SET shipment.updatedAt = current timestamp
  EntityService.update(shipment.technicalId, shipment, "MARK_DELIVERED")
  
  SET order.updatedAt = current timestamp
  
  RETURN order entity
END PROCESS
```

---

### 4. Shipment Processors

#### ShipmentReadyToSendProcessor

**Entity**: Shipment  
**Processor Name**: ShipmentReadyToSendProcessor  
**Transitions**: READY_TO_SEND  
**Description**: Updates shipment when ready to send.

**Input Data**: Shipment entity  
**Expected Output**: Shipment entity with updated quantities

**Pseudocode**:
```
PROCESS ShipmentReadyToSendProcessor:
  INPUT: shipment entity
  
  // Validate all items are picked
  FOR each line in shipment.lines:
    VALIDATE line.qtyPicked = line.qtyOrdered
  END FOR
  
  SET shipment.updatedAt = current timestamp
  
  LOG "Shipment ready to send: " + shipment.shipmentId
  
  RETURN shipment entity
END PROCESS
```

#### ShipmentMarkSentProcessor

**Entity**: Shipment  
**Processor Name**: ShipmentMarkSentProcessor  
**Transitions**: MARK_SENT  
**Description**: Updates shipment when sent.

**Input Data**: Shipment entity  
**Expected Output**: Shipment entity with updated quantities

**Pseudocode**:
```
PROCESS ShipmentMarkSentProcessor:
  INPUT: shipment entity
  
  // Mark all picked items as shipped
  FOR each line in shipment.lines:
    line.qtyShipped = line.qtyPicked
  END FOR
  
  SET shipment.updatedAt = current timestamp
  
  LOG "Shipment sent: " + shipment.shipmentId
  
  RETURN shipment entity
END PROCESS
```

#### ShipmentMarkDeliveredProcessor

**Entity**: Shipment  
**Processor Name**: ShipmentMarkDeliveredProcessor  
**Transitions**: MARK_DELIVERED  
**Description**: Marks shipment as delivered.

**Input Data**: Shipment entity  
**Expected Output**: Shipment entity marked as delivered

**Pseudocode**:
```
PROCESS ShipmentMarkDeliveredProcessor:
  INPUT: shipment entity
  
  // Validate all items are shipped
  FOR each line in shipment.lines:
    VALIDATE line.qtyShipped = line.qtyOrdered
  END FOR
  
  SET shipment.updatedAt = current timestamp
  
  LOG "Shipment delivered: " + shipment.shipmentId
  
  RETURN shipment entity
END PROCESS
```

## Processor Implementation Notes

1. **Entity Access**: Use `context.entityResponse().getEntity()` to access current entity
2. **Metadata Access**: Use `context.entityResponse().getMetadata()` for technical ID and state
3. **Other Entity Operations**: Use EntityService for CRUD operations on other entities
4. **State Management**: Cannot update current entity state - system handles transitions
5. **Error Handling**: Validate inputs and throw exceptions for business rule violations
6. **Logging**: Include relevant business identifiers in log messages
7. **Synchronous Execution**: Use SYNC execution mode for demo simplicity
