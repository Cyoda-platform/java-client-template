# Criteria Requirements

## Overview

This document defines the detailed requirements for all criteria in the Cyoda OMS Backend system. Criteria implement conditional logic for workflow transitions, ensuring business rules are enforced before state changes occur.

## Criteria Definitions

### 1. CartHasItemsCriterion

**Entity**: Cart
**Transition**: OPEN_CHECKOUT
**Purpose**: Validates that cart has items before allowing checkout

**Input Data**: 
- Cart entity with lines array

**Validation Logic**:
- Cart must have at least one line item
- All line items must have quantity > 0
- Cart totalItems must be > 0

**Pseudocode**:
```
CRITERION CartHasItems:
  INPUT: cart entity
  
  IF cart.lines is null OR cart.lines is empty:
    RETURN FAIL with reason "Cart has no items"
  
  SET validItemCount = 0
  FOR EACH line IN cart.lines:
    IF line.qty > 0:
      SET validItemCount = validItemCount + 1
  
  IF validItemCount = 0:
    RETURN FAIL with reason "Cart has no valid items with quantity > 0"
  
  IF cart.totalItems <= 0:
    RETURN FAIL with reason "Cart total items is zero or negative"
  
  RETURN SUCCESS
```

**Failure Actions**: Prevent transition to CHECKING_OUT state
**Success Actions**: Allow transition to CHECKING_OUT state

---

### 2. PaymentIsPaidCriterion

**Entity**: Order (checking Payment state)
**Transition**: CREATE_ORDER_FROM_PAID
**Purpose**: Validates that payment is in PAID state before creating order

**Input Data**: 
- Order entity with payment reference
- Payment entity (via paymentId)

**Validation Logic**:
- Payment must exist
- Payment state must be PAID
- Payment amount must match cart total

**Pseudocode**:
```
CRITERION PaymentIsPaid:
  INPUT: order entity, paymentId
  
  GET payment = findPaymentById(paymentId)
  
  IF payment is null:
    RETURN FAIL with reason "Payment not found: " + paymentId
  
  IF payment.meta.state != "PAID":
    RETURN FAIL with reason "Payment is not in PAID state: " + payment.meta.state
  
  GET cart = findCartById(payment.cartId)
  IF cart is null:
    RETURN FAIL with reason "Cart not found for payment: " + payment.cartId
  
  IF payment.amount != cart.grandTotal:
    RETURN FAIL with reason "Payment amount does not match cart total"
  
  RETURN SUCCESS
```

**Failure Actions**: Prevent order creation
**Success Actions**: Allow order creation to proceed

---

### 3. ProductStockAvailableCriterion

**Entity**: Cart (checking Product stock)
**Transition**: ADD_ITEM, UPDATE_ITEM
**Purpose**: Validates that sufficient product stock is available

**Input Data**: 
- Cart entity with line items
- Product entities (via SKU references)

**Validation Logic**:
- Each product must exist
- Each product must have sufficient quantityAvailable
- Total requested quantity must not exceed available stock

**Pseudocode**:
```
CRITERION ProductStockAvailable:
  INPUT: cart entity
  
  FOR EACH line IN cart.lines:
    GET product = findProductBySku(line.sku)
    
    IF product is null:
      RETURN FAIL with reason "Product not found: " + line.sku
    
    IF product.quantityAvailable < line.qty:
      RETURN FAIL with reason "Insufficient stock for " + line.sku + 
                             ". Available: " + product.quantityAvailable + 
                             ", Requested: " + line.qty
  
  RETURN SUCCESS
```

**Failure Actions**: Prevent cart item addition/update
**Success Actions**: Allow cart modification

---

### 4. CartNotEmptyForCheckoutCriterion

**Entity**: Cart
**Transition**: CHECKOUT
**Purpose**: Final validation before converting cart to order

**Input Data**: 
- Cart entity in CHECKING_OUT state

**Validation Logic**:
- Cart must be in CHECKING_OUT state
- Cart must have guest contact information
- Cart must have valid address for shipping

