# Criteria Requirements

## Overview
This document defines the detailed requirements for all criteria in the Cyoda OMS Backend system. Criteria implement conditional logic for workflow transitions and validation rules.

**Important Notes**:
- Keep criteria simple and focused on single validation concerns
- Use EvaluationOutcome sealed classes for type-safe result handling
- Chain multiple validations using logical operators (AND/OR)
- Criteria should be stateless and deterministic

## Criteria Definitions

### 1. Cart Criteria

#### CartHasValidItemsCriterion

**Entity**: Cart  
**Criterion Name**: CartHasValidItemsCriterion  
**Description**: Validates that cart has valid items with positive quantities and prices.

**Validation Logic**:
- Cart must have at least one line item
- Each line must have positive quantity
- Each line must have positive price
- Each line must have valid SKU

**Pseudocode**:
```
EVALUATE CartHasValidItemsCriterion:
  INPUT: cart entity
  
  IF cart.lines is empty:
    RETURN Fail.businessRuleFailure("Cart must have at least one item")
  END IF
  
  FOR each line in cart.lines:
    IF line.sku is null or empty:
      RETURN Fail.structuralFailure("Line item missing SKU")
    END IF
    
    IF line.qty <= 0:
      RETURN Fail.businessRuleFailure("Line item quantity must be positive")
    END IF
    
    IF line.price < 0:
      RETURN Fail.businessRuleFailure("Line item price cannot be negative")
    END IF
  END FOR
  
  RETURN success()
END EVALUATE
```

#### CartHasGuestContactCriterion

**Entity**: Cart  
**Criterion Name**: CartHasGuestContactCriterion  
**Description**: Validates that cart has complete guest contact information for checkout.

**Validation Logic**:
- Guest contact must be present
- Name must be provided
- Address must be complete (line1, city, postcode, country)

**Pseudocode**:
```
EVALUATE CartHasGuestContactCriterion:
  INPUT: cart entity
  
  IF cart.guestContact is null:
    RETURN Fail.businessRuleFailure("Guest contact information is required")
  END IF
  
  IF cart.guestContact.name is null or empty:
    RETURN Fail.businessRuleFailure("Guest name is required")
  END IF
  
  IF cart.guestContact.address is null:
    RETURN Fail.businessRuleFailure("Guest address is required")
  END IF
  
  address = cart.guestContact.address
  
  IF address.line1 is null or empty:
    RETURN Fail.businessRuleFailure("Address line 1 is required")
  END IF
  
  IF address.city is null or empty:
    RETURN Fail.businessRuleFailure("City is required")
  END IF
  
  IF address.postcode is null or empty:
    RETURN Fail.businessRuleFailure("Postcode is required")
  END IF
  
  IF address.country is null or empty:
    RETURN Fail.businessRuleFailure("Country is required")
  END IF
  
  RETURN success()
END EVALUATE
```

---

### 2. Payment Criteria

#### PaymentIsValidForProcessingCriterion

**Entity**: Payment  
**Criterion Name**: PaymentIsValidForProcessingCriterion  
**Description**: Validates that payment is valid for processing.

**Validation Logic**:
- Payment amount must be positive
- Cart ID must be valid
- Provider must be "DUMMY"

**Pseudocode**:
```
EVALUATE PaymentIsValidForProcessingCriterion:
  INPUT: payment entity
  
  IF payment.amount <= 0:
    RETURN Fail.businessRuleFailure("Payment amount must be positive")
  END IF
  
  IF payment.cartId is null or empty:
    RETURN Fail.structuralFailure("Payment must reference a cart")
  END IF
  
  IF payment.provider != "DUMMY":
    RETURN Fail.businessRuleFailure("Only DUMMY provider is supported")
  END IF
  
  RETURN success()
END EVALUATE
```

#### PaymentIsPaidCriterion

**Entity**: Payment  
**Criterion Name**: PaymentIsPaidCriterion  
**Description**: Validates that payment is in PAID state.

