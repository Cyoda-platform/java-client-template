# Quick Reference - Crypto Exchange Platform

## 🚀 Essential Commands

```bash
# Build
./gradlew clean build              # Full build
./gradlew compileJava              # Compile only
./gradlew bootJar                  # Create JAR

# Run
./gradlew bootRun                  # Start application
./gradlew bootRun --debug          # Start with debug

# Test
./gradlew test                     # Run all tests
./gradlew test --tests TestClass   # Run specific test

# Validate
./gradlew validateWorkflowImplementations  # Validate workflows
./gradlew validateFunctionalRequirements   # Validate requirements

# Clean
./gradlew clean                    # Clean build artifacts
```

## 📁 Key Directories

```
src/main/java/com/java_template/application/
├── entity/              # Entity models (11)
├── processor/           # Workflow processors (16)
└── controller/          # REST controllers (11)

src/main/resources/
├── entity/              # Entity JSON examples
├── workflow/            # Workflow definitions (11)
└── functional_requirements/
    └── crypto_exchange.md
```

## 🔗 API Endpoints Quick Map

### User Management
```
POST   /ui/user                    Create user
GET    /ui/user/{id}              Get user
GET    /ui/user/business/{userId} Get by business ID
PUT    /ui/user/{id}              Update user
GET    /ui/user/search             Search users
DELETE /ui/user/{id}              Delete user
```

### Trading
```
POST   /ui/order                   Place order
GET    /ui/order/{id}             Get order
GET    /ui/order/account/{accountId} Get account orders
POST   /ui/order/{id}/cancel      Cancel order

POST   /ui/trade                   Create trade
GET    /ui/trade/{id}             Get trade
GET    /ui/trade/market/{marketId} Get market trades
```

### Wallets
```
POST   /ui/wallet                  Create wallet
GET    /ui/wallet/{id}            Get wallet
GET    /ui/wallet/account/{accountId} Get account wallets
POST   /ui/wallet/{id}/deposit    Initiate deposit
POST   /ui/wallet/{id}/withdraw   Initiate withdrawal
```

### Compliance
```
POST   /ui/kyc                     Create KYC profile
GET    /ui/kyc/{id}               Get KYC profile
GET    /ui/kyc/user/{userId}      Get user KYC
GET    /ui/kyc/status/{status}    Get profiles by status

POST   /ui/compliance-alert        Create alert
GET    /ui/compliance-alert/{id}  Get alert
GET    /ui/compliance-alert/status/{status} Get alerts by status
```

### Markets & Assets
```
POST   /ui/asset                   Create asset
GET    /ui/asset/{id}             Get asset
GET    /ui/asset/symbol/{symbol}  Get asset by symbol

POST   /ui/market                  Create market
GET    /ui/market/{id}            Get market
GET    /ui/market                 List all markets

POST   /ui/liquidity-pool          Create pool
GET    /ui/liquidity-pool/{id}    Get pool
GET    /ui/liquidity-pool/market/{marketId} Get market pools
```

## 📊 Entity Reference

| Entity | Business ID | Key Fields | Workflow States |
|--------|------------|-----------|-----------------|
| User | userId | email, roles, status | active, suspended, inactive |
| Account | accountId | userId, tier, limits | active, suspended, closed |
| Wallet | walletId | balance, type, status | active, suspended, locked |
| Asset | assetId | symbol, decimals | active, suspended, delisted |
| Market | marketId | baseAsset, quoteAsset | active, suspended, closed |
| Order | orderId | side, type, status | placed, matching, filled, settled |
| Trade | tradeId | buyOrderId, sellOrderId | matched, settled, failed |
| Transaction | transactionId | type, amount, status | pending, confirmed, failed |
| KYCProfile | kycProfileId | userId, status | submitted, approved, rejected |
| ComplianceAlert | alertId | type, severity, status | open, under_review, resolved |
| LiquidityPool | poolId | marketId, strategy | active, paused, closed |

## 🔄 Workflow State Transitions

### Order Workflow
```
initial → placed → matching → [partially_filled | filled] → settled
                                                    ↓
                                                  cancelled
```

### Wallet Deposit Workflow
```
initial → deposit_pending → deposit_detected → kyc_aml_check → settled
                                                      ↓
                                                  rejected
```

### Wallet Withdrawal Workflow
```
initial → withdrawal_pending → aml_check → signing → confirmed → finalized
                                    ↓
                                  rejected
```

### KYC Workflow
```
initial → submitted → automated_screening → [manual_review | auto_approve] → approved
                                                    ↓
                                                  rejected
```

## 🛠 Processor Reference

