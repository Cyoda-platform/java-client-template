# Payment Entity Requirements

## Overview
Payment entity handles dummy payment processing with automatic approval for demo purposes.

## Attributes
- **paymentId**: Unique payment identifier (required)
- **cartId**: Associated cart identifier (required)
- **amount**: Payment amount (required)
- **status**: Payment state - INITIATED, PAID, FAILED, CANCELED (required)
- **provider**: Payment provider, always "DUMMY" (required)
- **createdAt**, **updatedAt**: Timestamps

## Relationships
- Associated with Cart via cartId
- Triggers Order creation when status becomes PAID

## Business Rules
- Dummy payment provider only
- Auto-approval after approximately 3 seconds
- Payment must be PAID before order creation
- Single payment per cart
