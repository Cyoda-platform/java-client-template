```markdown
# Lovable UI Java Application Requirements Specification

---

## Overview

Build a **Java 21 Spring Boot** application powering the Lovable UI using **only Cyoda’s standard REST APIs**.  
No custom endpoints allowed; strictly use `/entity/<Entity>` CRUD + optional SQL reads.

The application covers end-to-end e-commerce flow:  
**Product list → Cart → Checkout (User profile with inline address) → Dummy Payment (auto-approved after 3s) → Order created (auto-split shipments) → Picking loop (audit) → Waiting to send → Sent → Delivered**

---

## 0) Entities Definition

### Product  
- Unique: `sku` (string)  
- Fields:  
  - `sku*`: string  
  - `name*`: string  
  - `description?`: string  
  - `price*`: number  
  - `quantityAvailable*`: number  
  - `category?`: string  

### Cart  
- Unique: `cartId` (string)  
- Fields:  
  - `cartId*`: string  
  - `userId?`: string  
  - `status*`: enum `"NEW" | "ACTIVE" | "CHECKING_OUT" | "CONVERTED"`  
  - `lines*`: array of `{ sku, name, price, qty }`  
  - `totalItems*`: number  
  - `grandTotal*`: number  
  - `reservationBatchId?`: string  
  - `createdAt`, `updatedAt`: datetime  

### User  
- Unique: `email` (string)  
- Fields:  
  - `userId*`: string  
  - `name*`: string  
  - `email*`: string  
  - `phone?`: string  
  - `address`: inline object  
    - `line1*`: string  
    - `city*`: string  
    - `postcode*`: string  
    - `country*`: string  
    - `updatedAt`: datetime  

### Payment (Dummy)  
- Unique: `paymentId` (string)  
- Fields:  
  - `paymentId*`: string  
  - `cartId*`: string  
  - `amount*`: number  
  - `status*`: enum `"INITIATED" | "PAID" | "FAILED" | "CANCELED"`  
  - `provider*`: `"DUMMY"`  
  - `createdAt`, `updatedAt`: datetime  

### Order  
- Unique: `orderNumber` (short ULID string)  
- Fields:  
  - `orderId*`: string  
  - `orderNumber*`: string (ULID)  
  - `userId*`: string  
  - `shippingAddress*`: snapshot of User address at checkout  
    - `line1`: string  
    - `city`: string  
    - `postcode`: string  
    - `country`: string  
  - `lines*`: array of `{ sku, name, unitPrice, qty, lineTotal }`  
  - `totals*`: `{ items, grand }`  
  - `status*`: enum `"WAITING_TO_FULFILL" | "PICKING" | "WAITING_TO_SEND" | "SENT" | "DELIVERED"`  
  - `createdAt`, `updatedAt`: datetime  

### Shipment  
- Unique: `shipmentId` (string)  
- Fields:  
  - `shipmentId*`: string  
  - `orderId*`: string  
  - `status*`: enum `"PICKING" | "WAITING_TO_SEND" | "SENT" | "DELIVERED"`  
  - `carrier?`: string  
  - `trackingNumber?`: string  
  - `lines*`: array of `{ sku, qtyOrdered, qtyPicked, qtyShipped }`  
  - `createdAt`, `updatedAt`: datetime  

### Reservation  
- Unique: `reservationId` (string)  
- Fields:  
  - `reservationId*`: string  
  - `reservationBatchId*`: string  
  - `cartId*`: string  
  - `sku*`: string  
  - `qty*`: number  
  - `expiresAt*`: datetime (TTL 4h from last cart action)  
  - `status*`: enum `"ACTIVE" | "EXPIRED" | "RELEASED" | "COMMITTED"`  

### PickLedger  
- Unique: `pickId` (string)  
- Fields:  
  - `pickId*`: string  
  - `orderId*`: string  
  - `shipmentId*`: string  
  - `sku*`: string  
  - `delta*`: number  
  - `at*`: datetime  
  - `actor?`: string  
  - `note?`: string  

---

## 1) Workflows

### CartFlow  
- States: `NEW → ACTIVE → CHECKING_OUT → CONVERTED`  
- Behavior:  
  - Every add/increment/decrement/remove refreshes Reservation TTL (4h)  
  - Reservations created/released per SKU  
  - Totals recalculated on each cart change  

### IdentityFlow  
- States: `ANON → IDENTIFIED`  
- `SUBMIT_CHECKOUT_DETAILS` event: upsert User by email  
- Address updated inline on User entity  
- On Order creation, snapshot User.address into Order.shippingAddress  

### PaymentFlow (Dummy)  
- States: `INITIATED → PAID | FAILED | CANCELED`  
- `START_DUMMY_PAYMENT` event creates Payment with status `INITIATED`  
- Auto processor marks Payment as `PAID` after 3 seconds  

### OrderLifecycle (+ Auto-split Shipments)  
- States: `WAITING_TO_FULFILL → PICKING → WAITING_TO_SEND → SENT → DELIVERED`  
- `CREATE_ORDER_FROM_PAID` event:  
  - commit Reservations  
  - decrement stock on Products  
  - snapshot User.address into Order.shippingAddress  
  - auto-split Order lines into Shipments  
  - set Order status to `PICKING`  
- Picking loop: logs PickLedger entries  
- Shipments auto advance when fully picked  
- Partial shipments allowed  
- Order aggregates shipment states into overall Order status  

---

## 2) Orchestration (Happy Path)

1. User browses Products:  
   - `GET /entity/Product`  
2. User adds items to Cart:  
   - Cart created `NEW → ACTIVE`  
   - Reservations created with TTL = 4h  
3. Cart edits:  
   - Reservations adjusted  
   - TTL refreshed  
4. Checkout:  
   - Submit checkout details → upsert User with inline address  
5. Payment:  
   - Trigger dummy payment → auto `PAID` after 3 seconds  
6. Order creation:  
   - `CREATE_ORDER_FROM_PAID` commits reservations, decrements stock, snapshots User.address, auto-splits shipments, sets Order to `PICKING`  
7. Picking updates:  
   - `PICKING_UPDATE` adds PickLedger entries  
8. Shipments marked `SENT` then `DELIVERED`  
9. Order status aggregates to `DELIVERED`  

---

## 3) API Surface (UI uses only standard endpoints)

| Entity   | Methods                 | Notes                                  |
|----------|-------------------------|---------------------------------------|
| Product  | `GET/POST/PATCH /entity/Product`                 | CRUD on products                      |
| Cart     | `POST /entity/Cart`<br>`PATCH /entity/Cart/{id}`<br>`GET /entity/Cart/{id}` | Create, update cart and get cart     |
| User     | `POST /entity/User`<br>`PATCH /entity/User/{id}` | Upsert user with inline address      |
| Payment  | `POST /entity/Payment`                             | Create payment (auto transitions)    |
| Order    | Created via processors                              | Use PATCH on shipments to advance    |
| Shipment | Created via processors                              | Use PATCH on shipments to advance    |
| PickLedger | Created via processors                            | Audit logs during picking             |

*No custom endpoints allowed; only standard `/entity/<Entity>` REST API surface.*

---

## 4) Processors

### Cart Processors  
- `ReserveOnAdd` — create reservations when items added  
- `ReserveDelta` — adjust reservations on qty change  
- `ReleaseReservationForSku` — release reservations on remove  
- `RecalculateTotals` — update `totalItems` and `grandTotal`  
- `RefreshReservationTTL` — extend reservation TTL (4h) on cart activity  

### Identity Processor  
- `UpsertUserWithAddressInline` — create or update User including inline address  

### Payment Processors  
- `CreateDummyPayment` — create Payment with status `INITIATED`  
- `AutoMarkPaidAfter3s` — after 3 seconds, transition Payment to `PAID`  

### Order Processors  
- `CreateOrderFromPaid` —  
  - commit reservations  
  - decrement Product stock  
  - snapshot User.address into Order.shippingAddress  
  - create Order entity with ULID orderNumber  
  - auto-split Order lines into Shipments  
  - set Order status to `PICKING`  

### Fulfillment Processors  
- `PickUpdateAndAudit` — process picking updates, create PickLedger entries  
- `AggregateOrderStatus` — aggregate shipment statuses to update overall Order status  

### Background Tasks  
- `ExpireReservations` — expire reservations when TTL passes (4h inactivity)  

---

## 5) Guards (Business Rules)

- Cart **must not be empty** at checkout  
- Reservations **must be ACTIVE** to be committed in Order  
- Address **always taken from User** entity and snapshotted into Order at checkout  
- Order `orderNumber` **must be ULID** (short lexicographically sortable unique ID)  

---

## Summary

- **Java 21 Spring Boot** application using Cyoda standard REST API endpoints only  
- Entities: Product, Cart, User, Payment, Order, Shipment, Reservation, PickLedger  
- Workflows: CartFlow, IdentityFlow, PaymentFlow, OrderLifecycle  
- Processors implement business logic and automatic transitions, including dummy payment auto-approval and auto-split shipments  
- Orchestrate happy path scenario strictly using CRUD and PATCH on `/entity/<Entity>` endpoints  
- Enforce guards and data integrity rules  
- Background task for reservation expiration  

---

This specification preserves all business logic, entity structure, workflows, processor responsibilities, API endpoints, and guard conditions as requested.
```