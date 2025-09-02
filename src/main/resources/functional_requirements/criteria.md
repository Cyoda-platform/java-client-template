# Criteria Requirements

## Overview

This document defines the detailed requirements for criteria in the Cyoda OMS Backend system. Criteria implement conditional logic for workflow transitions and business rule validation.

Based on the workflow analysis, most transitions in this system are straightforward and do not require complex criteria. The workflows are designed to be simple and demo-focused with clear state progressions.

## Criteria Definitions

### 1. CartHasItemsCriterion

**Entity**: Cart
**Purpose**: Validates that cart has at least one item before certain operations
**Usage**: Can be used to prevent checkout of empty carts

**Input Data**:
- Cart entity

**Validation Logic**:
- Check if cart.lines array is not empty
- Check if totalItems > 0
- Validate that all line quantities are positive

**Pseudocode**:
```
BEGIN CartHasItemsCriterion
    GET cart entity from input
    
    IF cart.lines is empty THEN
        RETURN false WITH reason "Cart has no items"
    END IF
    
    IF cart.totalItems <= 0 THEN
        RETURN false WITH reason "Cart total items is zero or negative"
    END IF
    
    FOR each line in cart.lines DO
        IF line.qty <= 0 THEN
            RETURN false WITH reason "Invalid quantity for item " + line.sku
        END IF
    END FOR
    
    RETURN true
END
```

### 2. PaymentValidCriterion

**Entity**: Payment
**Purpose**: Validates payment data before processing
**Usage**: Ensures payment has valid amount and cart reference

**Input Data**:
- Payment entity

**Validation Logic**:
- Check if amount is positive
- Check if cartId is valid
- Validate provider is "DUMMY"

**Pseudocode**:
```
BEGIN PaymentValidCriterion
    GET payment entity from input
    
    IF payment.amount <= 0 THEN
        RETURN false WITH reason "Payment amount must be positive"
    END IF
    
    IF payment.cartId is null or empty THEN
        RETURN false WITH reason "Payment must have valid cart ID"
    END IF
    
    IF payment.provider != "DUMMY" THEN
        RETURN false WITH reason "Only DUMMY provider is supported"
    END IF
    
    RETURN true
END
```

### 3. ProductStockAvailableCriterion

**Entity**: Product
**Purpose**: Validates that product has sufficient stock for requested quantity
**Usage**: Used before decrementing stock in order creation

**Input Data**:
- Product entity
- Requested quantity

**Validation Logic**:
- Check if quantityAvailable >= requested quantity
- Validate product is active/available

**Pseudocode**:
```
BEGIN ProductStockAvailableCriterion
    GET product entity from input
    GET requestedQty from input parameters
    
    IF product.quantityAvailable < requestedQty THEN
        RETURN false WITH reason "Insufficient stock. Available: " + product.quantityAvailable + ", Requested: " + requestedQty
    END IF
    
    IF product.quantityAvailable < 0 THEN
        RETURN false WITH reason "Product has negative stock"
    END IF
    
    RETURN true
END
```

### 4. OrderValidForFulfillmentCriterion

**Entity**: Order
**Purpose**: Validates that order is ready for fulfillment operations
**Usage**: Used before starting picking or shipping operations

**Input Data**:
- Order entity

**Validation Logic**:
- Check if order has valid lines
- Check if guest contact information is complete
- Validate order totals

**Pseudocode**:
```
BEGIN OrderValidForFulfillmentCriterion
    GET order entity from input
    
    IF order.lines is empty THEN
        RETURN false WITH reason "Order has no line items"
    END IF
    
    IF order.guestContact is null THEN
        RETURN false WITH reason "Order missing guest contact information"
    END IF
    
    IF order.guestContact.name is null or empty THEN
        RETURN false WITH reason "Order missing guest name"
    END IF
    
    IF order.guestContact.address is null THEN
        RETURN false WITH reason "Order missing shipping address"
    END IF
    
    IF order.guestContact.address.line1 is null or empty THEN
        RETURN false WITH reason "Order missing address line 1"
    END IF
    
    IF order.guestContact.address.city is null or empty THEN
        RETURN false WITH reason "Order missing city"
    END IF
    
    IF order.guestContact.address.postcode is null or empty THEN
        RETURN false WITH reason "Order missing postcode"
    END IF
    
    IF order.guestContact.address.country is null or empty THEN
        RETURN false WITH reason "Order missing country"
    END IF
    
    IF order.totals.grand <= 0 THEN
        RETURN false WITH reason "Order has invalid total amount"
    END IF
    
    RETURN true
END
```

### 5. ShipmentReadyForDispatchCriterion

**Entity**: Shipment
**Purpose**: Validates that shipment is ready to be sent
**Usage**: Used before marking shipment as sent

**Input Data**:
- Shipment entity

**Validation Logic**:
- Check if all items have been picked
- Validate shipment quantities

**Pseudocode**:
```
BEGIN ShipmentReadyForDispatchCriterion
    GET shipment entity from input
    
    IF shipment.lines is empty THEN
        RETURN false WITH reason "Shipment has no line items"
    END IF
    
    FOR each line in shipment.lines DO
        IF line.qtyPicked < line.qtyOrdered THEN
            RETURN false WITH reason "Item " + line.sku + " not fully picked"
        END IF
        
        IF line.qtyPicked < 0 THEN
            RETURN false WITH reason "Invalid picked quantity for item " + line.sku
        END IF
    END FOR
    
    RETURN true
END
```

## Simple Criteria (Built-in)

For most transitions in this system, simple built-in criteria can be used instead of custom criteria classes:

### State-based Criteria

These can be implemented using simple criteria in workflow configuration:

```json
{
  "type": "simple",
  "jsonPath": "state",
  "operation": "EQUALS",
  "value": "EXPECTED_STATE"
}
```

**Examples**:
- Cart in ACTIVE state before checkout
- Payment in PAID state before order creation
- Order in PICKING state before ready to send

### Field Validation Criteria

```json
{
  "type": "simple",
  "jsonPath": "$.fieldName",
  "operation": "GREATER_THAN",
  "value": 0
}
```

**Examples**:
- Cart totalItems > 0
- Payment amount > 0
- Product quantityAvailable >= 0

### Group Criteria for Complex Validation

```json
{
  "type": "group",
  "operator": "AND",
  "conditions": [
    {
      "type": "simple",
      "jsonPath": "$.totalItems",
      "operation": "GREATER_THAN",
      "value": 0
    },
    {
      "type": "simple",
      "jsonPath": "$.grandTotal",
      "operation": "GREATER_THAN",
      "value": 0
    }
  ]
}
```

## Criteria Usage Guidelines

1. **Keep Simple**: Use built-in simple/group criteria when possible
2. **Custom Only When Needed**: Create custom criteria classes only for complex business logic
3. **Clear Error Messages**: Provide descriptive failure reasons
4. **Performance**: Avoid expensive operations in criteria
5. **Stateless**: Criteria should be stateless and deterministic
6. **Validation Focus**: Criteria should focus on validation, not business logic

## Recommended Criteria Implementation

For this OMS system, most validation can be handled by:

1. **Simple criteria** for state checks
2. **Group criteria** for multi-field validation
3. **Custom criteria** only for:
   - Stock availability checks
   - Complex address validation
   - Multi-entity validation scenarios

The workflows are designed to be straightforward, so extensive criteria are not required for the demo implementation.
