# Customer Entity Functional Requirements

## Overview
The Customer entity represents users who can purchase pets from the store. Each customer has personal information, contact details, and account status.

## Entity Structure

### Required Fields
- **customerId** (String) - Business identifier for the customer (e.g., "CUST001", "USER123")
- **username** (String) - Unique username for login
- **email** (String) - Customer email address
- **firstName** (String) - Customer first name
- **lastName** (String) - Customer last name

### Optional Fields
- **password** (String) - Encrypted password (for authentication)
- **phone** (String) - Customer phone number
- **address** (CustomerAddress) - Customer address information
  - **street** (String) - Street address
  - **city** (String) - City
  - **state** (String) - State/Province
  - **zipCode** (String) - ZIP/Postal code
  - **country** (String) - Country
- **dateOfBirth** (LocalDate) - Customer date of birth
- **preferences** (CustomerPreferences) - Customer preferences
  - **preferredPetTypes** (List<String>) - Preferred pet categories
  - **communicationPreferences** (List<String>) - Email, SMS, etc.
  - **newsletter** (Boolean) - Newsletter subscription
- **loyaltyPoints** (Integer) - Customer loyalty points
- **totalOrders** (Integer) - Total number of orders placed
- **totalSpent** (Double) - Total amount spent
- **lastLoginAt** (LocalDateTime) - Last login timestamp
- **createdAt** (LocalDateTime) - Account creation timestamp
- **updatedAt** (LocalDateTime) - Last update timestamp

## Business Rules

### Validation Rules
1. Customer ID must be unique across all customers
2. Username must be unique and cannot be empty
3. Email must be valid format and unique
4. First name and last name are required
5. Phone number must be valid format if provided
6. Email must be unique across all customers
7. Username must be unique across all customers

### Status Management
Customer status is managed through workflow states:
- **active** - Customer account is active and can place orders
- **inactive** - Customer account is temporarily disabled
- **suspended** - Customer account is suspended due to policy violations
- **deleted** - Customer account is marked for deletion

## Entity Relationships
- Customer can have multiple Orders (customerId field in Order)
- Customer preferences can reference Pet categories
