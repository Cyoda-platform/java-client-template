# Criteria Requirements

## Overview
This document defines the detailed requirements for criteria in the Cyoda OMS Backend system. Criteria implement conditional logic for workflow transitions and business rule validation.

**Important Notes**:
- Criteria are used for conditional workflow transitions
- Keep criteria simple and focused on single conditions
- Use simple or group criteria in workflow JSON when possible
- Function criteria are for complex business logic that cannot be expressed in JSON

## Current Workflow Analysis

Based on the defined workflows, the current system uses primarily automatic and manual transitions without complex conditional logic. Most transitions are straightforward state progressions.

## Potential Criteria (Future Extensions)

While the current demo implementation doesn't require complex criteria, the following criteria could be implemented for future enhancements:

### 1. CartValidationCriterion

**Purpose**: Validate cart state before checkout
**Entity**: Cart
**Usage**: Could be used before OPEN_CHECKOUT transition

**Validation Logic**:
- Cart has at least one line item
- All line items have valid products
- All products have sufficient stock
- Cart totals are correctly calculated

**Pseudocode**:
```
CHECK CartValidationCriterion:
  INPUT: cart entity
  
  1. IF cart.lines is empty:
     RETURN false ("Cart is empty")
  
  2. FOR each line in cart.lines:
     a. Get product by line.sku using EntityService
     b. IF product not found:
        RETURN false ("Product not found: " + line.sku)
     c. IF product.quantityAvailable < line.qty:
        RETURN false ("Insufficient stock for: " + line.sku)
  
  3. Recalculate expected totals
  4. IF cart.grandTotal != expected total:
     RETURN false ("Cart totals are incorrect")
  
  5. RETURN true ("Cart is valid")
```

### 2. PaymentValidationCriterion

**Purpose**: Validate payment before order creation
**Entity**: Payment
**Usage**: Could be used before CREATE_ORDER_FROM_PAID transition

**Validation Logic**:
- Payment is in PAID state
- Associated cart exists and is in CONVERTED state
- Payment amount matches cart total

**Pseudocode**:
```
CHECK PaymentValidationCriterion:
  INPUT: payment entity
  
  1. IF payment.meta.state != "PAID":
     RETURN false ("Payment not completed")
  
  2. Get cart by payment.cartId using EntityService
  3. IF cart not found:
     RETURN false ("Associated cart not found")
  
  4. IF cart.meta.state != "CONVERTED":
     RETURN false ("Cart not converted")
  
  5. IF payment.amount != cart.grandTotal:
     RETURN false ("Payment amount mismatch")
  
  6. RETURN true ("Payment is valid")
```

### 3. StockAvailabilityCriterion

**Purpose**: Check product stock availability
**Entity**: Product
**Usage**: Could be used for stock validation in various workflows

**Validation Logic**:
- Product has sufficient quantity available
- Product is not discontinued
- Product is available in specified warehouse

**Pseudocode**:
```
CHECK StockAvailabilityCriterion:
  INPUT: product entity, required quantity, warehouse (optional)
  
  1. IF product.quantityAvailable < required quantity:
     RETURN false ("Insufficient stock")
  
  2. IF warehouse specified:
     a. Check product.inventory.nodes for warehouse
     b. IF warehouse not found or insufficient stock:
        RETURN false ("Insufficient stock in warehouse")
  
  3. RETURN true ("Stock available")
```

### 4. OrderFulfillmentCriterion

**Purpose**: Validate order can be fulfilled
**Entity**: Order
**Usage**: Could be used before START_PICKING transition

**Validation Logic**:
- All order line items have sufficient stock
- Order is in correct state for fulfillment
- Guest contact information is complete

**Pseudocode**:
```
CHECK OrderFulfillmentCriterion:
  INPUT: order entity
  
  1. IF order.meta.state != "WAITING_TO_FULFILL":
     RETURN false ("Order not ready for fulfillment")
  
  2. FOR each line in order.lines:
     a. Get product by line.sku using EntityService
     b. IF product.quantityAvailable < line.qty:
        RETURN false ("Insufficient stock for: " + line.sku)
  
  3. IF order.guestContact.address is incomplete:
     RETURN false ("Incomplete shipping address")
  
  4. RETURN true ("Order can be fulfilled")
```

## Simple Criteria Examples (JSON Configuration)

For most validation needs, use simple criteria in workflow JSON instead of function criteria:

### Cart Line Count Validation
```json
"criterion": {
  "type": "simple",
  "jsonPath": "$.lines",
  "operation": "NOT_NULL",
  "value": null
}
```

### Payment Amount Validation
```json
"criterion": {
  "type": "simple",
  "jsonPath": "$.amount",
  "operation": "GREATER_THAN",
  "value": 0
}
```

### Stock Quantity Check
```json
"criterion": {
  "type": "simple",
  "jsonPath": "$.quantityAvailable",
  "operation": "GREATER_OR_EQUAL",
  "value": 1
}
```

## Group Criteria Examples

For multiple conditions, use group criteria:

### Cart Checkout Validation
```json
"criterion": {
  "type": "group",
  "operator": "AND",
  "conditions": [
    {
      "type": "simple",
      "jsonPath": "$.lines",
      "operation": "NOT_NULL"
    },
    {
      "type": "simple",
      "jsonPath": "$.grandTotal",
      "operation": "GREATER_THAN",
      "value": 0
    },
    {
      "type": "simple",
      "jsonPath": "$.guestContact.name",
      "operation": "NOT_NULL"
    }
  ]
}
```

## Implementation Guidelines

### When to Use Function Criteria
- Complex business logic that cannot be expressed in JSON
- Multi-entity validation requiring EntityService calls
- Dynamic validation based on external data
- Custom validation algorithms

### When to Use Simple/Group Criteria
- Field value comparisons
- Null/not null checks
- Range validations
- Basic logical combinations

### Performance Considerations
- Simple criteria are faster than function criteria
- Group criteria with AND operator fail fast
- Function criteria should minimize EntityService calls
- Cache validation results when possible

### Error Handling
- Return clear, descriptive error messages
- Log validation failures for debugging
- Handle null/missing data gracefully
- Provide actionable feedback to users

## Criteria Naming Convention
- Format: `{Entity}{Purpose}Criterion`
- Examples: CartValidationCriterion, PaymentValidationCriterion
- Must match the criterion function name in workflow configuration

## Current Implementation Status

For the initial demo implementation, most workflows use automatic transitions without complex criteria. The system relies on:

1. **Processor validation**: Business logic validation within processors
2. **Simple state checks**: Using entity.meta.state comparisons
3. **Manual transitions**: User-driven state changes without complex conditions

Future enhancements can add the criteria defined above as needed for more sophisticated business rules.