**Validation Logic**:
- Payment state must be PAID

**Pseudocode**:
```
EVALUATE PaymentIsPaidCriterion:
  INPUT: payment entity with metadata
  
  currentState = payment.metadata.state
  
  IF currentState != "PAID":
    RETURN Fail.businessRuleFailure("Payment must be in PAID state, current: " + currentState)
  END IF
  
  RETURN success()
END EVALUATE
```

---

### 3. Order Criteria

#### OrderHasValidLinesCriterion

**Entity**: Order  
**Criterion Name**: OrderHasValidLinesCriterion  
**Description**: Validates that order has valid line items.

**Validation Logic**:
- Order must have at least one line
- Each line must have valid SKU, positive quantity, and positive unit price
- Line totals must be calculated correctly

**Pseudocode**:
```
EVALUATE OrderHasValidLinesCriterion:
  INPUT: order entity
  
  IF order.lines is empty:
    RETURN Fail.businessRuleFailure("Order must have at least one line item")
  END IF
  
  FOR each line in order.lines:
    IF line.sku is null or empty:
      RETURN Fail.structuralFailure("Order line missing SKU")
    END IF
    
    IF line.qty <= 0:
      RETURN Fail.businessRuleFailure("Order line quantity must be positive")
    END IF
    
    IF line.unitPrice < 0:
      RETURN Fail.businessRuleFailure("Order line unit price cannot be negative")
    END IF
    
    expectedLineTotal = line.unitPrice * line.qty
    IF line.lineTotal != expectedLineTotal:
      RETURN Fail.dataQualityFailure("Order line total mismatch")
    END IF
  END FOR
  
  RETURN success()
END EVALUATE
```

#### OrderHasCompleteGuestContactCriterion

**Entity**: Order  
**Criterion Name**: OrderHasCompleteGuestContactCriterion  
**Description**: Validates that order has complete guest contact information.

**Validation Logic**:
- Guest contact must be present and complete
- Name is required
- Complete address is required (all fields)

**Pseudocode**:
```
EVALUATE OrderHasCompleteGuestContactCriterion:
  INPUT: order entity
  
  IF order.guestContact is null:
    RETURN Fail.structuralFailure("Order must have guest contact")
  END IF
  
  contact = order.guestContact
  
  IF contact.name is null or empty:
    RETURN Fail.businessRuleFailure("Guest name is required for order")
  END IF
  
  IF contact.address is null:
    RETURN Fail.structuralFailure("Guest address is required for order")
  END IF
  
  address = contact.address
  
  // All address fields are required for orders (stricter than cart)
  IF address.line1 is null or empty:
    RETURN Fail.businessRuleFailure("Address line 1 is required for order")
  END IF
  
  IF address.city is null or empty:
    RETURN Fail.businessRuleFailure("City is required for order")
  END IF
  
  IF address.postcode is null or empty:
    RETURN Fail.businessRuleFailure("Postcode is required for order")
  END IF
  
  IF address.country is null or empty:
    RETURN Fail.businessRuleFailure("Country is required for order")
  END IF
  
  RETURN success()
END EVALUATE
```

---

### 4. Product Criteria

#### ProductHasValidInventoryCriterion

**Entity**: Product  
**Criterion Name**: ProductHasValidInventoryCriterion  
**Description**: Validates that product has valid inventory information.

**Validation Logic**:
- Quantity available must be non-negative
- Price must be positive
- SKU must be unique and valid

**Pseudocode**:
```
EVALUATE ProductHasValidInventoryCriterion:
  INPUT: product entity
  
  IF product.sku is null or empty:
    RETURN Fail.structuralFailure("Product SKU is required")
  END IF
  
  IF product.quantityAvailable < 0:
    RETURN Fail.businessRuleFailure("Product quantity available cannot be negative")
  END IF
  
  IF product.price <= 0:
    RETURN Fail.businessRuleFailure("Product price must be positive")
  END IF
  
  IF product.name is null or empty:
    RETURN Fail.structuralFailure("Product name is required")
  END IF
  
  IF product.category is null or empty:
    RETURN Fail.businessRuleFailure("Product category is required")
  END IF
  
  RETURN success()
END EVALUATE
```

