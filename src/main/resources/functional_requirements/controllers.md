# Controllers Requirements

## Overview
This document defines the detailed requirements for all controllers in the food delivery aggregation system. Each entity has its own controller providing REST API endpoints for CRUD operations and workflow transitions.

## Controller Definitions

### 1. RestaurantController

**Base Path:** `/api/restaurants`  
**Description:** Manages restaurant entities and their lifecycle

#### Endpoints:

**POST /api/restaurants**
- **Purpose:** Register a new restaurant
- **Transition:** Triggers `register_restaurant` (none → PENDING_APPROVAL)
- **Request Body:**
```json
{
  "restaurantId": "rest_001",
  "name": "Mario's Pizza",
  "description": "Authentic Italian pizza and pasta",
  "cuisine": "Italian",
  "address": {
    "line1": "123 Main Street",
    "line2": "Suite 100",
    "city": "New York",
    "state": "NY",
    "postcode": "10001",
    "country": "USA",
    "latitude": 40.7128,
    "longitude": -74.0060
  },
  "contact": {
    "phone": "+1-555-0123",
    "email": "info@mariospizza.com",
    "managerName": "Mario Rossi"
  },
  "operatingHours": [
    {
      "dayOfWeek": "MONDAY",
      "openTime": "11:00",
      "closeTime": "22:00",
      "isOpen": true
    }
  ],
  "minimumOrderAmount": 15.00,
  "deliveryFee": 3.99
}
```
- **Response:** EntityWithMetadata<Restaurant>

**GET /api/restaurants/{id}**
- **Purpose:** Get restaurant by technical UUID
- **Response:** EntityWithMetadata<Restaurant>

**GET /api/restaurants/business/{restaurantId}**
- **Purpose:** Get restaurant by business ID
- **Response:** EntityWithMetadata<Restaurant>

**PUT /api/restaurants/{id}?transition={transitionName}**
- **Purpose:** Update restaurant with optional workflow transition
- **Transitions:** `approve_restaurant`, `suspend_restaurant`, `reactivate_restaurant`, `deactivate_restaurant`
- **Request Body:** Restaurant entity
- **Response:** EntityWithMetadata<Restaurant>

**GET /api/restaurants**
- **Purpose:** Get all restaurants
- **Response:** List<EntityWithMetadata<Restaurant>>

**GET /api/restaurants/search?city={city}&cuisine={cuisine}&isActive={isActive}**
- **Purpose:** Search restaurants by criteria
- **Response:** List<EntityWithMetadata<Restaurant>>

### 2. MenuItemController

**Base Path:** `/api/menu-items`  
**Description:** Manages menu items and their availability

#### Endpoints:

**POST /api/menu-items**
- **Purpose:** Create a new menu item
- **Transition:** Triggers `create_menu_item` (none → DRAFT)
- **Request Body:**
```json
{
  "menuItemId": "item_001",
  "restaurantId": "rest_001",
  "name": "Margherita Pizza",
  "description": "Fresh tomato sauce, mozzarella, and basil",
  "category": "Pizza",
  "price": 18.99,
  "preparationTime": 15,
  "isVegetarian": true,
  "isVegan": false,
  "isGlutenFree": false,
  "allergens": ["gluten", "dairy"],
  "nutritionalInfo": {
    "calories": 280,
    "protein": 12.5,
    "carbohydrates": 35.0,
    "fat": 8.5,
    "fiber": 2.0,
    "sodium": 640
  },
  "customizations": [
    {
      "customizationId": "size",
      "name": "Size",
      "type": "SINGLE_SELECT",
      "options": ["Small", "Medium", "Large"],
      "additionalPrice": 0.0
    }
  ]
}
```
- **Response:** EntityWithMetadata<MenuItem>

**PUT /api/menu-items/{id}?transition={transitionName}**
- **Purpose:** Update menu item with optional workflow transition
- **Transitions:** `publish_menu_item`, `make_unavailable`, `make_available`, `discontinue_item`
- **Request Body:** MenuItem entity
- **Response:** EntityWithMetadata<MenuItem>

**GET /api/menu-items/restaurant/{restaurantId}**
- **Purpose:** Get all menu items for a restaurant
- **Response:** List<EntityWithMetadata<MenuItem>>

