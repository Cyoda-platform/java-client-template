# Product Entity Requirements

## Overview
Product represents catalog items available for purchase in the OMS system.

## Attributes
- **sku** (string, required, unique): Product identifier
- **name** (string, required): Product display name
- **description** (string, required): Product description
- **price** (number, required): Base price
- **quantityAvailable** (number, required): Available stock quantity
- **category** (string, required): Product category for filtering
- **warehouseId** (string, optional): Primary warehouse location

## Complex Attributes
- **attributes**: Brand, model, dimensions, weight, hazards, custom fields
- **localizations**: Multi-language content and regulatory info
- **media**: Images, documents with metadata
- **options**: Product variants (color, capacity) with constraints
- **variants**: SKU variants with option values and pricing
- **bundles**: Kit/bundle configurations with components
- **inventory**: Multi-node inventory tracking with lots and reservations
- **compliance**: Regulatory documents and restrictions
- **relationships**: Supplier contracts and related products
- **events**: Audit trail of product lifecycle events

## Relationships
- Referenced by Cart lines (sku)
- Referenced by Order lines (sku)
- Referenced by Shipment lines (sku)
- Stock decremented by Order creation

## Business Rules
- Must use complete Product schema for persistence
- UI list view uses slim DTO projection
- Stock decremented on order creation (no reservations)
- Supports category and price range filtering
- Supports free-text search on name/description
