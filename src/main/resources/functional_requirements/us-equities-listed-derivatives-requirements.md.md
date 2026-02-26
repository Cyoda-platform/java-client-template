# Functional Requirements: US Equities & Listed Derivatives

## Overview

This document captures the core functional and non-functional requirements for an institutional trading platform targeting US Equities and listed derivatives, with compliance to SEC and Reg NMS. The platform will support real-time market data ingestion (L1 & L2), advanced order management, portfolio tracking, risk controls, and real-time P&L.

## Primary Goals

1. Real-time market data ingestion and distribution (L1 and L2) from major US exchanges and consolidated feeds.
2. Advanced order management system (OMS) with support for market, limit, stop, stop-limit, IOC, FOK, and peg orders; multi-leg orders for options.
3. Smart Order Router (SOR) to select best execution across venues per Reg NMS rules.
4. Trade reporting and audit trails meeting SEC requirements.
5. Real-time P&L and mark-to-market calculations across portfolios.
6. Risk controls including pre-trade (limits, credit checks), intra-day (exposure, VaR), and post-trade controls.
7. Compliance and surveillance hooks for Reg NMS, order marking, and trade reporting.
8. Resiliency and high-availability design with horizontal scaling.

## Functional Requirements

- Market Data
  - Ingest L1 (top-of-book) and L2 (order book depth) feeds.
  - Normalize feeds into a common market-data model.
  - Provide subscription API for internal components and external clients.
  - Support recovery and replay for missed messages.

- Order Management
  - Create, amend, cancel orders with state-machine handling and audit logs.
  - Support multi-leg options strategies and complex orders.
  - Persist order lifecycle events for compliance and recovery.

- Smart Order Routing
  - Implement venue selection based on price, liquidity, fees, and speed.
  - Enforce Reg NMS trade-through protections and best execution policies.

- Portfolio & P&L
  - Real-time position updates and mark-to-market valuations.
  - Support historical P&L, realized/unrealized breakdown, and attribution.

- Risk & Compliance
  - Pre-trade checks: size, credit, concentration limits.
  - Real-time risk monitors (VaR, stress scenarios) and alerts.
  - Transaction reporting per SEC/FINRA formats.

## Non-Functional Requirements

- Latency targets: L1 updates sub-10ms internal deliveries; execution paths under 50ms.
- Scalability: horizontal scaling for data feed handlers and execution engines.
- Security: role-based access control, encrypted at rest and in transit.
- Observability: metrics, distributed tracing, and centralized logging.

## Acceptance Criteria

- End-to-end simulated trading flow with L2 feed ingestion, SOR decision, order lifecycle completion, trade reporting, and P&L update.
- Automated test coverage for critical components: market-data normalization, order lifecycle, SOR logic, and risk checks.

## Next Steps

- Define core Entities (Order, Trade, MarketSnapshot, Position, Portfolio, RiskLimit).
- Design Workflows: OrderLifecycle, MarketDataIngestion, SORDecision, TradeSettlement.
- Generate initial entity JSON and a sample OrderLifecycle workflow.

