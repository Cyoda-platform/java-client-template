# Criteria Requirements

## Overview
This document defines the detailed requirements for all criteria in the Cyoda OMS Backend system. Criteria implement conditional logic for workflow transitions and business rule validation.

## Product Criteria

### ProductValidCriterion
**Entity:** Product  
**Purpose:** Validates product data integrity and business rules  
**Usage:** Can be used in product workflow transitions to ensure data quality  

**Validation Rules:**
- SKU is not null and not empty
- Name is not null and not empty
- Price is greater than 0
- Category is not null and not empty
- QuantityAvailable is not negative

**Pseudocode:**
```
check(product):
    if product is null:
        return fail("Product entity is null")
    
    if product.sku is null or empty:
        return fail("Product SKU is required")
    
    if product.name is null or empty:
        return fail("Product name is required")
    
    if product.price <= 0:
        return fail("Product price must be greater than 0")
    
    if product.category is null or empty:
        return fail("Product category is required")
    
    if product.quantityAvailable < 0:
        return fail("Product quantity cannot be negative")
    
    return success()
```

### ProductAvailableCriterion
**Entity:** Product  
**Purpose:** Checks if product is available for purchase  
**Usage:** Used in cart operations to validate product availability  

**Validation Rules:**
- Product state is ACTIVE
- QuantityAvailable is greater than 0

**Pseudocode:**
```
check(product):
    if product is null:
        return fail("Product not found")
    
    if product.meta.state != "ACTIVE":
        return fail("Product is not active")
    
    if product.quantityAvailable <= 0:
        return fail("Product is out of stock")
    
    return success()
```

## Cart Criteria

### CartValidCriterion
**Entity:** Cart  
**Purpose:** Validates cart data integrity and business rules  
**Usage:** Used in cart workflow transitions to ensure data quality  

**Validation Rules:**
- Cart has at least one line item
- All line items have valid SKU and positive quantity
- Total calculations are correct

**Pseudocode:**
```
check(cart):
    if cart is null:
        return fail("Cart entity is null")
    
    if cart.lines is null or empty:
        return fail("Cart must have at least one item")
    
    calculatedTotal = 0
    calculatedItems = 0
    
    for each line in cart.lines:
        if line.sku is null or empty:
            return fail("Cart line must have valid SKU")
        
        if line.qty <= 0:
            return fail("Cart line quantity must be positive")
        
        if line.price < 0:
            return fail("Cart line price cannot be negative")
        
        calculatedTotal += line.price * line.qty
        calculatedItems += line.qty
    
    if cart.grandTotal != calculatedTotal:
        return fail("Cart grand total is incorrect")
    
    if cart.totalItems != calculatedItems:
        return fail("Cart total items count is incorrect")
    
    return success()
```

### CartReadyForCheckoutCriterion
**Entity:** Cart  
**Purpose:** Validates cart is ready for checkout process  
**Usage:** Used before transitioning cart to CHECKING_OUT state  

**Validation Rules:**
- Cart is in ACTIVE state
- Cart has valid items
- All products in cart are still available

**Pseudocode:**
```
check(cart):
    if cart is null:
        return fail("Cart not found")
    
    if cart.meta.state != "ACTIVE":
        return fail("Cart is not in active state")
    
    if cart.lines is null or empty:
        return fail("Cart is empty")
    
    for each line in cart.lines:
        product = getProduct(line.sku)
        if product is null:
            return fail("Product " + line.sku + " not found")
        
        if product.meta.state != "ACTIVE":
            return fail("Product " + line.sku + " is no longer available")
        
        if product.quantityAvailable < line.qty:
            return fail("Insufficient stock for product " + line.sku)
    
    return success()
```

## Payment Criteria

### PaymentValidCriterion
**Entity:** Payment  
**Purpose:** Validates payment data integrity  
**Usage:** Used in payment workflow transitions  

**Validation Rules:**
- Payment has valid cartId
- Amount is positive
- Provider is set to "DUMMY"

**Pseudocode:**
```
check(payment):
    if payment is null:
        return fail("Payment entity is null")
    
    if payment.cartId is null or empty:
        return fail("Payment must have valid cart ID")
    
    if payment.amount <= 0:
        return fail("Payment amount must be positive")
    
    if payment.provider != "DUMMY":
        return fail("Invalid payment provider")
    
    return success()
```

### PaymentReadyForApprovalCriterion
**Entity:** Payment  
**Purpose:** Checks if payment can be automatically approved  
**Usage:** Used before auto-approval transition  

