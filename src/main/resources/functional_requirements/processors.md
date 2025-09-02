# Processor Requirements

## Overview

This document defines the detailed requirements for all processors in the Cyoda OMS Backend system. Processors implement the business logic for workflow transitions and entity manipulations.

## Processor Definitions

### 1. CartCreateProcessor

**Entity**: Cart
**Transition**: CREATE_ON_FIRST_ADD
**Purpose**: Creates a new cart and adds the first line item

**Input Data**:
- `sku` (string): Product SKU to add
- `qty` (number): Quantity to add

**Expected Output**: 
- Creates new Cart entity with generated cartId
- Adds first line item
- Sets initial totals

**Pseudocode**:
```
BEGIN CartCreateProcessor
    GENERATE unique cartId
    FETCH product by sku from Product entity
    IF product not found THEN
        THROW error "Product not found"
    END IF
    
    CREATE cart entity WITH
        cartId = generated cartId
        lines = [
            {
                sku = input.sku,
                name = product.name,
                price = product.price,
                qty = input.qty
            }
        ]
        totalItems = input.qty
        grandTotal = product.price * input.qty
        createdAt = current timestamp
        updatedAt = current timestamp
    END CREATE
    
    SAVE cart entity
    RETURN cart entity
END
```

### 2. CartRecalculateTotalsProcessor

**Entity**: Cart
**Transitions**: ACTIVATE_CART, ADD_ITEM, UPDATE_ITEM, REMOVE_ITEM
**Purpose**: Recalculates cart totals after line item changes

**Input Data**:
- Cart entity (current state)
- Optional: line item changes

**Expected Output**:
- Updates Cart entity with recalculated totals
- Updates timestamps

**Pseudocode**:
```
BEGIN CartRecalculateTotalsProcessor
    GET cart entity from input
    
    INITIALIZE totalItems = 0
    INITIALIZE grandTotal = 0
    
    FOR each line in cart.lines DO
        totalItems = totalItems + line.qty
        grandTotal = grandTotal + (line.price * line.qty)
    END FOR
    
    UPDATE cart entity WITH
        totalItems = totalItems
        grandTotal = grandTotal
        updatedAt = current timestamp
    END UPDATE
    
    SAVE cart entity
    RETURN cart entity
END
```

### 3. PaymentCreateDummyProcessor

**Entity**: Payment
**Transition**: START_DUMMY_PAYMENT
**Purpose**: Creates a dummy payment in INITIATED state

**Input Data**:
- `cartId` (string): Cart identifier
- `amount` (number): Payment amount

**Expected Output**:
- Creates new Payment entity
- Schedules auto-payment after 3 seconds

**Pseudocode**:
```
BEGIN PaymentCreateDummyProcessor
    GENERATE unique paymentId
    
    CREATE payment entity WITH
        paymentId = generated paymentId
        cartId = input.cartId
        amount = input.amount
        provider = "DUMMY"
        createdAt = current timestamp
        updatedAt = current timestamp
    END CREATE
    
    SAVE payment entity
    SCHEDULE PaymentAutoMarkPaidProcessor after 3 seconds
    RETURN payment entity
END
```

### 4. PaymentAutoMarkPaidProcessor

**Entity**: Payment
**Transition**: AUTO_MARK_PAID
**Purpose**: Automatically marks payment as paid after 3 seconds

**Input Data**:
- Payment entity (current state)

**Expected Output**:
- Updates Payment entity state to PAID
- Triggers order creation process

**Pseudocode**:
```
BEGIN PaymentAutoMarkPaidProcessor
    GET payment entity from input
    
    IF payment.meta.state != "INITIATED" THEN
        THROW error "Payment not in INITIATED state"
    END IF
    
    UPDATE payment entity WITH
        updatedAt = current timestamp
    END UPDATE
    
    SAVE payment entity
    TRIGGER OrderCreateFromPaidProcessor with payment
    RETURN payment entity
END
```

### 5. OrderCreateFromPaidProcessor

**Entity**: Order
**Transition**: CREATE_ORDER_FROM_PAID
**Purpose**: Creates order from paid cart, decrements stock, creates shipment

**Input Data**:
- `paymentId` (string): Payment identifier
- `cartId` (string): Cart identifier

**Expected Output**:
- Creates new Order entity
- Decrements Product.quantityAvailable for each line item
- Creates associated Shipment entity
- Updates Cart state to CONVERTED

