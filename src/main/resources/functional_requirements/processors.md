# Processors

This document defines the processors for the Cyoda OMS Backend system.

## Cart Processors

### CartRecalculateTotalsProcessor

**Entity**: Cart
**Transitions**: CREATE_ON_FIRST_ADD, ADD_ITEM, DECREMENT_ITEM, REMOVE_ITEM

**Input Data**: 
- Cart entity with updated lines
- Line item data (sku, qty, price, name)

**Process**:
```
1. Validate cart entity exists
2. For each line in cart.lines:
   - Calculate line total = price * qty
3. Calculate totalItems = sum of all line quantities
4. Calculate grandTotal = sum of all line totals
5. Update cart.totalItems = totalItems
6. Update cart.grandTotal = grandTotal
7. Update cart.updatedAt = current timestamp
8. Return updated cart entity
```

**Output**: Updated Cart entity with recalculated totals

---

## Payment Processors

### PaymentCreateDummyProcessor

**Entity**: Payment
**Transitions**: START_DUMMY_PAYMENT

**Input Data**:
- Cart ID
- Payment amount

**Process**:
```
1. Generate unique paymentId
2. Create Payment entity:
   - paymentId = generated ID
   - cartId = input cartId
   - amount = input amount
   - provider = "DUMMY"
   - createdAt = current timestamp
   - updatedAt = current timestamp
3. Save Payment entity
4. Schedule AUTO_MARK_PAID transition after 3 seconds
5. Return Payment entity
```

**Output**: New Payment entity in INITIATED state

### PaymentAutoMarkPaidProcessor

**Entity**: Payment
**Transitions**: AUTO_MARK_PAID

**Input Data**: Payment entity in INITIATED state

**Process**:
```
1. Validate payment is in INITIATED state
2. Update payment.updatedAt = current timestamp
3. Return payment entity (state will be updated to PAID by system)
```

**Output**: Payment entity ready for PAID state

---

## Order Processors

### OrderCreateFromPaidProcessor

**Entity**: Order
**Transitions**: CREATE_ORDER_FROM_PAID

**Input Data**:
- Payment entity (PAID state)
- Cart entity (CONVERTED state)

**Process**:
```
1. Validate payment is PAID and cart is CONVERTED
2. Generate short ULID for orderNumber
3. Generate unique orderId
4. Create Order entity:
   - orderId = generated ID
   - orderNumber = generated ULID
   - lines = snapshot of cart.lines with lineTotal calculations
   - totals.items = cart.totalItems
   - totals.grand = cart.grandTotal
   - guestContact = cart.guestContact
   - createdAt = current timestamp
   - updatedAt = current timestamp
5. For each order line:
   - Find Product by sku
   - Decrement Product.quantityAvailable by ordered qty
   - Update Product entity
6. Create Shipment entity in PICKING state
7. Save Order entity
8. Return Order entity
```

**Output**: New Order entity in WAITING_TO_FULFILL state, updated Product entities, new Shipment entity

### OrderStartPickingProcessor

**Entity**: Order
**Transitions**: START_PICKING

**Input Data**: Order entity

**Process**:
```
1. Find associated Shipment by orderId
2. Update shipment state to PICKING (via transition)
3. Update order.updatedAt = current timestamp
4. Return order entity
```

**Output**: Updated Order entity, Shipment in PICKING state

### OrderReadyToSendProcessor

**Entity**: Order
**Transitions**: READY_TO_SEND

**Input Data**: Order entity

**Process**:
```
1. Find associated Shipment by orderId
2. Update shipment state to WAITING_TO_SEND (via transition)
3. Update order.updatedAt = current timestamp
4. Return order entity
```

**Output**: Updated Order entity, Shipment in WAITING_TO_SEND state

### OrderMarkSentProcessor

**Entity**: Order
**Transitions**: MARK_SENT

**Input Data**: Order entity

**Process**:
```
1. Find associated Shipment by orderId
2. Update shipment state to SENT (via transition)
3. Update order.updatedAt = current timestamp
4. Return order entity
```

**Output**: Updated Order entity, Shipment in SENT state

### OrderMarkDeliveredProcessor

**Entity**: Order
**Transitions**: MARK_DELIVERED

**Input Data**: Order entity

**Process**:
```
1. Find associated Shipment by orderId
2. Update shipment state to DELIVERED (via transition)
3. Update order.updatedAt = current timestamp
4. Return order entity
```

**Output**: Updated Order entity, Shipment in DELIVERED state

---

## Shipment Processors

### ShipmentCreateProcessor

**Entity**: Shipment
**Transitions**: CREATE_SHIPMENT

**Input Data**: Order entity

**Process**:
```
1. Generate unique shipmentId
2. Create Shipment entity:
   - shipmentId = generated ID
   - orderId = order.orderId
   - lines = map order.lines to shipment format:
     - sku = order line sku
     - qtyOrdered = order line qty
     - qtyPicked = 0
     - qtyShipped = 0
   - createdAt = current timestamp
   - updatedAt = current timestamp
3. Save Shipment entity
4. Return Shipment entity
```

**Output**: New Shipment entity in PICKING state

### ShipmentReadyProcessor

**Entity**: Shipment
**Transitions**: READY_FOR_SHIPPING

**Input Data**: Shipment entity

**Process**:
```
1. For each shipment line:
   - Set qtyPicked = qtyOrdered
2. Update shipment.updatedAt = current timestamp
3. Return shipment entity
```

**Output**: Updated Shipment entity ready for WAITING_TO_SEND state

### ShipmentDispatchProcessor

**Entity**: Shipment
**Transitions**: DISPATCH

**Input Data**: Shipment entity

**Process**:
```
1. For each shipment line:
   - Set qtyShipped = qtyPicked
2. Update shipment.updatedAt = current timestamp
3. Return shipment entity
```

**Output**: Updated Shipment entity ready for SENT state

### ShipmentDeliverProcessor

**Entity**: Shipment
**Transitions**: DELIVER

**Input Data**: Shipment entity

**Process**:
```
1. Validate all lines have qtyShipped = qtyOrdered
2. Update shipment.updatedAt = current timestamp
3. Return shipment entity
```

**Output**: Updated Shipment entity ready for DELIVERED state
