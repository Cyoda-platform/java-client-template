# Real-Time Trading Platform - Quick Start Guide

## Build & Run

### Prerequisites
- Java 11 or higher
- Gradle 8.0+

### Compile the Project
```bash
./gradlew clean compileJava
```

### Build the Project (Skip Tests)
```bash
./gradlew build -x test
```

### Run the Application
```bash
./gradlew bootRun
```

The application will start on `http://localhost:8080`

## API Endpoints

### Instruments
```bash
# Create instrument
POST /ui/instruments
{
  "symbol": "AAPL",
  "instrumentType": "EQUITY",
  "exchange": "NASDAQ",
  "description": "Apple Inc.",
  "currency": "USD",
  "currentPrice": 150.25,
  "bidPrice": 150.20,
  "askPrice": 150.30,
  "status": "ACTIVE"
}

# Get instrument by ID
GET /ui/instruments/{id}

# Get instrument by symbol
GET /ui/instruments/symbol/{symbol}

# Search by type
GET /ui/instruments/search/by-type?type=EQUITY

# Update instrument
PUT /ui/instruments/{id}

# Delete instrument
DELETE /ui/instruments/{id}
```

### Orders
```bash
# Create order
POST /ui/orders
{
  "orderId": "ORD-001",
  "portfolioId": "PORT-001",
  "instrumentSymbol": "AAPL",
  "side": "BUY",
  "orderType": "LIMIT",
  "quantity": 100,
  "limitPrice": 150.00,
  "timeInForce": "DAY",
  "userId": "USER-001"
}

# Get order by ID
GET /ui/orders/{id}

# Search orders by portfolio
GET /ui/orders/search/by-portfolio?portfolioId=PORT-001

# Cancel order
POST /ui/orders/{id}/cancel

# Update order
PUT /ui/orders/{id}

# Delete order
DELETE /ui/orders/{id}
```

### Trades
```bash
# Create trade
POST /ui/trades
{
  "tradeId": "TRD-001",
  "orderId": "ORD-001",
  "portfolioId": "PORT-001",
  "instrumentSymbol": "AAPL",
  "side": "BUY",
  "quantity": 100,
  "executionPrice": 150.00,
  "tradeValue": 15000.00,
  "commission": 15.00,
  "netValue": 14985.00
}

# Get trade by ID
GET /ui/trades/{id}

# Search trades by portfolio
GET /ui/trades/search/by-portfolio?portfolioId=PORT-001

# Search trades by settlement status
GET /ui/trades/search/by-status?status=PENDING
```

### Portfolios
```bash
# Create portfolio
POST /ui/portfolios
{
  "portfolioId": "PORT-001",
  "ownerId": "USER-001",
  "name": "Main Portfolio",
  "cashBalance": 100000.00,
  "currency": "USD",
  "status": "ACTIVE"
}

# Get portfolio by ID
GET /ui/portfolios/{id}

# Mark portfolio to market
POST /ui/portfolios/{id}/mark-to-market

# Update portfolio
PUT /ui/portfolios/{id}

# Delete portfolio
DELETE /ui/portfolios/{id}
```

### Positions
```bash
# Create position
POST /ui/positions
{
  "positionId": "POS-001",
  "portfolioId": "PORT-001",
  "instrumentSymbol": "AAPL",
  "quantity": 100,
  "averageCost": 145.00,
  "currentPrice": 150.25
}

# Get position by ID
GET /ui/positions/{id}

# Search positions by portfolio
GET /ui/positions/search/by-portfolio?portfolioId=PORT-001

# Mark position to market
POST /ui/positions/{id}/mark-to-market

# Update position
PUT /ui/positions/{id}

# Delete position
DELETE /ui/positions/{id}
```

### Market Data
```bash
# Ingest market data tick
POST /ui/market-data/ticks
{
  "tickId": "TICK-AAPL-20251216-100000",
  "instrumentSymbol": "AAPL",
  "exchange": "NASDAQ",
  "timestamp": "2025-12-16T10:00:00",
  "bidPrice": 150.20,
  "bidSize": 5000,
  "askPrice": 150.30,
  "askSize": 5000,
  "lastPrice": 150.25,
  "volume": 50000000
}

# Get tick by ID
GET /ui/market-data/ticks/{id}

# Search ticks by symbol
GET /ui/market-data/search/by-symbol?symbol=AAPL

# Delete tick
DELETE /ui/market-data/ticks/{id}
```

