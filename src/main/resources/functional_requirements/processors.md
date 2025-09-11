# Processor Requirements

## Overview
This document defines the detailed requirements for all processors in the Cyoda OMS Backend system. Processors implement business logic during workflow transitions and can read the current entity, update other entities, but cannot update the current entity being processed.

## Processor Definitions

### 1. CartRecalculateTotalsProcessor

**Processor Name**: CartRecalculateTotalsProcessor
**Entity**: Cart
**Transitions**: ADD_FIRST_ITEM, MODIFY_ITEMS
**Description**: Recalculates cart totals when items are added, updated, or removed.

**Expected Input Data**:
- Current Cart entity with updated lines array
- Each line contains: sku, name, price, qty

**Business Logic**:
```
PROCESS CartRecalculateTotalsProcessor:
  INPUT: Cart entity with lines
  
  SET totalItems = 0
  SET grandTotal = 0.0
  
  FOR each line in cart.lines:
    VALIDATE line.sku exists in Product catalog
    IF sku not found:
      THROW validation error "Product not found: {sku}"
    
    GET product = entityService.get(Product, line.sku)
    SET line.price = product.price
    SET line.name = product.name
    SET lineTotal = line.price * line.qty
    
    ADD line.qty to totalItems
    ADD lineTotal to grandTotal
  
  SET cart.totalItems = totalItems
  SET cart.grandTotal = grandTotal
  SET cart.updatedAt = current timestamp
  
  RETURN updated cart entity
```

**Expected Output**:
- Cart entity with recalculated totalItems and grandTotal
- Updated line prices and names from current product data
- Updated timestamp

**Other Entity Updates**: None

---

### 2. PaymentCreateDummyProcessor

**Processor Name**: PaymentCreateDummyProcessor
**Entity**: Payment
**Transitions**: START_PAYMENT
**Description**: Creates a dummy payment record in INITIATED state.

**Expected Input Data**:
- Payment entity with cartId and amount

**Business Logic**:
```
PROCESS PaymentCreateDummyProcessor:
  INPUT: Payment entity with cartId, amount
  
  VALIDATE cartId references existing Cart
  GET cart = entityService.get(Cart, payment.cartId)
  IF cart not found:
    THROW validation error "Cart not found: {cartId}"
  
  VALIDATE cart.grandTotal equals payment.amount
  IF amounts don't match:
    THROW validation error "Payment amount doesn't match cart total"
  
  SET payment.provider = "DUMMY"
  SET payment.createdAt = current timestamp
  SET payment.updatedAt = current timestamp
  
  RETURN payment entity
```

**Expected Output**:
- Payment entity with provider set to "DUMMY"
- Validated against cart total
- Timestamps set

**Other Entity Updates**: None

---

### 3. PaymentAutoMarkPaidProcessor

**Processor Name**: PaymentAutoMarkPaidProcessor
**Entity**: Payment
**Transitions**: AUTO_APPROVE
**Description**: Automatically marks payment as paid after approximately 3 seconds delay.

**Expected Input Data**:
- Payment entity in INITIATED state

**Business Logic**:
```
PROCESS PaymentAutoMarkPaidProcessor:
  INPUT: Payment entity in INITIATED state
  
  SLEEP for 3 seconds (simulate processing time)
  
  SET payment.updatedAt = current timestamp
  
  RETURN payment entity
  
  NOTE: State transition to PAID is handled by workflow engine
```

**Expected Output**:
- Payment entity with updated timestamp
- Ready for state transition to PAID

**Other Entity Updates**: None

---

### 4. OrderCreateFromPaidProcessor

**Processor Name**: OrderCreateFromPaidProcessor
**Entity**: Order
**Transitions**: CREATE_ORDER
**Description**: Creates order from paid cart, decrements product stock, and creates associated shipment.

**Expected Input Data**:
- Order entity with basic information
- Associated cartId and paymentId from context

