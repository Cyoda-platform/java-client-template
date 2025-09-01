# Processor Requirements

## Cart Processors

### CartCreateProcessor
**Entity**: Cart
**Input**: Cart entity with first item details
**Description**: Creates new cart and adds first item with total calculation.

**Pseudocode**:
```
process(cartData):
    1. Generate unique cartId
    2. Initialize cart with NEW state
    3. Add first item to cart.lines
    4. Calculate totalItems = sum of all line quantities
    5. Calculate grandTotal = sum of all line totals (price * qty)
    6. Set createdAt and updatedAt timestamps
    7. Save cart entity
    8. Return updated cart
```

### CartAddItemProcessor
**Entity**: Cart
**Input**: Cart entity with item to add (sku, qty)
**Description**: Adds item to cart or increments existing item quantity.

**Pseudocode**:
```
process(cartData):
    1. Find existing line with matching SKU
    2. If line exists:
        - Increment quantity by input qty
    3. If line doesn't exist:
        - Get product details by SKU
        - Create new line with sku, name, price, qty
        - Add line to cart.lines
    4. Recalculate totalItems and grandTotal
    5. Update updatedAt timestamp
    6. Save cart entity
    7. Return updated cart
```

### CartUpdateItemProcessor
**Entity**: Cart
**Input**: Cart entity with item update (sku, qty)
**Description**: Updates item quantity or removes item if quantity is 0.

**Pseudocode**:
```
process(cartData):
    1. Find line with matching SKU
    2. If qty = 0:
        - Remove line from cart.lines
    3. If qty > 0:
        - Update line.qty to new quantity
    4. Recalculate totalItems and grandTotal
    5. Update updatedAt timestamp
    6. Save cart entity
    7. Return updated cart
```

### CartOpenCheckoutProcessor
**Entity**: Cart
**Input**: Cart entity
**Description**: Validates cart and moves to checkout state.

**Pseudocode**:
```
process(cartData):
    1. Validate cart has items (lines.length > 0)
    2. Validate all items have valid SKUs and quantities
    3. Check product availability for all items
    4. Update updatedAt timestamp
    5. Save cart entity
    6. Return cart (state change handled by workflow)
```

### CartCheckoutProcessor
**Entity**: Cart
**Input**: Cart entity with guestContact
**Description**: Finalizes cart for conversion to order.

**Pseudocode**:
```
process(cartData):
    1. Validate guestContact is complete
    2. Validate required address fields
    3. Final validation of cart totals
    4. Update updatedAt timestamp
    5. Save cart entity
    6. Trigger order creation orchestration
    7. Return finalized cart
```

---

## Payment Processors

### PaymentCreateProcessor
**Entity**: Payment
**Input**: Payment entity with cartId and amount
**Description**: Creates payment record in INITIATED state.

**Pseudocode**:
```
process(paymentData):
    1. Generate unique paymentId
    2. Set provider = "DUMMY"
    3. Set amount from cart grandTotal
    4. Set cartId reference
    5. Set createdAt and updatedAt timestamps
    6. Save payment entity
    7. Schedule auto-approval after 3 seconds
    8. Return payment
```

### PaymentAutoApproveProcessor
**Entity**: Payment
**Input**: Payment entity in INITIATED state
**Description**: Automatically approves payment after 3-second delay.

**Pseudocode**:
```
process(paymentData):
    1. Wait for 3 seconds (simulated processing)
    2. Update updatedAt timestamp
    3. Save payment entity
    4. Trigger order creation if cart is ready
    5. Return approved payment
```

### PaymentFailProcessor
**Entity**: Payment
**Input**: Payment entity
**Description**: Marks payment as failed for testing purposes.

**Pseudocode**:
```
process(paymentData):
    1. Update updatedAt timestamp
    2. Save payment entity
    3. Return failed payment
```

### PaymentCancelProcessor
**Entity**: Payment
**Input**: Payment entity
**Description**: Cancels payment for testing purposes.

**Pseudocode**:
```
process(paymentData):
    1. Update updatedAt timestamp
    2. Save payment entity
    3. Return canceled payment
```

