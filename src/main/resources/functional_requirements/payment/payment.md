# Payment Entity Requirements

## Overview
Payment represents dummy payment processing for demo purposes.

## Attributes
- **paymentId** (string, required): Unique payment identifier
- **cartId** (string, required): Associated cart identifier
- **amount** (number, required): Payment amount
- **provider** (string, required): Always "DUMMY" for demo
- **createdAt** (timestamp): Creation timestamp
- **updatedAt** (timestamp): Last update timestamp

## State Management
Payment state is managed via `entity.meta.state`:
- INITIATED: Payment started
- PAID: Payment successful (auto after ~3 seconds)
- FAILED: Payment failed
- CANCELED: Payment canceled

## Relationships
- Associated with Cart via cartId
- Required for Order creation

## Business Rules
- Dummy payment auto-approves after ~3 seconds
- Only PAID payments can create orders
- Single payment per cart
- Amount matches cart grandTotal
