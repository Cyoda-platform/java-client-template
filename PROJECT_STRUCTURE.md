# Project Structure - Crypto Exchange Platform

## 📂 Complete Directory Tree

```
crypto-exchange/
├── src/
│   ├── main/
│   │   ├── java/com/java_template/
│   │   │   ├── Application.java                    # Spring Boot entry point
│   │   │   ├── application/
│   │   │   │   ├── entity/                         # 11 Entity models
│   │   │   │   │   ├── user/version_1/
│   │   │   │   │   │   └── User.java
│   │   │   │   │   ├── account/version_1/
│   │   │   │   │   │   └── Account.java
│   │   │   │   │   ├── wallet/version_1/
│   │   │   │   │   │   └── Wallet.java
│   │   │   │   │   ├── asset/version_1/
│   │   │   │   │   │   └── Asset.java
│   │   │   │   │   ├── market/version_1/
│   │   │   │   │   │   └── Market.java
│   │   │   │   │   ├── order/version_1/
│   │   │   │   │   │   └── Order.java
│   │   │   │   │   ├── trade/version_1/
│   │   │   │   │   │   └── Trade.java
│   │   │   │   │   ├── transaction/version_1/
│   │   │   │   │   │   └── Transaction.java
│   │   │   │   │   ├── kycprofile/version_1/
│   │   │   │   │   │   └── KYCProfile.java
│   │   │   │   │   ├── compliancealert/version_1/
│   │   │   │   │   │   └── ComplianceAlert.java
│   │   │   │   │   └── liquiditypool/version_1/
│   │   │   │   │       └── LiquidityPool.java
│   │   │   │   ├── processor/                      # 16 Processors
│   │   │   │   │   ├── OrderValidationProcessor.java
│   │   │   │   │   ├── OrderMatchingProcessor.java
│   │   │   │   │   ├── OrderSettlementProcessor.java
│   │   │   │   │   ├── DepositDetectionProcessor.java
│   │   │   │   │   ├── DepositKYCAMLProcessor.java
│   │   │   │   │   ├── DepositSettlementProcessor.java
│   │   │   │   │   ├── WithdrawalAMLProcessor.java
│   │   │   │   │   ├── WithdrawalSigningProcessor.java
│   │   │   │   │   ├── WithdrawalFinalizationProcessor.java
│   │   │   │   │   ├── KYCAutomatedScreeningProcessor.java
│   │   │   │   │   ├── ComplianceAlertGenerationProcessor.java
│   │   │   │   │   ├── LiquidityPoolInitializationProcessor.java
│   │   │   │   │   ├── LiquidityRebalancingProcessor.java
│   │   │   │   │   ├── LiquidityWithdrawalProcessor.java
│   │   │   │   │   └── TransactionConfirmationProcessor.java
│   │   │   │   └── controller/                     # 11 Controllers
│   │   │   │       ├── UserController.java
│   │   │   │       ├── AccountController.java
│   │   │   │       ├── WalletController.java
│   │   │   │       ├── AssetController.java
│   │   │   │       ├── MarketController.java
│   │   │   │       ├── OrderController.java
│   │   │   │       ├── TradeController.java
│   │   │   │       ├── TransactionController.java
│   │   │   │       ├── KYCProfileController.java
│   │   │   │       ├── ComplianceAlertController.java
│   │   │   │       └── LiquidityPoolController.java
│   │   │   └── common/                             # Framework (READ-ONLY)
│   │   │       ├── auth/
│   │   │       ├── config/
│   │   │       ├── controller/
│   │   │       ├── dto/
│   │   │       ├── exception/
│   │   │       ├── grpc/
│   │   │       ├── repository/
│   │   │       ├── serializer/
│   │   │       ├── service/
│   │   │       ├── tool/
│   │   │       ├── util/
│   │   │       └── workflow/
│   │   └── resources/
│   │       ├── application.yml                     # Spring config
│   │       ├── entity/                             # 11 Entity JSON examples
│   │       │   ├── user/version_1/User.json
│   │       │   ├── account/version_1/Account.json
│   │       │   ├── wallet/version_1/Wallet.json
│   │       │   ├── asset/version_1/Asset.json
│   │       │   ├── market/version_1/Market.json
│   │       │   ├── order/version_1/Order.json
│   │       │   ├── trade/version_1/Trade.json
│   │       │   ├── transaction/version_1/Transaction.json
│   │       │   ├── kycprofile/version_1/KYCProfile.json
│   │       │   ├── compliancealert/version_1/ComplianceAlert.json
│   │       │   └── liquiditypool/version_1/LiquidityPool.json
│   │       ├── workflow/                           # 11 Workflow definitions
│   │       │   ├── user/version_1/User.json
│   │       │   ├── account/version_1/Account.json
│   │       │   ├── wallet/version_1/Wallet.json
│   │       │   ├── asset/version_1/Asset.json
│   │       │   ├── market/version_1/Market.json
│   │       │   ├── order/version_1/Order.json
│   │       │   ├── trade/version_1/Trade.json
│   │       │   ├── transaction/version_1/Transaction.json
│   │       │   ├── kycprofile/version_1/KYCProfile.json
│   │       │   ├── compliancealert/version_1/ComplianceAlert.json
│   │       │   └── liquiditypool/version_1/LiquidityPool.json
│   │       ├── schema/                             # JSON schemas (generated)
│   │       └── functional_requirements/
│   │           └── crypto_exchange.md              # Requirements document
│   └── test/
│       ├── java/com/example/application/           # Test examples
│       │   ├── entity/
│       │   ├── processor/
│       │   ├── controller/
│       │   └── criterion/
│       └── resources/
│           ├── example/
│           ├── features/
│           └── workflows/
├── gradle/                                         # Gradle wrapper
├── helm/                                           # Kubernetes Helm charts
│   ├── Chart.yaml
│   ├── values.yaml
│   └── templates/
├── build.gradle                                    # Gradle build config
├── gradlew                                         # Gradle wrapper script
├── gradlew.bat                                     # Gradle wrapper (Windows)
├── Dockerfile                                      # Docker configuration
├── README.md                                       # Original README
├── CRYPTO_EXCHANGE_README.md                       # Main documentation
├── DEVELOPMENT_GUIDE.md                            # Developer guide
├── IMPLEMENTATION_SUMMARY.md                       # Implementation details
├── QUICK_REFERENCE.md                              # Quick reference
├── PROJECT_STRUCTURE.md                            # This file
├── CONTRIBUTING.md                                 # Contribution guidelines
├── LICENSE                                         # License file
└── usage-rules.md                                  # Usage rules
```

