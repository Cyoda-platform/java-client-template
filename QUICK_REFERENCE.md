# Compliance Management Platform - Quick Reference

## Entity Files Location

```
src/main/java/com/example/application/entity/
├── customer/version_1/Customer.java
├── account/version_1/Account.java
├── transaction/version_1/Transaction.java
├── watchlist_entry/version_1/WatchlistEntry.java
├── alert/version_1/Alert.java
├── case_entity/version_1/Case.java
└── document_evidence/version_1/DocumentEvidence.java

src/main/resources/entity/
├── customer/version_1/Customer.json
├── account/version_1/Account.json
├── transaction/version_1/Transaction.json
├── watchlist_entry/version_1/WatchlistEntry.json
├── alert/version_1/Alert.json
├── case_entity/version_1/Case.json
└── document_evidence/version_1/DocumentEvidence.json
```

## Entity Constants

| Entity | ENTITY_NAME | ENTITY_VERSION |
|--------|-------------|----------------|
| Customer | "Customer" | 1 |
| Account | "Account" | 1 |
| Transaction | "Transaction" | 1 |
| WatchlistEntry | "WatchlistEntry" | 1 |
| Alert | "Alert" | 1 |
| Case | "Case" | 1 |
| DocumentEvidence | "DocumentEvidence" | 1 |

## Enum Values

### Customer
- **customerType:** INDIVIDUAL, BUSINESS
- **status:** PENDING, VERIFIED, SUSPENDED
- **kycLevel:** LOW, MEDIUM, HIGH
- **addressType:** HOME, BUSINESS, OTHER

### Account
- **accountType:** CHECKING, SAVINGS, WALLET
- **status:** ACTIVE, INACTIVE, CLOSED, SUSPENDED

### Transaction
- **type:** PAYMENT, TRANSFER, DEPOSIT, WITHDRAWAL
- **status:** PENDING, COMPLETED, FAILED, REVERSED

### WatchlistEntry
- **source:** OFAC, EU_SANCTIONS, CUSTOM
- **type:** INDIVIDUAL, ENTITY, VESSEL
- **riskLevel:** LOW, MEDIUM, HIGH, CRITICAL

### Alert
- **severity:** LOW, MEDIUM, HIGH
- **status:** OPEN, IN_REVIEW, ESCALATED, CLOSED

### Case
- **status:** OPEN, IN_PROGRESS, RESOLVED, ESCALATED

## Key Nested Classes

| Entity | Nested Class | Fields |
|--------|-------------|--------|
| Customer | Address | line1, line2, city, state, postcode, country, addressType |
| Account | Balance | available, ledger, lastUpdated |
| Transaction | GeoLocation | latitude, longitude, city, country |
| WatchlistEntry | Identifier | type, value |
| Case | AuditEvent | timestamp, actor, action, details |

## Validation Rules

| Entity | Required Fields | Unique |
|--------|-----------------|--------|
| Customer | id, legalName | id |
| Account | id, customerId | id, accountNumber (per customer) |
| Transaction | id, accountId | id |
| WatchlistEntry | id, name | id |
| Alert | id | id |
| Case | id, title | id, title |
| DocumentEvidence | id, caseId, filename | id |

## Common Patterns

### Create Entity
```java
EntityWithMetadata<Customer> response = entityService.create(customer);
```

### Get by Technical ID
```java
EntityWithMetadata<Customer> response = entityService.getById(
    uuid, modelSpec, Customer.class);
```

### Get by Business ID
```java
EntityWithMetadata<Customer> response = entityService.findByBusinessId(
    modelSpec, "CUST-001", "id", Customer.class);
```

### Search with Conditions
```java
List<QueryCondition> conditions = new ArrayList<>();
conditions.add(new SimpleCondition()
    .withJsonPath("$.status")
    .withOperation(Operation.EQUALS)
    .withValue(objectMapper.valueToTree("VERIFIED")));

GroupCondition group = new GroupCondition()
    .withOperator(GroupCondition.Operator.AND)
    .withConditions(conditions);

PageResult<EntityWithMetadata<Customer>> result = 
    entityService.search(modelSpec, group, Customer.class, params);
```

### Update Entity
```java
EntityWithMetadata<Customer> response = entityService.update(
    uuid, customer, "transition_name");
```

### Delete Entity
```java
entityService.deleteById(uuid);
```

## ModelSpec Creation

```java
ModelSpec modelSpec = new ModelSpec()
    .withName(Customer.ENTITY_NAME)
    .withVersion(Customer.ENTITY_VERSION);
```

## SearchAndRetrievalParams

```java
SearchAndRetrievalParams params = SearchAndRetrievalParams.builder()
    .pageSize(50)
    .pageNumber(0)
    .inMemory(false)
    .pointInTime(pointInTimeDate)
    .searchId(searchId)
    .build();
```

## Common Imports

```java
import com.example.application.entity.customer.version_1.Customer;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.*;
```

## Build Commands

```bash
# Compile
./gradlew clean compileJava

# Full build
./gradlew build

# Validate workflows
./gradlew validateWorkflowImplementations

# Run tests
./gradlew test
```

## File Naming Convention

- **Java:** PascalCase (e.g., Customer.java)
- **Package:** snake_case (e.g., customer, watchlist_entry)
- **JSON:** PascalCase (e.g., Customer.json)
- **Directory:** snake_case (e.g., customer, case_entity)

