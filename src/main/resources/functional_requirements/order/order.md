# Order Entity Requirements

## Overview
The Order entity represents adoption/purchase transactions in the Purrfect Pets store.

## Attributes
- **orderId** (String, required): Unique identifier for the order
- **customerId** (String, required): Reference to customer making the order
- **petId** (String, required): Reference to pet being adopted/purchased
- **orderDate** (LocalDateTime, required): When the order was created
- **totalAmount** (Double, required): Total order amount
- **paymentMethod** (String, optional): Payment method used
- **deliveryAddress** (Address, optional): Delivery address if different from customer address
- **specialInstructions** (String, optional): Special delivery or care instructions
- **adoptionFee** (Double, required): Base adoption fee
- **additionalServices** (List<Service>, optional): Additional services (grooming, supplies, etc.)

## Nested Classes
### Address
- **street** (String): Street address
- **city** (String): City
- **state** (String): State/province
- **zipCode** (String): Postal code
- **country** (String): Country

### Service
- **serviceName** (String): Name of additional service
- **price** (Double): Service price

## Relationships
- References Pet entity via petId
- References Customer entity via customerId

## Validation Rules
- orderId, customerId, petId, orderDate, totalAmount, and adoptionFee are required
- totalAmount must be non-negative
- adoptionFee must be non-negative
