# Entities Requirements

## Overview
This document defines the detailed requirements for all entities in the food delivery aggregation system. The system aggregates different delivery services to deliver food from restaurants and grocery stores, providing a single API for integration with restaurant management platforms and delivery services.

## Entity Definitions

### 1. Restaurant Entity

**Name:** Restaurant  
**Description:** Represents a restaurant or food establishment that offers food for delivery

**Attributes:**
- `restaurantId` (String, required) - Unique business identifier for the restaurant
- `name` (String, required) - Restaurant name
- `description` (String, optional) - Restaurant description
- `cuisine` (String, optional) - Type of cuisine (e.g., "Italian", "Chinese", "Fast Food")
- `address` (RestaurantAddress, required) - Restaurant physical address
- `contact` (RestaurantContact, required) - Restaurant contact information
- `operatingHours` (List<OperatingHour>, optional) - Restaurant operating hours
- `deliveryZones` (List<DeliveryZone>, optional) - Areas where restaurant delivers
- `isActive` (Boolean, required) - Whether restaurant is currently accepting orders
- `averagePreparationTime` (Integer, optional) - Average time to prepare orders in minutes
- `minimumOrderAmount` (Double, optional) - Minimum order amount for delivery
- `deliveryFee` (Double, optional) - Restaurant's delivery fee
- `rating` (Double, optional) - Average customer rating (0.0 to 5.0)
- `totalOrders` (Integer, optional) - Total number of orders processed
- `createdAt` (LocalDateTime, required) - Restaurant registration timestamp
- `updatedAt` (LocalDateTime, required) - Last update timestamp

**Nested Classes:**
- `RestaurantAddress`: line1, line2, city, state, postcode, country, latitude, longitude
- `RestaurantContact`: phone, email, managerName
- `OperatingHour`: dayOfWeek, openTime, closeTime, isOpen
- `DeliveryZone`: zoneName, radius, additionalFee

**Relationships:**
- One-to-many with MenuItem (restaurant has multiple menu items)
- One-to-many with Order (restaurant receives multiple orders)
- Many-to-many with DeliveryService (restaurant can use multiple delivery services)

**Entity State:** Managed by workflow system (not part of schema)
- States: PENDING_APPROVAL, ACTIVE, SUSPENDED, INACTIVE

### 2. MenuItem Entity

**Name:** MenuItem  
**Description:** Represents a food item available for order from a restaurant

**Attributes:**
- `menuItemId` (String, required) - Unique business identifier for the menu item
- `restaurantId` (String, required) - Reference to parent restaurant
- `name` (String, required) - Item name
- `description` (String, optional) - Item description
- `category` (String, required) - Menu category (e.g., "Appetizers", "Main Course", "Desserts")
- `price` (Double, required) - Item price
- `preparationTime` (Integer, optional) - Time to prepare this item in minutes
- `isAvailable` (Boolean, required) - Whether item is currently available
- `isVegetarian` (Boolean, optional) - Whether item is vegetarian
- `isVegan` (Boolean, optional) - Whether item is vegan
- `isGlutenFree` (Boolean, optional) - Whether item is gluten-free
- `allergens` (List<String>, optional) - List of allergens
- `nutritionalInfo` (NutritionalInfo, optional) - Nutritional information
- `imageUrl` (String, optional) - URL to item image
- `customizations` (List<MenuItemCustomization>, optional) - Available customizations
- `createdAt` (LocalDateTime, required) - Item creation timestamp
- `updatedAt` (LocalDateTime, required) - Last update timestamp

**Nested Classes:**
- `NutritionalInfo`: calories, protein, carbohydrates, fat, fiber, sodium
- `MenuItemCustomization`: customizationId, name, type, options, additionalPrice

**Relationships:**
- Many-to-one with Restaurant (menu item belongs to one restaurant)
- One-to-many with OrderItem (menu item can be in multiple order items)

**Entity State:** Managed by workflow system (not part of schema)
- States: DRAFT, ACTIVE, UNAVAILABLE, DISCONTINUED

### 3. Order Entity

**Name:** Order  
**Description:** Represents a customer order for food delivery

**Attributes:**
- `orderId` (String, required) - Unique business identifier for the order
- `restaurantId` (String, required) - Reference to restaurant
- `customerId` (String, required) - Customer identifier
- `items` (List<OrderItem>, required) - List of ordered items
- `subtotal` (Double, required) - Order subtotal before fees and taxes
- `deliveryFee` (Double, required) - Delivery fee amount
- `serviceFee` (Double, optional) - Service fee amount
- `tax` (Double, required) - Tax amount
- `tip` (Double, optional) - Tip amount
- `totalAmount` (Double, required) - Total order amount
- `currency` (String, required) - Currency code (e.g., "USD", "EUR")
- `customer` (CustomerInfo, required) - Customer information
- `deliveryAddress` (DeliveryAddress, required) - Delivery address
- `specialInstructions` (String, optional) - Special delivery instructions
- `requestedDeliveryTime` (LocalDateTime, optional) - Customer requested delivery time
- `estimatedDeliveryTime` (LocalDateTime, optional) - Estimated delivery time
- `actualDeliveryTime` (LocalDateTime, optional) - Actual delivery time
- `paymentMethod` (String, required) - Payment method used
- `paymentStatus` (String, required) - Payment status
- `assignedDeliveryService` (String, optional) - Assigned delivery service
- `deliveryPersonId` (String, optional) - Assigned delivery person ID
- `trackingUrl` (String, optional) - Order tracking URL
- `createdAt` (LocalDateTime, required) - Order creation timestamp
- `updatedAt` (LocalDateTime, required) - Last update timestamp

