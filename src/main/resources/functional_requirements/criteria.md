# Criteria Specification - Purrfect Pets API

## Overview
This document defines the conditional logic criteria used in workflow transitions. Criteria implement business rules that determine whether specific transitions should be allowed or triggered automatically.

## Owner Entity Criteria

### 1. OwnerVerificationCriterion

**Entity:** Owner  
**Transition:** verify_owner (PENDING → ACTIVE)  
**Purpose:** Determines if an owner account can be automatically verified and activated

**Validation Logic:**
- Email address has been verified (external verification system)
- All required contact information is complete and valid
- No duplicate accounts exist with the same email
- Phone number format is valid for the region
- Address information is complete

**Implementation Approach:**
```
1. Check email verification status
   - Verify email confirmation was received
   - Ensure email domain is valid

2. Validate contact completeness
   - firstName and lastName are not empty
   - phoneNumber matches valid format
   - address, city, zipCode are provided

3. Check for duplicates
   - Search existing owners by email address
   - Ensure no active accounts with same email

4. Return success if all validations pass
```

**Success Condition:** All validation checks pass  
**Failure Condition:** Any validation check fails

---

## PetCareOrder Entity Criteria

### 2. OrderValidationCriterion

**Entity:** PetCareOrder  
**Transition:** confirm_order (PENDING → CONFIRMED)  
**Purpose:** Determines if a pending order can be automatically confirmed

**Validation Logic:**
- Referenced pet exists and is in ACTIVE state
- Referenced owner exists and is in ACTIVE state
- Pet belongs to the specified owner
- Scheduled date is at least 24 hours in the future
- Service type is valid and available
- Cost is within reasonable range for service type
- Payment method is valid

**Implementation Approach:**
```
1. Validate entity references
   - Get pet by petId using EntityService
   - Verify pet state is ACTIVE
   - Get owner by ownerId using EntityService
   - Verify owner state is ACTIVE

2. Check ownership relationship
   - Verify pet.ownerId matches order.ownerId
   - Ensure owner has permission for this pet

3. Validate scheduling
   - Check scheduledDate is at least 24 hours from now
   - Verify service type is supported
   - Check service availability for the date

4. Validate business rules
   - Cost is positive and within expected range
   - Payment method is accepted
   - Special instructions are reasonable

5. Return success if all validations pass
```

**Success Condition:** All validation checks pass  
**Failure Condition:** Any validation check fails

---

### 3. ServiceAvailabilityCriterion

**Entity:** PetCareOrder  
**Transition:** start_service (CONFIRMED → IN_PROGRESS)  
**Purpose:** Determines if a confirmed service can begin (optional criterion for automatic start)

**Validation Logic:**
- Scheduled date and time has arrived
- Service provider is available
- Pet is present and ready for service
- No conflicting appointments exist
- Required equipment/facilities are available

**Implementation Approach:**
```
1. Check timing
   - Current time is at or after scheduledDate
   - Service window is still valid (not too late)

2. Verify readiness
   - Service provider is on duty
   - Required facilities are available
   - No emergency situations preventing service

3. Check pet status
   - Pet is still in ACTIVE state
   - No recent health issues reported
   - Owner is reachable if needed

4. Return success if service can begin
```

**Success Condition:** Service can begin immediately  
**Failure Condition:** Service cannot start due to constraints

---

## Pet Entity Criteria

### 4. PetHealthCriterion

**Entity:** Pet  
**Transition:** activate_pet (REGISTERED → ACTIVE)  
**Purpose:** Optional criterion to verify pet health status before activation

**Validation Logic:**
- Pet age is within acceptable range (0-30 years)
- Weight is reasonable for the species and breed
- No critical health flags in health notes
- Recent checkup date if required for certain services

**Implementation Approach:**
```
1. Validate basic health data
   - Age is positive and reasonable (0-30 years)
   - Weight is positive and within species norms
   - No "CRITICAL" flags in healthNotes

2. Check health requirements
   - For certain species, verify recent checkup
   - Ensure vaccination status if required
   - Check for any quarantine requirements

3. Return success if health status is acceptable
```

**Success Condition:** Pet health status is acceptable for activation  
**Failure Condition:** Health concerns prevent activation

---

### 5. PetOrderEligibilityCriterion

**Entity:** Pet  
**Transition:** Used in order validation (not direct pet transition)  
**Purpose:** Determines if a pet is eligible for specific service types

**Validation Logic:**
- Pet is in ACTIVE state
- Pet species is compatible with requested service
- Pet age/size meets service requirements
- No recent service conflicts
- Owner account is in good standing

**Implementation Approach:**
```
1. Check pet status
   - Pet state is ACTIVE
   - Pet has required information for service type

2. Validate service compatibility
   - Species matches service requirements
   - Age/weight within service limits
   - No conflicting recent services

3. Check owner relationship
   - Owner is ACTIVE
   - No outstanding payment issues
   - Owner has permission for this service type

4. Return success if pet is eligible
```

**Success Condition:** Pet meets all service eligibility requirements  
**Failure Condition:** Pet does not meet service requirements

---

## Business Rule Criteria

### 6. PaymentValidationCriterion

**Entity:** PetCareOrder  
**Transition:** Used in order processing  
**Purpose:** Validates payment information and processing capability

**Validation Logic:**
- Payment method is supported
- Payment amount matches order cost
- Payment authorization is valid
- No fraud indicators detected

**Implementation Approach:**
```
1. Validate payment method
   - Method is in supported list
   - Payment details are complete
   - Authorization is current

2. Check payment amount
   - Amount matches order cost exactly
   - Currency is correct
   - No suspicious pricing

3. Fraud detection
   - Payment pattern is normal
   - No blacklisted payment sources
   - Geographic consistency checks

4. Return success if payment is valid
```

**Success Condition:** Payment can be processed successfully  
**Failure Condition:** Payment validation fails

---

### 7. SchedulingConflictCriterion

**Entity:** PetCareOrder  
**Transition:** Used in order confirmation  
**Purpose:** Checks for scheduling conflicts with existing appointments

**Validation Logic:**
- No overlapping appointments for the same pet
- Service provider availability
- Facility capacity not exceeded
- No maintenance windows during scheduled time

**Implementation Approach:**
```
1. Check pet conflicts
   - Search existing orders for same petId
   - Find orders with overlapping time windows
   - Verify no conflicts exist

2. Check resource availability
   - Service provider schedule is free
   - Required facilities are available
   - Equipment is not reserved

3. Check system constraints
   - No planned maintenance during service time
   - Facility capacity limits not exceeded
   - Emergency time slots are preserved

4. Return success if no conflicts found
```

**Success Condition:** No scheduling conflicts detected  
**Failure Condition:** Scheduling conflict prevents booking

## Criteria Implementation Guidelines

1. **Keep Simple**: Criteria should focus on specific, testable conditions
2. **Fast Execution**: Criteria should execute quickly to avoid workflow delays
3. **Clear Logic**: Each criterion should have a single, well-defined purpose
4. **Error Handling**: Gracefully handle missing data or system errors
5. **Logging**: Log criterion decisions for audit and debugging purposes
6. **Stateless**: Criteria should not maintain state between evaluations
