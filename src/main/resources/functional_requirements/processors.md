# Processors Requirements

## Overview
This document defines the detailed requirements for all processors in the food delivery aggregation system. Each processor implements specific business logic for workflow transitions.

## Processor Definitions

### Restaurant Processors

#### 1. RestaurantRegistrationProcessor
**Entity:** Restaurant  
**Transition:** none → PENDING_APPROVAL  
**Input:** Restaurant entity with basic registration data  
**Output:** Restaurant entity with registration timestamp and initial setup

**Pseudocode:**
```
PROCESS restaurant_registration:
  SET restaurant.createdAt = current_timestamp
  SET restaurant.updatedAt = current_timestamp
  SET restaurant.isActive = false
  SET restaurant.totalOrders = 0
  SET restaurant.rating = 0.0
  
  VALIDATE restaurant.address is complete
  VALIDATE restaurant.contact information is valid
  
  LOG "Restaurant registered: " + restaurant.name
  RETURN restaurant
```

#### 2. RestaurantApprovalProcessor
**Entity:** Restaurant  
**Transition:** PENDING_APPROVAL → ACTIVE  
**Input:** Restaurant entity pending approval  
**Output:** Restaurant entity activated for operations

**Pseudocode:**
```
PROCESS restaurant_approval:
  SET restaurant.isActive = true
  SET restaurant.updatedAt = current_timestamp
  
  // Create default delivery zones if not specified
  IF restaurant.deliveryZones is empty:
    CREATE default_zone with 5km radius
    ADD default_zone to restaurant.deliveryZones
  
  // Notify delivery services about new restaurant
  FOR each active_delivery_service:
    SEND restaurant_activation_notification to delivery_service
  
  LOG "Restaurant approved and activated: " + restaurant.name
  RETURN restaurant
```

#### 3. RestaurantSuspensionProcessor
**Entity:** Restaurant  
**Transition:** ACTIVE → SUSPENDED  
**Input:** Restaurant entity to be suspended  
**Output:** Restaurant entity with suspended status

**Pseudocode:**
```
PROCESS restaurant_suspension:
  SET restaurant.isActive = false
  SET restaurant.updatedAt = current_timestamp
  
  // Cancel all pending orders for this restaurant
  FIND all orders WHERE restaurantId = restaurant.restaurantId AND state IN [PENDING, CONFIRMED]
  FOR each pending_order:
    UPDATE pending_order with transition = "cancel_order"
  
  // Notify delivery services
  FOR each delivery_service:
    SEND restaurant_suspension_notification to delivery_service
  
  LOG "Restaurant suspended: " + restaurant.name
  RETURN restaurant
```

### MenuItem Processors

#### 4. MenuItemCreationProcessor
**Entity:** MenuItem  
**Transition:** none → DRAFT  
**Input:** MenuItem entity with basic item data  
**Output:** MenuItem entity with creation timestamp

**Pseudocode:**
```
PROCESS menu_item_creation:
  SET menuItem.createdAt = current_timestamp
  SET menuItem.updatedAt = current_timestamp
  SET menuItem.isAvailable = false
  
  VALIDATE menuItem.price > 0
  VALIDATE menuItem.restaurantId exists
  
  // Check if restaurant is active
  FIND restaurant WHERE restaurantId = menuItem.restaurantId
  IF restaurant.state != "ACTIVE":
    THROW error "Cannot create menu item for inactive restaurant"
  
  LOG "Menu item created: " + menuItem.name
  RETURN menuItem
```

#### 5. MenuItemPublishProcessor
**Entity:** MenuItem  
**Transition:** DRAFT → ACTIVE  
**Input:** MenuItem entity in draft state  
**Output:** MenuItem entity published and available

**Pseudocode:**
```
PROCESS menu_item_publish:
  SET menuItem.isAvailable = true
  SET menuItem.updatedAt = current_timestamp
  
  // Validate all required fields are complete
  VALIDATE menuItem.name is not empty
  VALIDATE menuItem.category is not empty
  VALIDATE menuItem.price > 0
  
  // Notify delivery services about new menu item
  FIND restaurant WHERE restaurantId = menuItem.restaurantId
  FOR each delivery_service connected to restaurant:
    SEND menu_item_published_notification to delivery_service
  
  LOG "Menu item published: " + menuItem.name
  RETURN menuItem
```

