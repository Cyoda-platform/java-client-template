# Requirement Specification: Simple Order Management System

## Overview
Build a simple order management system with the following core features:

- Import products and users into the system.
- Two types of users:
  - **Admin**: Manages the system.
  - **Customer**: Can browse products, create shopping carts, and checkout.
- Shopping cart functionality enabling Customers to add products and perform checkout.

---

## Functional Requirements

### 1. User Management
- Support importing users in bulk (e.g., from CSV or JSON).
- Users have two roles:
  - **Admin**
  - **Customer**
- Authentication and authorization must distinguish between Admin and Customer roles.
- Admin users have elevated privileges (e.g., product management, user management).
- Customer users can browse products, manage their shopping cart, and checkout.

### 2. Product Management
- Support importing products in bulk (e.g., from CSV or JSON).
- Each product should have:
  - Unique identifier (e.g., SKU)
  - Name
  - Description
  - Price
  - Available stock quantity
- Admin users can manage products (create, update, delete).

### 3. Shopping Cart
- Customers can create and maintain a shopping cart.
- Customers can add products to the cart with specified quantities.
- The system should verify product availability (stock) on add.
- Customers can view, update (change quantities), or remove items from the cart.
- Checkout process:
  - Validate cart items against stock.
  - Deduct product stock accordingly.
  - Generate an order record.
  - Clear the shopping cart upon successful checkout.

---

## Technical Details

### Platform & Technology
- **Programming Language:** Java 21 Spring Boot (chosen as per Cyoda assistant best practice)
- **Architecture:** Event-driven, Cyoda design values applied:
  - Core component is an **Entity** representing domain objects (User, Product, Cart, Order).
  - Each Entity has a **workflow triggered by events** (e.g., user import event, add-to-cart event, checkout event).
  - Integration with Trino for querying (if applicable to product/user data or analytics).
  - Dynamic workflows handling order lifecycle and user interactions.

### APIs

#### User Import API
- Endpoint: `POST /api/admin/users/import`
- Accepts bulk user data in JSON or CSV format.
- Parses and creates users with roles Admin or Customer.
- Triggers user creation events in the workflow.

#### Product Import API
- Endpoint: `POST /api/admin/products/import`
- Accepts bulk product data in JSON or CSV format.
- Parses and creates product entities.
- Triggers product creation/update events.

#### Shopping Cart APIs (Customer)
- `POST /api/customers/{customerId}/cart/items`
  - Add product with quantity to the customer's cart.
  - Validates stock availability.
- `GET /api/customers/{customerId}/cart`
  - Retrieve current cart items.
- `PUT /api/customers/{customerId}/cart/items/{itemId}`
  - Update quantity of a product in the cart.
- `DELETE /api/customers/{customerId}/cart/items/{itemId}`
  - Remove a product from the cart.
- `POST /api/customers/{customerId}/cart/checkout`
  - Trigger checkout workflow.
  - Validates stock and creates order entity.
  - Deducts stock from products.
  - Clears cart on success.

#### Order Management API
- `GET /api/customers/{customerId}/orders`
  - List customer orders.
- `GET /api/admin/orders`
  - Admin view of all orders (with filtering options).

---

## Event-Driven Workflow Examples

- **UserImportEvent:** Triggers creation of user entities, assigns roles.
- **ProductImportEvent:** Triggers creation or update of product entities.
- **AddToCartEvent:** Validates stock, updates cart entity state.
- **CheckoutEvent:** Validates cart, creates order entity, updates stock, clears cart.
- **OrderCreatedEvent:** Notifies relevant systems or updates order status.

---

## Security & Access Control
- Role-based access control (RBAC):
  - Admin endpoints secured for Admin users only.
  - Customer endpoints secured for respective authenticated customers.
- Authentication via JWT or session-based mechanism.

---

## Persistence
- Entities persisted in a relational database (e.g., PostgreSQL).
- Entity models:
  - User (id, name, email, role, passwordHash, etc.)
  - Product (id, SKU, name, description, price, stock)
  - Cart (id, customerId, list of CartItems)
  - CartItem (productId, quantity)
  - Order (id, customerId, orderItems, status, timestamps)
  - OrderItem (productId, quantity, price)

---

## Summary
This system will be a Java 21 Spring Boot application structured around Cyoda’s event-driven, workflow-centric architecture. It supports product and user imports, distinguishes Admin and Customer roles, and offers Customers a shopping cart with a checkout workflow, maintaining business logic around stock validation and order creation.