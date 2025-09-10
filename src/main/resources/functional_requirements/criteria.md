# Criteria Requirements

## Overview
This document defines the detailed requirements for all criteria in the Cyoda OMS Backend system. Criteria are pure functions that evaluate conditions without side effects to determine if workflow transitions should be allowed.

**Critical Limitations:**
- Criteria MUST NOT modify entities or have side effects
- Criteria are pure functions that only evaluate conditions
- Use CriterionSerializer for type-safe evaluation
- Criteria can only read entity data, not modify it

## 1. CartHasItemsCriterion

**Criterion Name:** CartHasItemsCriterion
**Entity:** Cart
**Package:** `com.java_template.application.criterion`
**Transitions:** OPEN_CHECKOUT

### Purpose
Validates that a cart has at least one item before allowing checkout to begin.

### Input Data
- Cart entity with lines collection

### Evaluation Logic
Returns true if cart has at least one line item with quantity > 0, false otherwise.

### Pseudocode
```
FUNCTION check(cart):
    IF cart.lines is null OR cart.lines is empty:
        RETURN false
    END IF
    
    FOR each line in cart.lines:
        IF line.qty > 0:
            RETURN true
        END IF
    END FOR
    
    RETURN false
END FUNCTION
```

### Validation Rules
- Cart must have at least one line
- At least one line must have qty > 0
- Lines collection cannot be null

### Error Conditions
- Returns false if cart has no lines
- Returns false if all lines have qty = 0
- Returns false if lines collection is null

## 2. CartHasGuestContactCriterion

**Criterion Name:** CartHasGuestContactCriterion
**Entity:** Cart
**Package:** `com.java_template.application.criterion`
**Transitions:** CHECKOUT

### Purpose
Validates that a cart has complete guest contact information before allowing conversion to order.

### Input Data
- Cart entity with guestContact object

### Evaluation Logic
Returns true if cart has complete guest contact with required address fields, false otherwise.

### Pseudocode
```
FUNCTION check(cart):
    IF cart.guestContact is null:
        RETURN false
    END IF
    
    guestContact = cart.guestContact
    
    // Check required name
    IF guestContact.name is null OR guestContact.name is empty:
        RETURN false
    END IF
    
    // Check required address
    IF guestContact.address is null:
        RETURN false
    END IF
    
    address = guestContact.address
    
    // Check required address fields
    IF address.line1 is null OR address.line1 is empty:
        RETURN false
    END IF
    
    IF address.city is null OR address.city is empty:
        RETURN false
    END IF
    
    IF address.postcode is null OR address.postcode is empty:
        RETURN false
    END IF
    
    IF address.country is null OR address.country is empty:
        RETURN false
    END IF
    
    RETURN true
END FUNCTION
```

### Validation Rules
- Guest contact must not be null
- Guest name must not be null or empty
- Address must not be null
- Address line1 must not be null or empty
- Address city must not be null or empty
- Address postcode must not be null or empty
- Address country must not be null or empty

### Error Conditions
- Returns false if guestContact is null
- Returns false if required name is missing
- Returns false if address is null
- Returns false if any required address field is missing

## 3. PaymentIsPaidCriterion

**Criterion Name:** PaymentIsPaidCriterion
**Entity:** Payment
**Package:** `com.java_template.application.criterion`
**Transitions:** Used in order creation validation (if needed)

### Purpose
Validates that a payment is in PAID state before allowing order creation.

### Input Data
- Payment entity with state information

### Evaluation Logic
Returns true if payment state is PAID, false otherwise.

### Pseudocode
```
FUNCTION check(payment):
    // Access payment state through meta.state
    IF payment.meta.state == "PAID":
        RETURN true
    ELSE:
        RETURN false
    END IF
END FUNCTION
```

### Validation Rules
- Payment state must be exactly "PAID"
- State comparison is case-sensitive

### Error Conditions
- Returns false if payment state is not "PAID"
- Returns false if payment meta or state is null

## 4. ProductHasSufficientStockCriterion

**Criterion Name:** ProductHasSufficientStockCriterion
**Entity:** Product
**Package:** `com.java_template.application.criterion`
**Transitions:** Used in cart/order validation (if needed)

