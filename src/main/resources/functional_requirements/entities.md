# Purrfect Pets - Entity Requirements

## Overview
The Purrfect Pets application manages pets, orders, and users in a pet store system. This document defines the detailed requirements for all entities in the system.

## Entity Definitions

### 1. Pet Entity

**Entity Name:** Pet  
**Package:** `com.java_template.application.entity.pet.version_1`  
**Description:** Represents a pet available in the store with its details, category, and availability status.

#### Attributes:
- **petId** (String, Required): Unique identifier for the pet
- **name** (String, Required): Name of the pet (e.g., "Fluffy", "Buddy")
- **category** (Category, Optional): Category information for the pet
- **photoUrls** (List<String>, Required): List of photo URLs for the pet
- **tags** (List<Tag>, Optional): List of tags associated with the pet
- **description** (String, Optional): Additional description of the pet
- **price** (Double, Optional): Price of the pet
- **birthDate** (LocalDate, Optional): Birth date of the pet
- **breed** (String, Optional): Breed of the pet
- **weight** (Double, Optional): Weight of the pet in kg
- **vaccinated** (Boolean, Optional): Whether the pet is vaccinated
- **createdAt** (LocalDateTime, Optional): When the pet was added to the system
- **updatedAt** (LocalDateTime, Optional): When the pet was last updated

#### Nested Classes:
```java
public static class Category {
    private String categoryId;
    private String name; // e.g., "Dogs", "Cats", "Birds"
}

public static class Tag {
    private String tagId;
    private String name; // e.g., "friendly", "trained", "young"
}
```

#### Entity State:
The Pet entity uses the following states managed by the workflow system:
- **available**: Pet is available for purchase
- **pending**: Pet is reserved/pending sale
- **sold**: Pet has been sold

**Note:** The status field from the Petstore API is replaced by the entity.meta.state system. Do not include a status field in the entity schema.

#### Validation Rules:
- petId must not be null or empty
- name must not be null or empty
- photoUrls must not be null (can be empty list)

### 2. Order Entity

**Entity Name:** Order  
**Package:** `com.java_template.application.entity.order.version_1`  
**Description:** Represents a purchase order for pets in the store.

#### Attributes:
- **orderId** (String, Required): Unique identifier for the order
- **petId** (String, Required): ID of the pet being ordered
- **quantity** (Integer, Required): Quantity of pets ordered (default: 1)
- **shipDate** (LocalDateTime, Optional): Expected shipping date
- **complete** (Boolean, Optional): Whether the order is complete
- **customerInfo** (CustomerInfo, Required): Customer information
- **totalAmount** (Double, Optional): Total amount for the order
- **paymentMethod** (String, Optional): Payment method used
- **shippingAddress** (Address, Optional): Shipping address
- **orderNotes** (String, Optional): Additional notes for the order
- **createdAt** (LocalDateTime, Optional): When the order was created
- **updatedAt** (LocalDateTime, Optional): When the order was last updated

#### Nested Classes:
```java
public static class CustomerInfo {
    private String customerId;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
}

public static class Address {
    private String line1;
    private String line2;
    private String city;
    private String state;
    private String postcode;
    private String country;
}
```

#### Entity State:
The Order entity uses the following states managed by the workflow system:
- **placed**: Order has been placed
- **approved**: Order has been approved
- **delivered**: Order has been delivered

**Note:** The status field from the Petstore API is replaced by the entity.meta.state system. Do not include a status field in the entity schema.

#### Validation Rules:
- orderId must not be null or empty
- petId must not be null or empty
- quantity must be greater than 0
- customerInfo must not be null

### 3. User Entity

**Entity Name:** User  
**Package:** `com.java_template.application.entity.user.version_1`  
**Description:** Represents a user/customer in the pet store system.

#### Attributes:
- **userId** (String, Required): Unique identifier for the user
- **username** (String, Required): Username for login
- **firstName** (String, Required): First name of the user
- **lastName** (String, Required): Last name of the user
- **email** (String, Required): Email address
- **phone** (String, Optional): Phone number
- **password** (String, Required): Encrypted password
- **addresses** (List<Address>, Optional): List of user addresses
- **preferences** (UserPreferences, Optional): User preferences
- **registrationDate** (LocalDateTime, Optional): When the user registered
- **lastLoginDate** (LocalDateTime, Optional): Last login date
- **isActive** (Boolean, Optional): Whether the user account is active

#### Nested Classes:
```java
public static class Address {
    private String addressId;
    private String line1;
    private String line2;
    private String city;
    private String state;
    private String postcode;
    private String country;
    private Boolean isDefault;
}

public static class UserPreferences {
    private List<String> preferredCategories; // e.g., ["Dogs", "Cats"]
    private Boolean emailNotifications;
    private Boolean smsNotifications;
    private String preferredContactMethod; // "email" or "sms"
}
```

#### Entity State:
The User entity uses the following states managed by the workflow system:
- **registered**: User has registered but not verified
- **active**: User account is active and verified
- **suspended**: User account is temporarily suspended
- **inactive**: User account is deactivated

**Note:** The userStatus field from the Petstore API is replaced by the entity.meta.state system. Do not include a userStatus field in the entity schema.

#### Validation Rules:
- userId must not be null or empty
- username must not be null or empty
- firstName must not be null or empty
- lastName must not be null or empty
- email must not be null and must be valid email format
- password must not be null or empty

## Entity Relationships

### Pet → Order
- One Pet can be referenced by multiple Orders (one-to-many)
- Order.petId references Pet.petId

### User → Order
- One User can have multiple Orders (one-to-many)
- Order.customerInfo.customerId references User.userId

### Category → Pet
- One Category can be associated with multiple Pets (one-to-many)
- Pet.category.categoryId is the relationship key

### Tag → Pet
- Many-to-many relationship through Pet.tags list
- Each Tag can be associated with multiple Pets

## Notes

1. All entities implement the CyodaEntity interface with proper getModelKey() and isValid() methods
2. Entity states are managed by the workflow system and accessed via entity.meta.state
3. All date/time fields use appropriate Java time classes (LocalDateTime, LocalDate)
4. Nested classes are used for complex data structures to maintain clean entity design
5. Validation rules ensure data integrity at the entity level
