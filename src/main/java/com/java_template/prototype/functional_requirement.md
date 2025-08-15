# Final Functional Requirements

## Complete Requirement (verbatim)
- Build a simple sample order management system with the ability to import products and users.
- Users are two types: Admin and Customers.
- A customer can create a shopping cart of products and then check out.

## Finalized Functional Details (as confirmed)

Entities: Products, Users (Admin, Customer), ShoppingCart, Order, Inventory, Payment, Shipment

Product fields: id, sku, name, description, price, currency, stockQuantity, weight, dimensions, category, images, attributes, importSource

Customer fields: id, firstName, lastName, email, phone, billingAddress, shippingAddress, passwordHash, roles (Admin or Customer), createdAt, isActive

Import behavior: Support importing products and users (e.g., CSV or API). Imported users must be assigned role Admin or Customer and imported products must populate stockQuantity and attributes.

Checkout workflow: validate cart contents, check stockQuantity for each product, reserve or decrement stock, process payment (capture or authorize based on configuration), create Order record with status (e.g., PENDING, PAID), send confirmation notification to customer, and trigger shipment creation/notification.

APIs: Provide endpoints for importing products and users, CRUD for Products and Users, Cart management (add/remove items, view cart), and Checkout endpoint that triggers the checkout workflow.