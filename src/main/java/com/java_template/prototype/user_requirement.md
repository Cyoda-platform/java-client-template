```markdown
# Order Management System Requirements

## 1. User Management (Basic)
- Ability to create and manage user accounts, including:
  - Customers
  - Administrators
- Simple authentication system:
  - Login
  - Logout
- Role-based access control:
  - Administrators can view and update **all** orders.
  - Customers can view and manage their own orders.

## 2. Product Catalog
- Ability to bulk upload product catalog.
- Each product entry contains:
  - Name
  - Description
  - Price
  - Available quantity (stock)

## 3. Order Creation
- Customers can browse products.
- Customers can add products to a temporary selection (shopping cart).
- Customers can finalize and submit an order from the shopping cart.
- Order details must include:
  - Customer information
  - List of selected items with quantities
  - Price per item and total price
  - Order date

## 4. Basic Reporting (Administrator View)
- View a comprehensive list of all orders.
- Filter orders by:
  - Order status
  - Associated customer
- Stretch Goal: Simple sales summary including:
  - Total revenue
  - Number of orders

---

# 5. System Architecture

### Frontend (User Interface)
- Provides visual interface for customers and administrators.
- Key screens:
  - Login/Registration
  - Product listing and detail views
  - Shopping cart management
  - Checkout and order submission
  - Order history for customers
  - Administrator dashboard for product and order management, including reporting

### Backend (Application Logic Layer)
- Handles all business logic.
- Provides RESTful APIs for:
  - User authentication and management
  - Product catalog management (bulk upload, CRUD operations)
  - Shopping cart and order processing
  - Reporting and filtering for administrators
- Implements access control based on user roles.

### Data Storage
- Stores all data related to:
  - Users (unique ID, username, secure password hash, role)
  - Products (unique ID, name, description, price, stock quantity)
  - Orders (unique ID, associated user ID, order date, total amount, status)
  - Order Items (unique ID, associated order ID, product ID, quantity, price at purchase time)

---

# 6. Detailed Data Model

| Entity       | Fields                                                                                 | Relationships                      |
|--------------|----------------------------------------------------------------------------------------|----------------------------------|
| User         | id (UUID), username, passwordHash, role (e.g., CUSTOMER, ADMIN)                        | One-to-many with Orders           |
| Product      | id (UUID), name, description, price (decimal), stockQuantity (int)                    | One-to-many with OrderItems       |
| Order        | id (UUID), userId (FK), orderDate (timestamp), totalAmount (decimal), status (enum)    | One-to-many with OrderItems       |
| OrderItem    | id (UUID), orderId (FK), productId (FK), quantity (int), priceAtPurchase (decimal)     | Many-to-one with Order and Product|

---

# 7. Backend APIs (Java 21 Spring Boot recommended)

### User Management
- `POST /api/users/register` - Register new user (customer)
- `POST /api/users/login` - Authenticate user, return JWT or session token
- `POST /api/users/logout` - Logout user
- `GET /api/users/{id}` - Get user details (admin or self)
- `PUT /api/users/{id}` - Update user (admin or self)
  
### Product Catalog
- `POST /api/products/bulk-upload` - Bulk upload products (admin only)
- `GET /api/products` - List all products
- `GET /api/products/{id}` - Get product details
- `POST /api/products` - Add product (admin only)
- `PUT /api/products/{id}` - Update product (admin only)
- `DELETE /api/products/{id}` - Delete product (admin only)

### Shopping Cart & Order
- `GET /api/cart` - Get current user’s cart items
- `POST /api/cart` - Add product to cart (with quantity)
- `PUT /api/cart/{productId}` - Update quantity in cart
- `DELETE /api/cart/{productId}` - Remove product from cart
- `POST /api/orders` - Finalize and submit order from cart
- `GET /api/orders` - Get orders (admin: all orders; customer: own orders)
- `GET /api/orders/{id}` - Get order details
- `PUT /api/orders/{id}/status` - Update order status (admin only)

### Reporting (Admin)
- `GET /api/reports/orders` - List all orders with optional filters:
  - `?status=`
  - `?customerId=`
- `GET /api/reports/sales-summary` - Return total revenue and number of orders

---

# 8. Core Business Logic and Workflow

- User registration and authentication with secure password hashing.
- Role-based authorization to restrict access to admin-only APIs.
- Bulk upload validates product information and updates stock quantities.
- Shopping cart is session or user-associated, persists until order submission.
- Order creation checks product stock availability before confirmation.
- On order submission:
  - Deduct product quantities from stock.
  - Save order and order items with price at purchase time.
  - Generate order date timestamp.
- Admin can update order status (e.g., Pending, Shipped, Delivered, Cancelled).
- Reporting queries aggregate orders by filters and compute sales summary.

---

# 9. Stretch Recommendations (Optional)

- Use event-driven architecture with state machines to model order lifecycle states and transitions.
- Integrate dynamic workflows for complex order processing logic.
- Use Trino for advanced reporting and analytics over order data.
- Implement entity-centric design per Cyoda principles, where entities (User, Product, Order) have workflows triggered by events (e.g., order creation, stock update).

---

This specification preserves all business logic, data structures, APIs, and architecture details needed to develop the requested order management system in Java 21 Spring Boot.
```