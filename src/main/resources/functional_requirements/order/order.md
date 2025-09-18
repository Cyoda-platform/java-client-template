# Order Entity Requirements

## Overview
The Order entity represents adoption/purchase transactions in the Purrfect Pets store.

## Attributes
- **petId**: String - Reference to Pet being ordered
- **ownerId**: String - Reference to Owner placing the order
- **orderDate**: LocalDateTime - When the order was placed
- **totalAmount**: BigDecimal - Total order amount
- **paymentMethod**: String - Payment method (Credit Card, Cash, etc.)
- **notes**: String - Additional order notes

## Relationships
- Links Pet and Owner entities through the adoption/purchase process

## Notes
- Order state is managed internally via `entity.meta.state` (Pending, Confirmed, Processing, Completed, Cancelled)
- Total amount must match pet price
- Order date is automatically set when created
