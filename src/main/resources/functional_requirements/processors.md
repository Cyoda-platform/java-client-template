# Processor Requirements

## Overview
This document defines the processor requirements for the Cyoda OMS Backend system. Processors implement business logic for workflow transitions.

## Cart Processors

### CartActivateProcessor
**Entity**: Cart
**Transition**: activate_cart (new → active)
**Input**: Cart entity in 'new' state
**Purpose**: Activates a newly created cart

**Pseudocode**:
```
process(cart):
    validate cart exists and is in 'new' state
    set cart as active
    recalculate totals
    update timestamps
    return updated cart
```

**Output**: Cart entity ready for item operations

### CartAddItemProcessor  
**Entity**: Cart
**Transition**: add_item (active → active)
**Input**: Cart entity + item details (sku, qty)
**Purpose**: Adds or increments item quantity in cart

**Pseudocode**:
```
process(cart, itemData):
    validate cart is in 'active' state
    validate product exists and has sufficient stock
    
    if item already exists in cart:
        increment quantity by requested amount
    else:
        fetch product details (name, price)
        add new line item to cart
    
    recalculate cart totals (totalItems, grandTotal)
    update timestamps
    return updated cart
```

**Output**: Cart entity with updated line items and totals

### CartUpdateItemProcessor
**Entity**: Cart  
**Transition**: update_item (active → active)
**Input**: Cart entity + item update (sku, new_qty)
**Purpose**: Updates item quantity or removes if qty=0

**Pseudocode**:
```
process(cart, itemData):
    validate cart is in 'active' state
    validate item exists in cart
    
    if new_qty > 0:
        validate product has sufficient stock
        update line item quantity
    else:
        remove line item from cart
    
    recalculate cart totals
    update timestamps
    return updated cart
```

**Output**: Cart entity with updated line items and totals

### CartRemoveItemProcessor
**Entity**: Cart
**Transition**: remove_item (active → active)  
**Input**: Cart entity + item identifier (sku)
**Purpose**: Removes item completely from cart

**Pseudocode**:
```
process(cart, itemData):
    validate cart is in 'active' state
    validate item exists in cart
    
    remove line item from cart
    recalculate cart totals
    update timestamps
    return updated cart
```

**Output**: Cart entity with item removed and totals recalculated

### CartOpenCheckoutProcessor
**Entity**: Cart
**Transition**: open_checkout (active → checking_out)
**Input**: Cart entity
**Purpose**: Prepares cart for checkout process

**Pseudocode**:
```
process(cart):
    validate cart is in 'active' state
    validate cart has items
    validate all items still have sufficient stock
    
    lock cart for checkout
    update timestamps
    return cart ready for checkout
```

**Output**: Cart entity in checkout state

### CartCheckoutProcessor
**Entity**: Cart
**Transition**: checkout (checking_out → converted)
**Input**: Cart entity + guest contact information
**Purpose**: Finalizes cart and prepares for order creation

**Pseudocode**:
```
process(cart, guestContact):
    validate cart is in 'checking_out' state
    validate guest contact information is complete
    validate required address fields
    
    attach guest contact to cart
    mark cart as converted
    update timestamps
    return finalized cart
```

**Output**: Cart entity with guest contact and converted state

## Payment Processors

### PaymentStartProcessor
**Entity**: Payment
**Transition**: start_dummy_payment (none → initiated)
**Input**: Payment entity with cartId and amount
**Purpose**: Initiates dummy payment processing

**Pseudocode**:
```
process(payment):
    validate payment data
    validate associated cart exists and is in 'checking_out' state
    
    set payment provider to "DUMMY"
    set payment status to initiated
    schedule auto-payment after 3 seconds
    update timestamps
    return initiated payment
```

**Output**: Payment entity in initiated state

### PaymentAutoMarkPaidProcessor
**Entity**: Payment  
**Transition**: auto_mark_paid (initiated → paid)
**Input**: Payment entity in initiated state
**Purpose**: Automatically marks dummy payment as paid after delay

**Pseudocode**:
```
process(payment):
    validate payment is in 'initiated' state
    validate payment is dummy provider
    
    mark payment as paid
    update timestamps
    trigger order creation workflow
    return paid payment
```

**Output**: Payment entity in paid state
**Other Entity Updates**: Triggers Order creation (null transition)

### PaymentMarkFailedProcessor
**Entity**: Payment
**Transition**: mark_failed (initiated → failed)
**Input**: Payment entity
**Purpose**: Marks payment as failed

**Pseudocode**:
```
process(payment):
    validate payment is in 'initiated' state
    
    mark payment as failed
    update timestamps
    return failed payment
```

**Output**: Payment entity in failed state

### PaymentMarkCanceledProcessor
**Entity**: Payment
**Transition**: mark_canceled (initiated → canceled)
**Input**: Payment entity  
**Purpose**: Marks payment as canceled

**Pseudocode**:
```
process(payment):
    validate payment is in 'initiated' state
    
    mark payment as canceled
    update timestamps
    return canceled payment
```

**Output**: Payment entity in canceled state

## Order Processors

### OrderCreateFromPaidProcessor
**Entity**: Order
**Transition**: create_order_from_paid (none → waiting_to_fulfill)
**Input**: Order entity + paymentId + cartId
**Purpose**: Creates order from paid payment and cart data

