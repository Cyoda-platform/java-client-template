# Order Entity Requirements

## Overview
The Order entity represents a transaction between an Owner and Store for pet adoption/purchase.

## Attributes
- **petId**: String - Reference to the pet being ordered
- **ownerId**: String - Reference to the customer placing order
- **storeId**: String - Reference to the store processing order
- **orderDate**: LocalDateTime - When order was placed
- **totalAmount**: BigDecimal - Total cost including fees
- **adoptionFee**: BigDecimal - Base adoption/purchase fee
- **serviceFee**: BigDecimal - Additional service charges
- **notes**: String - Special instructions or notes

## Relationships
- References one Pet entity
- References one Owner entity
- References one Store entity

## Business Rules
- Total amount must equal adoption fee plus service fee
- Order date cannot be in the future
- Pet must be available when order is created
- Owner must be verified for orders over $500

## Notes
Entity state is managed internally via `entity.meta.state` and should not appear in the entity schema.
