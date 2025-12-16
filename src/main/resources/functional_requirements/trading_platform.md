# Real-Time Trading Platform — Functional Requirements

## Overview
A Cyoda-based real-time trading platform for equities and derivatives providing:
- Market data ingestion (multiple feeds, L1/L2 orderbook snapshots, trade ticks, options chains)
- Order management system (OMS) supporting limit/market/stop/iceberg orders, order routing, and OMS lifecycle events
- Execution and settlement (trade matching, fills, trade lifecycle, confirmations)
- Portfolio management (positions, P&L, risk exposures, rebalancing, multi-account support)
- Risk controls (pre-trade checks, position limits, margin checks, circuit breakers)
- Compliance & audit trail (trade reporting, surveillance alerts, regulatory reports)
- Low-latency components for market data and order execution; event-driven processors for business logic

## Non-functional Requirements
- High throughput and low latency for market data and order processing
- Exactly-once processing for order lifecycle events where required
- Auditable events with immutable logs for compliance
- Scalable architecture: consumer groups, partitioning for market data and orders
- Secure access control for sensitive operations

## Key Actors
- Market Data Provider
- Executing Broker
- Internal Trading Engine
- Portfolio Manager
- Risk Engine
- Compliance Engine

## Core Entities
- MarketData (tick, orderbook)
- Order (clientOrderId, side, instrument, quantity, price, type, timeInForce, status)
- Trade (tradeId, orderId, instrument, price, quantity, timestamp)
- Portfolio (portfolioId, accounts, positions)
- Position (instrument, quantity, avgPrice)
- Account (accountId, owner, balance, margin)
- RiskRule (ruleId, type, threshold, action)
- ComplianceEvent (eventId, type, severity, metadata)

## Core Workflows
- MarketDataIngestion: normalize feeds, enrich with reference data, publish to event bus
- OrderLifecycle: validate -> route -> accept/reject -> match -> fill -> confirm
- TradeSettlement: settle trade, update positions and cash, generate confirmations
- RiskChecks: pre-trade checks, real-time position/margin checks
- ComplianceReporting: capture events, run surveillance rules, generate reports

## Integration Points
- Market data feeds (FIX/Protobuf/Websocket) — handle variable schemas and snapshots
- Execution venues/brokers — FIX/REST gateways
- Reference data service (instruments, corporate actions)
- Post-trade systems (clearing, settlement)

## Data Retention and Auditing
- Keep immutable audit logs for orders, trades, and compliance events
- Retain market data snapshots for specified windows for replays

## Operational Considerations
- Monitoring: metrics for latency, throughput, error rates
- Alerting for risk breaches and compliance events
- Backpressure handling for spikes in market data

## Next Steps
- Define entity JSON examples and versioned workflow definitions in Canvas
- Create risk rules, surveillance checks, and test data feeds
- Generate the Java application and deploy to a Cyoda environment

---

## FIX & Venue Connectivity
- Overview: Support low-latency, reliable connectivity to execution venues and brokers via FIX (and optional REST/websocket adapters). Provide session management, message mapping, sequencing, and recovery to ensure no loss or duplication of orders/executions.
- Supported protocols & versions: FIX 4.2, 4.4, and FIXT 1.1/EP (gateway support for future versions). REST/Websocket adapters for non-FIX venues.
- Session management: Persistent sessions with configurable reconnect/backoff, sequence number persistence, gap detection, resend handling, and end-of-day reset policies.
- Message handling & mappings: Canonical internal message model; venue-specific mapping layers for order/new, cancel/replace, execution reports, market data. Support custom field mappings per venue.
- Reliability & ordering: Exactly-once semantics for order acceptance and trade events where required; idempotent processing of duplicate messages and robust handling of out-of-order messages via sequencing and application-level dedup keys.
- Failover & high-availability: Active/passive or active/active gateway configurations, automatic failover, and hot-standby session takeover with minimal message loss.
- Security & authentication: TLS for transport, mutual TLS or token-based authentication to venue gateways as required, and secure storage/rotation for venue credentials.
- Monitoring & observability: Per-venue metrics (latency, message rates, session state), alerts for sequence gaps/resends, and audit logs of raw FIX messages for compliance & replay.
- Testing & certification: Facilities for venue certification tests and a sandbox connectivity mode (replay of historical messages, synthetic traffic) to validate mappings and failover.
- Configuration: Per-venue configuration registry (host, port, protocol, credentials, FIX dialect, mappings, heartbeat and resend settings) manageable via Canvas/Cloud or config files.
- Non-functional targets: Document expected per-venue connection latency SLAs, maximum message throughput per connection, and recovery RTO/RPO for session state.
