# Criteria Requirements

## Overview
This document defines the detailed requirements for all criteria in the food delivery aggregation system. Criteria are pure functions that evaluate conditions without side effects to determine if workflow transitions should be allowed.

## Criteria Definitions

### Restaurant Criteria

#### 1. RestaurantApprovalCriterion
**Entity:** Restaurant  
**Transition:** PENDING_APPROVAL → ACTIVE  
**Purpose:** Validate that restaurant meets approval requirements

**Validation Logic:**
```
CHECK restaurant_approval_requirements:
  VERIFY restaurant.name is not empty
  VERIFY restaurant.address is complete (line1, city, state, postcode, country)
  VERIFY restaurant.contact.phone is valid format
  VERIFY restaurant.contact.email is valid format
  VERIFY restaurant.operatingHours has at least one day defined
  VERIFY restaurant.deliveryZones is not empty OR restaurant can use default zone
  
  RETURN true IF all validations pass
  RETURN false OTHERWISE
```

#### 2. RestaurantReactivationCriterion
**Entity:** Restaurant  
**Transition:** SUSPENDED → ACTIVE  
**Purpose:** Ensure restaurant can be safely reactivated

**Validation Logic:**
```
CHECK restaurant_reactivation_requirements:
  VERIFY restaurant.isActive is false (currently suspended)
  VERIFY restaurant.address is still valid
  VERIFY restaurant.contact information is current
  VERIFY no outstanding compliance issues exist
  
  // Check if restaurant has active menu items
  COUNT active_menu_items WHERE restaurantId = restaurant.restaurantId AND state = "ACTIVE"
  VERIFY active_menu_items > 0
  
  RETURN true IF all validations pass
  RETURN false OTHERWISE
```

### MenuItem Criteria

#### 3. MenuItemValidationCriterion
**Entity:** MenuItem  
**Transition:** DRAFT → ACTIVE  
**Purpose:** Validate menu item is ready for publication

**Validation Logic:**
```
CHECK menu_item_publication_requirements:
  VERIFY menuItem.name is not empty
  VERIFY menuItem.description is not empty
  VERIFY menuItem.category is valid category
  VERIFY menuItem.price > 0
  VERIFY menuItem.preparationTime >= 0 (if specified)
  
  // Verify parent restaurant is active
  FIND restaurant WHERE restaurantId = menuItem.restaurantId
  VERIFY restaurant.state = "ACTIVE"
  
  // Check for duplicate menu items
  COUNT existing_items WHERE restaurantId = menuItem.restaurantId 
    AND name = menuItem.name 
    AND state = "ACTIVE"
  VERIFY existing_items = 0
  
  RETURN true IF all validations pass
  RETURN false OTHERWISE
```

#### 4. MenuItemAvailabilityCriterion
**Entity:** MenuItem  
**Transition:** UNAVAILABLE → ACTIVE  
**Purpose:** Ensure menu item can be made available again

**Validation Logic:**
```
CHECK menu_item_availability_requirements:
  // Verify parent restaurant is still active
  FIND restaurant WHERE restaurantId = menuItem.restaurantId
  VERIFY restaurant.state = "ACTIVE"
  
  // Verify menu item data is still valid
  VERIFY menuItem.price > 0
  VERIFY menuItem.name is not empty
  
  // Check if restaurant is currently accepting orders
  VERIFY restaurant.isActive = true
  
  RETURN true IF all validations pass
  RETURN false OTHERWISE
```

### Order Criteria

#### 5. OrderValidationCriterion
**Entity:** Order  
**Transition:** PENDING → CONFIRMED  
**Purpose:** Validate order can be confirmed by restaurant

**Validation Logic:**
```
CHECK order_confirmation_requirements:
  // Verify restaurant is active and accepting orders
  FIND restaurant WHERE restaurantId = order.restaurantId
  VERIFY restaurant.state = "ACTIVE"
  VERIFY restaurant.isActive = true
  
  // Verify all menu items are still available
  FOR each item in order.items:
    FIND menuItem WHERE menuItemId = item.menuItemId
    VERIFY menuItem.state = "ACTIVE"
    VERIFY menuItem.isAvailable = true
    VERIFY menuItem.restaurantId = order.restaurantId
  
  // Verify order totals are correct
  CALCULATE expected_subtotal from order.items
  VERIFY order.subtotal = expected_subtotal
  VERIFY order.totalAmount > 0
  
  // Verify delivery address is in restaurant's delivery zones
  FOR each zone in restaurant.deliveryZones:
    IF order.deliveryAddress is within zone.radius:
      RETURN true
  
  RETURN false IF delivery address not in any zone
```

#### 6. DeliveryAvailabilityCriterion
**Entity:** Order  
**Transition:** READY_FOR_PICKUP → OUT_FOR_DELIVERY  
**Purpose:** Ensure delivery can be assigned to the order

**Validation Logic:**
```
CHECK delivery_assignment_requirements:
  // Verify restaurant is still active
  FIND restaurant WHERE restaurantId = order.restaurantId
  VERIFY restaurant.state = "ACTIVE"
  
  // Check if delivery services are available for this restaurant
  FIND delivery_services WHERE restaurant is supported AND state = "ACTIVE"
  VERIFY delivery_services.count > 0
  
  // Check if any delivery person is available
  FOR each delivery_service in delivery_services:
    COUNT available_persons WHERE deliveryServiceId = delivery_service.id 
      AND state = "ACTIVE" 
      AND isAvailable = true 
      AND isOnline = true
    IF available_persons > 0:
      RETURN true
  
  RETURN false IF no delivery person available
```

