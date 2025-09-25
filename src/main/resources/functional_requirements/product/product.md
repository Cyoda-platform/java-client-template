# Product Entity Requirements

## Overview
Product entity represents catalog items in the e-commerce system with comprehensive schema for inventory, variants, compliance, and relationships.

## Attributes
- **sku**: Unique product identifier (required)
- **name**: Product display name (required)
- **description**: Product description (required)
- **price**: Base price (required)
- **quantityAvailable**: Available stock count (required)
- **category**: Product category for filtering (required)
- **warehouseId**: Primary warehouse location (optional)

## Complex Schema
Uses the complete Product schema with attributes, localizations, media, options, variants, bundles, inventory, compliance, relationships, and events as specified in requirements.

## Relationships
- Referenced by Cart lines via SKU
- Referenced by Order lines via SKU
- Stock decremented by Order creation
- Supports catalog filtering by category, name/description search, and price range

## Business Rules
- Stock decremented on order creation (no reservations)
- Must include category for filtering
- Full schema persistence required for round-trip compatibility
- UI list view uses slim DTO, detail view returns full document