**Pseudocode**:
```
process(order, paymentId, cartId):
    validate payment exists and is in 'paid' state
    validate cart exists and is in 'converted' state

    generate short ULID for order number
    snapshot cart lines into order lines
    copy guest contact from cart to order
    calculate order totals

    for each order line:
        fetch product by sku
        decrement product.quantityAvailable by ordered quantity
        update product (null transition)

    create shipment entity in 'picking' state (create_shipment transition)
    update timestamps
    return order in waiting_to_fulfill state
```

**Output**: Order entity in waiting_to_fulfill state
**Other Entity Updates**:
- Updates Product quantities (null transition)
- Creates Shipment (create_shipment transition)

### OrderStartPickingProcessor
**Entity**: Order
**Transition**: start_picking (waiting_to_fulfill → picking)
**Input**: Order entity
**Purpose**: Starts order picking process

**Pseudocode**:
```
process(order):
    validate order is in 'waiting_to_fulfill' state
    validate associated shipment exists

    update order state to picking
    update timestamps
    return order in picking state
```

**Output**: Order entity in picking state

### OrderReadyToSendProcessor
**Entity**: Order
**Transition**: ready_to_send (picking → waiting_to_send)
**Input**: Order entity
**Purpose**: Marks order ready for shipment

**Pseudocode**:
```
process(order):
    validate order is in 'picking' state
    validate associated shipment is ready

    update shipment to 'waiting_to_send' state (ready_to_send transition)
    update order state to waiting_to_send
    update timestamps
    return order in waiting_to_send state
```

**Output**: Order entity in waiting_to_send state
**Other Entity Updates**: Updates Shipment (ready_to_send transition)

### OrderMarkSentProcessor
**Entity**: Order
**Transition**: mark_sent (waiting_to_send → sent)
**Input**: Order entity
**Purpose**: Marks order as sent

**Pseudocode**:
```
process(order):
    validate order is in 'waiting_to_send' state
    validate associated shipment exists

    update shipment to 'sent' state (mark_sent transition)
    update order state to sent
    update timestamps
    return order in sent state
```

**Output**: Order entity in sent state
**Other Entity Updates**: Updates Shipment (mark_sent transition)

### OrderMarkDeliveredProcessor
**Entity**: Order
**Transition**: mark_delivered (sent → delivered)
**Input**: Order entity
**Purpose**: Marks order as delivered

**Pseudocode**:
```
process(order):
    validate order is in 'sent' state
    validate associated shipment exists

    update shipment to 'delivered' state (mark_delivered transition)
    update order state to delivered
    update timestamps
    return order in delivered state
```

**Output**: Order entity in delivered state
**Other Entity Updates**: Updates Shipment (mark_delivered transition)

## Shipment Processors

### ShipmentCreateProcessor
**Entity**: Shipment
**Transition**: create_shipment (none → picking)
**Input**: Shipment entity + orderId
**Purpose**: Creates shipment from order data

**Pseudocode**:
```
process(shipment, orderId):
    validate order exists and is being created

    copy order lines to shipment lines
    set qtyOrdered from order line quantities
    initialize qtyPicked and qtyShipped to 0
    link shipment to order
    update timestamps
    return shipment in picking state
```

**Output**: Shipment entity in picking state

### ShipmentReadyToSendProcessor
**Entity**: Shipment
**Transition**: ready_to_send (picking → waiting_to_send)
**Input**: Shipment entity
**Purpose**: Marks shipment ready for dispatch

**Pseudocode**:
```
process(shipment):
    validate shipment is in 'picking' state
    validate all items are picked (qtyPicked = qtyOrdered)

    update shipment state to waiting_to_send
    update timestamps
    return shipment in waiting_to_send state
```

**Output**: Shipment entity in waiting_to_send state

### ShipmentMarkSentProcessor
**Entity**: Shipment
**Transition**: mark_sent (waiting_to_send → sent)
**Input**: Shipment entity
**Purpose**: Marks shipment as dispatched

**Pseudocode**:
```
process(shipment):
    validate shipment is in 'waiting_to_send' state

    set qtyShipped = qtyPicked for all lines
    update shipment state to sent
    update timestamps
    return shipment in sent state
```

**Output**: Shipment entity in sent state

### ShipmentMarkDeliveredProcessor
**Entity**: Shipment
**Transition**: mark_delivered (sent → delivered)
**Input**: Shipment entity
**Purpose**: Marks shipment as delivered

**Pseudocode**:
```
process(shipment):
    validate shipment is in 'sent' state

    update shipment state to delivered
    update timestamps
    return shipment in delivered state
```

**Output**: Shipment entity in delivered state

## Common Validation Rules

### Stock Validation
- Always check product.quantityAvailable before adding/updating cart items
- Decrement stock atomically during order creation
- Handle insufficient stock gracefully with appropriate error messages

### State Validation
- Always validate current entity state before processing
- Ensure entity is in expected state for transition
- Validate related entities exist and are in correct states

### Data Integrity
- Recalculate totals after any cart modifications
- Maintain consistency between order and shipment states
- Update timestamps on all entity modifications
