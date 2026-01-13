# Order Management System (OMS) Requirements

Goal: Build an Order Management System for a retail trading platform that supports order lifecycle management, basic risk checks, and execution routing.

Functional Requirements:

1. Order Creation
- Support limit and market orders.
- Capture fields: orderId, symbol, side (buy/sell), quantity, price (optional for market), orderType, timeInForce, traderId, timestamp.

2. Order Validation
- Validate mandatory fields, positive quantity, and supported symbols.
- Apply basic risk checks: max order size per trader, max notional per day.

3. Order Routing
- Route validated orders to execution venues based on symbol and available liquidity.
- Support configurable routing rules (direct, smart-routers).

4. Order Lifecycle
- States: NEW, VALIDATED, ROUTED, ACKNOWLEDGED, FILLED, PARTIALLY_FILLED, CANCELLED, REJECTED.
- Support partial fills and fills with multiple execution reports.

5. Execution Reports
- Emit execution reports containing: executionId, orderId, filledQuantity, remainingQuantity, price, status, timestamp.

6. Cancellations
- Support cancel requests with validation and propagation to venue.

7. Auditing & Persistence
- Persist all order events for audit and replay.

Non-functional Requirements:
- Low latency for routing decision (<200ms).
- Scalable to 10k orders/sec.
- Configurable via environment variables.

Deliverables:
- Entities: Order, ExecutionReport, Trader, Venue
- Workflows: OrderLifecycle workflow processing state transitions and processors for validation, routing, and persistence.

