# Purrfect Pets - Entity Requirements

## Overview
The Purrfect Pets API manages a pet store with pets, categories, orders, and users. This document defines the entity structure and relationships for the system.

## Entities

### 1. Pet Entity
**Purpose**: Represents pets available in the store

**Attributes**:
- `petId` (String, required): Unique identifier for the pet
- `name` (String, required): Pet's name
- `categoryId` (String, required): Reference to category
- `photoUrls` (List<String>, optional): URLs of pet photos
- `tags` (List<Tag>, optional): Tags associated with the pet
- `price` (Double, required): Pet price
- `breed` (String, optional): Pet breed
- `age` (Integer, optional): Pet age in months
- `description` (String, optional): Pet description
- `weight` (Double, optional): Pet weight in kg
- `color` (String, optional): Pet color
- `vaccinated` (Boolean, optional): Vaccination status
- `createdAt` (LocalDateTime, optional): Creation timestamp
- `updatedAt` (LocalDateTime, optional): Last update timestamp

**Nested Classes**:
- `Tag`: Contains `id` (String) and `name` (String)

**State Management**: 
- Entity state represents pet availability status (available, pending, sold)
- State is managed automatically by the workflow system via `entity.meta.state`

**Relationships**:
- Belongs to one Category (via categoryId)
- Can be referenced by multiple OrderItems

### 2. Category Entity
**Purpose**: Represents pet categories for organization

**Attributes**:
- `categoryId` (String, required): Unique identifier for the category
- `name` (String, required): Category name (e.g., "Dogs", "Cats", "Birds")
- `description` (String, optional): Category description
- `imageUrl` (String, optional): Category image URL
- `createdAt` (LocalDateTime, optional): Creation timestamp
- `updatedAt` (LocalDateTime, optional): Last update timestamp

**State Management**: 
- Entity state represents category status (active, inactive)
- State is managed automatically by the workflow system via `entity.meta.state`

**Relationships**:
- Has many Pets

### 3. Order Entity
**Purpose**: Represents customer orders

**Attributes**:
- `orderId` (String, required): Unique identifier for the order
- `userId` (String, required): Reference to the user who placed the order
- `items` (List<OrderItem>, required): List of ordered items
- `totalAmount` (Double, required): Total order amount
- `orderDate` (LocalDateTime, required): Order placement date
- `shipDate` (LocalDateTime, optional): Expected shipping date
- `shippingAddress` (Address, required): Shipping address
- `paymentMethod` (String, optional): Payment method used
- `notes` (String, optional): Order notes
- `createdAt` (LocalDateTime, optional): Creation timestamp
- `updatedAt` (LocalDateTime, optional): Last update timestamp

**Nested Classes**:
- `OrderItem`: Contains `petId` (String), `petName` (String), `quantity` (Integer), `unitPrice` (Double), `totalPrice` (Double)
- `Address`: Contains `street` (String), `city` (String), `state` (String), `zipCode` (String), `country` (String)

**State Management**: 
- Entity state represents order status (placed, approved, delivered, cancelled)
- State is managed automatically by the workflow system via `entity.meta.state`

**Relationships**:
- Belongs to one User (via userId)
- Contains multiple OrderItems that reference Pets

### 4. User Entity
**Purpose**: Represents customers and users of the system

**Attributes**:
- `userId` (String, required): Unique identifier for the user
- `username` (String, required): User's username
- `firstName` (String, required): User's first name
- `lastName` (String, required): User's last name
- `email` (String, required): User's email address
- `phone` (String, optional): User's phone number
- `addresses` (List<Address>, optional): User's addresses
- `dateOfBirth` (LocalDate, optional): User's date of birth
- `preferences` (UserPreferences, optional): User preferences
- `createdAt` (LocalDateTime, optional): Creation timestamp
- `updatedAt` (LocalDateTime, optional): Last update timestamp

**Nested Classes**:
- `Address`: Contains `street` (String), `city` (String), `state` (String), `zipCode` (String), `country` (String), `isDefault` (Boolean)
- `UserPreferences`: Contains `favoriteCategories` (List<String>), `newsletter` (Boolean), `notifications` (Boolean)

**State Management**: 
- Entity state represents user account status (active, inactive, suspended)
- State is managed automatically by the workflow system via `entity.meta.state`

**Relationships**:
- Has many Orders

## Entity Relationships Summary

```
Category (1) -----> (Many) Pet
User (1) -----> (Many) Order
Order (1) -----> (Many) OrderItem
OrderItem (Many) -----> (1) Pet
```

## Validation Rules

### Pet Entity
- `petId` must not be null or empty
- `name` must not be null or empty
- `categoryId` must not be null or empty
- `price` must be greater than 0

### Category Entity
- `categoryId` must not be null or empty
- `name` must not be null or empty

### Order Entity
- `orderId` must not be null or empty
- `userId` must not be null or empty
- `items` must not be null or empty
- `totalAmount` must be greater than 0
- `orderDate` must not be null
- `shippingAddress` must not be null

### User Entity
- `userId` must not be null or empty
- `username` must not be null or empty
- `firstName` must not be null or empty
- `lastName` must not be null or empty
- `email` must not be null or empty and must be valid email format

## Notes

1. All entities implement the `CyodaEntity` interface with proper `getModelKey()` and `isValid()` methods
2. Entity states are managed automatically by the workflow system - do not include state/status fields in entity schemas
3. All timestamps use `LocalDateTime` for consistency
4. All entities use version 1 for initial implementation
5. Nested classes are used for complex data structures to maintain clean entity design
