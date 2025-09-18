# Store Entity Requirements

## Overview
The Store entity represents physical or virtual pet store locations managing inventory and operations.

## Attributes
- **name**: String - Store name
- **address**: String - Store physical address
- **phone**: String - Store contact phone
- **email**: String - Store contact email
- **managerName**: String - Store manager's name
- **operatingHours**: String - Business hours
- **capacity**: Integer - Maximum pets the store can house

## Relationships
- Manages multiple Pet entities
- Processes multiple Order entities
- Serves multiple Owner entities

## Business Rules
- Store name must be unique
- Capacity must be positive
- Operating hours must be valid format
- Manager name is required for operational stores

## Notes
Entity state is managed internally via `entity.meta.state` and should not appear in the entity schema.
