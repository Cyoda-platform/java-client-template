Generate Cyoda OMS Backend (UI → App APIs → Cyoda Service)

   Objective

   Build a Spring Boot client application that exposes simple REST APIs for a browser UI (no login in the browser).
   The app will hold server-side Cyoda credentials and talk to Cyoda’s standard /entity/ APIs and workflows on behalf of the UI.

   Key demo rules
   • Anonymous checkout only (no user accounts).
   • Payment (dummy) auto-approves after ~3 seconds.
   • Stock policy: decrement Product.quantityAvailable on order creation (no reservations).
   • Shipping: single shipment per order.
   • Order number: short ULID.
   • Catalog filters: category, free-text (name/description), price range.
   • Product must include category and must use the attached Product schema verbatim for persistence/round-trip (UI list can use a slim view).

   ⸻

   Entities (in Cyoda)

   Product (use attached schema in full; persist & round-trip)

   Fields include at least:

   sku*: string (unique)
   name*: string
   description?: string
   price*: number
   quantityAvailable*: number
   category*: string
   warehouseId?: string
   ... (all other fields present in the attached Product schema: attributes/localizations/media/options/variants/bundles/inventory/compliance/relationships/events, etc.)

   Cart

   cartId*: string
   status*: "NEW" | "ACTIVE" | "CHECKING_OUT" | "CONVERTED"
   lines*: [ { sku, name, price, qty } ]
   totalItems*: number
   grandTotal*: number
   guestContact?: {
   name?: string, email?: string, phone?: string,
   address?: { line1?: string, city?: string, postcode?: string, country?: string }
   }
   createdAt, updatedAt

   Payment (dummy)

   paymentId*: string
   cartId*: string
   amount*: number
   status*: "INITIATED" | "PAID" | "FAILED" | "CANCELED"
   provider*: "DUMMY"
   createdAt, updatedAt

   Order

   orderId*: string
   orderNumber*: string // short ULID
   status*: "WAITING_TO_FULFILL" | "PICKING" | "WAITING_TO_SEND" | "SENT" | "DELIVERED"
   lines*: [ { sku, name, unitPrice, qty, lineTotal } ]
   totals*: { items, grand }
   guestContact*: {
   name: string, email?: string, phone?: string,
   address: { line1: string, city: string, postcode: string, country: string }
   }
   createdAt, updatedAt

   Shipment (single for demo)

   shipmentId*: string
   orderId*: string
   status*: "PICKING" | "WAITING_TO_SEND" | "SENT" | "DELIVERED"
   lines*: [ { sku, qtyOrdered, qtyPicked, qtyShipped } ]
   createdAt, updatedAt

   ⸻

   Workflows (in Cyoda)

   CartFlow

   States: NEW → ACTIVE → CHECKING_OUT → CONVERTED
   Transitions:
   • CREATE_ON_FIRST_ADD → create cart, add first line, recalc totals → ACTIVE
   • ADD_ITEM / DECREMENT_ITEM / REMOVE_ITEM → stay ACTIVE, recalc totals
   • OPEN_CHECKOUT → ACTIVE → CHECKING_OUT
   • CHECKOUT → CHECKING_OUT → CONVERTED (signals orchestration)

   PaymentFlow (dummy, auto 3s)

   States: INITIATED → PAID | FAILED | CANCELED
   Transitions:
   • START_DUMMY_PAYMENT → create Payment:INITIATED
   • AUTO_MARK_PAID → processor flips to PAID after ~3s

   OrderLifecycle (single shipment)

   States: WAITING_TO_FULFILL → PICKING → WAITING_TO_SEND → SENT → DELIVERED
   Transitions:
   • CREATE_ORDER_FROM_PAID:
   • Snapshot cart lines + guestContact into Order
   • Decrement each Product.quantityAvailable by ordered qty
   • Create one Shipment in PICKING
   • READY_TO_SEND / MARK_SENT / MARK_DELIVERED advance Shipment and derive Order status

   ⸻

   Backend (client app) — UI-Facing REST APIs (no browser auth)

   Create controller endpoints under /ui/**. The app calls Cyoda via server-side credentials using the template EntityService (or equivalent), never exposing service tokens to the browser.

   Products
   • GET /ui/products?search=&category=&minPrice=&maxPrice=&page=&pageSize=
   • Free-text on name/description, filter by category, price range on price.
   • Return slim list DTO for speed:
   { sku, name, description, price, quantityAvailable, category, imageUrl? }
   • Internally query full Product documents (attached schema) and map to the slim DTO.
   • GET /ui/products/{sku} → return the full Product document (entire attached schema).

   Cart
   • POST /ui/cart → create or return cart (on first add, initialize NEW→ACTIVE).
   • POST /ui/cart/{cartId}/lines { sku, qty } → add/increment; recalc totals.
   • PATCH /ui/cart/{cartId}/lines { sku, qty } → set/decrement (remove if qty=0); recalc totals.
   • POST /ui/cart/{cartId}/open-checkout → set CHECKING_OUT.
   • GET /ui/cart/{cartId} → read.

   Checkout (anonymous)
   • POST /ui/checkout/{cartId}
   Body:

   {
   "guestContact": {
   "name": "string",
   "email": "string?",
   "phone": "string?",
   "address": { "line1": "...", "city": "...", "postcode": "...", "country": "..." }
   }
   }

   Attach to Cart.guestContact.

   Payment (dummy)
   • POST /ui/payment/start { cartId } → create Payment:INITIATED; auto-PAID after ~3s (processor). Return { paymentId }.
   • GET /ui/payment/{paymentId} → poll status.

   Order
   • POST /ui/order/create { paymentId, cartId }
   Preconditions: Payment PAID. Execute CREATE_ORDER_FROM_PAID; returns { orderId, orderNumber, status }.
   • GET /ui/order/{orderId} → read order for confirmation/status.

   ⸻

   Processors (server-side)
   • RecalculateTotals (Cart)
   • CreateDummyPayment + AutoMarkPaidAfter3s (Payment)
   • CreateOrderFromPaid (Order: snapshot cart → order, decrement stock, create Shipment)

   ⸻

   Product Search/Filter Implementation

   Use Cyoda’s condition query (or SQL helper) to combine:
   • Free-text: CONTAINS on $.name OR $.description
   • Category: EQUALS on $.category
   • Price range: $.price >= minPrice and/or $.price <= maxPrice

   List view returns the slim DTO; detail view returns the full Product document (attached schema).

   ⸻

   Security & Config
   • No browser auth. UI calls only /ui/**.
   • Server stores Cyoda credentials (token or client credentials); never expose to browser.
   • Provide Swagger UI for manual Ops actions at /swagger-ui/index.html.

   ⸻

   Acceptance (happy path)

   UI lists products via /ui/products with category/search/price filters; product detail uses /ui/products/{sku} (full document).
   First “Add” creates cart; subsequent edits update lines and totals.
   Checkout posts guest contact + address (no account).
   Payment starts and auto-PAID after ~3s.
   Order is created (short ULID), stock is decremented, single Shipment created, and order progresses to DELIVERED.
   UI never calls Cyoda directly; all traffic is /ui/**.
   ⸻

   Notes for generation
   • Use your standard EntityService patterns to call /entity/Product|Cart|Payment|Order|Shipment.
   • Persist the entire Product document exactly as per the attached schema; list endpoint maps to a slim DTO, detail endpoint returns the whole document.
   • Keep code minimal and demo-focused; prefer synchronous processors where practical.

   Product schema:

   {
   "sku": "string", // required, unique
   "name": "string", // required
   "description": "string", // required
   "price": 0, // required: default/base price (fallback)
   "quantityAvailable": 0, // required: quick projection field
   "category": "string", // required
   "warehouseId": "string|null", // optional default primary node

   "attributes": {
   "brand": "string",
   "model": "string",
   "dimensions": { "l": "number", "w": "number", "h": "number", "unit": "cm" },
   "weight": { "value": "number", "unit": "kg" },
   "hazards": [{ "class": "UN3481", "transportNotes": "string" }],
   "custom": { "any": "json" } // open extension bag for teams
   },

   "localizations": {
   "defaultLocale": "en-GB",
   "content": [
   { "locale": "en-GB", "name": "string", "description": "string", "regulatory": { "ukca": true } },
   { "locale": "de-DE", "name": "string", "description": "string", "regulatory": { "ce": true }, "salesRestrictions": ["noLithiumBatteries"] }
   ]
   },

   "media": [
   { "type": "image", "url": "https://...", "alt": "string", "tags": ["hero"], "sha256": "..." },
   { "type": "doc", "url": "https://...", "title": "MSDS", "regionScope": ["EU"] }
   ],

   "options": {
   "axes": [
   { "code": "color", "values": ["black","silver","blue"] },
   { "code": "capacity", "values": ["128GB","256GB","512GB"] }
   ],
   "constraints": [
   { "if": { "color": "blue" }, "then": { "forbid": { "capacity": ["512GB"] } } },
   { "requires": [{ "option": "capacity", "oneOf": ["256GB","512GB"] }], "whenRegionIn": ["US","CA"] }
   ]
   },

   "variants": [
   {
   "variantSku": "string", // unique within product
   "optionValues": { "color": "black", "capacity": "256GB" },
   "attributes": { "weight": { "value": 0.02, "unit": "kg" } }, // overrides
   "barcodes": ["EAN:...","UPC:..."],
   "priceOverrides": {
   "base": 0, // optional override of product.price
   "priceBooks": ["pb:consumer-eu-tiered-2025"]
   },
   "inventoryPolicy": { "backorder": true, "maxBackorderDays": 30 }
   }
   ],

   "bundles": [
   {
   "type": "kit", // "kit" (shipped together) or "bundle" (virtual)
   "sku": "KIT-STARTER-01",
   "components": [
   { "ref": { "sku": "CASE-XL" }, "qty": 1, "optional": true, "defaultSelected": true,
   "constraints": [{ "ifVariant": { "option": "capacity", "in": ["512GB"] }, "then": { "forbid": true } }]
   },
   { "ref": { "sku": "CHARGER-45W" }, "qty": 1, "substitutions": [{ "sku": "CHARGER-65W", "whenRegionIn": ["US"] }] }
   ]
   }
   ],

   "inventory": {
   "nodes": [
   {
   "nodeId": "LON-01",
   "type": "Warehouse",
   "capacity": { "maxUnits": 100000 },
   "lots": [
   { "lotId": "LOT-A1", "mfgDate": "2025-01-10", "expires": "2027-01-10",
   "qty": 500, "serials": ["S100..."], "quality": "Released"
   },
   { "lotId": "LOT-Q1", "qty": 80, "quality": "Quarantine", "reason": "inspection" }
   ],
   "reservations": [
   { "ref": "order:O-12345", "variantSku": "…", "qty": 20, "until": "2025-09-15T18:00:00Z" }
   ],
   "inTransit": [
   { "po": "PO-998", "eta": "2025-09-05T12:00:00Z", "qty": 1000, "status": "Scheduled" }
   ]
   },
   { "nodeId": "AMS-3PL", "type": "3PL", "qtyOnHand": 320 }
   ],
   "policies": {
   "allocation": "earliest-expiry-first",
   "oversellGuard": { "maxPercent": 5 }
   }
   },

   "compliance": {
   "docs": [
   { "id": "MSDS-2025-01", "regions": ["EU","US"], "url": "https://..." },
   { "id": "UKCA-2025", "regions": ["UK"], "approved": true }
   ],
   "restrictions": [
   { "region": "CA", "rules": ["noAirTransport"], "reason": "Lithium content" }
   ]
   },

   "relationships": {
   "suppliers": [
   { "partyId": "SUP-FOXLINK", "contract": { "id": "C-2025-07", "incoterm": "DAP", "leadTimeDays": 21 } }
   ],
   "relatedProducts": [
   { "type": "accessory", "sku": "CASE-XL" },
   { "type": "replacement", "sku": "BAT-002" }
   ]
   },

   "events": [
   { "type": "ProductCreated", "at": "2025-08-20T09:00:00Z", "payload": { "sku": "…" } },
   { "type": "InventoryReceived", "at": "2025-08-25T16:30:00Z", "payload": { "nodeId": "LON-01", "lotId": "LOT-A1", "qty": 500 } },
   { "type": "ReservationCreated", "at": "2025-08-28T11:00:00Z", "payload": { "orderRef": "O-12345", "qty": 20 } }
   ]
   }