# Order Entity Requirements

## Overview
The Order entity represents adoption or purchase transactions linking customers to pets.

## Attributes
- **orderId** (String, required): Unique identifier for the order
- **customerId** (String, required): Reference to the customer
- **petId** (String, required): Reference to the pet being adopted/purchased
- **orderType** (String, required): Type of transaction (adoption, purchase)
- **totalAmount** (Double): Total cost including fees
- **adoptionFee** (Double): Base adoption/purchase fee
- **additionalFees** (Double): Veterinary, processing, or other fees
- **orderDate** (LocalDateTime): When the order was created
- **scheduledPickupDate** (LocalDateTime): Planned pickup date
- **actualPickupDate** (LocalDateTime): Actual pickup date
- **notes** (String): Special instructions or notes
- **paymentMethod** (String): How payment was made
- **isPaymentComplete** (Boolean): Payment completion status

## Relationships
- References one Customer (many-to-one)
- References one Pet (many-to-one)
- Managed through workflow states for order processing

## Validation Rules
- orderId must be unique and non-null
- customerId and petId must reference valid entities
- orderType must be specified
- totalAmount must be non-negative
- orderDate is automatically set on creation