### Risk Rules
```bash
# Create risk rule
POST /ui/risk-rules
{
  "ruleId": "RISK-001",
  "ruleName": "Max Order Size",
  "ruleType": "PRE_TRADE",
  "riskMetric": "ORDER_SIZE",
  "limitValue": 10000.0,
  "actionOnBreach": "REJECT",
  "applicableTo": "PORTFOLIO",
  "targetId": "PORT-001",
  "status": "ACTIVE"
}

# Get rule by ID
GET /ui/risk-rules/{id}

# Search rules by type
GET /ui/risk-rules/search/by-type?type=PRE_TRADE

# Update rule
PUT /ui/risk-rules/{id}

# Delete rule
DELETE /ui/risk-rules/{id}
```

### Audit Logs
```bash
# Create audit log
POST /ui/audit-logs
{
  "logId": "AUDIT-001",
  "entityType": "ORDER",
  "entityId": "ORD-001",
  "action": "CREATE",
  "userId": "USER-001",
  "userName": "John Trader",
  "result": "SUCCESS"
}

# Get log by ID
GET /ui/audit-logs/{id}

# Search logs by entity
GET /ui/audit-logs/search/by-entity?entityId=ORD-001

# Search logs by user
GET /ui/audit-logs/search/by-user?userId=USER-001
```

### Users
```bash
# Create user
POST /ui/users
{
  "userId": "USER-001",
  "name": "John Trader",
  "email": "john@example.com",
  "role": "TRADER",
  "permissions": ["CREATE_ORDER", "CANCEL_ORDER"],
  "accountStatus": "ACTIVE"
}

# Get user by ID
GET /ui/users/{id}

# Search users by role
GET /ui/users/search/by-role?role=TRADER

# Update user
PUT /ui/users/{id}

# Delete user
DELETE /ui/users/{id}
```

### Settlement Instructions
```bash
# Create settlement instruction
POST /ui/settlement-instructions
{
  "settlementInstructionId": "SETTLE-001",
  "tradeId": "TRD-001",
  "portfolioId": "PORT-001",
  "instrumentSymbol": "AAPL",
  "side": "BUY",
  "quantity": 100,
  "settlementPrice": 150.00,
  "settlementDate": "2025-12-18T00:00:00",
  "settlementStatus": "PENDING"
}

# Get instruction by ID
GET /ui/settlement-instructions/{id}

# Search by status
GET /ui/settlement-instructions/search/by-status?status=PENDING

# Search by trade
GET /ui/settlement-instructions/search/by-trade?tradeId=TRD-001

# Delete instruction
DELETE /ui/settlement-instructions/{id}
```

## Project Structure

```
src/main/java/com/java_template/
├── Application.java                    # Spring Boot entry point
├── application/
│   ├── entity/                        # Domain entities
│   │   ├── instrument/version_1/
│   │   ├── order/version_1/
│   │   ├── trade/version_1/
│   │   ├── portfolio/version_1/
│   │   ├── position/version_1/
│   │   ├── market_data_tick/version_1/
│   │   ├── risk_rule/version_1/
│   │   ├── audit_log/version_1/
│   │   ├── user_account/version_1/
│   │   └── settlement_instruction/version_1/
│   ├── processor/                    # Workflow processors (18 total)
│   ├── criterion/                    # Workflow criteria (1 total)
│   └── controller/                   # REST controllers (10 total)
└── common/                           # Framework code (DO NOT MODIFY)

src/main/resources/
├── workflow/                         # Workflow definitions (10 total)
│   ├── instrument/version_1/
│   ├── order/version_1/
│   ├── trade/version_1/
│   ├── portfolio/version_1/
│   ├── position/version_1/
│   ├── market_data_tick/version_1/
│   ├── risk_rule/version_1/
│   ├── audit_log/version_1/
│   ├── user_account/version_1/
│   └── settlement_instruction/version_1/
└── entity/                           # JSON entity definitions (10 total)
    ├── instrument/version_1/
    ├── order/version_1/
    ├── trade/version_1/
    ├── portfolio/version_1/
    ├── position/version_1/
    ├── market_data_tick/version_1/
    ├── risk_rule/version_1/
    ├── audit_log/version_1/
    ├── user_account/version_1/
    └── settlement_instruction/version_1/
```

## Key Features

✅ 10 Domain Entities
✅ 18 Workflow Processors
✅ 1 Workflow Criterion
✅ 10 REST Controllers
✅ Complete Order Lifecycle
✅ Trade Execution & Settlement
✅ Portfolio Valuation
✅ Risk Management
✅ Audit Trail
✅ RBAC Support

## Documentation

See `IMPLEMENTATION_SUMMARY.md` for detailed implementation documentation.

