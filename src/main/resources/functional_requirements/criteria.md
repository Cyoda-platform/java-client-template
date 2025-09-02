# Criteria Requirements

## Overview

This document defines the detailed requirements for all criteria in the Cyoda OMS Backend system. Criteria implement conditional logic for workflow transitions and determine whether a transition should be allowed based on entity state and business rules.

## Criteria Design Principles

1. Each criterion implements the `CyodaCriterion` interface
2. Criteria should be simple and focused on single validation concerns
3. Return `EvaluationOutcome` with clear success/failure reasons
4. Use logical chaining for complex validations
5. Keep validation logic business-focused and readable
6. Handle edge cases gracefully

## 1. CartHasItemsCriterion

**Entity:** Cart
**Transitions:** `open_checkout`
**Purpose:** Validates that cart contains at least one item before allowing checkout

### Input Data
- Cart entity with lines array

### Validation Logic (Pseudocode)
```
BEGIN CartHasItemsCriterion.check()
    INPUT: cart entity
    
    IF cart is null:
        RETURN EvaluationOutcome.Fail.structuralFailure("Cart entity is null")
    END IF
    
    IF cart.lines is null or empty:
        RETURN EvaluationOutcome.Fail.businessRuleFailure("Cart has no items")
    END IF
    
    SET validItemCount = 0
    FOR each line in cart.lines:
        IF line.qty > 0:
            INCREMENT validItemCount
        END IF
    END FOR
    
    IF validItemCount == 0:
        RETURN EvaluationOutcome.Fail.businessRuleFailure("Cart has no items with positive quantity")
    END IF
    
    IF cart.totalItems <= 0:
        RETURN EvaluationOutcome.Fail.dataQualityFailure("Cart total items is not positive despite having line items")
    END IF
    
    RETURN EvaluationOutcome.success()
END
```

### Expected Outcome
- **Success:** Cart has at least one line item with positive quantity
- **Failure:** Cart is empty, has no valid items, or totals are inconsistent

### Error Categories
- **Structural Failure:** Cart entity is null
- **Business Rule Failure:** No items in cart or all items have zero quantity
- **Data Quality Failure:** Inconsistent totals vs line items

## 2. CartHasGuestContactCriterion

**Entity:** Cart
**Transitions:** `checkout`
**Purpose:** Validates that cart has complete guest contact information required for order creation

### Input Data
- Cart entity with guestContact object

### Validation Logic (Pseudocode)
```
BEGIN CartHasGuestContactCriterion.check()
    INPUT: cart entity
    
    IF cart is null:
        RETURN EvaluationOutcome.Fail.structuralFailure("Cart entity is null")
    END IF
    
    IF cart.guestContact is null:
        RETURN EvaluationOutcome.Fail.businessRuleFailure("Guest contact information is required for checkout")
    END IF
    
    SET contact = cart.guestContact
    
    // Validate required name
    IF contact.name is null or empty or only whitespace:
        RETURN EvaluationOutcome.Fail.businessRuleFailure("Guest name is required")
    END IF
    
    // Validate required address
    IF contact.address is null:
        RETURN EvaluationOutcome.Fail.businessRuleFailure("Shipping address is required")
    END IF
    
    SET address = contact.address
    
    // Validate required address fields
    IF address.line1 is null or empty or only whitespace:
        RETURN EvaluationOutcome.Fail.businessRuleFailure("Address line 1 is required")
    END IF
    
    IF address.city is null or empty or only whitespace:
        RETURN EvaluationOutcome.Fail.businessRuleFailure("City is required")
    END IF
    
    IF address.postcode is null or empty or only whitespace:
        RETURN EvaluationOutcome.Fail.businessRuleFailure("Postal code is required")
    END IF
    
    IF address.country is null or empty or only whitespace:
        RETURN EvaluationOutcome.Fail.businessRuleFailure("Country is required")
    END IF
    
    // Optional fields validation (warn if missing but don't fail)
    IF contact.email is not null and not empty:
        IF contact.email does not match email pattern:
            LOG warning "Invalid email format: {contact.email}"
        END IF
    END IF
    
    IF contact.phone is not null and not empty:
        IF contact.phone length < 10:
            LOG warning "Phone number seems too short: {contact.phone}"
        END IF
    END IF
    
    RETURN EvaluationOutcome.success()
END
```

### Expected Outcome
- **Success:** Cart has complete guest contact with required fields
- **Failure:** Missing or invalid required contact information

### Error Categories
- **Structural Failure:** Cart entity is null
- **Business Rule Failure:** Missing required contact fields (name, address components)

### Validation Rules
- **Required Fields:** name, address.line1, address.city, address.postcode, address.country
- **Optional Fields:** email, phone (validated if provided)
- **Email Validation:** Basic format check if provided
- **Phone Validation:** Length check if provided

