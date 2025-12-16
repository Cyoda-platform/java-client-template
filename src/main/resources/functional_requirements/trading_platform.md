# Functional Requirements — Real-time Trading Platform (Equities & Derivatives)

## Overview
Build an end-to-end real-time trading platform (Java) that supports equities and derivatives with core production-ready capabilities: market data feeds, order management, portfolio tracking, risk controls, and compliance.

## Core Domain Entities
- Instrument
- Order
- Trade/Execution
- Portfolio
- Position
- MarketDataTick
- RiskRule
- AuditLog
- User/Account
- SettlementInstruction

## Core Workflows
### Market Data Ingestion & Normalization
- Support multi-exchange feeds with adapters
- Tick aggregation (time buckets) and normalization to canonical Instrument IDs
- Persist time-series ticks in a replayable store
- Provide replay API for backtesting and sandbox

### Order Lifecycle / Order Management System (OMS)
- Order states: NEW → ACK → ROUTED → FILLED/PARTIAL_FILL → CANCELLED/REJECTED
- Support replace and cancel
- Order routing to execution adapters or internal matching
- Execution confirmations and trade capture
- Idempotency and guaranteed delivery for critical messages

### Execution Adapters
- Exchange gateway adapters for connectivity (FIX-like) and client-facing adapters for order entry
- Adapter abstraction for latency-sensitive routing and failover

### Portfolio & Positions
- Real-time position updates on trade execution
- Mark-to-market and P&L calculations for equities and derivatives
- Greeks calculation for options (delta, gamma, vega, theta)
- End-of-day snapshot and intraday aggregation

### Risk Evaluation
- Pre-trade checks (size, credit limits, sanctions)
- Intra-day limits and throttling
- Post-trade risk recalculation and alerts

### Compliance & Audit
- Immutable audit trail for orders, executions, and configuration changes
- Trade surveillance rules and reporting exports
- Regulatory reporting format support (configurable)

### Clearing & Settlement
- Trade matching and netting
- Generation of settlement instructions and reconciliation feeds

## Integration & APIs
- Real-time publish/subscribe event bus for internal events
- REST and WebSocket APIs for clients and dashboards
- Adapter layer for exchange protocols and market data

## Data & Persistence
- Time-series store for market ticks and historical prices
- Transactional store for orders, trades, positions, and audit logs
- Event sourcing/changelog for replayability

## Non-functional Requirements
- Low-latency order paths and high-throughput market data handling
- High availability and horizontal scalability
- Security: authentication, RBAC, encryption in transit and at rest
- Observability: metrics, tracing, structured logs, and alerting

## Testing & Environments
- Sandbox with recorded/synthetic feeds for simulation
- Backtesting and replay for scenario testing

## MVP Milestones
1. Market data ingestion + normalization + time-series persistence
2. Order entry (REST/WebSocket) + OMS core lifecycle + simple execution adapter
3. Real-time portfolio updates and P&L

## Phase 2
- Full risk engine, surveillance, regulatory exports, clearing & settlement automation

## Acceptance Criteria
- Endpoints to ingest market data, create/modify/cancel orders, and retrieve portfolio state
- Replayable market data and deterministic sandbox for testing
- Automated tests covering core workflows

