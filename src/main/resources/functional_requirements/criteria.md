# Criteria

This document defines the criteria for the Cyoda OMS Backend system.

## Payment Criteria

### PaymentPaidCriterion

**Entity**: Payment
**Used in**: OrderLifecycle.CREATE_ORDER_FROM_PAID transition

**Purpose**: Validates that a payment is in PAID state before allowing order creation.

**Input Data**: Payment entity

**Evaluation Logic**:
```
1. Check if payment entity exists
   - If null: return FAIL with "Payment entity not found"
2. Check if payment.meta.state equals "PAID"
   - If not PAID: return FAIL with "Payment must be in PAID state"
3. Check if payment.amount > 0
   - If not: return FAIL with "Payment amount must be greater than zero"
4. Check if payment.provider equals "DUMMY"
   - If not: return FAIL with "Invalid payment provider"
5. Return SUCCESS
```

**Expected Outcome**: 
- SUCCESS: Payment is valid and paid
- FAIL: Payment is invalid, not paid, or has invalid data

---

## Cart Criteria

### CartValidForCheckoutCriterion

**Entity**: Cart
**Used in**: CartFlow.OPEN_CHECKOUT transition (optional validation)

**Purpose**: Validates that a cart is ready for checkout process.

**Input Data**: Cart entity

**Evaluation Logic**:
```
1. Check if cart entity exists
   - If null: return FAIL with "Cart entity not found"
2. Check if cart.lines is not empty
   - If empty: return FAIL with "Cart must have at least one item"
3. Check if cart.totalItems > 0
   - If not: return FAIL with "Cart must have items"
4. Check if cart.grandTotal > 0
   - If not: return FAIL with "Cart total must be greater than zero"
5. For each line in cart.lines:
   - Check if line.qty > 0: return FAIL with "Invalid line quantity"
   - Check if line.price > 0: return FAIL with "Invalid line price"
6. Validate totals are correctly calculated:
   - Calculate expected totalItems = sum of line quantities
   - Calculate expected grandTotal = sum of line totals
   - If cart.totalItems != expected: return FAIL with "Invalid total items"
   - If cart.grandTotal != expected: return FAIL with "Invalid grand total"
7. Return SUCCESS
```

**Expected Outcome**:
- SUCCESS: Cart is valid for checkout
- FAIL: Cart has invalid data or empty lines

---

## Order Criteria

### OrderReadyForPickingCriterion

**Entity**: Order
**Used in**: OrderLifecycle.START_PICKING transition (optional validation)

**Purpose**: Validates that an order is ready to start the picking process.

**Input Data**: Order entity

**Evaluation Logic**:
```
1. Check if order entity exists
   - If null: return FAIL with "Order entity not found"
2. Check if order.meta.state equals "WAITING_TO_FULFILL"
   - If not: return FAIL with "Order must be in WAITING_TO_FULFILL state"
3. Check if order.lines is not empty
   - If empty: return FAIL with "Order must have line items"
4. Check if order.guestContact.address is complete:
   - Validate line1, city, postcode, country are not null/empty
   - If incomplete: return FAIL with "Incomplete shipping address"
5. For each order line:
   - Find Product by sku
   - Check if Product.quantityAvailable >= 0
   - If negative: return FAIL with "Insufficient stock for product: {sku}"
6. Return SUCCESS
```

**Expected Outcome**:
- SUCCESS: Order is ready for picking
- FAIL: Order has invalid state, missing data, or stock issues

---

## Shipment Criteria

### ShipmentReadyForDispatchCriterion

**Entity**: Shipment
**Used in**: ShipmentFlow.DISPATCH transition (optional validation)

**Purpose**: Validates that a shipment is ready for dispatch.

**Input Data**: Shipment entity

**Evaluation Logic**:
```
1. Check if shipment entity exists
   - If null: return FAIL with "Shipment entity not found"
2. Check if shipment.meta.state equals "WAITING_TO_SEND"
   - If not: return FAIL with "Shipment must be in WAITING_TO_SEND state"
3. Check if shipment.lines is not empty
   - If empty: return FAIL with "Shipment must have line items"
4. For each shipment line:
   - Check if line.qtyPicked > 0
   - If not: return FAIL with "All items must be picked before dispatch"
   - Check if line.qtyPicked <= line.qtyOrdered
   - If not: return FAIL with "Picked quantity cannot exceed ordered quantity"
5. Return SUCCESS
```

**Expected Outcome**:
- SUCCESS: Shipment is ready for dispatch
- FAIL: Shipment has invalid state or unpicked items

### ShipmentReadyForDeliveryCriterion

**Entity**: Shipment
**Used in**: ShipmentFlow.DELIVER transition (optional validation)

**Purpose**: Validates that a shipment is ready to be marked as delivered.

**Input Data**: Shipment entity

**Evaluation Logic**:
```
1. Check if shipment entity exists
   - If null: return FAIL with "Shipment entity not found"
2. Check if shipment.meta.state equals "SENT"
   - If not: return FAIL with "Shipment must be in SENT state"
3. Check if shipment.lines is not empty
   - If empty: return FAIL with "Shipment must have line items"
4. For each shipment line:
   - Check if line.qtyShipped > 0
   - If not: return FAIL with "All items must be shipped before delivery"
   - Check if line.qtyShipped = line.qtyOrdered
   - If not: return FAIL with "Shipped quantity must equal ordered quantity"
5. Return SUCCESS
```

**Expected Outcome**:
- SUCCESS: Shipment is ready to be delivered
- FAIL: Shipment has invalid state or unshipped items

---

## Product Criteria

### ProductAvailableCriterion

**Entity**: Product
**Used in**: General validation for cart operations

**Purpose**: Validates that a product is available for purchase.

**Input Data**: Product entity, requested quantity

**Evaluation Logic**:
```
1. Check if product entity exists
   - If null: return FAIL with "Product not found"
2. Check if product.quantityAvailable >= requested quantity
   - If not: return FAIL with "Insufficient stock available"
3. Check if product.price > 0
   - If not: return FAIL with "Invalid product price"
4. Check if product.category is not null/empty
   - If empty: return FAIL with "Product must have a category"
5. Return SUCCESS
```

**Expected Outcome**:
- SUCCESS: Product is available for the requested quantity
- FAIL: Product is unavailable, out of stock, or has invalid data