**Pseudocode**:
```
CRITERION CartNotEmptyForCheckout:
  INPUT: cart entity
  
  IF cart.meta.state != "CHECKING_OUT":
    RETURN FAIL with reason "Cart is not in checkout state"
  
  IF cart.guestContact is null:
    RETURN FAIL with reason "Guest contact information is required"
  
  IF cart.guestContact.name is null OR cart.guestContact.name is empty:
    RETURN FAIL with reason "Guest name is required"
  
  IF cart.guestContact.address is null:
    RETURN FAIL with reason "Guest address is required"
  
  SET address = cart.guestContact.address
  IF address.line1 is null OR address.line1 is empty:
    RETURN FAIL with reason "Address line 1 is required"
  
  IF address.city is null OR address.city is empty:
    RETURN FAIL with reason "City is required"
  
  IF address.postcode is null OR address.postcode is empty:
    RETURN FAIL with reason "Postcode is required"
  
  IF address.country is null OR address.country is empty:
    RETURN FAIL with reason "Country is required"
  
  RETURN SUCCESS
```

**Failure Actions**: Prevent cart conversion to order
**Success Actions**: Allow cart to be converted to order

---

### 5. PaymentNotExpiredCriterion

**Entity**: Payment
**Transition**: AUTO_MARK_PAID
**Purpose**: Validates that payment processing hasn't timed out

**Input Data**: 
- Payment entity with creation timestamp

**Validation Logic**:
- Payment must be created within last 10 minutes
- Payment must still be in INITIATED state

**Pseudocode**:
```
CRITERION PaymentNotExpired:
  INPUT: payment entity
  
  SET currentTime = current timestamp
  SET paymentAge = currentTime - payment.createdAt
  SET maxAgeMinutes = 10
  
  IF paymentAge > maxAgeMinutes minutes:
    RETURN FAIL with reason "Payment has expired. Created: " + payment.createdAt
  
  IF payment.meta.state != "INITIATED":
    RETURN FAIL with reason "Payment is not in INITIATED state: " + payment.meta.state
  
  RETURN SUCCESS
```

**Failure Actions**: Prevent auto-payment approval
**Success Actions**: Allow payment to be marked as paid

---

### 6. OrderHasValidShipmentCriterion

**Entity**: Order
**Transition**: START_PICKING
**Purpose**: Validates that order has a valid shipment before starting picking

**Input Data**: 
- Order entity
- Shipment entity (via orderId)

**Validation Logic**:
- Shipment must exist for the order
- Shipment must be in PICKING state
- Shipment lines must match order lines

**Pseudocode**:
```
CRITERION OrderHasValidShipment:
  INPUT: order entity
  
  GET shipment = findShipmentByOrderId(order.orderId)
  
  IF shipment is null:
    RETURN FAIL with reason "No shipment found for order: " + order.orderId
  
  IF shipment.meta.state != "PICKING":
    RETURN FAIL with reason "Shipment is not in PICKING state: " + shipment.meta.state
  
  IF shipment.lines.size != order.lines.size:
    RETURN FAIL with reason "Shipment line count does not match order line count"
  
  FOR EACH orderLine IN order.lines:
    SET found = false
    FOR EACH shipmentLine IN shipment.lines:
      IF shipmentLine.sku = orderLine.sku AND 
         shipmentLine.qtyOrdered = orderLine.qty:
        SET found = true
        BREAK
    
    IF NOT found:
      RETURN FAIL with reason "Shipment missing line for SKU: " + orderLine.sku
  
  RETURN SUCCESS
```

**Failure Actions**: Prevent picking process from starting
**Success Actions**: Allow picking to begin

## Criteria Summary

| Criterion | Entity | Transition | Purpose |
|-----------|--------|------------|---------|
| CartHasItemsCriterion | Cart | OPEN_CHECKOUT | Validate cart has items |
| PaymentIsPaidCriterion | Order | CREATE_ORDER_FROM_PAID | Validate payment is paid |
| ProductStockAvailableCriterion | Cart | ADD_ITEM, UPDATE_ITEM | Validate stock availability |
| CartNotEmptyForCheckoutCriterion | Cart | CHECKOUT | Validate checkout requirements |
| PaymentNotExpiredCriterion | Payment | AUTO_MARK_PAID | Validate payment not expired |
| OrderHasValidShipmentCriterion | Order | START_PICKING | Validate shipment exists |

## Business Rules

1. **Stock Validation**: Always check product availability before cart modifications
2. **Payment Validation**: Ensure payment is completed before order creation
3. **Contact Validation**: Require complete guest information for checkout
4. **Timeout Handling**: Prevent processing of expired payments
5. **Data Consistency**: Validate entity relationships before state transitions
6. **User Experience**: Provide clear error messages for validation failures

## Error Handling

All criteria should return descriptive error messages that can be displayed to users or logged for debugging. Criteria failures should prevent workflow transitions and maintain data integrity.