**Validation Rules:**
- Payment is in INITIATED state
- At least 3 seconds have passed since creation
- Associated cart exists and is valid

**Pseudocode:**
```
check(payment):
    if payment is null:
        return fail("Payment not found")
    
    if payment.meta.state != "INITIATED":
        return fail("Payment is not in initiated state")
    
    currentTime = getCurrentTime()
    timeDiff = currentTime - payment.createdAt
    if timeDiff < 3000: // 3 seconds in milliseconds
        return fail("Payment approval time not reached")
    
    cart = getCart(payment.cartId)
    if cart is null:
        return fail("Associated cart not found")
    
    if cart.meta.state != "CHECKING_OUT":
        return fail("Associated cart is not in checkout state")
    
    return success()
```

## Order Criteria

### OrderValidCriterion
**Entity:** Order  
**Purpose:** Validates order data integrity  
**Usage:** Used in order workflow transitions  

**Validation Rules:**
- Order has valid guest contact information
- Order lines are valid
- Totals are calculated correctly

**Pseudocode:**
```
check(order):
    if order is null:
        return fail("Order entity is null")
    
    if order.guestContact is null:
        return fail("Order must have guest contact")
    
    if order.guestContact.name is null or empty:
        return fail("Guest name is required")
    
    if order.guestContact.address is null:
        return fail("Guest address is required")
    
    if order.guestContact.address.line1 is null or empty:
        return fail("Address line 1 is required")
    
    if order.guestContact.address.city is null or empty:
        return fail("City is required")
    
    if order.guestContact.address.postcode is null or empty:
        return fail("Postcode is required")
    
    if order.guestContact.address.country is null or empty:
        return fail("Country is required")
    
    if order.lines is null or empty:
        return fail("Order must have line items")
    
    calculatedTotal = 0
    calculatedItems = 0
    
    for each line in order.lines:
        if line.sku is null or empty:
            return fail("Order line must have valid SKU")
        
        if line.qty <= 0:
            return fail("Order line quantity must be positive")
        
        if line.unitPrice < 0:
            return fail("Order line unit price cannot be negative")
        
        expectedLineTotal = line.unitPrice * line.qty
        if line.lineTotal != expectedLineTotal:
            return fail("Order line total is incorrect")
        
        calculatedTotal += line.lineTotal
        calculatedItems += line.qty
    
    if order.totals.grand != calculatedTotal:
        return fail("Order grand total is incorrect")
    
    if order.totals.items != calculatedItems:
        return fail("Order total items count is incorrect")
    
    return success()
```

## Shipment Criteria

### ShipmentValidCriterion
**Entity:** Shipment  
**Purpose:** Validates shipment data integrity  
**Usage:** Used in shipment workflow transitions  

**Validation Rules:**
- Shipment has valid orderId
- Shipment lines are valid
- Quantities are logical (picked <= ordered, shipped <= picked)

**Pseudocode:**
```
check(shipment):
    if shipment is null:
        return fail("Shipment entity is null")
    
    if shipment.orderId is null or empty:
        return fail("Shipment must have valid order ID")
    
    if shipment.lines is null or empty:
        return fail("Shipment must have line items")
    
    for each line in shipment.lines:
        if line.sku is null or empty:
            return fail("Shipment line must have valid SKU")
        
        if line.qtyOrdered <= 0:
            return fail("Shipment line ordered quantity must be positive")
        
        if line.qtyPicked < 0:
            return fail("Shipment line picked quantity cannot be negative")
        
        if line.qtyShipped < 0:
            return fail("Shipment line shipped quantity cannot be negative")
        
        if line.qtyPicked > line.qtyOrdered:
            return fail("Picked quantity cannot exceed ordered quantity")
        
        if line.qtyShipped > line.qtyPicked:
            return fail("Shipped quantity cannot exceed picked quantity")
    
    return success()
```

## Criteria Implementation Notes

### Return Values
- Use `success()` for passing criteria
- Use `fail(reason)` for failing criteria with descriptive messages
- Provide specific error messages for debugging

### Entity State Validation
- Always check entity state when required
- Use `entity.meta.state` to access current workflow state
- Validate state transitions are allowed

### Business Rule Enforcement
- Implement business-specific validation logic
- Check data integrity and consistency
- Validate relationships between entities

### Performance Considerations
- Keep criteria lightweight and fast
- Avoid complex database queries in criteria
- Cache frequently accessed data when appropriate