**GET /api/menu-items/search?category={category}&isVegetarian={isVegetarian}&maxPrice={maxPrice}**
- **Purpose:** Search menu items by criteria
- **Response:** List<EntityWithMetadata<MenuItem>>

### 3. OrderController

**Base Path:** `/api/orders`  
**Description:** Manages customer orders and delivery process

#### Endpoints:

**POST /api/orders**
- **Purpose:** Place a new order
- **Transition:** Triggers `place_order` (none → PENDING)
- **Request Body:**
```json
{
  "orderId": "order_001",
  "restaurantId": "rest_001",
  "customerId": "cust_001",
  "items": [
    {
      "menuItemId": "item_001",
      "name": "Margherita Pizza",
      "quantity": 2,
      "unitPrice": 18.99,
      "customizations": [
        {
          "customizationId": "size",
          "selectedOption": "Large",
          "additionalPrice": 3.00
        }
      ]
    }
  ],
  "deliveryFee": 3.99,
  "serviceFee": 2.50,
  "tip": 5.00,
  "currency": "USD",
  "customer": {
    "name": "John Doe",
    "phone": "+1-555-0456",
    "email": "john.doe@email.com"
  },
  "deliveryAddress": {
    "line1": "456 Oak Avenue",
    "city": "New York",
    "state": "NY",
    "postcode": "10002",
    "country": "USA",
    "latitude": 40.7589,
    "longitude": -73.9851,
    "deliveryInstructions": "Ring doorbell twice"
  },
  "specialInstructions": "Extra napkins please",
  "requestedDeliveryTime": "2024-01-15T19:30:00",
  "paymentMethod": "CREDIT_CARD"
}
```
- **Response:** EntityWithMetadata<Order>

**PUT /api/orders/{id}?transition={transitionName}**
- **Purpose:** Update order with workflow transition
- **Transitions:** `confirm_order`, `start_preparation`, `mark_ready`, `assign_delivery`, `complete_delivery`, `cancel_order`, `cancel_confirmed_order`
- **Request Body:** Order entity (optional for some transitions)
- **Response:** EntityWithMetadata<Order>

**GET /api/orders/{id}**
- **Purpose:** Get order by technical UUID
- **Response:** EntityWithMetadata<Order>

**GET /api/orders/business/{orderId}**
- **Purpose:** Get order by business ID
- **Response:** EntityWithMetadata<Order>

**GET /api/orders/restaurant/{restaurantId}**
- **Purpose:** Get all orders for a restaurant
- **Response:** List<EntityWithMetadata<Order>>

**GET /api/orders/customer/{customerId}**
- **Purpose:** Get all orders for a customer
- **Response:** List<EntityWithMetadata<Order>>

**GET /api/orders/search?status={status}&restaurantId={restaurantId}&customerId={customerId}**
- **Purpose:** Search orders by criteria
- **Response:** List<EntityWithMetadata<Order>>

### 4. DeliveryServiceController

**Base Path:** `/api/delivery-services`  
**Description:** Manages delivery service providers and their integration

#### Endpoints:

**POST /api/delivery-services**
- **Purpose:** Register a new delivery service
- **Transition:** Triggers `register_service` (none → PENDING_INTEGRATION)
- **Request Body:**
```json
{
  "deliveryServiceId": "wolt_001",
  "name": "Wolt",
  "description": "Fast food delivery service",
  "apiEndpoint": "https://api.wolt.com/v1",
  "apiKey": "wolt_api_key_12345",
  "supportedRegions": ["New York", "Los Angeles", "Chicago"],
  "commissionRate": 15.0,
  "averageDeliveryTime": 30,
  "maxDeliveryDistance": 10.0,
  "operatingHours": [
    {
      "dayOfWeek": "MONDAY",
      "openTime": "08:00",
      "closeTime": "23:00",
      "isOperating": true
    }
  ],
  "contact": {
    "phone": "+1-800-WOLT",
    "email": "partners@wolt.com",
    "supportUrl": "https://support.wolt.com",
    "contactPerson": "Jane Smith"
  },
  "integrationConfig": {
    "webhookUrl": "https://our-system.com/webhooks/wolt",
    "timeoutMs": 5000,
    "retryAttempts": 3,
    "authType": "API_KEY"
  }
}
```
- **Response:** EntityWithMetadata<DeliveryService>