| Processor | Entity | Workflow | Purpose |
|-----------|--------|----------|---------|
| OrderValidationProcessor | Order | Validation | Validates orders before placement |
| OrderMatchingProcessor | Order | Matching | Matches orders in order book |
| OrderSettlementProcessor | Order | Settlement | Settles filled orders |
| DepositDetectionProcessor | Wallet | Deposit | Detects external deposits |
| DepositKYCAMLProcessor | Wallet | Deposit | KYC/AML checks on deposits |
| DepositSettlementProcessor | Wallet | Deposit | Settles deposits to wallet |
| WithdrawalAMLProcessor | Wallet | Withdrawal | Validates withdrawal destinations |
| WithdrawalSigningProcessor | Wallet | Withdrawal | Signs and broadcasts transactions |
| WithdrawalFinalizationProcessor | Wallet | Withdrawal | Finalizes confirmed withdrawals |
| KYCAutomatedScreeningProcessor | KYCProfile | Screening | Automated KYC screening |
| ComplianceAlertGenerationProcessor | ComplianceAlert | Alert | Generates AML alerts |
| LiquidityPoolInitializationProcessor | LiquidityPool | Init | Initializes pools |
| LiquidityRebalancingProcessor | LiquidityPool | Rebalance | Rebalances inventory |
| LiquidityWithdrawalProcessor | LiquidityPool | Withdrawal | Closes pools |
| TransactionConfirmationProcessor | Transaction | Confirmation | Confirms transactions |

## 📝 Common Patterns

### Create Entity
```bash
curl -X POST http://localhost:8080/ui/user \
  -H "Content-Type: application/json" \
  -d '{"userId":"user_001","email":"user@example.com","name":"John","status":"ACTIVE"}'
```

### Get Entity
```bash
curl http://localhost:8080/ui/user/550e8400-e29b-41d4-a716-446655440000
```

### Update Entity
```bash
curl -X PUT http://localhost:8080/ui/user/550e8400-e29b-41d4-a716-446655440000 \
  -H "Content-Type: application/json" \
  -d '{"userId":"user_001","email":"newemail@example.com","name":"John","status":"ACTIVE"}'
```

### Trigger Workflow Transition
```bash
curl -X PUT http://localhost:8080/ui/user/550e8400-e29b-41d4-a716-446655440000?transition=suspend \
  -H "Content-Type: application/json" \
  -d '{"userId":"user_001","email":"user@example.com","name":"John","status":"SUSPENDED"}'
```

### Search Entities
```bash
curl "http://localhost:8080/ui/user/search?email=user@example.com&status=ACTIVE&page=0&size=50"
```

## 🔍 Debugging Tips

1. **Check logs**: Look for processor execution logs
2. **Verify state**: Check entity metadata for current state
3. **Validate workflow**: Run `./gradlew validateWorkflowImplementations`
4. **Test processor**: Create unit test for processor logic
5. **Check database**: Verify entity was persisted correctly

## 📚 Documentation Files

- `CRYPTO_EXCHANGE_README.md` - Main documentation
- `DEVELOPMENT_GUIDE.md` - Developer guide
- `IMPLEMENTATION_SUMMARY.md` - Implementation details
- `QUICK_REFERENCE.md` - This file
- `src/main/resources/functional_requirements/crypto_exchange.md` - Requirements

## 🎯 Common Tasks

### Add New Entity
1. Create Java class in `entity/{name}/version_1/`
2. Create JSON example in `entity/{name}/version_1/`
3. Create workflow in `workflow/{name}/version_1/`
4. Create controller in `controller/`
5. Run `./gradlew validateWorkflowImplementations`

### Add New Processor
1. Create class in `processor/` extending `CyodaProcessor`
2. Implement `process()` and `supports()` methods
3. Add to workflow JSON in processors array
4. Run `./gradlew validateWorkflowImplementations`

### Add New Endpoint
1. Add method to controller
2. Use `@PostMapping`, `@GetMapping`, `@PutMapping`, `@DeleteMapping`
3. Follow existing patterns for error handling
4. Test with curl or Postman

## ⚡ Performance Tips

- Use pagination for large result sets
- Use streaming for memory-efficient processing
- Cache frequently accessed entities
- Index database columns used in searches
- Use connection pooling for database

## 🔐 Security Checklist

- [ ] HTTPS enabled in production
- [ ] Authentication configured
- [ ] Authorization (RBAC) implemented
- [ ] Input validation on all endpoints
- [ ] SQL injection prevention (use parameterized queries)
- [ ] CORS properly configured
- [ ] Sensitive data encrypted at rest
- [ ] Audit logging enabled

---

**Last Updated**: 2025-12-17  
**Version**: 1.0.0

