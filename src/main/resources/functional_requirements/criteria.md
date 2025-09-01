# Criteria Requirements

## Order Criteria

### OrderCreateCriterion
**Entity**: Order
**Description**: Validates that payment is in PAID state before allowing order creation.
**Input**: Payment entity and Cart entity
**Expected Output**: Boolean (true if order can be created)

**Validation Logic**:
```
evaluate(paymentData, cartData):
    1. Check payment.meta.state == "PAID"
    2. Check cart.meta.state == "CONVERTED" 
    3. Check cart has valid lines (not empty)
    4. Check cart has valid guestContact with required fields
    5. Check cart.grandTotal > 0
    6. Return true if all validations pass, false otherwise
```

**Failure Reasons**:
- "Payment is not in PAID state"
- "Cart is not in CONVERTED state"
- "Cart has no items"
- "Guest contact information is incomplete"
- "Cart total is invalid"

---

## Cart Criteria

### CartCheckoutCriterion
**Entity**: Cart
**Description**: Validates cart is ready for checkout process.
**Input**: Cart entity
**Expected Output**: Boolean (true if cart can proceed to checkout)

**Validation Logic**:
```
evaluate(cartData):
    1. Check cart.meta.state == "ACTIVE"
    2. Check cart.lines is not empty
    3. Check all cart lines have valid SKUs
    4. Check all cart lines have qty > 0
    5. Check cart.totalItems > 0
    6. Check cart.grandTotal > 0
    7. For each line, verify product exists and has sufficient stock
    8. Return true if all validations pass, false otherwise
```

**Failure Reasons**:
- "Cart is not in ACTIVE state"
- "Cart is empty"
- "Invalid SKU in cart line"
- "Invalid quantity in cart line"
- "Cart totals are invalid"
- "Insufficient stock for product {sku}"

---

## Payment Criteria

### PaymentValidCriterion
**Entity**: Payment
**Description**: Validates payment data before processing.
**Input**: Payment entity
**Expected Output**: Boolean (true if payment is valid)

**Validation Logic**:
```
evaluate(paymentData):
    1. Check payment.cartId is not null/empty
    2. Check payment.amount > 0
    3. Check payment.provider == "DUMMY"
    4. Check referenced cart exists
    5. Check referenced cart.grandTotal == payment.amount
    6. Return true if all validations pass, false otherwise
```

**Failure Reasons**:
- "Payment cart reference is missing"
- "Payment amount must be greater than zero"
- "Invalid payment provider"
- "Referenced cart not found"
- "Payment amount does not match cart total"

---

## Product Criteria

### ProductAvailabilityCriterion
**Entity**: Product
**Description**: Validates product availability for cart operations.
**Input**: Product entity and requested quantity
**Expected Output**: Boolean (true if product is available)

**Validation Logic**:
```
evaluate(productData, requestedQty):
    1. Check product exists (not null)
    2. Check product.quantityAvailable >= requestedQty
    3. Check product.quantityAvailable >= 0 (no negative stock)
    4. Return true if all validations pass, false otherwise
```

**Failure Reasons**:
- "Product not found"
- "Insufficient stock available"
- "Product has negative stock"

---

## Shipment Criteria

### ShipmentReadyCriterion
**Entity**: Shipment
**Description**: Validates shipment is ready for state transitions.
**Input**: Shipment entity
**Expected Output**: Boolean (true if shipment can advance)

**Validation Logic**:
```
evaluate(shipmentData):
    1. Check shipment.orderId is not null/empty
    2. Check referenced order exists
    3. Check shipment.lines is not empty
    4. For READY_FOR_DISPATCH transition:
        - Check all lines have qtyPicked == qtyOrdered
    5. For DISPATCH_SHIPMENT transition:
        - Check all lines have qtyPicked > 0
    6. For CONFIRM_DELIVERY transition:
        - Check all lines have qtyShipped > 0
    7. Return true if all validations pass, false otherwise
```

**Failure Reasons**:
- "Shipment order reference is missing"
- "Referenced order not found"
- "Shipment has no lines"
- "Not all items have been picked"
- "No items have been picked"
- "No items have been shipped"

---

## Guest Contact Criteria

### GuestContactValidCriterion
**Entity**: Cart (guestContact field)
**Description**: Validates guest contact information for checkout.
**Input**: GuestContact object
**Expected Output**: Boolean (true if contact info is valid)

**Validation Logic**:
```
evaluate(guestContactData):
    1. Check guestContact is not null
    2. Check guestContact.name is not null/empty
    3. Check guestContact.address is not null
    4. Check guestContact.address.line1 is not null/empty
    5. Check guestContact.address.city is not null/empty
    6. Check guestContact.address.postcode is not null/empty
    7. Check guestContact.address.country is not null/empty
    8. If email provided, validate email format
    9. If phone provided, validate phone format
    10. Return true if all validations pass, false otherwise
```

**Failure Reasons**:
- "Guest contact information is required"
- "Guest name is required"
- "Shipping address is required"
- "Address line 1 is required"
- "City is required"
- "Postal code is required"
- "Country is required"
- "Invalid email format"
- "Invalid phone format"

---

## Stock Validation Criteria

### StockSufficientCriterion
**Entity**: Product
**Description**: Validates sufficient stock for order fulfillment.
**Input**: Product entity and order lines
**Expected Output**: Boolean (true if stock is sufficient)

**Validation Logic**:
```
evaluate(productData, orderLines):
    1. For each order line:
        - Get product by line.sku
        - Check product exists
        - Check product.quantityAvailable >= line.qty
    2. Calculate total required stock per SKU across all lines
    3. Validate no double-counting of stock requirements
    4. Return true if all products have sufficient stock, false otherwise
```

**Failure Reasons**:
- "Product {sku} not found"
- "Insufficient stock for product {sku}: required {qty}, available {available}"
- "Stock validation failed for multiple products"

---

## Business Rule Criteria

### OrderValueCriterion
**Entity**: Order
**Description**: Validates order meets minimum business requirements.
**Input**: Order entity
**Expected Output**: Boolean (true if order is valid)

**Validation Logic**:
```
evaluate(orderData):
    1. Check order.totals.grand > 0
    2. Check order.lines is not empty
    3. Check all lines have lineTotal > 0
    4. Check sum of line totals == order.totals.grand
    5. Check order.totals.items > 0
    6. Check sum of line quantities == order.totals.items
    7. Return true if all validations pass, false otherwise
```

**Failure Reasons**:
- "Order total must be greater than zero"
- "Order must have line items"
- "Invalid line total calculation"
- "Order total calculation mismatch"
- "Order item count is invalid"
- "Item count calculation mismatch"
