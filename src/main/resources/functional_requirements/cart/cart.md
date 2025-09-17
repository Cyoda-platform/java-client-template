# Cart Entity Requirements

## Overview
Cart represents a shopping session for anonymous users before checkout.

## Attributes
- **cartId** (string, required): Unique cart identifier
- **lines** (array, required): Cart line items with sku, name, price, qty
- **totalItems** (number, required): Total quantity of items
- **grandTotal** (number, required): Total cart value
- **guestContact** (object, optional): Guest contact information
  - **name** (string, optional): Guest name
  - **email** (string, optional): Guest email
  - **phone** (string, optional): Guest phone
  - **address** (object, optional): Guest address
    - **line1** (string, optional): Address line 1
    - **city** (string, optional): City
    - **postcode** (string, optional): Postal code
    - **country** (string, optional): Country
- **createdAt** (timestamp): Creation timestamp
- **updatedAt** (timestamp): Last update timestamp

## State Management
Cart state is managed via `entity.meta.state`:
- NEW: Initial state
- ACTIVE: Has items, can be modified
- CHECKING_OUT: In checkout process
- CONVERTED: Successfully converted to order

## Relationships
- References Product entities via line item SKUs
- Converted to Order during checkout
- Associated with Payment during checkout

## Business Rules
- Anonymous checkout only (no user accounts)
- Totals recalculated on line item changes
- Guest contact attached during checkout
- Single cart per session