**Nested Classes:**
- `OrderItem`: menuItemId, name, quantity, unitPrice, customizations, itemTotal
- `CustomerInfo`: name, phone, email
- `DeliveryAddress`: line1, line2, city, state, postcode, country, latitude, longitude, deliveryInstructions

**Relationships:**
- Many-to-one with Restaurant (order belongs to one restaurant)
- Many-to-one with DeliveryService (order assigned to one delivery service)
- One-to-many with OrderItem (order contains multiple items)

**Entity State:** Managed by workflow system (not part of schema)
- States: PENDING, CONFIRMED, PREPARING, READY_FOR_PICKUP, OUT_FOR_DELIVERY, DELIVERED, CANCELLED

### 4. DeliveryService Entity

**Name:** DeliveryService  
**Description:** Represents a delivery service provider (e.g., Wolt, Glovo, UberEats)

**Attributes:**
- `deliveryServiceId` (String, required) - Unique business identifier for the delivery service
- `name` (String, required) - Service name (e.g., "Wolt", "Glovo")
- `description` (String, optional) - Service description
- `apiEndpoint` (String, required) - API endpoint for integration
- `apiKey` (String, required) - API key for authentication
- `isActive` (Boolean, required) - Whether service is currently active
- `supportedRegions` (List<String>, required) - List of supported regions/cities
- `commissionRate` (Double, required) - Commission rate percentage
- `averageDeliveryTime` (Integer, optional) - Average delivery time in minutes
- `maxDeliveryDistance` (Double, optional) - Maximum delivery distance in kilometers
- `operatingHours` (List<OperatingHour>, optional) - Service operating hours
- `contact` (ServiceContact, required) - Service contact information
- `integrationConfig` (IntegrationConfig, optional) - Integration configuration
- `createdAt` (LocalDateTime, required) - Service registration timestamp
- `updatedAt` (LocalDateTime, required) - Last update timestamp

**Nested Classes:**
- `OperatingHour`: dayOfWeek, openTime, closeTime, isOperating
- `ServiceContact`: phone, email, supportUrl, contactPerson
- `IntegrationConfig`: webhookUrl, timeoutMs, retryAttempts, authType

**Relationships:**
- Many-to-many with Restaurant (delivery service can serve multiple restaurants)
- One-to-many with Order (delivery service can handle multiple orders)

**Entity State:** Managed by workflow system (not part of schema)
- States: PENDING_INTEGRATION, ACTIVE, SUSPENDED, INACTIVE

### 5. DeliveryPerson Entity

**Name:** DeliveryPerson  
**Description:** Represents a delivery person working for a delivery service

**Attributes:**
- `deliveryPersonId` (String, required) - Unique business identifier for the delivery person
- `deliveryServiceId` (String, required) - Reference to delivery service
- `name` (String, required) - Delivery person name
- `phone` (String, required) - Contact phone number
- `email` (String, optional) - Email address
- `vehicleType` (String, required) - Vehicle type (e.g., "BIKE", "CAR", "MOTORCYCLE")
- `vehicleDetails` (VehicleDetails, optional) - Vehicle information
- `currentLocation` (Location, optional) - Current GPS location
- `isAvailable` (Boolean, required) - Whether delivery person is available
- `isOnline` (Boolean, required) - Whether delivery person is online
- `rating` (Double, optional) - Average customer rating (0.0 to 5.0)
- `totalDeliveries` (Integer, optional) - Total number of deliveries completed
- `workingHours` (List<WorkingHour>, optional) - Working schedule
- `createdAt` (LocalDateTime, required) - Registration timestamp
- `updatedAt` (LocalDateTime, required) - Last update timestamp

**Nested Classes:**
- `VehicleDetails`: licensePlate, model, color, capacity
- `Location`: latitude, longitude, address, timestamp
- `WorkingHour`: dayOfWeek, startTime, endTime, isWorking

**Relationships:**
- Many-to-one with DeliveryService (delivery person belongs to one service)
- One-to-many with Order (delivery person can handle multiple orders)

**Entity State:** Managed by workflow system (not part of schema)
- States: PENDING_VERIFICATION, ACTIVE, BUSY, OFFLINE, SUSPENDED

## Entity State Management

**Important Note:** All entities have a state field that is managed automatically by the workflow system. This state is accessed via `entity.meta.state` in processor code and cannot be directly modified. The workflow system manages state transitions based on defined workflows.

If entities have semantically similar concepts like "status", these should be treated as the entity state and not included in the entity schema. The system will manage these states automatically through workflow transitions.

## Validation Rules

Each entity must implement the `isValid()` method to validate required fields:
- Restaurant: restaurantId, name, address, contact, isActive must not be null
- MenuItem: menuItemId, restaurantId, name, category, price, isAvailable must not be null
- Order: orderId, restaurantId, customerId, items, totalAmount, customer, deliveryAddress must not be null
- DeliveryService: deliveryServiceId, name, apiEndpoint, apiKey, supportedRegions, commissionRate must not be null
- DeliveryPerson: deliveryPersonId, deliveryServiceId, name, phone, vehicleType, isAvailable, isOnline must not be null
