# Processor Requirements

## Overview
This document defines the detailed requirements for all processors in the Cyoda OMS Backend system. Processors implement the business logic for workflow transitions and entity operations.

**Important Notes**:
- Processors cannot modify the current entity being processed
- Processors can read current entity data and modify other entities
- Use EntityService to interact with other entities
- Entity state is managed automatically by the workflow system

## Cart Processors

### 1. CartCreateProcessor

**Entity**: Cart
**Transition**: CREATE_ON_FIRST_ADD
**Purpose**: Initialize new cart with first item

**Input Data**:
- Cart entity (empty/new)
- First item details: sku, qty

**Expected Output**:
- Cart entity with first line item added
- No transition (null) - automatic progression to ACTIVATE_CART

**Pseudocode**:
```
PROCESS CartCreateProcessor:
  INPUT: cart entity, item data (sku, qty)
  
  1. Get product by sku using EntityService
  2. Validate product exists and has sufficient stock
  3. Create cart line item:
     - sku = product.sku
     - name = product.name
     - price = product.price
     - qty = requested qty
  4. Add line item to cart.lines array
  5. Set cart.totalItems = qty
  6. Set cart.grandTotal = price * qty
  7. Return updated cart
  
  TRANSITION: null (automatic progression)
```

### 2. CartRecalculateTotalsProcessor

**Entity**: Cart
**Transitions**: ACTIVATE_CART, ADD_ITEM, UPDATE_ITEM, REMOVE_ITEM
**Purpose**: Recalculate cart totals after line item changes

**Input Data**:
- Cart entity with modified lines
- Operation type (add/update/remove)
- Item details if applicable

**Expected Output**:
- Cart entity with recalculated totals
- No transition (null) - stays in current state

**Pseudocode**:
```
PROCESS CartRecalculateTotalsProcessor:
  INPUT: cart entity, operation details
  
  1. Initialize totals: totalItems = 0, grandTotal = 0
  2. FOR each line in cart.lines:
     a. Add line.qty to totalItems
     b. Add (line.price * line.qty) to grandTotal
  3. Update cart.totalItems = totalItems
  4. Update cart.grandTotal = grandTotal
  5. Set cart.updatedAt = current timestamp
  6. Return updated cart
  
  TRANSITION: null (stays in current state)
```

## Payment Processors

### 3. PaymentCreateDummyProcessor

**Entity**: Payment
**Transition**: START_DUMMY_PAYMENT
**Purpose**: Create dummy payment record

**Input Data**:
- Payment entity (new)
- Cart ID and amount

**Expected Output**:
- Payment entity initialized
- No transition (null) - automatic progression to AUTO_MARK_PAID

**Pseudocode**:
```
PROCESS PaymentCreateDummyProcessor:
  INPUT: payment entity, cartId, amount
  
  1. Set payment.cartId = cartId
  2. Set payment.amount = amount
  3. Set payment.provider = "DUMMY"
  4. Set payment.createdAt = current timestamp
  5. Return initialized payment
  
  TRANSITION: null (automatic progression)
```

### 4. PaymentAutoMarkPaidProcessor

**Entity**: Payment
**Transition**: AUTO_MARK_PAID
**Purpose**: Automatically mark payment as paid after delay

**Input Data**:
- Payment entity in INITIATED state

**Expected Output**:
- Payment marked as paid
- No transition (null) - automatic progression to PAID state

**Pseudocode**:
```
PROCESS PaymentAutoMarkPaidProcessor:
  INPUT: payment entity
  
  1. Wait for 3 seconds (simulate processing time)
  2. Set payment.updatedAt = current timestamp
  3. Log payment completion
  4. Return payment entity
  
  TRANSITION: null (automatic progression to PAID)
```

## Order Processors

### 5. OrderCreateFromPaidProcessor

**Entity**: Order
**Transition**: CREATE_ORDER_FROM_PAID
**Purpose**: Create order from paid cart, decrement stock, create shipment

**Input Data**:
- Order entity (new)
- Cart ID and Payment ID

**Expected Output**:
- Order entity created with cart data snapshot
- Product stock decremented
- Shipment entity created
- No transition (null) - automatic progression to WAITING_TO_FULFILL

