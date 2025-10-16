# Order Entity Functional Requirements

## Overview
The Order entity represents purchase orders placed by customers for pets. Each order contains customer information, pet details, pricing, and fulfillment status.

## Entity Structure

### Required Fields
- **orderId** (String) - Business identifier for the order (e.g., "ORD001", "ORDER123")
- **customerId** (String) - Reference to customer who placed the order
- **petId** (String) - Reference to pet being ordered
- **quantity** (Integer) - Quantity of pets ordered (typically 1)

### Optional Fields
- **orderDate** (LocalDateTime) - When the order was placed
- **shipDate** (LocalDateTime) - When the order was shipped/fulfilled
- **deliveryDate** (LocalDateTime) - When the order was delivered
- **pricing** (OrderPricing) - Pricing breakdown
  - **petPrice** (Double) - Base price of the pet
  - **tax** (Double) - Tax amount
  - **shippingCost** (Double) - Shipping/delivery cost
  - **discount** (Double) - Discount amount
  - **totalAmount** (Double) - Total order amount
- **shipping** (ShippingInfo) - Shipping information
  - **method** (String) - Shipping method (pickup, delivery, etc.)
  - **address** (ShippingAddress) - Delivery address
    - **street** (String) - Street address
    - **city** (String) - City
    - **state** (String) - State/Province
    - **zipCode** (String) - ZIP/Postal code
    - **country** (String) - Country
  - **trackingNumber** (String) - Shipping tracking number
- **payment** (PaymentInfo) - Payment information
  - **method** (String) - Payment method (credit_card, cash, etc.)
  - **transactionId** (String) - Payment transaction ID
  - **paymentDate** (LocalDateTime) - When payment was processed
- **notes** (String) - Special instructions or notes
- **complete** (Boolean) - Whether order is complete
- **createdAt** (LocalDateTime) - Order creation timestamp
- **updatedAt** (LocalDateTime) - Last update timestamp

## Business Rules

### Validation Rules
1. Order ID must be unique across all orders
2. Customer ID must reference an existing active customer
3. Pet ID must reference an existing available pet
4. Quantity must be positive (typically 1 for pets)
5. Total amount must be positive if specified
6. Ship date cannot be before order date
7. Delivery date cannot be before ship date

### Status Management
Order status is managed through workflow states:
- **placed** - Order has been placed but not yet processed
- **confirmed** - Order has been confirmed and payment processed
- **preparing** - Order is being prepared for shipment
- **shipped** - Order has been shipped/is in transit
- **delivered** - Order has been delivered to customer
- **cancelled** - Order has been cancelled
- **returned** - Order has been returned

## Entity Relationships
- Order references Customer (customerId field)
- Order references Pet (petId field)
- Order may trigger Pet status changes (available → pending → sold)
