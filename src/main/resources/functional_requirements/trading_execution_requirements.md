# Institutional Trading Platform — Requirements (Trading & Execution First)

## Overview
Scope: Institutional trading platform focused on Equities and Listed Derivatives with US/EU compliance. Prioritizing Trading & Execution: Order Management System (OMS), Smart Order Routing (SOR), low-latency execution, and connectivity to exchange/venue FIX/REST gateways.

## Functional Requirements
1. Order Management System (OMS)
   - Support for order lifecycle: New, Replace, Cancel, Fill, Partial Fill, Reject, Suspend, Resume.
   - Order types: Market, Limit, Stop, Stop-Limit, IOC, FOK, Pegged.
   - Child/Parent orders and algorithmic strategies (TWAP, VWAP, POV, Dynamic Slicing).
   - Order tagging, time-in-force, execution instructions, notional/quantity handling.
   - Audit trail for order events with timestamps and user/system attribution.

2. Smart Order Router (SOR)
   - Route orders across multiple venues based on latency, fees, liquidity, and user preferences.
   - Venue adapters supporting FIX, REST, and native APIs.
   - Real-time order book and liquidity scoring to inform routing decisions.
   - Fallback and retry strategies for venue failures.

3. Market Connectivity and Data
   - Integrate real-time market data feeds (top-of-book and depth) from primary exchanges and market data vendors.
   - Normalize feeds into a common market data model.
   - Support tick-level persistence for audit/compliance and strategy analysis.

4. Execution & Matching
   - Support self-match prevention, auto-routing, and smart order splitting.
   - Matching engine or integration with venue matching for internal crossing.

5. Trade Execution Reporting
   - Real-time execution reports to downstream systems and user dashboards.
   - Support FIX execution reports, confirmations, and trade blotter export.

6. User & Role Management
   - Role-based access control (RBAC) with separation of duties.
   - Support for trader desks, compliance officers, and algorithm owners.

## Non-Functional Requirements
- Latency: end-to-end order placement to venue accept target of sub-50ms for critical paths.
- Throughput: handle burst loads up to 5k orders/sec with horizontal scalability.
- Availability: 99.95% with multi-AZ-like redundancy in Cyoda cloud.
- Observability: tracing, metrics, and structured logs for order lifecycle and SOR decisions.
- Security: encryption at rest/in transit, secure credential storage, audit logs.

## Compliance & Reporting
- Support OATS/CAT-like reporting for US and MiFID II transaction reporting for EU.
- Retention policies for trade and order data per regulatory requirements.

## Integration & APIs
- REST and WebSocket APIs for order entry, market data, and execution reports.
- SDKs for Java clients and language-agnostic gRPC endpoints.

## Acceptance Criteria
- End-to-end trade from order entry to confirmed fill in production-like environment within target latency under normal load.
- SOR routes correctly under simulated multi-venue liquidity scenarios.

## Out of Scope for Phase 1
- OTC derivatives and complex margining (deferred to Derivatives-Heavy phase)
- Full portfolio accounting and tax lot matching (to be added later)

## Next Steps
After saving these requirements we can:
- Define core Entities (Order, Trade, Venue, Instrument, ExecutionReport)
- Design Workflows (OrderLifecycle, SORDecision, ExecutionReporting)
- Generate initial Java entity JSONs and workflows or the full application
