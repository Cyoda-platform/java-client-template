# Purrfect Pets - Entity Requirements

## Overview
The Purrfect Pets API manages pet store data with four main entities: Pet, Category, Tag, and Order. Each entity follows the CyodaEntity pattern and includes workflow state management.

## Entity Definitions

### 1. Pet Entity
**Package**: `com.java_template.application.entity.pet.version_1`
**Class Name**: `Pet`
**Description**: Represents a pet available in the store

#### Attributes:
- `petId` (String, required) - Business identifier for the pet
- `name` (String, required) - Pet's name
- `categoryId` (String, optional) - Reference to category
- `photoUrls` (List<String>, optional) - URLs of pet photos
- `tags` (List<String>, optional) - List of tag IDs associated with the pet
- `status` (String, optional) - Pet availability status (available, pending, sold) - **Note: This will be managed as entity.meta.state**
- `price` (Double, optional) - Pet price
- `breed` (String, optional) - Pet breed
- `age` (Integer, optional) - Pet age in months
- `description` (String, optional) - Pet description
- `weight` (Double, optional) - Pet weight in kg
- `vaccinated` (Boolean, optional) - Vaccination status
- `createdAt` (LocalDateTime, optional) - Creation timestamp
- `updatedAt` (LocalDateTime, optional) - Last update timestamp

#### Relationships:
- **Many-to-One** with Category (via categoryId)
- **Many-to-Many** with Tag (via tags list)
- **One-to-Many** with Order (pets can be ordered)

#### Validation Rules:
- `petId` must not be null or empty
- `name` must not be null or empty
- `price` must be positive if provided
- `age` must be positive if provided
- `weight` must be positive if provided

### 2. Category Entity
**Package**: `com.java_template.application.entity.category.version_1`
**Class Name**: `Category`
**Description**: Represents a pet category (e.g., Dogs, Cats, Birds)

#### Attributes:
- `categoryId` (String, required) - Business identifier for the category
- `name` (String, required) - Category name
- `description` (String, optional) - Category description
- `active` (Boolean, optional) - Whether category is active (default: true)
- `createdAt` (LocalDateTime, optional) - Creation timestamp
- `updatedAt` (LocalDateTime, optional) - Last update timestamp

#### Relationships:
- **One-to-Many** with Pet (category can have multiple pets)

#### Validation Rules:
- `categoryId` must not be null or empty
- `name` must not be null or empty

### 3. Tag Entity
**Package**: `com.java_template.application.entity.tag.version_1`
**Class Name**: `Tag`
**Description**: Represents tags for categorizing pets (e.g., "friendly", "trained", "hypoallergenic")

#### Attributes:
- `tagId` (String, required) - Business identifier for the tag
- `name` (String, required) - Tag name
- `color` (String, optional) - Tag color for UI display
- `description` (String, optional) - Tag description
- `active` (Boolean, optional) - Whether tag is active (default: true)
- `createdAt` (LocalDateTime, optional) - Creation timestamp
- `updatedAt` (LocalDateTime, optional) - Last update timestamp

#### Relationships:
- **Many-to-Many** with Pet (tags can be applied to multiple pets)

#### Validation Rules:
- `tagId` must not be null or empty
- `name` must not be null or empty

### 4. Order Entity
**Package**: `com.java_template.application.entity.order.version_1`
**Class Name**: `Order`
**Description**: Represents a pet purchase order

#### Attributes:
- `orderId` (String, required) - Business identifier for the order
- `petId` (String, required) - Reference to the pet being ordered
- `quantity` (Integer, required) - Quantity ordered (default: 1)
- `orderDate` (LocalDateTime, required) - When the order was placed
- `status` (String, optional) - Order status (placed, approved, delivered) - **Note: This will be managed as entity.meta.state**
- `customerInfo` (CustomerInfo, required) - Customer information
- `shippingAddress` (Address, optional) - Shipping address
- `totalAmount` (Double, optional) - Total order amount
- `notes` (String, optional) - Order notes
- `shipDate` (LocalDateTime, optional) - Expected ship date
- `complete` (Boolean, optional) - Whether order is complete
- `createdAt` (LocalDateTime, optional) - Creation timestamp
- `updatedAt` (LocalDateTime, optional) - Last update timestamp

#### Nested Classes:

##### CustomerInfo
- `firstName` (String, required) - Customer first name
- `lastName` (String, required) - Customer last name
- `email` (String, required) - Customer email
- `phone` (String, optional) - Customer phone number

##### Address
- `street` (String, required) - Street address
- `city` (String, required) - City
- `state` (String, required) - State/Province
- `zipCode` (String, required) - ZIP/Postal code
- `country` (String, required) - Country

#### Relationships:
- **Many-to-One** with Pet (via petId)

#### Validation Rules:
- `orderId` must not be null or empty
- `petId` must not be null or empty
- `quantity` must be positive
- `orderDate` must not be null
- `customerInfo` must not be null
- `customerInfo.firstName` must not be null or empty
- `customerInfo.lastName` must not be null or empty
- `customerInfo.email` must be valid email format
- `totalAmount` must be positive if provided

## Entity State Management

### Important Notes:
1. **Status Fields**: The `status` fields in Pet and Order entities represent the workflow state and will be managed automatically by the system via `entity.meta.state`. These should NOT be included in the entity schema as regular fields.

2. **State Access**: To access the current state in processors, use `entity.meta.state`. The state cannot be directly modified by application code - it's managed by the workflow engine.

3. **State Values**:
   - **Pet States**: available, pending, sold
   - **Order States**: placed, approved, delivered

4. **Category and Tag**: These entities have simpler lifecycles and may not require complex workflow states beyond basic active/inactive status.

## Entity Relationships Summary

```
Category (1) -----> (Many) Pet (Many) -----> (Many) Tag
                            |
                            |
                    (1) Pet -----> (Many) Order
```

This design supports:
- Pets belonging to categories
- Pets having multiple tags
- Multiple orders for the same pet (if quantity allows)
- Rich customer and shipping information for orders
