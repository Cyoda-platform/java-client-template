# Customer Workflow Requirements

## Workflow States
1. **initial** - Customer is being registered in the system
2. **active** - Customer account is active and can place orders
3. **inactive** - Customer account is temporarily disabled
4. **suspended** - Customer account is suspended due to policy violations
5. **deleted** - Customer account is marked for deletion

## Workflow Transitions

### 1. activate_customer (initial → active)
- **Type**: Automatic transition
- **Trigger**: When customer registration is completed
- **Processors**: CustomerActivationProcessor
- **Description**: Activates new customer account

### 2. deactivate_customer (active → inactive)
- **Type**: Manual transition
- **Trigger**: Customer requests account deactivation
- **Processors**: CustomerDeactivationProcessor
- **Description**: Temporarily deactivates customer account

### 3. reactivate_customer (inactive → active)
- **Type**: Manual transition
- **Trigger**: Customer requests account reactivation
- **Processors**: CustomerReactivationProcessor
- **Description**: Reactivates previously inactive account

### 4. suspend_customer (active → suspended)
- **Type**: Manual transition
- **Trigger**: Administrative action for policy violations
- **Processors**: CustomerSuspensionProcessor
- **Description**: Suspends customer account

### 5. unsuspend_customer (suspended → active)
- **Type**: Manual transition
- **Trigger**: Administrative action to lift suspension
- **Processors**: CustomerUnsuspensionProcessor
- **Description**: Removes suspension and reactivates account

### 6. delete_customer (active/inactive/suspended → deleted)
- **Type**: Manual transition
- **Trigger**: Customer or admin requests account deletion
- **Processors**: CustomerDeletionProcessor
- **Description**: Marks account for deletion

## Processors

### CustomerActivationProcessor
- **Purpose**: Processes new customer activation
- **Business Logic**:
  - Validates customer data
  - Sets activation timestamp
  - Initializes loyalty points
  - Sends welcome communication

### CustomerDeactivationProcessor
- **Purpose**: Handles customer account deactivation
- **Business Logic**:
  - Sets deactivation timestamp
  - Logs deactivation reason
  - May cancel pending orders

### CustomerReactivationProcessor
- **Purpose**: Handles customer account reactivation
- **Business Logic**:
  - Sets reactivation timestamp
  - Validates account status
  - Sends reactivation confirmation

### CustomerSuspensionProcessor
- **Purpose**: Processes customer account suspension
- **Business Logic**:
  - Sets suspension timestamp
  - Logs suspension reason
  - Cancels pending orders
  - Sends suspension notification

### CustomerUnsuspensionProcessor
- **Purpose**: Handles removal of customer suspension
- **Business Logic**:
  - Clears suspension data
  - Sets unsuspension timestamp
  - Sends account restoration notification

### CustomerDeletionProcessor
- **Purpose**: Processes customer account deletion
- **Business Logic**:
  - Sets deletion timestamp
  - Anonymizes personal data
  - Cancels pending orders
  - Logs deletion activity

## Criteria

### CustomerActiveStatusCriterion
- **Purpose**: Checks if customer can place orders
- **Logic**: Validates customer is in "active" state

### CustomerSuspensionEligibilityCriterion
- **Purpose**: Checks if customer can be suspended
- **Logic**: Validates customer is in "active" state and meets suspension criteria
