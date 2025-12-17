# Crypto Exchange Platform - Production-Ready Scaffold

A comprehensive, workflow-driven Spring Boot application for a production-ready cryptocurrency exchange platform. This scaffold provides a complete foundation for building a secure, scalable, and compliant crypto trading platform.

## 🎯 Overview

This project implements a **workflow-driven architecture** using the Cyoda framework, supporting:

- **Order Management**: Limit/market orders with matching and settlement
- **Multi-Asset Wallets**: Hot/cold/custodial custody separation
- **KYC/AML Compliance**: Identity verification and sanctions screening
- **Liquidity Management**: Market-making and pool rebalancing
- **Immutable Ledger**: Complete audit trail for all transactions
- **REST APIs**: Public, authenticated, and admin endpoints

## 📦 Core Entities (11 Models)

| Entity | Purpose | Key Fields |
|--------|---------|-----------|
| **User** | Platform users | userId, email, roles, status |
| **Account** | Trading accounts | accountId, userId, tier, limits |
| **Wallet** | Multi-asset wallets | walletId, balance, type (hot/cold/custodial) |
| **Asset** | Supported cryptocurrencies | symbol, decimals, trading status |
| **Market** | Trading pairs | baseAsset, quoteAsset, tickSize |
| **Order** | Trading orders | orderId, side, type, status, filled |
| **Trade** | Matched trades | buyOrderId, sellOrderId, price, quantity |
| **Transaction** | Ledger entries | type (deposit/withdraw/settlement), amount |
| **KYCProfile** | Compliance data | documents, screening status, verification |
| **ComplianceAlert** | AML alerts | type, severity, status, investigation |
| **LiquidityPool** | Market-making pools | balanceBase, balanceQuote, strategy |

## 🔄 Core Workflows (11 Workflows)

### 1. **Order Lifecycle** (`Order.json`)
```
initial → placed → matching → [partially_filled | filled] → settled
```
- Validation → Matching → Settlement with ledger entries

### 2. **Wallet Deposit/Withdrawal** (`Wallet.json`)
```
initial → deposit_pending → deposit_detected → kyc_aml_check → settled
initial → withdrawal_pending → aml_check → signing → confirmed → finalized
```

### 3. **KYC Onboarding** (`KYCProfile.json`)
```
initial → submitted → automated_screening → [manual_review | auto_approve] → approved
```
- Document verification, sanctions screening, PEP checks

### 4. **AML Alert Handling** (`ComplianceAlert.json`)
```
initial → open → under_review → [resolved | escalated] → closed
```

### 5. **Liquidity Management** (`LiquidityPool.json`)
```
initial → active → [rebalancing | paused] → [withdrawal_pending] → closed
```

### 6-11. **Lifecycle Workflows**
- User, Account, Asset, Market, Transaction, Trade (simple state machines)

## 🛠 Processors (16 Implementations)

### Order Processing
- `OrderValidationProcessor` - Validates orders before placement
- `OrderMatchingProcessor` - Matches orders in order book
- `OrderSettlementProcessor` - Settles filled orders

### Wallet Processing
- `DepositDetectionProcessor` - Detects external deposits
- `DepositKYCAMLProcessor` - Performs KYC/AML checks on deposits
- `DepositSettlementProcessor` - Settles deposits to wallet
- `WithdrawalAMLProcessor` - Validates withdrawal destinations
- `WithdrawalSigningProcessor` - Signs and broadcasts transactions
- `WithdrawalFinalizationProcessor` - Finalizes confirmed withdrawals

### Compliance Processing
- `KYCAutomatedScreeningProcessor` - Automated KYC screening
- `ComplianceAlertGenerationProcessor` - Generates AML alerts

### Liquidity Processing
- `LiquidityPoolInitializationProcessor` - Initializes pools
- `LiquidityRebalancingProcessor` - Rebalances inventory
- `LiquidityWithdrawalProcessor` - Closes pools

### Transaction Processing
- `TransactionConfirmationProcessor` - Confirms transactions

## 🌐 REST API Endpoints

### User Management
```
POST   /ui/user                    - Create user
GET    /ui/user/{id}              - Get user by ID
GET    /ui/user/business/{userId} - Get user by business ID
PUT    /ui/user/{id}              - Update user
GET    /ui/user/search             - Search users
DELETE /ui/user/{id}              - Delete user
```

### Account Management
```
POST   /ui/account                 - Create account
GET    /ui/account/{id}           - Get account
GET    /ui/account/user/{userId}  - Get accounts by user
PUT    /ui/account/{id}           - Update account
DELETE /ui/account/{id}           - Delete account
```

### Trading
```
POST   /ui/order                   - Place order
GET    /ui/order/{id}             - Get order
GET    /ui/order/account/{accountId} - Get account orders
POST   /ui/order/{id}/cancel      - Cancel order
PUT    /ui/order/{id}             - Update order

POST   /ui/trade                   - Create trade
GET    /ui/trade/{id}             - Get trade
GET    /ui/trade/market/{marketId} - Get market trades
```

### Wallet Operations
```
POST   /ui/wallet                  - Create wallet
GET    /ui/wallet/{id}            - Get wallet
GET    /ui/wallet/account/{accountId} - Get account wallets
POST   /ui/wallet/{id}/deposit    - Initiate deposit
POST   /ui/wallet/{id}/withdraw   - Initiate withdrawal
PUT    /ui/wallet/{id}            - Update wallet
```