**Pseudocode**:
```
PROCESS OrderCreateFromPaidProcessor:
  INPUT: order entity, cartId, paymentId
  
  1. Get cart entity by cartId using EntityService
  2. Get payment entity by paymentId using EntityService
  3. Validate payment.status = PAID and payment.cartId = cartId
  
  4. Generate short ULID for orderNumber
  5. Snapshot cart data to order:
     - Copy cart.lines to order.lines (with lineTotal calculation)
     - Copy cart.guestContact to order.guestContact
     - Set order.totals from cart totals
  
  6. FOR each line in order.lines:
     a. Get product by line.sku using EntityService
     b. Validate product.quantityAvailable >= line.qty
     c. Decrement product.quantityAvailable by line.qty
     d. Update product using EntityService with null transition
  
  7. Create shipment entity:
     a. Generate shipmentId
     b. Set shipment.orderId = order.orderId
     c. Copy order.lines to shipment.lines (with qtyOrdered, qtyPicked=0, qtyShipped=0)
     d. Save shipment using EntityService with CREATE_SHIPMENT transition
  
  8. Return order entity
  
  TRANSITION: null (automatic progression to WAITING_TO_FULFILL)
```

## Shipment Processors

### 6. ShipmentCreateProcessor

**Entity**: Shipment
**Transition**: CREATE_SHIPMENT
**Purpose**: Initialize shipment with order line items

**Input Data**:
- Shipment entity (new)
- Order ID

**Expected Output**:
- Shipment entity initialized with order lines
- No transition (null) - automatic progression to PICKING

**Pseudocode**:
```
PROCESS ShipmentCreateProcessor:
  INPUT: shipment entity, orderId
  
  1. Get order entity by orderId using EntityService
  2. FOR each line in order.lines:
     a. Create shipment line:
        - sku = line.sku
        - qtyOrdered = line.qty
        - qtyPicked = 0
        - qtyShipped = 0
     b. Add to shipment.lines
  3. Set shipment.createdAt = current timestamp
  4. Return shipment entity
  
  TRANSITION: null (automatic progression to PICKING)
```

### 7. ShipmentUpdateOrderStateProcessor

**Entity**: Shipment
**Transitions**: COMPLETE_PICKING, DISPATCH_SHIPMENT, CONFIRM_DELIVERY
**Purpose**: Update shipment quantities and synchronize order state

**Input Data**:
- Shipment entity
- Updated quantities (if applicable)
- Target state

**Expected Output**:
- Shipment entity updated
- Order entity state synchronized
- No transition (null) - automatic state progression

**Pseudocode**:
```
PROCESS ShipmentUpdateOrderStateProcessor:
  INPUT: shipment entity, operation type, quantity updates
  
  1. Get order entity by shipment.orderId using EntityService
  
  2. IF operation = COMPLETE_PICKING:
     a. Update shipment.lines with picked quantities
     b. Update order state to WAITING_TO_SEND using EntityService with READY_TO_SEND transition
  
  3. IF operation = DISPATCH_SHIPMENT:
     a. Update shipment.lines with shipped quantities
     b. Update order state to SENT using EntityService with MARK_SENT transition
  
  4. IF operation = CONFIRM_DELIVERY:
     a. Update order state to DELIVERED using EntityService with MARK_DELIVERED transition
  
  5. Set shipment.updatedAt = current timestamp
  6. Return shipment entity
  
  TRANSITION: null (automatic state progression)
```

## Processor Implementation Guidelines

### Error Handling
- Validate all input data before processing
- Check entity states and business rules
- Throw appropriate exceptions for validation failures
- Log all significant operations

### Performance Considerations
- Use EntityService efficiently (prefer getById over findByBusinessId when possible)
- Batch operations when updating multiple entities
- Minimize external service calls

### Transaction Management
- Processors run within workflow transactions
- Use SYNC execution mode for critical operations
- Use ASYNC_NEW_TX for independent operations

### State Management
- Never modify the current entity's workflow state directly
- Use EntityService with appropriate transition names for other entities
- Use null transition when no state change is needed

## Processor Naming Convention
- Format: `{Entity}{Action}Processor`
- Examples: CartRecalculateTotalsProcessor, PaymentAutoMarkPaidProcessor
- Must match the processor name in workflow configuration