**PUT /api/delivery-services/{id}?transition={transitionName}**
- **Purpose:** Update delivery service with workflow transition
- **Transitions:** `activate_service`, `suspend_service`, `reactivate_service`, `deactivate_service`
- **Request Body:** DeliveryService entity
- **Response:** EntityWithMetadata<DeliveryService>

**GET /api/delivery-services/{id}**
- **Purpose:** Get delivery service by technical UUID
- **Response:** EntityWithMetadata<DeliveryService>

**GET /api/delivery-services/business/{deliveryServiceId}**
- **Purpose:** Get delivery service by business ID
- **Response:** EntityWithMetadata<DeliveryService>

**GET /api/delivery-services**
- **Purpose:** Get all delivery services
- **Response:** List<EntityWithMetadata<DeliveryService>>

**GET /api/delivery-services/search?region={region}&isActive={isActive}**
- **Purpose:** Search delivery services by criteria
- **Response:** List<EntityWithMetadata<DeliveryService>>

### 5. DeliveryPersonController

**Base Path:** `/api/delivery-persons`  
**Description:** Manages delivery personnel and their availability

#### Endpoints:

**POST /api/delivery-persons**
- **Purpose:** Register a new delivery person
- **Transition:** Triggers `register_delivery_person` (none → PENDING_VERIFICATION)
- **Request Body:**
```json
{
  "deliveryPersonId": "dp_001",
  "deliveryServiceId": "wolt_001",
  "name": "Mike Johnson",
  "phone": "+1-555-0789",
  "email": "mike.johnson@email.com",
  "vehicleType": "BIKE",
  "vehicleDetails": {
    "licensePlate": "ABC123",
    "model": "Honda CBR",
    "color": "Red",
    "capacity": "Small"
  },
  "workingHours": [
    {
      "dayOfWeek": "MONDAY",
      "startTime": "09:00",
      "endTime": "17:00",
      "isWorking": true
    }
  ]
}
```
- **Response:** EntityWithMetadata<DeliveryPerson>

**PUT /api/delivery-persons/{id}?transition={transitionName}**
- **Purpose:** Update delivery person with workflow transition
- **Transitions:** `verify_delivery_person`, `assign_delivery`, `complete_delivery`, `go_offline`, `go_online`, `suspend_delivery_person`
- **Request Body:** DeliveryPerson entity (optional for some transitions)
- **Response:** EntityWithMetadata<DeliveryPerson>

**PUT /api/delivery-persons/{id}/location**
- **Purpose:** Update delivery person's current location
- **Request Body:**
```json
{
  "latitude": 40.7589,
  "longitude": -73.9851,
  "address": "123 Street Name",
  "timestamp": "2024-01-15T14:30:00"
}
```
- **Response:** EntityWithMetadata<DeliveryPerson>

**GET /api/delivery-persons/{id}**
- **Purpose:** Get delivery person by technical UUID
- **Response:** EntityWithMetadata<DeliveryPerson>

**GET /api/delivery-persons/service/{deliveryServiceId}**
- **Purpose:** Get all delivery persons for a service
- **Response:** List<EntityWithMetadata<DeliveryPerson>>

**GET /api/delivery-persons/available?deliveryServiceId={serviceId}&vehicleType={vehicleType}**
- **Purpose:** Get available delivery persons
- **Response:** List<EntityWithMetadata<DeliveryPerson>>

## API Response Format

All endpoints return data in the EntityWithMetadata format:

```json
{
  "entity": {
    // Entity data
  },
  "metadata": {
    "id": "uuid-here",
    "state": "ACTIVE",
    "version": 1,
    "createdAt": "2024-01-15T10:00:00",
    "updatedAt": "2024-01-15T10:00:00"
  }
}
```

## Error Handling

All controllers should implement consistent error handling:
- 400 Bad Request: Invalid input data or validation errors
- 404 Not Found: Entity not found
- 409 Conflict: Invalid state transition
- 500 Internal Server Error: System errors

## Authentication & Authorization

All endpoints should implement appropriate authentication and authorization mechanisms based on the user role and entity ownership.
