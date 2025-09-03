# Criteria Requirements

## Overview
This document defines the criteria for the Cyoda OMS Backend system. Criteria implement conditional logic for workflow transitions.

## Criteria

### 1. CartHasItemsCriterion
**Entity:** Cart
**Transition:** `open_checkout`
**Description:** Validates that cart has items before allowing checkout.

**Input Data:**
- Cart entity with lines array

**Validation Logic:**
```
1. Validate cart entity exists
2. Check cart.lines is not null
3. Check cart.lines array is not empty
4. Check cart.totalItems > 0
5. Validate each line has positive quantity
6. Return success if all validations pass
```

**Success Condition:**
- Cart has at least one item with positive quantity

**Failure Conditions:**
- Cart is null
- Cart lines array is null or empty
- Total items is zero or negative
- Any line has zero or negative quantity

### 2. CartHasGuestContactCriterion
**Entity:** Cart
**Transition:** `checkout`
**Description:** Validates that cart has required guest contact information for checkout.

**Input Data:**
- Cart entity with guestContact object

**Validation Logic:**
```
1. Validate cart entity exists
2. Check cart.guestContact is not null
3. Validate required contact fields:
   a. guestContact.name is not null and not empty
   b. guestContact.address is not null
   c. guestContact.address.line1 is not null and not empty
   d. guestContact.address.city is not null and not empty
   e. guestContact.address.postcode is not null and not empty
   f. guestContact.address.country is not null and not empty
4. Validate optional fields if present:
   a. If email provided, validate email format
   b. If phone provided, validate phone format
5. Return success if all validations pass
```

**Success Condition:**
- Cart has complete guest contact information with required address fields

**Failure Conditions:**
- Cart is null
- Guest contact is null
- Required contact fields are missing or empty
- Email format is invalid (if provided)
- Phone format is invalid (if provided)

### 3. PaymentIsPaidCriterion
**Entity:** Payment
**Transition:** Used in order creation validation
**Description:** Validates that payment is in PAID state before order creation.

**Input Data:**
- Payment entity

**Validation Logic:**
```
1. Validate payment entity exists
2. Check payment state using entity.meta.state
3. Validate state equals "PAID"
4. Check payment.amount > 0
5. Validate payment.provider equals "DUMMY"
6. Return success if all validations pass
```

**Success Condition:**
- Payment is in PAID state with valid amount and provider

**Failure Conditions:**
- Payment is null
- Payment state is not PAID
- Payment amount is zero or negative
- Payment provider is not DUMMY

### 4. ProductHasStockCriterion
**Entity:** Product
**Transition:** Used in order creation validation
**Description:** Validates that product has sufficient stock for order quantity.

**Input Data:**
- Product entity
- Required quantity

**Validation Logic:**
```
1. Validate product entity exists
2. Check product.quantityAvailable is not null
3. Validate product.quantityAvailable >= required quantity
4. Check product.quantityAvailable >= 0 (no negative stock)
5. Return success if stock is sufficient
```

**Success Condition:**
- Product has sufficient available quantity

**Failure Conditions:**
- Product is null
- Quantity available is null
- Insufficient stock for required quantity
- Negative stock quantity

### 5. OrderHasShipmentCriterion
**Entity:** Order
**Transition:** Used in order state transitions
**Description:** Validates that order has an associated shipment.

**Input Data:**
- Order entity
- Shipment entity reference

**Validation Logic:**
```
1. Validate order entity exists
2. Retrieve shipment by orderId
3. Check shipment entity exists
4. Validate shipment.orderId matches order.orderId
5. Check shipment has valid lines
6. Return success if shipment is properly associated
```

**Success Condition:**
- Order has valid associated shipment

**Failure Conditions:**
- Order is null
- Shipment not found
- Shipment order ID mismatch
- Shipment has no lines

### 6. ShipmentQuantitiesValidCriterion
**Entity:** Shipment
**Transition:** Used in shipment state transitions
**Description:** Validates shipment quantities are consistent and valid.

**Input Data:**
- Shipment entity with lines

**Validation Logic:**
```
1. Validate shipment entity exists
2. Check shipment.lines is not null and not empty
3. For each line in shipment.lines:
   a. Validate qtyOrdered > 0
   b. Validate qtyPicked >= 0 and qtyPicked <= qtyOrdered
   c. Validate qtyShipped >= 0 and qtyShipped <= qtyPicked
4. Return success if all quantities are valid
```

**Success Condition:**
- All shipment line quantities are valid and consistent

**Failure Conditions:**
- Shipment is null
- Shipment has no lines
- Any quantity is negative
- Picked quantity exceeds ordered quantity
- Shipped quantity exceeds picked quantity

## Criteria Naming Convention
All criteria follow PascalCase naming starting with the entity name:
- Cart criteria: `Cart*Criterion`
- Payment criteria: `Payment*Criterion`
- Product criteria: `Product*Criterion`
- Order criteria: `Order*Criterion`
- Shipment criteria: `Shipment*Criterion`

## Validation Patterns

### Required Field Validation
```
1. Check entity is not null
2. Check field is not null
3. For strings: check not empty after trim
4. For numbers: check positive where applicable
5. For arrays: check not empty where applicable
```

### Business Rule Validation
```
1. Validate entity state using entity.meta.state
2. Check business constraints (stock levels, quantities, etc.)
3. Validate relationships between entities
4. Check data consistency rules
```

### Format Validation
```
1. Email: standard email regex pattern
2. Phone: basic phone number format
3. Postal codes: country-specific patterns
4. IDs: UUID or ULID format validation
```

## Error Handling
All criteria should:
1. Return clear success/failure outcomes
2. Provide descriptive failure reasons
3. Use appropriate failure categories (structural, business rule, data quality)
4. Handle null inputs gracefully
5. Log validation steps for debugging