### Compliance
```
POST   /ui/kyc                     - Create KYC profile
GET    /ui/kyc/{id}               - Get KYC profile
GET    /ui/kyc/user/{userId}      - Get user KYC
GET    /ui/kyc/status/{status}    - Get profiles by status
PUT    /ui/kyc/{id}               - Update KYC profile

POST   /ui/compliance-alert        - Create alert
GET    /ui/compliance-alert/{id}  - Get alert
GET    /ui/compliance-alert/status/{status} - Get alerts by status
GET    /ui/compliance-alert/severity/{severity} - Get alerts by severity
```

### Market & Assets
```
POST   /ui/asset                   - Create asset
GET    /ui/asset/{id}             - Get asset
GET    /ui/asset/symbol/{symbol}  - Get asset by symbol

POST   /ui/market                  - Create market
GET    /ui/market/{id}            - Get market
GET    /ui/market                 - List all markets

POST   /ui/liquidity-pool          - Create pool
GET    /ui/liquidity-pool/{id}    - Get pool
GET    /ui/liquidity-pool/market/{marketId} - Get market pools
GET    /ui/liquidity-pool/status/{status} - Get pools by status
```

## 🚀 Getting Started

### Prerequisites
- Java 21+
- Gradle 8.7+
- Docker (optional, for containerization)

### Build
```bash
# Clean build
./gradlew clean build

# Compile only
./gradlew compileJava

# Validate workflows
./gradlew validateWorkflowImplementations
```

### Run
```bash
# Start application
./gradlew bootRun

# Application runs on http://localhost:8080
```

### Test
```bash
# Run all tests
./gradlew test

# Run specific test
./gradlew test --tests TestClassName

# Run with coverage
./gradlew test jacocoTestReport
```

## 📁 Project Structure

```
src/main/java/com/java_template/application/
├── entity/              # Entity models (11 entities)
│   ├── user/
│   ├── account/
│   ├── wallet/
│   ├── asset/
│   ├── market/
│   ├── order/
│   ├── trade/
│   ├── transaction/
│   ├── kycprofile/
│   ├── compliancealert/
│   └── liquiditypool/
├── processor/           # Workflow processors (16 processors)
├── controller/          # REST controllers (11 controllers)
└── criterion/           # Search criteria (extensible)

src/main/resources/
├── entity/              # Entity JSON examples
├── workflow/            # Workflow definitions (11 workflows)
└── functional_requirements/
    └── crypto_exchange.md
```

## 🔐 Security & Compliance

- **RBAC**: Role-based access control via Spring Security
- **Encryption**: TLS for transit, encryption-at-rest for sensitive data
- **Audit Trail**: Immutable ledger for all transactions
- **KYC/AML**: Integrated compliance workflows
- **Key Management**: HSM integration hooks for production
- **Idempotency**: Guaranteed idempotent operations

## 📊 Non-Functional Requirements

- **Latency**: Single-digit ms matching under typical load
- **Throughput**: Thousands of orders/sec with horizontal scaling
- **Availability**: 99.9% uptime with failover
- **Scalability**: Microservices-ready architecture
- **Observability**: Metrics, tracing, and alerting hooks

## 🔧 Configuration

Edit `src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: crypto-exchange
  jpa:
    hibernate:
      ddl-auto: validate
  datasource:
    url: jdbc:postgresql://localhost:5432/crypto_exchange
    username: postgres
    password: password

server:
  port: 8080
  servlet:
    context-path: /api
```

## 📚 API Documentation

Swagger/OpenAPI documentation available at:
```
http://localhost:8080/swagger-ui.html
```

## 🧪 Testing

Example test patterns in `src/test/java/com/example/application/`:
- Entity tests
- Processor tests
- Controller tests
- Integration tests

## 🚢 Deployment

### Docker
```bash
./gradlew bootJar
docker build -t crypto-exchange:latest .
docker run -p 8080:8080 crypto-exchange:latest
```

### Kubernetes
Helm charts available in `helm/` directory:
```bash
helm install crypto-exchange ./helm
```

## 📝 Development Workflow

1. **Add Entity**: Create in `entity/{name}/version_1/{Name}.java`
2. **Add Workflow**: Create in `workflow/{name}/version_1/{Name}.json`
3. **Add Processors**: Implement in `processor/{Name}Processor.java`
4. **Add Controller**: Create in `controller/{Name}Controller.java`
5. **Validate**: Run `./gradlew validateWorkflowImplementations`
6. **Test**: Write tests in `src/test/`
7. **Build**: Run `./gradlew build`

## 🤝 Contributing

- Follow Spring Boot best practices
- Use Lombok for boilerplate reduction
- Implement comprehensive logging
- Write unit and integration tests
- Document API endpoints
- Validate workflows before commit

## 📄 License

Proprietary - Crypto Exchange Platform

## 📞 Support

For issues and questions, refer to:
- Functional requirements: `src/main/resources/functional_requirements/crypto_exchange.md`
- Example implementations: `src/test/java/com/example/application/`
- API documentation: Swagger UI at `/swagger-ui.html`

---

**Version**: 1.0.0  
**Last Updated**: 2025-12-17  
**Status**: Production-Ready Scaffold