**Pseudocode**:
```
BEGIN OrderCreateFromPaidProcessor
    GET payment entity by paymentId
    GET cart entity by cartId
    
    IF payment.meta.state != "PAID" THEN
        THROW error "Payment not paid"
    END IF
    
    IF cart.meta.state != "CHECKING_OUT" THEN
        THROW error "Cart not in checkout state"
    END IF
    
    GENERATE unique orderId
    GENERATE short ULID for orderNumber
    
    INITIALIZE orderLines = []
    INITIALIZE shipmentLines = []
    INITIALIZE itemsTotal = 0
    INITIALIZE grandTotal = 0
    
    FOR each line in cart.lines DO
        GET product by line.sku
        IF product.quantityAvailable < line.qty THEN
            THROW error "Insufficient stock for " + line.sku
        END IF
        
        // Decrement stock
        UPDATE product WITH
            quantityAvailable = product.quantityAvailable - line.qty
            updatedAt = current timestamp
        END UPDATE
        SAVE product (transition = null)
        
        // Add to order lines
        ADD to orderLines {
            sku = line.sku,
            name = line.name,
            unitPrice = line.price,
            qty = line.qty,
            lineTotal = line.price * line.qty
        }
        
        // Add to shipment lines
        ADD to shipmentLines {
            sku = line.sku,
            qtyOrdered = line.qty,
            qtyPicked = 0,
            qtyShipped = 0
        }
        
        itemsTotal = itemsTotal + line.qty
        grandTotal = grandTotal + (line.price * line.qty)
    END FOR
    
    CREATE order entity WITH
        orderId = generated orderId
        orderNumber = generated orderNumber
        lines = orderLines
        totals = {
            items = itemsTotal,
            grand = grandTotal
        }
        guestContact = cart.guestContact
        createdAt = current timestamp
        updatedAt = current timestamp
    END CREATE
    
    SAVE order entity
    
    GENERATE unique shipmentId
    CREATE shipment entity WITH
        shipmentId = generated shipmentId
        orderId = orderId
        lines = shipmentLines
        createdAt = current timestamp
        updatedAt = current timestamp
    END CREATE
    
    SAVE shipment entity (transition = "CREATE_SHIPMENT")
    
    UPDATE cart WITH
        updatedAt = current timestamp
    END UPDATE
    SAVE cart (transition = "CHECKOUT")
    
    RETURN order entity
END
```

### 6. OrderReadyToSendProcessor

**Entity**: Order
**Transition**: READY_TO_SEND
**Purpose**: Marks order ready for shipment

**Input Data**:
- Order entity (current state)

**Expected Output**:
- Updates Order timestamps
- Triggers shipment ready transition

**Pseudocode**:
```
BEGIN OrderReadyToSendProcessor
    GET order entity from input
    
    UPDATE order entity WITH
        updatedAt = current timestamp
    END UPDATE
    
    SAVE order entity
    
    GET shipment by orderId
    SAVE shipment (transition = "READY_TO_SEND")
    
    RETURN order entity
END
```

### 7. OrderMarkSentProcessor

**Entity**: Order
**Transition**: MARK_SENT
**Purpose**: Marks order as sent

**Input Data**:
- Order entity (current state)

**Expected Output**:
- Updates Order timestamps
- Triggers shipment sent transition

**Pseudocode**:
```
BEGIN OrderMarkSentProcessor
    GET order entity from input
    
    UPDATE order entity WITH
        updatedAt = current timestamp
    END UPDATE
    
    SAVE order entity
    
    GET shipment by orderId
    SAVE shipment (transition = "MARK_SENT")
    
    RETURN order entity
END
```

### 8. OrderMarkDeliveredProcessor

**Entity**: Order
**Transition**: MARK_DELIVERED
**Purpose**: Marks order as delivered

**Input Data**:
- Order entity (current state)

**Expected Output**:
- Updates Order timestamps
- Triggers shipment delivered transition

**Pseudocode**:
```
BEGIN OrderMarkDeliveredProcessor
    GET order entity from input
    
    UPDATE order entity WITH
        updatedAt = current timestamp
    END UPDATE
    
    SAVE order entity
    
    GET shipment by orderId
    SAVE shipment (transition = "MARK_DELIVERED")
    
    RETURN order entity
END
```

### 9. ShipmentReadyToSendProcessor

**Entity**: Shipment
**Transition**: READY_TO_SEND
**Purpose**: Updates shipment quantities when ready to send

**Input Data**:
- Shipment entity (current state)

**Expected Output**:
- Updates qtyPicked for all lines
- Updates timestamps

**Pseudocode**:
```
BEGIN ShipmentReadyToSendProcessor
    GET shipment entity from input
    
    FOR each line in shipment.lines DO
        line.qtyPicked = line.qtyOrdered
    END FOR
    
    UPDATE shipment entity WITH
        lines = updated lines
        updatedAt = current timestamp
    END UPDATE
    
    SAVE shipment entity
    RETURN shipment entity
END
```

### 10. ShipmentMarkSentProcessor

**Entity**: Shipment
**Transition**: MARK_SENT
**Purpose**: Updates shipment quantities when sent

**Input Data**:
- Shipment entity (current state)

**Expected Output**:
- Updates qtyShipped for all lines
- Updates timestamps

**Pseudocode**:
```
BEGIN ShipmentMarkSentProcessor
    GET shipment entity from input
    
    FOR each line in shipment.lines DO
        line.qtyShipped = line.qtyPicked
    END FOR
    
    UPDATE shipment entity WITH
        lines = updated lines
        updatedAt = current timestamp
    END UPDATE
    
    SAVE shipment entity
    RETURN shipment entity
END
```

### 11. ShipmentMarkDeliveredProcessor

**Entity**: Shipment
**Transition**: MARK_DELIVERED
**Purpose**: Finalizes shipment delivery

**Input Data**:
- Shipment entity (current state)

**Expected Output**:
- Updates timestamps
- Completes shipment lifecycle

**Pseudocode**:
```
BEGIN ShipmentMarkDeliveredProcessor
    GET shipment entity from input
    
    UPDATE shipment entity WITH
        updatedAt = current timestamp
    END UPDATE
    
    SAVE shipment entity
    RETURN shipment entity
END
```

## Error Handling Guidelines

1. **Validation**: All processors should validate input data before processing
2. **Stock Checks**: Verify product availability before decrementing quantities
3. **State Validation**: Ensure entities are in correct states before processing
4. **Rollback**: Implement compensation logic for failed multi-step operations
5. **Logging**: Log all significant operations and errors for debugging
