# Criteria Requirements

## Overview
This document defines the criteria requirements for the Cyoda OMS Backend system. Criteria implement conditional logic for workflow transitions. Most transitions in this system are straightforward and don't require complex criteria.

## Cart Criteria

### CartHasItemsCriterion
**Entity**: Cart
**Purpose**: Validates that cart contains items before checkout
**Usage**: Can be used in checkout transitions

**Logic**:
```
check(cart):
    return cart.lines is not empty AND cart.totalItems > 0
```

**Implementation**: Simple validation criterion

### CartItemStockCriterion  
**Entity**: Cart
**Purpose**: Validates that all cart items have sufficient stock
**Usage**: Can be used before cart operations

**Logic**:
```
check(cart):
    for each line in cart.lines:
        product = fetch product by line.sku
        if product.quantityAvailable < line.qty:
            return false with reason "Insufficient stock for " + line.sku
    return true
```

**Implementation**: Stock validation criterion

## Payment Criteria

### PaymentValidAmountCriterion
**Entity**: Payment  
**Purpose**: Validates payment amount is positive and reasonable
**Usage**: Can be used in payment initiation

**Logic**:
```
check(payment):
    return payment.amount > 0 AND payment.amount <= 999999
```

**Implementation**: Simple amount validation

### PaymentCartMatchCriterion
**Entity**: Payment
**Purpose**: Validates payment amount matches cart total
**Usage**: Can be used before payment processing

**Logic**:
```
check(payment):
    cart = fetch cart by payment.cartId
    return payment.amount equals cart.grandTotal
```

**Implementation**: Cross-entity validation

## Order Criteria

### OrderValidContactCriterion
**Entity**: Order
**Purpose**: Validates guest contact information is complete
**Usage**: Can be used in order creation

**Logic**:
```
check(order):
    contact = order.guestContact
    if contact is null:
        return false with reason "Guest contact required"
    
    if contact.name is empty:
        return false with reason "Guest name required"
        
    address = contact.address
    if address is null:
        return false with reason "Address required"
        
    required_fields = [address.line1, address.city, address.postcode, address.country]
    for field in required_fields:
        if field is empty:
            return false with reason "Complete address required"
            
    return true
```

**Implementation**: Complex validation criterion

### OrderHasLinesCriterion
**Entity**: Order
**Purpose**: Validates order has line items
**Usage**: Can be used in order processing

**Logic**:
```
check(order):
    return order.lines is not empty AND order.lines.size() > 0
```

**Implementation**: Simple validation criterion

## Shipment Criteria

### ShipmentAllItemsPickedCriterion
**Entity**: Shipment
**Purpose**: Validates all items are picked before marking ready to send
**Usage**: Can be used in ready_to_send transition

**Logic**:
```
check(shipment):
    for each line in shipment.lines:
        if line.qtyPicked < line.qtyOrdered:
            return false with reason "Not all items picked for shipment"
    return true
```

**Implementation**: Picking validation criterion

### ShipmentValidQuantitiesCriterion
**Entity**: Shipment
**Purpose**: Validates shipment quantities are consistent
**Usage**: Can be used in shipment state transitions

**Logic**:
```
check(shipment):
    for each line in shipment.lines:
        if line.qtyPicked > line.qtyOrdered:
            return false with reason "Picked quantity exceeds ordered quantity"
        if line.qtyShipped > line.qtyPicked:
            return false with reason "Shipped quantity exceeds picked quantity"
    return true
```

**Implementation**: Quantity consistency validation

## Product Criteria

### ProductHasStockCriterion
**Entity**: Product
**Purpose**: Validates product has available stock
**Usage**: Can be used in stock-dependent operations

**Logic**:
```
check(product):
    return product.quantityAvailable > 0
```

**Implementation**: Simple stock check

### ProductValidPriceCriterion
**Entity**: Product
**Purpose**: Validates product has valid price
**Usage**: Can be used in product operations

**Logic**:
```
check(product):
    return product.price > 0
```

**Implementation**: Simple price validation

## General Validation Criteria

### EntityExistsCriterion
**Purpose**: Generic criterion to validate entity exists
**Usage**: Can be used across multiple entity types

**Logic**:
```
check(entity):
    return entity is not null
```

**Implementation**: Generic existence check

### EntityStateValidCriterion
**Purpose**: Generic criterion to validate entity is in expected state
**Usage**: Can be used to validate state before transitions

**Logic**:
```
check(entity, expectedState):
    return entity.meta.state equals expectedState
```

**Implementation**: Generic state validation

## Criteria Usage Guidelines

### When to Use Criteria
- **Complex Validation**: Use criteria for multi-field or cross-entity validation
- **Business Rules**: Use criteria for business logic that determines transition eligibility  
- **Conditional Transitions**: Use criteria when multiple transitions are possible from one state
- **Data Quality**: Use criteria to ensure data integrity before state changes

### When NOT to Use Criteria
- **Simple State Checks**: Use simple workflow conditions instead
- **Basic Field Validation**: Handle in processors or controllers
- **Always-True Conditions**: Don't add unnecessary criteria
- **Performance-Critical Paths**: Minimize criteria in high-frequency operations

### Implementation Notes
- Keep criteria simple and focused on single concerns
- Return clear, actionable error messages
- Avoid side effects in criteria (read-only operations)
- Use criteria for validation, not data transformation
- Consider performance impact of database queries in criteria

### Error Handling
- Return descriptive error messages for business users
- Include relevant entity identifiers in error messages
- Use appropriate error categories (validation, business rule, data quality)
- Log criteria failures for debugging and monitoring
