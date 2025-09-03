# Criteria Requirements

This document defines the detailed requirements for criteria in the Cyoda OMS Backend system.

## Criteria Overview

Criteria implement conditional logic for workflow transitions. They are used when there are multiple possible transitions from a state and the system needs to determine which transition to take based on entity data or business rules.

## Current Workflow Analysis

Based on the defined workflows, the current system has simple linear workflows without branching transitions that require criteria. All transitions are either:

1. **Automatic single transitions** from initial states
2. **Manual single transitions** triggered by user actions
3. **Self-loop transitions** that stay in the same state

## Potential Future Criteria

While the current workflows don't require criteria, here are examples of criteria that might be needed for future enhancements:

### 1. CartValidationCriterion

**Purpose**: Validate cart state before allowing checkout
**Entity**: Cart
**Usage**: Could be used before OPEN_CHECKOUT transition

**Validation Logic**:
- Cart has at least one line item
- All line items reference valid products
- All products have sufficient stock
- Cart totals are correctly calculated

**Implementation Approach**:
```java
// Simple criterion checking cart validity
{
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
      "jsonPath": "$.totalItems",
      "operation": "GREATER_THAN",
      "value": 0
    }
  ]
}
```

### 2. PaymentValidationCriterion

**Purpose**: Validate payment before processing
**Entity**: Payment
**Usage**: Could be used before AUTO_MARK_PAID transition

**Validation Logic**:
- Payment amount matches cart total
- Cart is in CHECKING_OUT state
- Payment provider is DUMMY

**Implementation Approach**:
```java
// Simple criterion checking payment validity
{
  "type": "group",
  "operator": "AND", 
  "conditions": [
    {
      "type": "simple",
      "jsonPath": "$.provider",
      "operation": "EQUALS",
      "value": "DUMMY"
    },
    {
      "type": "simple",
      "jsonPath": "$.amount",
      "operation": "GREATER_THAN",
      "value": 0
    }
  ]
}
```

### 3. StockAvailabilityCriterion

**Purpose**: Check product stock availability before order creation
**Entity**: Order
**Usage**: Could be used before CREATE_ORDER_FROM_PAID transition

**Validation Logic**:
- All ordered products have sufficient stock
- Products are active and available
- Warehouse has capacity

**Implementation Approach**:
```java
// Function criterion for complex stock validation
{
  "type": "function",
  "function": {
    "name": "OrderStockAvailabilityCriterion",
    "config": {
      "attachEntity": true,
      "calculationNodesTags": "cyoda_application",
      "responseTimeoutMs": 5000,
      "retryPolicy": "FIXED"
    }
  }
}
```

### 4. OrderFulfillmentCriterion

**Purpose**: Determine if order can be fulfilled
**Entity**: Order
**Usage**: Could be used for branching fulfillment strategies

**Validation Logic**:
- Check warehouse capacity
- Validate shipping address
- Confirm product availability
- Check business rules (e.g., restricted regions)

**Implementation Approach**:
```java
// Function criterion for complex fulfillment logic
{
  "type": "function",
  "function": {
    "name": "OrderFulfillmentCriterion", 
    "config": {
      "attachEntity": true,
      "calculationNodesTags": "cyoda_application",
      "responseTimeoutMs": 3000,
      "retryPolicy": "FIXED"
    }
  }
}
```

## Naming Convention

All criteria follow PascalCase naming starting with the entity name:
- Cart criteria: Cart[CriterionName]Criterion
- Payment criteria: Payment[CriterionName]Criterion
- Order criteria: Order[CriterionName]Criterion
- Shipment criteria: Shipment[CriterionName]Criterion

## Implementation Guidelines

### Simple Criteria
Use simple criteria for:
- Field value comparisons
- Null/not null checks
- Numeric range validations
- String pattern matching

### Group Criteria
Use group criteria for:
- Multiple field validations
- Complex logical combinations (AND, OR, NOT)
- Nested condition groups

### Function Criteria
Use function criteria for:
- Cross-entity validations
- Complex business rule evaluations
- External system integrations
- Dynamic condition evaluation

## Best Practices

1. **Keep Simple**: Use simple criteria when possible for better performance
2. **Clear Naming**: Use descriptive names that indicate the validation purpose
3. **Error Messages**: Provide clear error messages for failed criteria
4. **Performance**: Consider caching for frequently evaluated criteria
5. **Testing**: Ensure comprehensive test coverage for all criteria logic

## Future Enhancements

As the system evolves, criteria might be needed for:

1. **Multi-warehouse fulfillment**: Choose optimal warehouse based on location and stock
2. **Payment method selection**: Route to different payment processors
3. **Shipping method selection**: Choose shipping based on location and urgency
4. **Inventory allocation**: Handle backorders and pre-orders
5. **Business rule enforcement**: Apply region-specific rules and restrictions

## Configuration

All function criteria should be configured with:
- **attachEntity**: true (attach entity data to request)
- **calculationNodesTags**: "cyoda_application"
- **responseTimeoutMs**: 3000-5000 (reasonable timeout)
- **retryPolicy**: "FIXED"

## Error Handling

All criteria should:
1. Return clear success/failure outcomes
2. Provide descriptive failure reasons
3. Handle edge cases gracefully
4. Log evaluation results for debugging
5. Fail fast for invalid input data
