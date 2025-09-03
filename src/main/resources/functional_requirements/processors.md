# Processor Requirements

## Overview
This document defines the processors for the Cyoda OMS Backend system. Processors implement business logic for workflow transitions.

## Processors

### 1. CartRecalculateTotalsProcessor
**Entity:** Cart
**Transition:** `create_on_first_add`, `add_item`, `update_item`
**Description:** Recalculates cart totals when items are added, updated, or removed.

**Input Data:**
- Cart entity with updated lines
- Line items with SKU, name, price, quantity

**Process Logic:**
```
1. Validate cart entity exists
2. Initialize totals: totalItems = 0, grandTotal = 0
3. For each line in cart.lines:
   a. Validate line has required fields (sku, name, price, qty)
   b. Calculate lineTotal = price * qty
   c. Add qty to totalItems
   d. Add lineTotal to grandTotal
4. Update cart.totalItems = totalItems
5. Update cart.grandTotal = grandTotal
6. Update cart.updatedAt = current timestamp
7. Return updated cart entity
```

**Expected Output:**
- Updated Cart entity with recalculated totals
- No state transition (stays in current state)

### 2. PaymentAutoMarkPaidProcessor
**Entity:** Payment
**Transition:** `auto_mark_paid`
**Description:** Automatically marks payment as paid after 3-second delay (dummy implementation).

**Input Data:**
- Payment entity in INITIATED state
- Payment amount and cart reference

**Process Logic:**
```
1. Validate payment entity exists and is in INITIATED state
2. Wait for 3 seconds (simulate payment processing)
3. Update payment.updatedAt = current timestamp
4. Log payment approval
5. Return payment entity (state will be updated to PAID by workflow)
```

**Expected Output:**
- Payment entity ready for PAID state
- State transition to PAID

### 3. OrderCreateFromPaidProcessor
**Entity:** Order
**Transition:** `create_order_from_paid`
**Description:** Creates order from paid cart, decrements product stock, and creates shipment.

**Input Data:**
- Order entity with basic information
- Associated cart ID and payment ID
- Cart entity data for snapshotting

**Process Logic:**
```
1. Validate order entity exists
2. Retrieve cart entity by cartId
3. Validate cart is in CONVERTED state
4. Retrieve payment entity and validate it's PAID
5. Generate short ULID for orderNumber
6. Snapshot cart data to order:
   a. Copy cart.lines to order.lines (transform format)
   b. Copy cart.totals to order.totals
   c. Copy cart.guestContact to order.guestContact
7. For each line in order.lines:
   a. Retrieve product by SKU
   b. Validate product.quantityAvailable >= line.qty
   c. Decrement product.quantityAvailable by line.qty
   d. Update product entity
8. Create shipment entity:
   a. Generate shipmentId
   b. Set orderId reference
   c. Copy order lines to shipment lines
   d. Set initial state to PICKING
9. Update order.updatedAt = current timestamp
10. Return updated order entity
```

**Expected Output:**
- Updated Order entity with snapshotted data
- Updated Product entities with decremented stock
- New Shipment entity created
- State transition to PICKING

### 4. OrderReadyToSendProcessor
**Entity:** Order
**Transition:** `ready_to_send`
**Description:** Marks order ready for shipping and updates shipment.

**Input Data:**
- Order entity in PICKING state
- Associated shipment entity

**Process Logic:**
```
1. Validate order entity exists and is in PICKING state
2. Retrieve associated shipment by orderId
3. Validate shipment is in PICKING state
4. Update shipment quantities:
   a. Set qtyPicked = qtyOrdered for all lines
   b. Update shipment.updatedAt = current timestamp
5. Trigger shipment transition to WAITING_TO_SEND
6. Update order.updatedAt = current timestamp
7. Return updated order entity
```

**Expected Output:**
- Updated Order entity
- Updated Shipment entity with picked quantities
- Order state transition to WAITING_TO_SEND
- Shipment transition: `ready_to_send`

### 5. OrderMarkSentProcessor
**Entity:** Order
**Transition:** `mark_sent`
**Description:** Marks order as sent and updates shipment.

**Input Data:**
- Order entity in WAITING_TO_SEND state
- Associated shipment entity

**Process Logic:**
```
1. Validate order entity exists and is in WAITING_TO_SEND state
2. Retrieve associated shipment by orderId
3. Validate shipment is in WAITING_TO_SEND state
4. Update shipment quantities:
   a. Set qtyShipped = qtyPicked for all lines
   b. Update shipment.updatedAt = current timestamp
5. Trigger shipment transition to SENT
6. Update order.updatedAt = current timestamp
7. Return updated order entity
```

**Expected Output:**
- Updated Order entity
- Updated Shipment entity with shipped quantities
- Order state transition to SENT
- Shipment transition: `mark_sent`

### 6. OrderMarkDeliveredProcessor
**Entity:** Order
**Transition:** `mark_delivered`
**Description:** Marks order as delivered and completes shipment.

**Input Data:**
- Order entity in SENT state
- Associated shipment entity

**Process Logic:**
```
1. Validate order entity exists and is in SENT state
2. Retrieve associated shipment by orderId
3. Validate shipment is in SENT state
4. Update shipment.updatedAt = current timestamp
5. Trigger shipment transition to DELIVERED
6. Update order.updatedAt = current timestamp
7. Log order completion
8. Return updated order entity
```

**Expected Output:**
- Updated Order entity
- Updated Shipment entity
- Order state transition to DELIVERED
- Shipment transition: `mark_delivered`

### 7. ShipmentReadyToSendProcessor
**Entity:** Shipment
**Transition:** `ready_to_send`
**Description:** Marks shipment ready for sending.

**Input Data:**
- Shipment entity in PICKING state

**Process Logic:**
```
1. Validate shipment entity exists and is in PICKING state
2. Update shipment quantities:
   a. Set qtyPicked = qtyOrdered for all lines
3. Update shipment.updatedAt = current timestamp
4. Return updated shipment entity
```

**Expected Output:**
- Updated Shipment entity with picked quantities
- State transition to WAITING_TO_SEND

### 8. ShipmentMarkSentProcessor
**Entity:** Shipment
**Transition:** `mark_sent`
**Description:** Marks shipment as sent.

**Input Data:**
- Shipment entity in WAITING_TO_SEND state

**Process Logic:**
```
1. Validate shipment entity exists and is in WAITING_TO_SEND state
2. Update shipment quantities:
   a. Set qtyShipped = qtyPicked for all lines
3. Update shipment.updatedAt = current timestamp
4. Return updated shipment entity
```

**Expected Output:**
- Updated Shipment entity with shipped quantities
- State transition to SENT

### 9. ShipmentMarkDeliveredProcessor
**Entity:** Shipment
**Transition:** `mark_delivered`
**Description:** Marks shipment as delivered.

**Input Data:**
- Shipment entity in SENT state

**Process Logic:**
```
1. Validate shipment entity exists and is in SENT state
2. Update shipment.updatedAt = current timestamp
3. Log shipment completion
4. Return updated shipment entity
```

**Expected Output:**
- Updated Shipment entity
- State transition to DELIVERED

## Processor Naming Convention
All processors follow PascalCase naming starting with the entity name:
- Cart processors: `Cart*Processor`
- Payment processors: `Payment*Processor`
- Order processors: `Order*Processor`
- Shipment processors: `Shipment*Processor`

## Error Handling
All processors should:
1. Validate input entity exists and is in expected state
2. Validate required relationships exist
3. Handle business rule violations gracefully
4. Log processing steps for debugging
5. Return appropriate error responses for failures
