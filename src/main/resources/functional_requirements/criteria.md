# Criteria Requirements

## Overview
This document defines the detailed requirements for all criteria in the Cyoda OMS Backend system. Criteria are pure functions that evaluate conditions without side effects. They must not modify entities or have any side effects.

## Criteria Definitions

Based on the workflow analysis, the current system design uses primarily automatic transitions and simple manual transitions without complex conditional logic. Therefore, minimal criteria are needed for the initial implementation.

### 1. CartHasItemsCriterion

**Criterion Name**: CartHasItemsCriterion
**Entity**: Cart
**Description**: Checks if cart has at least one item with quantity > 0.

**Expected Input Data**:
- Cart entity with lines array

**Evaluation Logic**:
```
CHECK CartHasItemsCriterion:
  INPUT: Cart entity
  
  IF cart.lines is null OR cart.lines is empty:
    RETURN false
  
  SET hasItems = false
  FOR each line in cart.lines:
    IF line.qty > 0:
      SET hasItems = true
      BREAK
  
  RETURN hasItems
```

**Return Value**: Boolean
- `true`: Cart has at least one item with quantity > 0
- `false`: Cart is empty or all items have quantity 0

**Use Cases**:
- Validate cart before checkout
- Prevent empty cart operations
- UI state management

---

### 2. PaymentIsPaidCriterion

**Criterion Name**: PaymentIsPaidCriterion
**Entity**: Payment
**Description**: Checks if payment is in PAID state.

**Expected Input Data**:
- Payment entity with state information

**Evaluation Logic**:
```
CHECK PaymentIsPaidCriterion:
  INPUT: Payment entity
  
  GET paymentState = entity.meta.state
  
  IF paymentState == "PAID":
    RETURN true
  ELSE:
    RETURN false
```

**Return Value**: Boolean
- `true`: Payment is in PAID state
- `false`: Payment is not in PAID state

**Use Cases**:
- Validate payment before order creation
- Prevent order creation from unpaid carts
- Business rule enforcement

---

### 3. ProductHasStockCriterion

**Criterion Name**: ProductHasStockCriterion
**Entity**: Product
**Description**: Checks if product has sufficient stock for requested quantity.

**Expected Input Data**:
- Product entity
- Requested quantity (from context)

**Evaluation Logic**:
```
CHECK ProductHasStockCriterion:
  INPUT: Product entity, requestedQuantity
  
  IF product.quantityAvailable >= requestedQuantity:
    RETURN true
  ELSE:
    RETURN false
```

**Return Value**: Boolean
- `true`: Product has sufficient stock
- `false`: Product does not have sufficient stock

**Use Cases**:
- Validate stock before adding to cart
- Prevent overselling
- Inventory management

---

### 4. OrderCanBeFulfilledCriterion

**Criterion Name**: OrderCanBeFulfilledCriterion
**Entity**: Order
**Description**: Checks if all order line items can be fulfilled based on current product stock.

**Expected Input Data**:
- Order entity with lines array
- Access to product information (read-only)

**Evaluation Logic**:
```
CHECK OrderCanBeFulfilledCriterion:
  INPUT: Order entity
  
  FOR each line in order.lines:
    // Note: This is read-only access for evaluation
    GET product = entityService.get(Product, line.sku)
    IF product is null:
      RETURN false
    
    IF product.quantityAvailable < line.qty:
      RETURN false
  
  RETURN true
```

**Return Value**: Boolean
- `true`: All order lines can be fulfilled
- `false`: One or more order lines cannot be fulfilled

**Use Cases**:
- Validate order before fulfillment
- Prevent fulfillment of unfulfillable orders
- Business rule enforcement

---

### 5. GuestContactIsValidCriterion

**Criterion Name**: GuestContactIsValidCriterion
**Entity**: Cart or Order
**Description**: Validates guest contact information for checkout.

**Expected Input Data**:
- Entity with guestContact object

**Evaluation Logic**:
```
CHECK GuestContactIsValidCriterion:
  INPUT: Entity with guestContact
  
  IF guestContact is null:
    RETURN false
  
  IF guestContact.name is null OR guestContact.name is empty:
    RETURN false
  
  IF guestContact.address is null:
    RETURN false
  
  IF guestContact.address.line1 is null OR guestContact.address.line1 is empty:
    RETURN false
  
  RETURN true
```

**Return Value**: Boolean
- `true`: Guest contact has required fields (name, address.line1)
- `false`: Guest contact is missing required fields

**Use Cases**:
- Validate checkout information
- Ensure order has valid delivery information
- Business rule enforcement

## Criteria Implementation Guidelines

### Pure Function Requirements
All criteria must be pure functions:
- No side effects
- No entity modifications
- No external API calls (except read-only entity access)
- Deterministic results for same input

### Read-Only Entity Access
Criteria may read entity data for evaluation:
- Use entityService.get() for read-only access
- Never call entityService.update() or entityService.create()
- Access current entity state via entity.meta.state

### Error Handling
Criteria should handle errors gracefully:
- Return false for invalid or missing data
- Log warnings for unexpected conditions
- Never throw exceptions for business logic failures

### Performance Considerations
- Keep evaluation logic simple and fast
- Minimize entity service calls
- Cache frequently accessed data when appropriate
- Avoid complex calculations

### Naming Conventions
- Criterion names follow pattern: {Entity}{Condition}Criterion
- Use PascalCase for criterion names
- Start with entity name for clear organization
- Use descriptive condition names (HasItems, IsPaid, etc.)

### Return Values
- Always return boolean values
- true = condition is met
- false = condition is not met
- Document the meaning of true/false clearly

## Future Criteria Considerations

As the system evolves, additional criteria may be needed for:

### Business Rules
- Customer eligibility checks
- Regional restrictions
- Promotional conditions
- Pricing rules

### Inventory Management
- Reservation policies
- Allocation rules
- Backorder conditions

### Order Management
- Shipping restrictions
- Payment method validation
- Delivery area checks

### Compliance
- Regulatory requirements
- Age restrictions
- Export controls

These criteria should follow the same pure function principles and implementation guidelines outlined above.