#### 7. OrderCancellationCriterion
**Entity:** Order  
**Transition:** PENDING/CONFIRMED → CANCELLED  
**Purpose:** Determine if order can be cancelled

**Validation Logic:**
```
CHECK order_cancellation_requirements:
  // Orders can be cancelled if not yet being prepared
  VERIFY order.state IN ["PENDING", "CONFIRMED"]
  
  // Check cancellation time window (e.g., within 5 minutes of placement)
  SET time_since_creation = current_timestamp - order.createdAt
  IF order.state = "PENDING":
    RETURN true  // Always allow cancellation of pending orders
  
  IF order.state = "CONFIRMED":
    VERIFY time_since_creation <= 5 minutes
    RETURN true IF within time window
  
  RETURN false OTHERWISE
```

### DeliveryService Criteria

#### 8. DeliveryServiceIntegrationCriterion
**Entity:** DeliveryService  
**Transition:** PENDING_INTEGRATION → ACTIVE  
**Purpose:** Validate delivery service integration is complete

**Validation Logic:**
```
CHECK delivery_service_integration_requirements:
  VERIFY deliveryService.apiEndpoint is accessible
  VERIFY deliveryService.apiKey is valid
  VERIFY deliveryService.supportedRegions is not empty
  VERIFY deliveryService.commissionRate between 0 and 100
  
  // Test API connectivity
  TRY:
    SEND test_request to deliveryService.apiEndpoint with apiKey
    VERIFY response is successful
  CATCH:
    RETURN false
  
  // Verify webhook configuration
  VERIFY deliveryService.integrationConfig.webhookUrl is valid
  VERIFY deliveryService.integrationConfig.timeoutMs > 0
  
  RETURN true IF all validations pass
  RETURN false OTHERWISE
```

#### 9. DeliveryServiceReactivationCriterion
**Entity:** DeliveryService  
**Transition:** SUSPENDED → ACTIVE  
**Purpose:** Ensure delivery service can be safely reactivated

**Validation Logic:**
```
CHECK delivery_service_reactivation_requirements:
  // Verify API is still functional
  TRY:
    SEND test_request to deliveryService.apiEndpoint
    VERIFY response is successful
  CATCH:
    RETURN false
  
  // Verify service configuration is still valid
  VERIFY deliveryService.supportedRegions is not empty
  VERIFY deliveryService.commissionRate between 0 and 100
  
  // Check if service has active delivery persons
  COUNT active_delivery_persons WHERE deliveryServiceId = deliveryService.id 
    AND state IN ["ACTIVE", "OFFLINE"]
  VERIFY active_delivery_persons > 0
  
  RETURN true IF all validations pass
  RETURN false OTHERWISE
```

### DeliveryPerson Criteria

#### 10. DeliveryPersonVerificationCriterion
**Entity:** DeliveryPerson  
**Transition:** PENDING_VERIFICATION → ACTIVE  
**Purpose:** Validate delivery person meets verification requirements

**Validation Logic:**
```
CHECK delivery_person_verification_requirements:
  VERIFY deliveryPerson.name is not empty
  VERIFY deliveryPerson.phone is valid format
  VERIFY deliveryPerson.vehicleType is valid enum ["BIKE", "CAR", "MOTORCYCLE"]
  
  // Verify parent delivery service is active
  FIND deliveryService WHERE deliveryServiceId = deliveryPerson.deliveryServiceId
  VERIFY deliveryService.state = "ACTIVE"
  
  // Verify vehicle details if required
  IF deliveryPerson.vehicleType IN ["CAR", "MOTORCYCLE"]:
    VERIFY deliveryPerson.vehicleDetails.licensePlate is not empty
    VERIFY deliveryPerson.vehicleDetails.model is not empty
  
  // Check for duplicate delivery persons
  COUNT existing_persons WHERE deliveryServiceId = deliveryPerson.deliveryServiceId 
    AND phone = deliveryPerson.phone 
    AND state != "SUSPENDED"
  VERIFY existing_persons = 0
  
  RETURN true IF all validations pass
  RETURN false OTHERWISE
```

#### 11. DeliveryPersonAvailabilityCriterion
**Entity:** DeliveryPerson  
**Transition:** OFFLINE → ACTIVE  
**Purpose:** Ensure delivery person can come back online

**Validation Logic:**
```
CHECK delivery_person_availability_requirements:
  // Verify parent delivery service is still active
  FIND deliveryService WHERE deliveryServiceId = deliveryPerson.deliveryServiceId
  VERIFY deliveryService.state = "ACTIVE"
  VERIFY deliveryService.isActive = true
  
  // Verify delivery person is not suspended
  VERIFY deliveryPerson.state != "SUSPENDED"
  
  // Check working hours if specified
  IF deliveryPerson.workingHours is not empty:
    GET current_day_of_week and current_time
    FIND working_hour WHERE dayOfWeek = current_day_of_week
    IF working_hour exists:
      VERIFY current_time between working_hour.startTime and working_hour.endTime
      VERIFY working_hour.isWorking = true
  
  RETURN true IF all validations pass
  RETURN false OTHERWISE
```

## Criteria Implementation Notes

- All criteria must implement the `CyodaCriterion` interface
- Criteria must be pure functions without side effects
- Criteria cannot modify entities or call external services
- Criteria should only read entity data and perform validations
- All criteria should return boolean values (true/false)
- Criteria should be fast and efficient as they may be called frequently
- Error handling should be minimal - return false for any validation failure
- Criteria can access related entities through the EntityService for read-only operations
