# Requirement: Minimal e-commerce OMS backend on Cyoda that powers the Lovable UI

Create a minimal e-commerce OMS backend on Cyoda that powers the Lovable UI. Use Cyoda entities + workflows with the exact state names the UI expects. Must align with Lovable UI (entities & workflows): Product, Cart, Order, User, Address; Cart: NEW → ACTIVE → CHECKING_OUT → CONVERTED Order: WAITING_TO_FULFILL → PICKING → SENT User Identification: ANON → IDENTIFIED

## Entities & Minimal Schemas
Use Cyoda Entities with JSON schemas (all fields snakeCase or camelCase consistently). Add sensible indexes on sku, email, orderNumber.

Product
{ "sku": "string", "name": "string", "description": "string?", "price": "number", "quantityAvailable": "number", "category": "string?" }

Cart
{ "cartId": "string", "userId": "string?", "status": "string: NEW|ACTIVE|CHECKING_OUT|CONVERTED", "lines": [ { "sku": "string", "name": "string", "price": "number", "qty": "number" } ], "totalItems": "number", "grandTotal": "number", "createdAt": "datetime", "updatedAt": "datetime" }

User
{ "userId": "string", "name": "string", "email": "string", "phone": "string?" }

Address
{ "addressId": "string", "userId": "string", "line1": "string", "city": "string", "postcode": "string", "country": "string" }

Order
{ "orderId": "string", "orderNumber": "string", "userId": "string", "shippingAddressId": "string", "lines": [ { "sku": "string", "name": "string", "unitPrice": "number", "qty": "number", "lineTotal": "number" } ], "totals": { "items": "number", "grand": "number" }, "status": "string: WAITING_TO_FULFILL|PICKING|SENT", "createdAt": "datetime" }

## Light validation (prototype)
- Cart: lines.length ≥ 1, each qty ≥ 1.
- Checkout: require name, email, line1, city, postcode, country.
- At order placement: ensure qty ≤ quantityAvailable for each line; if not, reject.

## Workflows & Transitions (FSM)

A) CartFlow
- States: NEW → ACTIVE → CHECKING_OUT → CONVERTED
- Transitions & Effects:
  - CREATE_CART (system)  
    - Pre: no cart;  
    - Post: create Cart with status=NEW, then move to ACTIVE.
  - ADD_ITEM(sku, qty)  
    - From: ACTIVE  
    - Effect: add line or increment qty; RecalculateTotals.
  - UPDATE_QTY(sku, qty)  
    - From: ACTIVE  
    - Effect: set qty; if qty=0 remove line; RecalculateTotals.
  - REMOVE_ITEM(sku)  
    - From: ACTIVE  
    - Effect: remove line; RecalculateTotals.
  - OPEN_CHECKOUT  
    - From: ACTIVE → CHECKING_OUT.
  - CHECKOUT  
    - From: CHECKING_OUT → CONVERTED  
    - Effect: emit OrderRequested event (used by Checkout orchestration).

Processors (sync unless noted):
- RecalculateTotals (sum of qty and price).  
- All mutations update totalItems, grandTotal, updatedAt.

B) IdentityFlow (User Identification)
- States: ANON → IDENTIFIED
- Transition: SUBMIT_CHECKOUT_DETAILS(form): Upsert User by email; create Address (linked to user). Move to IDENTIFIED and return { userId, addressId }.

C) OrderLifecycle
- States: WAITING_TO_FULFILL → PICKING → SENT
- Transitions:
  - PLACE_ORDER(cartId, userId, addressId): Creates Order snapshotting cart lines & totals. Sets status=WAITING_TO_FULFILL. (Prototype) decrement Product.quantityAvailable for each line.
  - START_PICKING(orderId): WAITING_TO_FULFILL → PICKING.
  - MARK_SENT(orderId, tracking?): PICKING → SENT.

## Orchestrations (Happy Path)
- Checkout Sequence
  - UI calls OPEN_CHECKOUT → Cart CHECKING_OUT.
  - UI submits form → SUBMIT_CHECKOUT_DETAILS → upsert User, create Address.
  - UI calls PLACE_ORDER with { cartId, userId, addressId }: Validate stock (qty ≤ quantityAvailable), else fail. Create Order with status=WAITING_TO_FULFILL. Reduce quantityAvailable per Product. Return { orderId, orderNumber, status }.
  - (Fulfillment moves via Swagger in prototype: START_PICKING, MARK_SENT.)

## API Surface (for Lovable UI)
Expose simple REST endpoints that map cleanly from the UI:

Products
- GET /entity/Product?search=&category= — list + search (by name/description; filter by category).
- POST /entity/Product — create (for seeding/demo via Swagger or demo page).
- PATCH /entity/Product/{sku} — adjust quantityAvailable (used at order placement).

Cart
- POST /cart — create (or return existing) cart; initialize NEW→ACTIVE.
- POST /cart/{cartId}/lines { sku, qty } — ADD_ITEM.
- PATCH /cart/{cartId}/lines/{sku} { qty } — UPDATE_QTY.
- DELETE /cart/{cartId}/lines/{sku} — REMOVE_ITEM.
- POST /cart/{cartId}/open-checkout — OPEN_CHECKOUT.
- GET /cart/{cartId} — fetch current cart (for badge and pages).

Identity
- POST /checkout/identify { name, email, phone, address }  
  - Executes SUBMIT_CHECKOUT_DETAILS → returns { userId, addressId }.

Order
- POST /checkout/place-order { cartId, userId, addressId }  
  - Executes PLACE_ORDER → returns { orderId, orderNumber, status: "WAITING_TO_FULFILL" }.
- GET /entity/Order/{orderId} — read order (for confirmation page).

Fulfillment (Swagger-only for prototype)
- POST /order/{orderId}/start-picking — START_PICKING.
- POST /order/{orderId}/mark-sent — MARK_SENT.

## Implementation Hints (Cyoda)
- Ensure idempotency via requestId on cart mutations and order placement.
- Add unique indexes: Product.sku, User.email, Order.orderNumber.
- Emit basic events: CartUpdated, OrderRequested, OrderCreated, OrderStatusChanged.
- Keep everything single-location stock and no payments/taxes (prototype scope).

## Success criteria
- UI can list/search products, maintain cart badge, edit cart, perform checkout form, and land on order confirmation with status=WAITING_TO_FULFILL.
- Swagger can advance orders through PICKING → SENT.
- All state names match the Lovable UI’s expectations.