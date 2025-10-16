# Pet Workflow Requirements

## Workflow States
1. **initial** - Pet is being registered in the system
2. **available** - Pet is available for adoption/purchase
3. **pending** - Pet adoption/purchase is in progress
4. **sold** - Pet has been adopted/purchased

## Workflow Transitions

### 1. register_pet (initial → available)
- **Type**: Automatic transition
- **Trigger**: When pet is first created
- **Processors**: PetRegistrationProcessor
- **Description**: Validates pet data and makes it available

### 2. reserve_pet (available → pending)
- **Type**: Manual transition
- **Trigger**: When customer places an order for the pet
- **Processors**: PetReservationProcessor
- **Description**: Reserves pet for a customer order

### 3. complete_sale (pending → sold)
- **Type**: Manual transition
- **Trigger**: When order is completed and payment processed
- **Processors**: PetSaleProcessor
- **Description**: Marks pet as sold and updates related records

### 4. cancel_reservation (pending → available)
- **Type**: Manual transition
- **Trigger**: When order is cancelled or payment fails
- **Processors**: PetReservationCancelProcessor
- **Description**: Returns pet to available status

## Processors

### PetRegistrationProcessor
- **Purpose**: Validates and processes new pet registration
- **Business Logic**:
  - Validates all required fields
  - Sets creation timestamp
  - Generates any missing optional data
  - Logs pet registration

### PetReservationProcessor
- **Purpose**: Handles pet reservation for orders
- **Business Logic**:
  - Updates reservation timestamp
  - Logs reservation activity
  - May update related inventory records

### PetSaleProcessor
- **Purpose**: Processes completed pet sale
- **Business Logic**:
  - Updates sale timestamp
  - Logs sale completion
  - May trigger inventory updates
  - May update customer records

### PetReservationCancelProcessor
- **Purpose**: Handles cancellation of pet reservations
- **Business Logic**:
  - Clears reservation data
  - Logs cancellation
  - May update related order records

## Criteria

### PetAvailabilityCriterion
- **Purpose**: Checks if pet is available for reservation
- **Logic**: Validates pet is in "available" state and not already reserved

### PetSaleEligibilityCriterion
- **Purpose**: Checks if pet sale can be completed
- **Logic**: Validates pet is in "pending" state and has valid order