### Order Processors

#### 6. OrderPlacementProcessor
**Entity:** Order  
**Transition:** none → PENDING  
**Input:** Order entity with customer order data  
**Output:** Order entity with calculated totals and timestamps

**Pseudocode:**
```
PROCESS order_placement:
  SET order.createdAt = current_timestamp
  SET order.updatedAt = current_timestamp
  SET order.paymentStatus = "PENDING"
  
  // Calculate order totals
  SET subtotal = 0
  FOR each item in order.items:
    VALIDATE item.menuItemId exists and is available
    SET item.itemTotal = item.unitPrice * item.quantity
    ADD item.itemTotal to subtotal
  
  SET order.subtotal = subtotal
  SET order.tax = subtotal * 0.1  // 10% tax rate
  SET order.totalAmount = order.subtotal + order.deliveryFee + order.serviceFee + order.tax + order.tip
  
  // Estimate delivery time
  FIND restaurant WHERE restaurantId = order.restaurantId
  SET estimated_prep_time = restaurant.averagePreparationTime
  SET order.estimatedDeliveryTime = current_timestamp + estimated_prep_time + 30 minutes
  
  LOG "Order placed: " + order.orderId + " for restaurant: " + order.restaurantId
  RETURN order
```

#### 7. OrderConfirmationProcessor
**Entity:** Order  
**Transition:** PENDING → CONFIRMED  
**Input:** Order entity pending confirmation  
**Output:** Order entity confirmed by restaurant

**Pseudocode:**
```
PROCESS order_confirmation:
  SET order.updatedAt = current_timestamp
  SET order.paymentStatus = "CONFIRMED"
  
  // Update estimated delivery time based on current restaurant load
  FIND restaurant WHERE restaurantId = order.restaurantId
  COUNT active_orders WHERE restaurantId = order.restaurantId AND state IN [CONFIRMED, PREPARING]
  SET queue_delay = active_orders * 5 minutes
  SET order.estimatedDeliveryTime = current_timestamp + restaurant.averagePreparationTime + queue_delay + 30 minutes
  
  // Reserve menu items (update availability if needed)
  FOR each item in order.items:
    FIND menuItem WHERE menuItemId = item.menuItemId
    // Could implement inventory management here
  
  // Send notification to customer
  SEND order_confirmation_notification to order.customer
  
  LOG "Order confirmed: " + order.orderId
  RETURN order
```

#### 8. DeliveryAssignmentProcessor
**Entity:** Order  
**Transition:** READY_FOR_PICKUP → OUT_FOR_DELIVERY  
**Input:** Order entity ready for pickup  
**Output:** Order entity assigned to delivery person

**Pseudocode:**
```
PROCESS delivery_assignment:
  SET order.updatedAt = current_timestamp
  
  // Find best available delivery service
  FIND restaurant WHERE restaurantId = order.restaurantId
  FIND available_delivery_services FOR restaurant
  
  FOR each delivery_service in available_delivery_services:
    FIND available_delivery_person WHERE deliveryServiceId = delivery_service.id AND state = "ACTIVE"
    IF available_delivery_person found:
      SET order.assignedDeliveryService = delivery_service.name
      SET order.deliveryPersonId = delivery_person.deliveryPersonId
      
      // Update delivery person status
      UPDATE delivery_person with transition = "assign_delivery"
      
      // Generate tracking URL
      SET order.trackingUrl = generate_tracking_url(order.orderId, delivery_service)
      
      // Send notifications
      SEND delivery_assignment_notification to delivery_person
      SEND tracking_info_notification to order.customer
      
      BREAK
  
  IF no delivery person assigned:
    THROW error "No available delivery person found"
  
  LOG "Delivery assigned: " + order.orderId + " to " + order.deliveryPersonId
  RETURN order
```

### DeliveryService Processors

#### 9. DeliveryServiceRegistrationProcessor
**Entity:** DeliveryService  
**Transition:** none → PENDING_INTEGRATION  
**Input:** DeliveryService entity with basic service data  
**Output:** DeliveryService entity with registration timestamp