## 3. PaymentIsPaidCriterion

**Entity:** Payment
**Transitions:** Used in order creation validation
**Purpose:** Validates that payment is in PAID state before allowing order creation

### Input Data
- Payment entity with state information

### Validation Logic (Pseudocode)
```
BEGIN PaymentIsPaidCriterion.check()
    INPUT: payment entity
    
    IF payment is null:
        RETURN EvaluationOutcome.Fail.structuralFailure("Payment entity is null")
    END IF
    
    IF payment.meta.state != "PAID":
        RETURN EvaluationOutcome.Fail.businessRuleFailure("Payment must be in PAID state, current state: {payment.meta.state}")
    END IF
    
    IF payment.amount <= 0:
        RETURN EvaluationOutcome.Fail.dataQualityFailure("Payment amount must be positive")
    END IF
    
    IF payment.provider != "DUMMY":
        RETURN EvaluationOutcome.Fail.businessRuleFailure("Only DUMMY payment provider is supported")
    END IF
    
    RETURN EvaluationOutcome.success()
END
```

### Expected Outcome
- **Success:** Payment is in PAID state with valid amount and provider
- **Failure:** Payment not paid, invalid amount, or unsupported provider

### Error Categories
- **Structural Failure:** Payment entity is null
- **Business Rule Failure:** Payment not in PAID state or wrong provider
- **Data Quality Failure:** Invalid payment amount

## 4. ProductHasSufficientStockCriterion

**Entity:** Product
**Transitions:** Used in cart validation (optional)
**Purpose:** Validates that product has sufficient stock for requested quantity

### Input Data
- Product entity with quantityAvailable
- Requested quantity

### Validation Logic (Pseudocode)
```
BEGIN ProductHasSufficientStockCriterion.check()
    INPUT: product entity, requestedQuantity
    
    IF product is null:
        RETURN EvaluationOutcome.Fail.structuralFailure("Product entity is null")
    END IF
    
    IF requestedQuantity <= 0:
        RETURN EvaluationOutcome.Fail.businessRuleFailure("Requested quantity must be positive")
    END IF
    
    IF product.quantityAvailable < requestedQuantity:
        RETURN EvaluationOutcome.Fail.businessRuleFailure("Insufficient stock: available={product.quantityAvailable}, requested={requestedQuantity}")
    END IF
    
    // Warn if stock is getting low
    IF product.quantityAvailable - requestedQuantity < 10:
        LOG warning "Low stock warning for product {product.sku}: remaining={product.quantityAvailable - requestedQuantity}"
    END IF
    
    RETURN EvaluationOutcome.success()
END
```

### Expected Outcome
- **Success:** Product has sufficient stock for requested quantity
- **Failure:** Insufficient stock or invalid request

### Error Categories
- **Structural Failure:** Product entity is null
- **Business Rule Failure:** Insufficient stock or invalid quantity

## Criteria Implementation Guidelines

### Naming Conventions
- All criteria names use PascalCase starting with entity name
- Use descriptive names that clearly indicate the validation purpose
- Example: `CartHasItemsCriterion`, `PaymentIsPaidCriterion`

### EvaluationOutcome Usage
- Use appropriate failure categories:
  - `structuralFailure()` - Entity is null or malformed
  - `businessRuleFailure()` - Business logic validation failed
  - `dataQualityFailure()` - Data inconsistency or format issues
- Provide clear, actionable error messages
- Use `success()` for passing validations

### Logical Chaining
```java
// Example of chaining multiple validations
return validateCartExists(cart)
    .and(validateCartHasItems(cart))
    .and(validateCartTotals(cart))
    .and(validateGuestContact(cart));
```

### Error Handling Strategy
- Always validate input parameters first
- Handle null entities gracefully
- Log warnings for non-critical issues
- Provide specific error messages for debugging

### Performance Considerations
- Keep validations lightweight and fast
- Avoid expensive operations in criteria
- Cache frequently accessed data if needed
- Use early returns for obvious failures

### Testing Considerations
- Test all success and failure paths
- Verify error messages are helpful
- Test edge cases (null, empty, boundary values)
- Ensure criteria are deterministic

## Integration with Workflows

### Workflow Configuration
Criteria are referenced in workflow JSON files:
```json
"criterion": {
  "type": "function",
  "function": {
    "name": "CartHasItemsCriterion",
    "config": {
      "attachEntity": true,
      "calculationNodesTags": "validation",
      "responseTimeoutMs": 5000,
      "retryPolicy": "FIXED"
    }
  }
}
```

### Component Registration
- Implement `CyodaCriterion` interface
- Add `@Component` annotation for Spring discovery
- Implement `supports()` method to match operation names
- Use `CriterionSerializer` for entity processing
