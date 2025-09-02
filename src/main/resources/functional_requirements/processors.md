# Processor Requirements

## Overview
This document defines the detailed requirements for all processors in the Cyoda OMS Backend system. Processors implement business logic for workflow transitions and entity operations.

## Product Processors

### ProductActivationProcessor
**Entity:** Product  
**Transition:** ACTIVATE_PRODUCT  
**Input:** Product entity in NONE state  
**Output:** Product entity in ACTIVE state  

**Pseudocode:**
```
process(product):
    validate product has required fields (sku, name, price, category)
    if quantityAvailable is null or negative:
        set quantityAvailable to 0
    set default warehouseId if not provided
    validate product schema completeness
    log product activation event
    return updated product
```

### ProductDiscontinuationProcessor
**Entity:** Product  
**Transition:** DISCONTINUE_PRODUCT  
**Input:** Product entity in ACTIVE state  
**Output:** Product entity in DISCONTINUED state  

**Pseudocode:**
```
process(product):
    validate product is in ACTIVE state
    check if product has pending orders
    if pending orders exist:
        log warning about discontinuing product with pending orders
    set quantityAvailable to 0
    log product discontinuation event
    return updated product
```

## Cart Processors

### CartCreationProcessor
**Entity:** Cart  
**Transition:** CREATE_ON_FIRST_ADD  
**Input:** Cart entity in NONE state with first item  
**Output:** Cart entity in NEW state  

**Pseudocode:**
```
process(cart):
    generate unique cartId
    validate first item has sku and qty > 0
    get product details from Product entity by sku
    if product not found or not ACTIVE:
        throw error "Product not available"
    create cart line with product details
    calculate totalItems and grandTotal
    set createdAt and updatedAt timestamps
    return updated cart
```

### CartActivationProcessor
**Entity:** Cart  
**Transition:** ACTIVATE_CART  
**Input:** Cart entity in NEW state  
**Output:** Cart entity in ACTIVE state  

**Pseudocode:**
```
process(cart):
    validate cart has at least one line item
    recalculate totals
    set updatedAt timestamp
    return updated cart
```

### CartAddItemProcessor
**Entity:** Cart  
**Transition:** ADD_ITEM  
**Input:** Cart entity in ACTIVE state, item to add  
**Output:** Cart entity in ACTIVE state with updated items  

**Pseudocode:**
```
process(cart, itemToAdd):
    validate item has sku and qty > 0
    get product details from Product entity by sku
    if product not found or not ACTIVE:
        throw error "Product not available"
    find existing line with same sku
    if existing line found:
        increment quantity by itemToAdd.qty
    else:
        add new line with product details
    recalculate totalItems and grandTotal
    set updatedAt timestamp
    return updated cart
```

### CartUpdateItemProcessor
**Entity:** Cart  
**Transition:** UPDATE_ITEM  
**Input:** Cart entity in ACTIVE state, item to update  
**Output:** Cart entity in ACTIVE state with updated items  

**Pseudocode:**
```
process(cart, itemToUpdate):
    validate item has sku
    find existing line with sku
    if line not found:
        throw error "Item not found in cart"
    if itemToUpdate.qty <= 0:
        remove line from cart
    else:
        update line quantity to itemToUpdate.qty
    recalculate totalItems and grandTotal
    set updatedAt timestamp
    return updated cart
```

### CartRemoveItemProcessor
**Entity:** Cart  
**Transition:** REMOVE_ITEM  
**Input:** Cart entity in ACTIVE state, item to remove  
**Output:** Cart entity in ACTIVE state with item removed  

**Pseudocode:**
```
process(cart, itemToRemove):
    validate item has sku
    find existing line with sku
    if line not found:
        throw error "Item not found in cart"
    remove line from cart
    recalculate totalItems and grandTotal
    set updatedAt timestamp
    return updated cart
```

### CartCheckoutProcessor
**Entity:** Cart  
**Transition:** OPEN_CHECKOUT  
**Input:** Cart entity in ACTIVE state  
**Output:** Cart entity in CHECKING_OUT state  

**Pseudocode:**
```
process(cart):
    validate cart has at least one line item
    validate all products in cart are still ACTIVE and available
    recalculate totals with current product prices
    set updatedAt timestamp
    return updated cart
```

### CartConversionProcessor
**Entity:** Cart  
**Transition:** CHECKOUT  
**Input:** Cart entity in CHECKING_OUT state  
**Output:** Cart entity in CONVERTED state  

**Pseudocode:**
```
process(cart):
    validate cart has guestContact information
    validate payment is in PAID state
    set updatedAt timestamp
    return updated cart
```

## Payment Processors

### PaymentInitiationProcessor
**Entity:** Payment  
**Transition:** START_DUMMY_PAYMENT  
**Input:** Payment entity in NONE state  
**Output:** Payment entity in INITIATED state  

**Pseudocode:**
```
process(payment):
    generate unique paymentId
    validate cartId exists and cart is in CHECKING_OUT state
    get cart total amount
    set payment amount to cart grandTotal
    set provider to "DUMMY"
    set createdAt and updatedAt timestamps
    schedule auto-approval after 3 seconds
    return updated payment
```

### PaymentAutoApprovalProcessor
**Entity:** Payment  
**Transition:** AUTO_MARK_PAID  
**Input:** Payment entity in INITIATED state  
**Output:** Payment entity in PAID state  

**Pseudocode:**
```
process(payment):
    validate payment is in INITIATED state
    validate 3 seconds have passed since creation
    set updatedAt timestamp
    log payment approval event
    return updated payment
```

### PaymentFailureProcessor
**Entity:** Payment  
**Transition:** MARK_FAILED  
**Input:** Payment entity in INITIATED state  
**Output:** Payment entity in FAILED state  