**Pseudocode:**
```
PROCESS delivery_service_registration:
  SET deliveryService.createdAt = current_timestamp
  SET deliveryService.updatedAt = current_timestamp
  SET deliveryService.isActive = false
  
  VALIDATE deliveryService.apiEndpoint is valid URL
  VALIDATE deliveryService.supportedRegions is not empty
  VALIDATE deliveryService.commissionRate between 0 and 100
  
  // Test API connectivity
  TRY:
    SEND test_request to deliveryService.apiEndpoint
    LOG "API connectivity test successful for: " + deliveryService.name
  CATCH:
    LOG "API connectivity test failed for: " + deliveryService.name
  
  LOG "Delivery service registered: " + deliveryService.name
  RETURN deliveryService
```

#### 10. DeliveryServiceActivationProcessor
**Entity:** DeliveryService  
**Transition:** PENDING_INTEGRATION → ACTIVE  
**Input:** DeliveryService entity with completed integration  
**Output:** DeliveryService entity activated for operations

**Pseudocode:**
```
PROCESS delivery_service_activation:
  SET deliveryService.isActive = true
  SET deliveryService.updatedAt = current_timestamp
  
  // Setup webhook endpoints
  CONFIGURE webhook_url for order notifications
  CONFIGURE webhook_url for delivery status updates
  
  // Sync with existing restaurants in supported regions
  FOR each region in deliveryService.supportedRegions:
    FIND restaurants WHERE address.city = region AND state = "ACTIVE"
    FOR each restaurant:
      SEND restaurant_sync_notification to deliveryService
  
  LOG "Delivery service activated: " + deliveryService.name
  RETURN deliveryService
```

### DeliveryPerson Processors

#### 11. DeliveryPersonRegistrationProcessor
**Entity:** DeliveryPerson  
**Transition:** none → PENDING_VERIFICATION  
**Input:** DeliveryPerson entity with basic person data  
**Output:** DeliveryPerson entity with registration timestamp

**Pseudocode:**
```
PROCESS delivery_person_registration:
  SET deliveryPerson.createdAt = current_timestamp
  SET deliveryPerson.updatedAt = current_timestamp
  SET deliveryPerson.isAvailable = false
  SET deliveryPerson.isOnline = false
  SET deliveryPerson.totalDeliveries = 0
  SET deliveryPerson.rating = 0.0
  
  VALIDATE deliveryPerson.phone is valid format
  VALIDATE deliveryPerson.vehicleType is valid enum
  
  // Check delivery service is active
  FIND deliveryService WHERE deliveryServiceId = deliveryPerson.deliveryServiceId
  IF deliveryService.state != "ACTIVE":
    THROW error "Cannot register delivery person for inactive service"
  
  LOG "Delivery person registered: " + deliveryPerson.name
  RETURN deliveryPerson
```

#### 12. DeliveryPersonVerificationProcessor
**Entity:** DeliveryPerson  
**Transition:** PENDING_VERIFICATION → ACTIVE  
**Input:** DeliveryPerson entity pending verification  
**Output:** DeliveryPerson entity verified and ready for work

**Pseudocode:**
```
PROCESS delivery_person_verification:
  SET deliveryPerson.isAvailable = true
  SET deliveryPerson.isOnline = false
  SET deliveryPerson.updatedAt = current_timestamp
  
  // Verify required documents (simulated)
  VALIDATE deliveryPerson.vehicleDetails is complete
  VALIDATE deliveryPerson.phone is verified
  
  // Notify delivery service
  FIND deliveryService WHERE deliveryServiceId = deliveryPerson.deliveryServiceId
  SEND delivery_person_verified_notification to deliveryService
  
  LOG "Delivery person verified: " + deliveryPerson.name
  RETURN deliveryPerson
```

## Processor Implementation Notes

- All processors must implement the `CyodaProcessor` interface
- Processors can read current entity data but cannot update the current entity's state
- Processors can update OTHER entities via EntityService
- All processors should include proper error handling and logging
- Timestamps should be updated in all processors that modify entities
- Validation should be performed before any business logic
- External service notifications should be handled asynchronously when possible
