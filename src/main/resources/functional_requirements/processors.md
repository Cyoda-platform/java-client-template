# Processor Requirements

This document defines the detailed requirements for all processors in the Cyoda OMS Backend system.

## Processor Overview

Processors implement business logic for workflow transitions. Each processor is a Java class that implements the CyodaProcessor interface and contains the business logic for a specific transition.

## Naming Convention

All processors follow PascalCase naming starting with the entity name:
- Cart processors: Cart[ProcessorName]Processor
- Payment processors: Payment[ProcessorName]Processor  
- Order processors: Order[ProcessorName]Processor
- Shipment processors: Shipment[ProcessorName]Processor

## 1. CartRecalculateTotalsProcessor

**Entity**: Cart
**Transitions**: CREATE_ON_FIRST_ADD, ADD_ITEM, DECREMENT_ITEM, REMOVE_ITEM
**Purpose**: Recalculates cart totals when items are added, modified, or removed

**Input Data**: 
- Cart entity with current lines
- Optional: new line item data (sku, qty)

**Business Logic (Pseudocode)**:
```
PROCESS cart_recalculate_totals:
    INPUT: cart entity, optional line_item_data
    
    IF line_item_data provided:
        existing_line = find line in cart.lines where sku = line_item_data.sku
        
        IF existing_line exists:
            IF line_item_data.qty > 0:
                existing_line.qty = line_item_data.qty
            ELSE:
                remove existing_line from cart.lines
        ELSE:
            IF line_item_data.qty > 0:
                product = get_product_by_sku(line_item_data.sku)
                new_line = {
                    sku: line_item_data.sku,
                    name: product.name,
                    price: product.price,
                    qty: line_item_data.qty
                }
                add new_line to cart.lines
    
    total_items = 0
    grand_total = 0
    
    FOR each line in cart.lines:
        total_items += line.qty
        grand_total += (line.price * line.qty)
    
    cart.totalItems = total_items
    cart.grandTotal = grand_total
    
    RETURN updated cart entity
```

**Expected Output**: Updated cart entity with recalculated totals
**Other Entity Updates**: None
**Transition**: null (stays in current state or moves to ACTIVE)

## 2. PaymentCreateDummyPaymentProcessor

**Entity**: Payment
**Transitions**: START_DUMMY_PAYMENT
**Purpose**: Creates a dummy payment record in INITIATED state

**Input Data**:
- Payment entity with cartId and amount
- Cart reference for validation

**Business Logic (Pseudocode)**:
```
PROCESS payment_create_dummy:
    INPUT: payment entity with cartId, amount
    
    cart = get_cart_by_id(payment.cartId)
    
    IF cart is null:
        THROW error "Cart not found"
    
    IF cart.meta.state != "CHECKING_OUT":
        THROW error "Cart must be in CHECKING_OUT state"
    
    IF payment.amount != cart.grandTotal:
        THROW error "Payment amount must match cart total"
    
    payment.provider = "DUMMY"
    payment.paymentId = generate_unique_id()
    
    RETURN payment entity
```

**Expected Output**: Payment entity with provider set to "DUMMY"
**Other Entity Updates**: None
**Transition**: null (stays in INITIATED state)

## 3. PaymentAutoMarkPaidAfter3sProcessor

**Entity**: Payment
**Transitions**: AUTO_MARK_PAID
**Purpose**: Automatically marks payment as PAID after a 3-second delay

**Input Data**:
- Payment entity in INITIATED state

**Business Logic (Pseudocode)**:
```
PROCESS payment_auto_mark_paid:
    INPUT: payment entity in INITIATED state
    
    IF payment.meta.state != "INITIATED":
        THROW error "Payment must be in INITIATED state"
    
    IF payment.provider != "DUMMY":
        THROW error "Auto-payment only for DUMMY provider"
    
    // Simulate 3-second processing delay
    WAIT 3 seconds
    
    // Payment will transition to PAID state automatically
    // No entity modification needed - state managed by workflow
    
    RETURN payment entity unchanged
```

**Expected Output**: Payment entity (state will be changed to PAID by workflow)
**Other Entity Updates**: None
**Transition**: AUTO_MARK_PAID (moves to PAID state)

## 4. OrderCreateOrderFromPaidProcessor

**Entity**: Order
**Transitions**: CREATE_ORDER_FROM_PAID
**Purpose**: Creates order from paid cart, decrements product stock, and creates shipment

**Input Data**:
- Order entity with basic info
- Payment reference (must be PAID)
- Cart reference for order data

**Business Logic (Pseudocode)**:
```
PROCESS order_create_from_paid:
    INPUT: order entity, paymentId, cartId
    
    payment = get_payment_by_id(paymentId)
    cart = get_cart_by_id(cartId)
    
    IF payment is null OR payment.meta.state != "PAID":
        THROW error "Payment must exist and be PAID"
    
    IF cart is null OR cart.meta.state != "CONVERTED":
        THROW error "Cart must exist and be CONVERTED"
    
    IF payment.cartId != cartId:
        THROW error "Payment and cart must match"
    
    // Generate order number (short ULID)
    order.orderNumber = generate_short_ulid()
    
    // Snapshot cart lines to order lines
    order.lines = []
    FOR each cart_line in cart.lines:
        order_line = {
            sku: cart_line.sku,
            name: cart_line.name,
            unitPrice: cart_line.price,
            qty: cart_line.qty,
            lineTotal: cart_line.price * cart_line.qty
        }
        add order_line to order.lines
        
        // Decrement product stock
        product = get_product_by_sku(cart_line.sku)
        IF product.quantityAvailable < cart_line.qty:
            THROW error "Insufficient stock for " + cart_line.sku
        
        product.quantityAvailable -= cart_line.qty
        update_product(product)
    
    // Set order totals
    order.totals = {
        items: cart.totalItems,
        grand: cart.grandTotal
    }
    
    // Snapshot guest contact
    order.guestContact = deep_copy(cart.guestContact)
    
    // Create shipment
    shipment = {
        shipmentId: generate_unique_id(),
        orderId: order.orderId,
        lines: []
    }
    
    FOR each order_line in order.lines:
        shipment_line = {
            sku: order_line.sku,
            qtyOrdered: order_line.qty,
            qtyPicked: 0,
            qtyShipped: 0
        }
        add shipment_line to shipment.lines
    
    create_shipment(shipment) // Creates shipment in PICKING state
    
    RETURN order entity
```

**Expected Output**: Complete order entity with lines, totals, and guest contact
**Other Entity Updates**: 
- Product entities (decrement quantityAvailable)
- Shipment entity (create new shipment with CREATE_SHIPMENT transition)
**Transition**: CREATE_ORDER_FROM_PAID (moves to PICKING state)

## Processor Configuration

All processors should be configured with:
- **executionMode**: "SYNC" (synchronous execution)
- **attachEntity**: true (attach entity data to request)
- **calculationNodesTags**: "cyoda_application"
- **responseTimeoutMs**: 5000 (5 second timeout)
- **retryPolicy**: "FIXED"

## Error Handling

All processors should:
1. Validate input data and entity states
2. Throw descriptive errors for invalid conditions
3. Ensure data consistency across entity updates
4. Handle concurrent access scenarios
5. Log processing steps for debugging

## Performance Considerations

1. **Stock Updates**: Use optimistic locking for product quantity updates
2. **Bulk Operations**: Process multiple line items efficiently
3. **Entity Retrieval**: Cache frequently accessed product data
4. **Transaction Management**: Ensure atomicity of multi-entity updates