### Purpose
Validates that a product has sufficient stock for a requested quantity.

### Input Data
- Product entity with quantityAvailable
- Requested quantity (passed as context)

### Evaluation Logic
Returns true if product has sufficient stock for requested quantity, false otherwise.

### Pseudocode
```
FUNCTION check(product, requestedQuantity):
    IF product.quantityAvailable is null:
        RETURN false
    END IF
    
    IF requestedQuantity is null OR requestedQuantity <= 0:
        RETURN false
    END IF
    
    IF product.quantityAvailable >= requestedQuantity:
        RETURN true
    ELSE:
        RETURN false
    END IF
END FUNCTION
```

### Validation Rules
- Product quantityAvailable must not be null
- Requested quantity must be greater than 0
- Available quantity must be >= requested quantity

### Error Conditions
- Returns false if quantityAvailable is null
- Returns false if requestedQuantity is null or <= 0
- Returns false if insufficient stock

## Common Criterion Patterns

### CriterionSerializer Usage
```
// Use CriterionSerializer for type-safe evaluation
CriterionSerializer<EntityType> serializer = new CriterionSerializer<>(EntityType.class);
EntityType entity = serializer.deserialize(entityData);
boolean result = evaluateCondition(entity);
return result;
```

### Entity State Access
```
// Access entity state through meta.state
String currentState = entity.meta.state;
if ("EXPECTED_STATE".equals(currentState)) {
    return true;
}
```

### Null Safety
```
// Always check for null values
if (entity == null || entity.field == null) {
    return false;
}

// Check for empty collections
if (entity.collection == null || entity.collection.isEmpty()) {
    return false;
}
```

### String Validation
```
// Check for null or empty strings
if (stringField == null || stringField.trim().isEmpty()) {
    return false;
}
```

## Implementation Guidelines

### Pure Function Requirements
Criteria must be pure functions:
- No side effects (no entity modifications)
- No external service calls
- No database writes
- No state mutations
- Deterministic results (same input = same output)

### Naming Convention
- Criterion name must start with entity name (e.g., CartHasItemsCriterion)
- Use descriptive names that clearly indicate the condition being checked
- Follow pattern: EntityConditionCriterion

### Package Location
- All criteria in `com.java_template.application.criterion`

### Interface Implementation
- Must implement `CyodaCriterion` interface
- Implement `check()` method for evaluation logic
- Implement `supports()` method for entity type matching

### Spring Discovery
- Use `@Component` annotation for automatic discovery

### Error Handling
- Return false for invalid conditions rather than throwing exceptions
- Handle null values gracefully
- Validate input parameters

### Performance Considerations
- Keep evaluation logic simple and fast
- Avoid complex computations
- Cache results if appropriate (but maintain purity)

### Testing Guidelines
- Test all positive and negative cases
- Test null and edge cases
- Verify no side effects occur
- Test with various entity states

## Validation Patterns

### Required Field Validation
```
if (entity.requiredField == null || entity.requiredField.isEmpty()) {
    return false;
}
```

### Numeric Range Validation
```
if (entity.numericField < minValue || entity.numericField > maxValue) {
    return false;
}
```

### Collection Validation
```
if (entity.collection == null || entity.collection.isEmpty()) {
    return false;
}

// Check all items in collection
for (Item item : entity.collection) {
    if (!isValidItem(item)) {
        return false;
    }
}
```

### State Validation
```
String currentState = entity.meta.state;
List<String> validStates = Arrays.asList("STATE1", "STATE2", "STATE3");
return validStates.contains(currentState);
```

## Usage in Workflows

Criteria are used in workflow transitions to:
1. **Guard Transitions** - Prevent invalid state changes
2. **Validate Preconditions** - Ensure entity is ready for transition
3. **Business Rule Enforcement** - Apply business logic constraints
4. **Data Integrity** - Ensure data consistency before state changes

Example workflow usage:
- Cart can only move to CHECKING_OUT if it has items (CartHasItemsCriterion)
- Cart can only be converted if it has guest contact (CartHasGuestContactCriterion)
- Order can only be created if payment is paid (PaymentIsPaidCriterion)