## 📊 File Statistics

| Category | Count | Location |
|----------|-------|----------|
| **Entities** | 11 | `src/main/java/.../entity/` |
| **Processors** | 16 | `src/main/java/.../processor/` |
| **Controllers** | 11 | `src/main/java/.../controller/` |
| **Entity JSON Examples** | 11 | `src/main/resources/entity/` |
| **Workflow Definitions** | 11 | `src/main/resources/workflow/` |
| **Documentation Files** | 6 | Root directory |
| **Total Java Classes** | 38 | `src/main/java/` |
| **Total JSON Files** | 22 | `src/main/resources/` |

## 🔍 File Navigation Guide

### To Find...

**Entity Models**
```
src/main/java/com/java_template/application/entity/{entity_name}/version_1/{EntityName}.java
```
Example: `src/main/java/com/java_template/application/entity/user/version_1/User.java`

**Entity JSON Examples**
```
src/main/resources/entity/{entity_name}/version_1/{EntityName}.json
```
Example: `src/main/resources/entity/user/version_1/User.json`

**Workflow Definitions**
```
src/main/resources/workflow/{entity_name}/version_1/{EntityName}.json
```
Example: `src/main/resources/workflow/order/version_1/Order.json`

**Processors**
```
src/main/java/com/java_template/application/processor/{Name}Processor.java
```
Example: `src/main/java/com/java_template/application/processor/OrderValidationProcessor.java`

