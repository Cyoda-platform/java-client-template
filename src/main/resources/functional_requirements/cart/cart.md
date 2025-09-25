# Cart Entity Requirements

## Overview
Cart entity manages shopping cart functionality for anonymous checkout with line items and totals calculation.

## Attributes
- **cartId**: Unique cart identifier (required)
- **status**: Cart state - NEW, ACTIVE, CHECKING_OUT, CONVERTED (required)
- **lines**: Array of cart line items with sku, name, price, qty (required)
- **totalItems**: Total quantity of items (required)
- **grandTotal**: Total cart value (required)
- **guestContact**: Anonymous customer contact info (optional)
  - name, email, phone (optional)
  - address with line1, city, postcode, country (optional)
- **createdAt**, **updatedAt**: Timestamps

## Relationships
- References Product entities via line item SKUs
- Converted to Order upon successful payment
- Associated with Payment for checkout process

## Business Rules
- Anonymous checkout only (no user accounts)
- Automatic totals recalculation on line changes
- Single cart per session
- Guest contact attached during checkout process