**Business Logic**:
```
PROCESS OrderCreateFromPaidProcessor:
  INPUT: Order entity, cartId, paymentId
  
  // Validate payment is PAID
  GET payment = entityService.get(Payment, paymentId)
  VALIDATE payment.state == "PAID"
  
  // Get cart data
  GET cart = entityService.get(Cart, cartId)
  VALIDATE cart.state == "CHECKING_OUT"
  
  // Generate short ULID for order number
  SET order.orderNumber = generateShortULID()
  
  // Snapshot cart data to order
  SET order.lines = []
  FOR each cartLine in cart.lines:
    CREATE orderLine:
      sku = cartLine.sku
      name = cartLine.name
      unitPrice = cartLine.price
      qty = cartLine.qty
      lineTotal = cartLine.price * cartLine.qty
    ADD orderLine to order.lines
  
  SET order.totals = {
    items: cart.totalItems,
    grand: cart.grandTotal
  }
  SET order.guestContact = cart.guestContact
  SET order.createdAt = current timestamp
  SET order.updatedAt = current timestamp
  
  // Decrement product stock
  FOR each line in order.lines:
    GET product = entityService.get(Product, line.sku)
    SET product.quantityAvailable = product.quantityAvailable - line.qty
    VALIDATE product.quantityAvailable >= 0
    entityService.update(Product, product, null) // No transition
  
  // Create shipment
  CREATE shipment:
    shipmentId = generateUUID()
    orderId = order.orderId
    lines = []
    FOR each orderLine in order.lines:
      CREATE shipmentLine:
        sku = orderLine.sku
        qtyOrdered = orderLine.qty
        qtyPicked = 0
        qtyShipped = 0
      ADD shipmentLine to shipment.lines
    createdAt = current timestamp
    updatedAt = current timestamp
  
  entityService.create(Shipment, shipment, "CREATE_SHIPMENT")
  
  // Update cart to CONVERTED
  entityService.update(Cart, cart, "COMPLETE_CHECKOUT")
  
  RETURN order entity
```

**Expected Output**:
- Order entity with complete order data
- Short ULID order number
- Snapshotted cart data

**Other Entity Updates**:
- Product entities: Decrement quantityAvailable (no transition)
- Shipment entity: Create new shipment with CREATE_SHIPMENT transition
- Cart entity: Update to CONVERTED state with COMPLETE_CHECKOUT transition

---

### 5. ShipmentUpdateOrderStatusProcessor

**Processor Name**: ShipmentUpdateOrderStatusProcessor
**Entity**: Shipment
**Transitions**: READY_TO_SEND, MARK_SENT, MARK_DELIVERED
**Description**: Updates corresponding order status when shipment status changes.

**Expected Input Data**:
- Shipment entity with orderId
- Target shipment state from transition

**Business Logic**:
```
PROCESS ShipmentUpdateOrderStatusProcessor:
  INPUT: Shipment entity, target state
  
  GET order = entityService.get(Order, shipment.orderId)
  
  // Map shipment state to order transition
  SET orderTransition = null
  IF target state == "WAITING_TO_SEND":
    SET orderTransition = "READY_TO_SEND"
  ELSE IF target state == "SENT":
    SET orderTransition = "MARK_SENT"
  ELSE IF target state == "DELIVERED":
    SET orderTransition = "MARK_DELIVERED"
  
  IF orderTransition is not null:
    entityService.update(Order, order, orderTransition)
  
  SET shipment.updatedAt = current timestamp
  
  RETURN shipment entity
```

**Expected Output**:
- Shipment entity with updated timestamp

**Other Entity Updates**:
- Order entity: Update state based on shipment transition

## Processor Implementation Notes

### Error Handling
All processors should implement proper error handling and validation:
- Validate input data before processing
- Check entity existence before updates
- Ensure business rule compliance
- Throw meaningful error messages

### Transaction Boundaries
Processors should be designed to work within transaction boundaries:
- All entity updates should succeed or fail together
- Use appropriate error handling for rollback scenarios

### Performance Considerations
- Minimize entity service calls
- Batch operations where possible
- Avoid unnecessary data retrieval

### State Management
- Processors cannot update the current entity's state directly
- State transitions are managed by the workflow engine
- Use transition names when updating other entities

### Naming Conventions
- Processor names follow pattern: {Entity}{Action}Processor
- Use PascalCase for processor names
- Start with entity name for clear organization