#### ProductHasSufficientStockCriterion

**Entity**: Product  
**Criterion Name**: ProductHasSufficientStockCriterion  
**Description**: Validates that product has sufficient stock for a given quantity.

**Validation Logic**:
- Check if requested quantity is available
- Consider current quantity available

**Pseudocode**:
```
EVALUATE ProductHasSufficientStockCriterion:
  INPUT: product entity, requestedQuantity (from context)
  
  IF requestedQuantity <= 0:
    RETURN Fail.businessRuleFailure("Requested quantity must be positive")
  END IF
  
  IF product.quantityAvailable < requestedQuantity:
    RETURN Fail.businessRuleFailure(
      "Insufficient stock. Available: " + product.quantityAvailable + 
      ", Requested: " + requestedQuantity
    )
  END IF
  
  RETURN success()
END EVALUATE
```

---

### 5. Shipment Criteria

#### ShipmentHasValidLinesCriterion

**Entity**: Shipment  
**Criterion Name**: ShipmentHasValidLinesCriterion  
**Description**: Validates that shipment has valid line items with correct quantities.

**Validation Logic**:
- Shipment must have at least one line
- Quantities must be consistent (picked <= ordered, shipped <= picked)
- All quantities must be non-negative

**Pseudocode**:
```
EVALUATE ShipmentHasValidLinesCriterion:
  INPUT: shipment entity
  
  IF shipment.lines is empty:
    RETURN Fail.businessRuleFailure("Shipment must have at least one line")
  END IF
  
  FOR each line in shipment.lines:
    IF line.sku is null or empty:
      RETURN Fail.structuralFailure("Shipment line missing SKU")
    END IF
    
    IF line.qtyOrdered <= 0:
      RETURN Fail.businessRuleFailure("Ordered quantity must be positive")
    END IF
    
    IF line.qtyPicked < 0:
      RETURN Fail.businessRuleFailure("Picked quantity cannot be negative")
    END IF
    
    IF line.qtyShipped < 0:
      RETURN Fail.businessRuleFailure("Shipped quantity cannot be negative")
    END IF
    
    IF line.qtyPicked > line.qtyOrdered:
      RETURN Fail.businessRuleFailure("Cannot pick more than ordered")
    END IF
    
    IF line.qtyShipped > line.qtyPicked:
      RETURN Fail.businessRuleFailure("Cannot ship more than picked")
    END IF
  END FOR
  
  RETURN success()
END EVALUATE
```

## Criteria Usage in Workflows

### Simple Criteria in Transitions
```json
"criterion": {
  "type": "function",
  "function": {
    "name": "CartHasValidItemsCriterion",
    "config": {
      "attachEntity": true,
      "calculationNodesTags": "validation",
      "responseTimeoutMs": 5000,
      "retryPolicy": "FIXED"
    }
  }
}
```

### Group Criteria with Multiple Conditions
```json
"criterion": {
  "type": "group",
  "operator": "AND",
  "conditions": [
    {
      "type": "function",
      "function": {
        "name": "CartHasValidItemsCriterion"
      }
    },
    {
      "type": "function", 
      "function": {
        "name": "CartHasGuestContactCriterion"
      }
    }
  ]
}
```

## Implementation Notes

1. **EvaluationOutcome Usage**: Use appropriate failure types (structuralFailure, businessRuleFailure, dataQualityFailure)
2. **Logical Chaining**: Chain multiple validations using `.and()` and `.or()` methods
3. **Error Messages**: Provide clear, actionable error messages
4. **Stateless Design**: Criteria should not maintain state between evaluations
5. **Performance**: Keep criteria lightweight and fast-executing
6. **Context Access**: Use context to access entity metadata and additional parameters
