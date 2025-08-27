Build a Java application to power the Lovable UI using only Cyoda’s standard REST APIs (/entity/<Entity> CRUD + optional SQL reads). No custom endpoints. Functionality: product list → cart → checkout (user profile with inline address) → dummy payment auto-approves after 3s → order created (auto-split shipments) → picking loop (audit) → waiting to send → sent → delivered.

0) Entities
Product  
(unique: sku)
sku*: string
name*: string
description?: string
price*: number
quantityAvailable*: number
category?: string
Cart
cartId*: string
userId?: string
status*: "NEW" | "ACTIVE" | "CHECKING_OUT" | "CONVERTED"
lines*: [ { sku, name, price, qty } ]
totalItems*: number
grandTotal*: number
reservationBatchId?: string
createdAt, updatedAt
User  
(unique: email; includes address inline)
userId*: string
name*: string
email*: string
phone?: string
address: {
  line1*: string
  city*: string
  postcode*: string
  country*: string
  updatedAt: datetime
}
Payment (dummy)
paymentId*: string
cartId*: string
amount*: number
status*: "INITIATED" | "PAID" | "FAILED" | "CANCELED"
provider*: "DUMMY"
createdAt, updatedAt
Order  
(unique: orderNumber)
orderId*: string
orderNumber*: string        // short ULID
userId*: string
shippingAddress*: {         // snapshot from User at checkout
  line1: string
  city: string
  postcode: string
  country: string
}
lines*: [ { sku, name, unitPrice, qty, lineTotal } ]
totals*: { items, grand }
status*: "WAITING_TO_FULFILL" | "PICKING" | "WAITING_TO_SEND" | "SENT" | "DELIVERED"
createdAt, updatedAt
Shipment  
(for auto-split)
shipmentId*: string
orderId*: string
status*: "PICKING" | "WAITING_TO_SEND" | "SENT" | "DELIVERED"
carrier?: string
trackingNumber?: string
lines*: [ { sku, qtyOrdered, qtyPicked, qtyShipped } ]
createdAt, updatedAt
Reservation  
(cart-time stock hold with TTL)
reservationId*: string
reservationBatchId*: string
cartId*: string
sku*: string
qty*: number
expiresAt*: datetime      // 4h from last cart action
status*: "ACTIVE" | "EXPIRED" | "RELEASED" | "COMMITTED"
PickLedger
pickId*: string
orderId*: string
shipmentId*: string
sku*: string
delta*: number
at*: datetime
actor?: string
note?: string

1) Workflows
CartFlow
States: NEW → ACTIVE → CHECKING_OUT → CONVERTED
Every add/decrement/remove refreshes Reservation TTL (4h).


Reservations created/released per SKU.


Totals recalculated.


IdentityFlow
States: ANON → IDENTIFIED
SUBMIT_CHECKOUT_DETAILS → upsert User (by email).


Address is updated inline on the User.


At order creation, snapshot user.address into Order.shippingAddress.


PaymentFlow (dummy; auto 3s)
States: INITIATED → PAID | FAILED | CANCELED
START_DUMMY_PAYMENT → create Payment INITIATED → auto processor sets PAID after 3s.


OrderLifecycle (+ auto-split shipments)
States: WAITING_TO_FULFILL → PICKING → WAITING_TO_SEND → SENT → DELIVERED
CREATE_ORDER_FROM_PAID → commit reservations, decrement stock, snapshot User.address into Order, auto-split into Shipments.


Picking loop logs to PickLedger; shipments auto advance when fully picked.


Partial shipments allowed.


Order aggregates shipment states into overall status.



2) Orchestration (Happy Path)
User browses Products (GET /entity/Product).


Adds items: Cart created (NEW→ACTIVE), Reservations created, TTL=4h.


Cart edits: Reservations adjusted, TTL refreshed.


Checkout: SUBMIT_CHECKOUT_DETAILS → upsert User (with inline address).


Payment: START_DUMMY_PAYMENT → auto PAID after 3s.


Order: CREATE_ORDER_FROM_PAID → commit reservations, decrement stock, snapshot User.address, auto-split shipments, set Order PICKING.


Picking updates: PICKING_UPDATE adds PickLedger entries.


Shipments marked SENT then DELIVERED; Order aggregates status to DELIVERED.



3) API Surface (UI uses only standard endpoints)
Products: GET/POST/PATCH /entity/Product


Cart: POST /entity/Cart, PATCH /entity/Cart/{id}, GET /entity/Cart/{id}


User (with address): POST /entity/User, PATCH /entity/User/{id}


Payment: POST /entity/Payment (auto to PAID after 3s)


Order/Shipment/Picking: created via processors, advanced via PATCH /entity/Shipment/{id} (Swagger for ops)



4) Processors
Cart: ReserveOnAdd, ReserveDelta, ReleaseReservationForSku, RecalculateTotals, RefreshReservationTTL


Identity: UpsertUserWithAddressInline


Payment: CreateDummyPayment, AutoMarkPaidAfter3s


Order: CreateOrderFromPaid, AutoSplitShipments


Fulfillment: PickUpdateAndAudit, AggregateOrderStatus


Background: ExpireReservations



5) Guards
Cart must not be empty at checkout.


Reservations must be ACTIVE.


Address is always taken from User and snapshotted into Order.


ULID required for Order.orderNumber.



