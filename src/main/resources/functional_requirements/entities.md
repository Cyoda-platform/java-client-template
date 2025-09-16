# Purrfect Pets - Entity Requirements

## Overview
The Purrfect Pets API manages a pet store with pets, categories, orders, and users. This document defines the entity structure and relationships for the system.

## Entities

### 1. Pet Entity
**Purpose**: Represents pets available in the store

**Attributes**:
- `petId` (String, required): Unique identifier for the pet
- `name` (String, required): Pet's name
- `category` (PetCategory, required): Pet's category information
- `photoUrls` (List<String>, optional): URLs of pet photos
- `tags` (List<PetTag>, optional): Tags associated with the pet
- `price` (Double, required): Pet's price
- `description` (String, optional): Pet description
- `birthDate` (LocalDate, optional): Pet's birth date
- `breed` (String, optional): Pet's breed
- `color` (String, optional): Pet's color
- `weight` (Double, optional): Pet's weight in kg
- `vaccinated` (Boolean, optional): Vaccination status
- `createdAt` (LocalDateTime, optional): Creation timestamp
- `updatedAt` (LocalDateTime, optional): Last update timestamp

**Nested Classes**:
- `PetCategory`: Contains `categoryId` (String), `name` (String)
- `PetTag`: Contains `tagId` (String), `name` (String)

**Relationships**:
- Referenced by Order entity in orderItems
- No direct entity relationships (uses nested objects)

**State Management**: 
Pet entity uses workflow state management. States include: available, pending, sold, unavailable.
Do NOT include status field in entity schema - use entity.meta.state instead.

### 2. Category Entity
**Purpose**: Represents pet categories for organization

**Attributes**:
- `categoryId` (String, required): Unique identifier for the category
- `name` (String, required): Category name
- `description` (String, optional): Category description
- `parentCategoryId` (String, optional): Parent category for hierarchical structure
- `displayOrder` (Integer, optional): Display order for UI
- `active` (Boolean, optional): Whether category is active
- `createdAt` (LocalDateTime, optional): Creation timestamp
- `updatedAt` (LocalDateTime, optional): Last update timestamp

**Relationships**:
- Self-referential through parentCategoryId
- Referenced by Pet entity

**State Management**: 
Category entity uses workflow state management. States include: active, inactive.
Do NOT include status field in entity schema - use entity.meta.state instead.

### 3. Order Entity
**Purpose**: Represents customer orders for pets

**Attributes**:
- `orderId` (String, required): Unique identifier for the order
- `userId` (String, required): Customer who placed the order
- `orderItems` (List<OrderItem>, required): Items in the order
- `orderDate` (LocalDateTime, required): When order was placed
- `shipDate` (LocalDateTime, optional): When order was shipped
- `totalAmount` (Double, required): Total order amount
- `shippingAddress` (ShippingAddress, required): Delivery address
- `paymentMethod` (String, optional): Payment method used
- `notes` (String, optional): Order notes
- `createdAt` (LocalDateTime, optional): Creation timestamp
- `updatedAt` (LocalDateTime, optional): Last update timestamp

**Nested Classes**:
- `OrderItem`: Contains `petId` (String), `petName` (String), `quantity` (Integer), `unitPrice` (Double), `totalPrice` (Double)
- `ShippingAddress`: Contains `street` (String), `city` (String), `state` (String), `zipCode` (String), `country` (String)

**Relationships**:
- References User entity through userId
- References Pet entity through orderItems.petId

**State Management**: 
Order entity uses workflow state management. States include: placed, approved, preparing, shipped, delivered, cancelled.
Do NOT include status field in entity schema - use entity.meta.state instead.

### 4. User Entity
**Purpose**: Represents customers and users of the pet store

**Attributes**:
- `userId` (String, required): Unique identifier for the user
- `username` (String, required): User's username
- `firstName` (String, required): User's first name
- `lastName` (String, required): User's last name
- `email` (String, required): User's email address
- `phone` (String, optional): User's phone number
- `address` (UserAddress, optional): User's address
- `preferences` (UserPreferences, optional): User preferences
- `registrationDate` (LocalDateTime, optional): When user registered
- `lastLoginDate` (LocalDateTime, optional): Last login timestamp
- `createdAt` (LocalDateTime, optional): Creation timestamp
- `updatedAt` (LocalDateTime, optional): Last update timestamp

**Nested Classes**:
- `UserAddress`: Contains `street` (String), `city` (String), `state` (String), `zipCode` (String), `country` (String)
- `UserPreferences`: Contains `preferredCategories` (List<String>), `emailNotifications` (Boolean), `smsNotifications` (Boolean)

**Relationships**:
- Referenced by Order entity through userId

**State Management**: 
User entity uses workflow state management. States include: active, inactive, suspended.
Do NOT include status field in entity schema - use entity.meta.state instead.

## Entity Validation Rules

### Pet Entity
- `petId` must not be null or empty
- `name` must not be null or empty
- `category` must not be null
- `price` must be greater than 0

### Category Entity
- `categoryId` must not be null or empty
- `name` must not be null or empty

### Order Entity
- `orderId` must not be null or empty
- `userId` must not be null or empty
- `orderItems` must not be null or empty
- `orderDate` must not be null
- `totalAmount` must be greater than 0
- `shippingAddress` must not be null

### User Entity
- `userId` must not be null or empty
- `username` must not be null or empty
- `firstName` must not be null or empty
- `lastName` must not be null or empty
- `email` must not be null or empty and must be valid email format

## Notes
- All entities implement CyodaEntity interface
- Entity state is managed through workflow state machine, not entity fields
- Timestamps are automatically managed where specified
- All entities use String IDs for flexibility
- Nested classes provide structured data without separate entity relationships