---

## Order Processors

### OrderCreateProcessor
**Entity**: Order
**Input**: Cart and Payment entities
**Description**: Creates order from paid cart, decrements stock, creates shipment.

**Pseudocode**:
```
process(orderData):
    1. Generate unique orderId and short ULID orderNumber
    2. Snapshot cart.lines to order.lines with lineTotal calculations
    3. Copy cart.guestContact to order.guestContact
    4. Calculate order.totals from lines
    5. For each order line:
        - Get product by SKU
        - Decrement product.quantityAvailable by line.qty
        - Save updated product
    6. Set createdAt and updatedAt timestamps
    7. Save order entity
    8. Create shipment with order reference
    9. Return created order
```

### OrderStartPickingProcessor
**Entity**: Order
**Input**: Order entity
**Description**: Begins picking process for order fulfillment.

**Pseudocode**:
```
process(orderData):
    1. Validate order is in WAITING_TO_FULFILL state
    2. Update associated shipment to PICKING state
    3. Update updatedAt timestamp
    4. Save order entity
    5. Return order
```

### OrderReadyToSendProcessor
**Entity**: Order
**Input**: Order entity
**Description**: Marks order as picked and ready for shipment.

**Pseudocode**:
```
process(orderData):
    1. Validate order is in PICKING state
    2. Update associated shipment to WAITING_TO_SEND state
    3. Update shipment.lines with picked quantities
    4. Update updatedAt timestamp
    5. Save order entity
    6. Return order
```

### OrderMarkSentProcessor
**Entity**: Order
**Input**: Order entity
**Description**: Marks order as shipped with tracking information.

**Pseudocode**:
```
process(orderData):
    1. Validate order is in WAITING_TO_SEND state
    2. Update associated shipment to SENT state
    3. Update shipment.lines with shipped quantities
    4. Update updatedAt timestamp
    5. Save order entity
    6. Return order
```

### OrderMarkDeliveredProcessor
**Entity**: Order
**Input**: Order entity
**Description**: Marks order as delivered, completing fulfillment.

**Pseudocode**:
```
process(orderData):
    1. Validate order is in SENT state
    2. Update associated shipment to DELIVERED state
    3. Update updatedAt timestamp
    4. Save order entity
    5. Return completed order
```

---

## Shipment Processors

### ShipmentCreateProcessor
**Entity**: Shipment
**Input**: Order entity reference
**Description**: Creates shipment when order is created.

**Pseudocode**:
```
process(shipmentData):
    1. Generate unique shipmentId
    2. Set orderId reference
    3. Create shipment.lines from order.lines:
        - Set qtyOrdered from order line qty
        - Set qtyPicked = 0
        - Set qtyShipped = 0
    4. Set createdAt and updatedAt timestamps
    5. Save shipment entity
    6. Return created shipment
```

### ShipmentReadyProcessor
**Entity**: Shipment
**Input**: Shipment entity
**Description**: Marks shipment as ready for dispatch.

**Pseudocode**:
```
process(shipmentData):
    1. Validate shipment is in PICKING state
    2. Update shipment.lines qtyPicked = qtyOrdered
    3. Update updatedAt timestamp
    4. Save shipment entity
    5. Update associated order state
    6. Return shipment
```

### ShipmentDispatchProcessor
**Entity**: Shipment
**Input**: Shipment entity
**Description**: Dispatches shipment with tracking updates.

**Pseudocode**:
```
process(shipmentData):
    1. Validate shipment is in WAITING_TO_SEND state
    2. Update shipment.lines qtyShipped = qtyPicked
    3. Update updatedAt timestamp
    4. Save shipment entity
    5. Update associated order state
    6. Return dispatched shipment
```

### ShipmentDeliveryProcessor
**Entity**: Shipment
**Input**: Shipment entity
**Description**: Confirms delivery, completing shipment.

**Pseudocode**:
```
process(shipmentData):
    1. Validate shipment is in SENT state
    2. Update updatedAt timestamp
    3. Save shipment entity
    4. Update associated order state to DELIVERED
    5. Return delivered shipment
```
