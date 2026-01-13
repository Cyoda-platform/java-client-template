# Institutional Trading Platform - Entity JSON Definitions (Version 1)

## Overview
This document summarizes the 8 core entity definitions created for the institutional trading platform. All entities follow Cyoda conventions with UUID primary keys, relationships via ID references, and validation rules.

## Entity Definitions

### 1. Account
**Location:** `src/main/resources/entity/Account/version_1/Account.json`

Core trading account entity with balance and risk limits.

**Key Fields:**
- `id` (UUID): Primary key
- `accountNumber`: Business identifier (e.g., "ACC-2024-001")
- `accountType`: INSTITUTIONAL
- `status`: ACTIVE
- `balance`: Account balance in currency
- `currency`: USD
- `riskLimits`: Nested object with dailyLossLimit, positionLimit, orderValueLimit

### 2. Order
**Location:** `src/main/resources/entity/Order/version_1/Order.json`

Trading order with side, type, and execution details.

**Key Fields:**
- `id` (UUID): Primary key
- `accountId` (UUID): Reference to Account
- `symbol`: Security symbol (e.g., "AAPL")
- `side`: BUY or SELL
- `type`: MARKET, LIMIT, STOP, STOP_LIMIT, ICEBERG
- `quantity`: Order quantity (validation: > 0)
- `price`: Limit price (validation: > 0)
- `status`: PENDING, FILLED, PARTIAL, CANCELLED

### 3. Trade
**Location:** `src/main/resources/entity/Trade/version_1/Trade.json`

Executed trade with fees and settlement information.

**Key Fields:**
- `id` (UUID): Primary key
- `orderId` (UUID): Reference to Order
- `accountId` (UUID): Reference to Account
- `symbol`: Security symbol
- `quantity`: Executed quantity (validation: > 0)
- `executionPrice`: Price per share (validation: > 0)
- `fees`: Nested object with commissionFee, exchangeFee, clearingFee
- `settlementDate`: T+2 settlement date

### 4. Position
**Location:** `src/main/resources/entity/Position/version_1/Position.json`

Current holding with mark-to-market valuation and P&L.

**Key Fields:**
- `id` (UUID): Primary key
- `accountId` (UUID): Reference to Account
- `symbol`: Security symbol
- `quantity`: Shares held (validation: > 0)
- `averageCost`: Cost basis per share
- `currentPrice`: Mark-to-market price (validation: > 0)
- `unrealizedPnL`: Unrealized profit/loss
- `realizedPnL`: Realized profit/loss

### 5. MarketQuote
**Location:** `src/main/resources/entity/MarketQuote/version_1/MarketQuote.json`

Real-time market data with bid/ask prices and volume.

**Key Fields:**
- `id` (UUID): Primary key
- `symbol`: Security symbol
- `bid`: Best bid price (validation: > 0)
- `ask`: Best ask price (validation: > bid)
- `last`: Last trade price
- `volume`: Daily volume (validation: >= 0)
- `timestamp`: Quote timestamp

### 6. RiskLimit
**Location:** `src/main/resources/entity/RiskLimit/version_1/RiskLimit.json`

Pre-trade and real-time risk control thresholds.

**Key Fields:**
- `id` (UUID): Primary key
- `accountId` (UUID): Reference to Account
- `limitType`: DAILY_LOSS_LIMIT, POSITION_LIMIT, ORDER_VALUE_LIMIT
- `limitValue`: Threshold amount (validation: > 0)
- `currentUsage`: Current usage (validation: >= 0 AND <= limitValue)
- `status`: ACTIVE, BREACHED, SUSPENDED

### 7. Portfolio
**Location:** `src/main/resources/entity/Portfolio/version_1/Portfolio.json`

Aggregated account holdings with total value and P&L metrics.

**Key Fields:**
- `id` (UUID): Primary key
- `accountId` (UUID): Reference to Account
- `totalValue`: Total portfolio value (validation: > 0)
- `cash`: Available cash (validation: >= 0)
- `positionValue`: Value of all positions
- `realizedPnL`: Realized profit/loss
- `unrealizedPnL`: Unrealized profit/loss
- `positions`: Array of position summaries

### 8. ExecutionVenue
**Location:** `src/main/resources/entity/ExecutionVenue/version_1/ExecutionVenue.json`

Trading venue with connectivity status and fee structure.

**Key Fields:**
- `id` (UUID): Primary key
- `venueName`: Venue name (e.g., "NYSE")
- `type`: EXCHANGE, ATS, DARK_POOL
- `status`: ACTIVE, INACTIVE
- `connectivity`: Nested object with status, latency, lastHeartbeat
- `fees`: Nested object with commissionRate, exchangeFee, clearingFee
- `priority`: Routing priority (1 = highest)

## Relationships

```
Account (1) ──→ (N) Order
Account (1) ──→ (N) Trade
Account (1) ──→ (N) Position
Account (1) ──→ (N) RiskLimit
Account (1) ──→ (1) Portfolio
Order (1) ──→ (N) Trade
```

## Validation Rules Summary

- **Quantity fields**: Must be > 0
- **Price fields**: Must be > 0
- **Ask price**: Must be > bid price
- **Volume**: Must be >= 0
- **Risk usage**: Must be >= 0 AND <= limit value
- **Cash/Balance**: Must be >= 0

## File Structure

All entity JSON files follow the pattern:
```
src/main/resources/entity/{EntityName}/version_1/{EntityName}.json
```

## Status

✅ All 8 entity JSON definitions created and validated
✅ All files contain valid JSON
✅ All files committed to git
✅ Ready for Java entity class implementation