**Pseudocode:**
```
process(payment):
    validate payment is in INITIATED state
    set updatedAt timestamp
    log payment failure event
    return updated payment
```

### PaymentCancellationProcessor
**Entity:** Payment  
**Transition:** CANCEL_PAYMENT  
**Input:** Payment entity in INITIATED state  
**Output:** Payment entity in CANCELED state  

**Pseudocode:**
```
process(payment):
    validate payment is in INITIATED state
    set updatedAt timestamp
    log payment cancellation event
    return updated payment
```

## Order Processors

### OrderCreationProcessor
**Entity:** Order  
**Transition:** CREATE_ORDER_FROM_PAID  
**Input:** Order entity in NONE state, cartId, paymentId  
**Output:** Order entity in WAITING_TO_FULFILL state  
**Other Entity Updates:** Product (decrement stock), Shipment (create new)  

**Pseudocode:**
```
process(order, cartId, paymentId):
    validate payment is in PAID state
    get cart entity by cartId
    validate cart is in CHECKING_OUT state
    generate unique orderId and short ULID orderNumber
    
    for each cart line:
        get product by sku
        validate product is ACTIVE and has sufficient stock
        decrement product.quantityAvailable by line.qty
        update product entity (no transition)
        create order line with snapshot data
    
    copy cart.guestContact to order.guestContact
    calculate order totals
    set createdAt and updatedAt timestamps
    
    create shipment entity:
        generate unique shipmentId
        set orderId reference
        copy order lines to shipment lines
        trigger shipment CREATE_SHIPMENT transition
    
    trigger cart CHECKOUT transition
    return updated order
```

### OrderPickingProcessor
**Entity:** Order
**Transition:** START_PICKING
**Input:** Order entity in WAITING_TO_FULFILL state
**Output:** Order entity in PICKING state

**Pseudocode:**
```
process(order):
    validate order is in WAITING_TO_FULFILL state
    validate all order lines have products in stock
    set updatedAt timestamp
    return updated order
```

### OrderReadyToSendProcessor
**Entity:** Order
**Transition:** READY_TO_SEND
**Input:** Order entity in PICKING state
**Output:** Order entity in WAITING_TO_SEND state

**Pseudocode:**
```
process(order):
    validate order is in PICKING state
    get associated shipment by orderId
    trigger shipment READY_TO_SEND transition
    set updatedAt timestamp
    return updated order
```

### OrderSentProcessor
**Entity:** Order
**Transition:** MARK_SENT
**Input:** Order entity in WAITING_TO_SEND state
**Output:** Order entity in SENT state

**Pseudocode:**
```
process(order):
    validate order is in WAITING_TO_SEND state
    get associated shipment by orderId
    trigger shipment MARK_SENT transition
    set updatedAt timestamp
    return updated order
```

### OrderDeliveredProcessor
**Entity:** Order
**Transition:** MARK_DELIVERED
**Input:** Order entity in SENT state
**Output:** Order entity in DELIVERED state

**Pseudocode:**
```
process(order):
    validate order is in SENT state
    get associated shipment by orderId
    trigger shipment MARK_DELIVERED transition
    set updatedAt timestamp
    return updated order
```

## Shipment Processors

### ShipmentCreationProcessor
**Entity:** Shipment
**Transition:** CREATE_SHIPMENT
**Input:** Shipment entity in NONE state
**Output:** Shipment entity in PICKING state

**Pseudocode:**
```
process(shipment):
    validate shipment has orderId
    validate order exists and is in WAITING_TO_FULFILL state
    get order entity by orderId

    for each order line:
        create shipment line:
            set sku from order line
            set qtyOrdered from order line qty
            set qtyPicked to 0
            set qtyShipped to 0

    set createdAt and updatedAt timestamps
    return updated shipment
```

### ShipmentReadyProcessor
**Entity:** Shipment
**Transition:** READY_TO_SEND
**Input:** Shipment entity in PICKING state
**Output:** Shipment entity in WAITING_TO_SEND state

**Pseudocode:**
```
process(shipment):
    validate shipment is in PICKING state

    for each shipment line:
        if qtyPicked is 0:
            set qtyPicked to qtyOrdered (assume full pick)
        validate qtyPicked <= qtyOrdered

    set updatedAt timestamp
    return updated shipment
```

### ShipmentSentProcessor
**Entity:** Shipment
**Transition:** MARK_SENT
**Input:** Shipment entity in WAITING_TO_SEND state
**Output:** Shipment entity in SENT state

**Pseudocode:**
```
process(shipment):
    validate shipment is in WAITING_TO_SEND state

    for each shipment line:
        set qtyShipped to qtyPicked

    set updatedAt timestamp
    return updated shipment
```

### ShipmentDeliveredProcessor
**Entity:** Shipment
**Transition:** MARK_DELIVERED
**Input:** Shipment entity in SENT state
**Output:** Shipment entity in DELIVERED state

**Pseudocode:**
```
process(shipment):
    validate shipment is in SENT state
    set updatedAt timestamp
    return updated shipment
```

## Processor Implementation Notes

### Error Handling
- All processors should validate input entity state
- Processors should throw descriptive errors for invalid operations
- Failed processors should not change entity state

### Entity Updates
- Processors that update other entities should specify transition names
- Use null transition when updating entity fields without state change
- Always validate entity existence before updates

### Timing and Scheduling
- PaymentAutoApprovalProcessor uses 3-second delay
- Use appropriate scheduling mechanism for timed operations
- Ensure idempotent processing for retry scenarios

### Stock Management
- OrderCreationProcessor decrements Product.quantityAvailable
- No stock reservations are implemented (immediate decrement policy)
- Validate sufficient stock before decrementing
