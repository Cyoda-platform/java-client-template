# Purrfect Pets API - Entity Requirements

## Overview
This document defines the detailed requirements for all entities in the Purrfect Pets API application, based on the Swagger Petstore API data structure.

## Entity Definitions

### 1. Pet Entity

**Description**: The main entity representing a pet in the store.

**Attributes**:
- `id` (Long): Unique identifier for the pet
- `name` (String, required): Name of the pet (e.g., "doggie")
- `category` (Category): Category the pet belongs to (relationship to Category entity)
- `photoUrls` (List<String>, required): Array of photo URLs for the pet
- `tags` (List<Tag>): Array of tags associated with the pet (relationship to Tag entity)

**Entity State**: The pet's workflow state will be managed by `entity.meta.state` instead of the status field from the original API. The status concept from Petstore API (available, pending, sold) will be represented as workflow states.

**Relationships**:
- Many-to-One with Category (pet belongs to one category)
- Many-to-Many with Tag (pet can have multiple tags)
- One-to-Many with Order (pet can be in multiple orders)

### 2. Category Entity

**Description**: Represents a category that pets can belong to.

**Attributes**:
- `id` (Long): Unique identifier for the category
- `name` (String): Name of the category (e.g., "Dogs", "Cats", "Birds")

**Relationships**:
- One-to-Many with Pet (category can have multiple pets)

### 3. Tag Entity

**Description**: Represents tags that can be associated with pets for classification and filtering.

**Attributes**:
- `id` (Long): Unique identifier for the tag
- `name` (String): Name of the tag (e.g., "friendly", "small", "trained")

**Relationships**:
- Many-to-Many with Pet (tag can be associated with multiple pets)

### 4. Order Entity

**Description**: Represents a purchase order for pets in the store.

**Attributes**:
- `id` (Long): Unique identifier for the order
- `petId` (Long): ID of the pet being ordered (relationship to Pet entity)
- `quantity` (Integer): Quantity of pets ordered
- `shipDate` (LocalDateTime): Shipping date for the order
- `complete` (Boolean): Whether the order is complete

**Entity State**: The order's workflow state will be managed by `entity.meta.state` instead of the status field. The status concept from Petstore API (placed, approved, delivered) will be represented as workflow states.

**Relationships**:
- Many-to-One with Pet (order references one pet)
- Many-to-One with User (order belongs to one user)

### 5. User Entity

**Description**: Represents a user of the pet store system.

**Attributes**:
- `id` (Long): Unique identifier for the user
- `username` (String): Username for login
- `firstName` (String): User's first name
- `lastName` (String): User's last name
- `email` (String): User's email address
- `password` (String): User's password (should be encrypted)
- `phone` (String): User's phone number
- `userStatus` (Integer): User status code

**Relationships**:
- One-to-Many with Order (user can have multiple orders)

## Entity Relationship Summary

```
User (1) -----> (Many) Order
Order (Many) -----> (1) Pet
Pet (Many) -----> (1) Category
Pet (Many) <-----> (Many) Tag
```

## Important Notes

1. **State Management**: All entities that had status fields in the original Petstore API (Pet and Order) will use the workflow state management system (`entity.meta.state`) instead of explicit status attributes.

2. **Required Fields**: 
   - Pet: name and photoUrls are required
   - All other entities have flexible requirements based on business logic

3. **Data Types**: 
   - IDs are Long type for all entities
   - Dates use LocalDateTime
   - Collections use appropriate Java collection types (List, Set)

4. **Security**: User passwords should be properly encrypted and never stored in plain text.

5. **Validation**: All entities should have appropriate validation rules implemented in their respective processors and criteria.