**Controllers**
```
src/main/java/com/java_template/application/controller/{Name}Controller.java
```
Example: `src/main/java/com/java_template/application/controller/UserController.java`

**Test Examples**
```
src/test/java/com/example/application/{category}/{Name}.java
```
Example: `src/test/java/com/example/application/entity/example_entity/version_1/ExampleEntity.java`

## 🔗 Key Files

| File | Purpose | Size |
|------|---------|------|
| `build.gradle` | Gradle build configuration | 272 lines |
| `src/main/resources/application.yml` | Spring Boot configuration | ~50 lines |
| `src/main/resources/functional_requirements/crypto_exchange.md` | Requirements | 107 lines |
| `CRYPTO_EXCHANGE_README.md` | Main documentation | 300 lines |
| `DEVELOPMENT_GUIDE.md` | Developer guide | 300 lines |
| `IMPLEMENTATION_SUMMARY.md` | Implementation details | 300 lines |
| `QUICK_REFERENCE.md` | Quick reference | 300 lines |

## 🚀 Getting Around

### To Add a New Entity
1. Create directory: `src/main/java/com/java_template/application/entity/{name}/version_1/`
2. Create Java file: `{Name}.java`
3. Create JSON example: `src/main/resources/entity/{name}/version_1/{Name}.json`
4. Create workflow: `src/main/resources/workflow/{name}/version_1/{Name}.json`
5. Create controller: `src/main/java/com/java_template/application/controller/{Name}Controller.java`

### To Add a New Processor
1. Create file: `src/main/java/com/java_template/application/processor/{Name}Processor.java`
2. Extend `CyodaProcessor`
3. Add to workflow JSON in `processors` array
4. Validate: `./gradlew validateWorkflowImplementations`

### To Add a New Endpoint
1. Open controller: `src/main/java/com/java_template/application/controller/{Name}Controller.java`
2. Add method with `@PostMapping`, `@GetMapping`, `@PutMapping`, or `@DeleteMapping`
3. Follow existing patterns for error handling
4. Test with curl or Postman

## 📚 Documentation Map

| Document | Purpose | Audience |
|----------|---------|----------|
| `README.md` | Original project README | Everyone |
| `CRYPTO_EXCHANGE_README.md` | Platform overview and API reference | Everyone |
| `DEVELOPMENT_GUIDE.md` | Step-by-step development guide | Developers |
| `IMPLEMENTATION_SUMMARY.md` | What was built and next steps | Project managers |
| `QUICK_REFERENCE.md` | Commands and common patterns | Developers |
| `PROJECT_STRUCTURE.md` | This file - codebase navigation | Developers |
| `CONTRIBUTING.md` | Contribution guidelines | Contributors |
| `usage-rules.md` | Framework usage rules | Developers |

## 🔐 Important Notes

- ✅ **DO NOT MODIFY**: `src/main/java/com/java_template/common/` (framework code)
- ✅ **DO MODIFY**: `src/main/java/com/java_template/application/` (application code)
- ✅ **DO MODIFY**: `src/main/resources/entity/` (entity examples)
- ✅ **DO MODIFY**: `src/main/resources/workflow/` (workflow definitions)
- ✅ **VALIDATE AFTER CHANGES**: Run `./gradlew validateWorkflowImplementations`

## 🎯 Quick Navigation

```bash
# View all entities
ls -la src/main/java/com/java_template/application/entity/*/version_1/

# View all processors
ls -la src/main/java/com/java_template/application/processor/

# View all controllers
ls -la src/main/java/com/java_template/application/controller/

# View all workflows
find src/main/resources/workflow -name "*.json"

# View all entity examples
find src/main/resources/entity -name "*.json"

# Count lines of code
find src/main/java/com/java_template/application -name "*.java" -exec wc -l {} + | tail -1
```

---

**Last Updated**: 2025-12-17  
**Version**: 1.0.0

