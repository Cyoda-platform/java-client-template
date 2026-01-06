# Implementation Notes

## Architecture Decisions

### 1. Entity Design
- **Lombok @Data**: All entities use Lombok for boilerplate reduction
- **Nested Classes**: Complex types (Address, Contact, etc.) are nested static classes for type safety
- **Business IDs**: Each entity has a business identifier (cartId, orderId, etc.) separate from technical UUID
- **Validation**: `isValid()` method checks required fields

### 2. Workflow State Machines
- **Initial States**: Each workflow has a clear initial state (NEW for Cart, INITIATED for Payment, etc.)
- **Manual Transitions**: Update operations use manual transitions (ADD_ITEM, DECREMENT_ITEM, etc.)
- **Automatic Transitions**: Creation uses automatic transitions (CREATE_ON_FIRST_ADD)
- **Processor Integration**: Transitions trigger processors for business logic

### 3. Processor Implementation
- **RecalculateTotalsProcessor**: Recalculates cart totals on every line change
- **AutoMarkPaidProcessor**: Simulates 3-second payment processing delay
- **CreateOrderFromPaidProcessor**: Complex processor that:
  - Snapshots cart data into order
  - Decrements product quantities
  - Creates shipment entity
  - Uses EntityService for cross-entity operations

### 4. Controller Design
- **Thin Proxy Pattern**: Controllers map HTTP requests to EntityService calls
- **No Business Logic**: All business logic in processors
- **Search Patterns**:
  - In-memory search for small result sets (products by category)
  - Paginated search for large result sets (product list)
  - Streaming for memory-efficient processing
- **Error Handling**: RFC 7807 ProblemDetail for all errors

### 5. Search Implementation
- **QueryCondition Pattern**: Uses List<QueryCondition> for type safety
- **GroupCondition**: Combines multiple conditions with AND/OR operators
- **SimpleCondition**: Individual field conditions with operations (EQUALS, CONTAINS, GREATER_OR_EQUAL, etc.)
- **ObjectMapper**: Converts values to JsonNode for Cyoda API

## Key Implementation Details

### Cart Workflow
```
NEW --[CREATE_ON_FIRST_ADD]--> ACTIVE
ACTIVE --[ADD_ITEM/DECREMENT_ITEM/REMOVE_ITEM]--> ACTIVE (recalc totals)
ACTIVE --[OPEN_CHECKOUT]--> CHECKING_OUT
CHECKING_OUT --[CHECKOUT]--> CONVERTED
```

### Payment Workflow
```
INITIATED --[START_DUMMY_PAYMENT]--> INITIATED
INITIATED --[AUTO_MARK_PAID]--> PAID (after 3 seconds)
```

### Order Workflow
```
WAITING_TO_FULFILL --[CREATE_ORDER_FROM_PAID]--> PICKING
PICKING --[READY_TO_SEND]--> WAITING_TO_SEND
WAITING_TO_SEND --[MARK_SENT]--> SENT
SENT --[MARK_DELIVERED]--> DELIVERED
```

## Processor Execution Flow

### RecalculateTotalsProcessor
1. Receives Cart entity
2. Iterates through cart lines
3. Calculates line totals (price × qty)
4. Sums totalItems and grandTotal
5. Updates timestamps
6. Returns updated Cart

### AutoMarkPaidProcessor
1. Receives Payment entity
2. Sleeps for 3 seconds (simulating processing)
3. Sets status to "PAID"
4. Updates timestamps
5. Returns updated Payment

### CreateOrderFromPaidProcessor
1. Receives Order entity (pre-created)
2. Decrements Product.quantityAvailable for each line
3. Creates Shipment entity with PICKING status
4. Copies cart lines and guest contact to order
5. Returns updated Order

## Search Pattern Usage

### In-Memory Search (ProductController.searchByCategory)
```java
entityService.search(modelSpec, condition, Product.class,
    SearchAndRetrievalParams.builder()
        .pageSize(1000)
        .inMemory(true)  // Load all results into memory
        .build())
```
**Use Case**: Small, bounded result sets (e.g., products in a category)

### Paginated Search (ProductController.searchWithPagination)
```java
entityService.search(modelSpec, condition, Product.class,
    SearchAndRetrievalParams.builder()
        .pageSize(50)
        .pageNumber(page)
        .searchId(searchId)  // For multi-page navigation
        .build())
```
**Use Case**: Large or unknown result sets with UI pagination

### Streaming (ExampleEntityController.exportEntities)
```java
try (Stream<EntityWithMetadata<Product>> stream =
        entityService.searchAsStream(modelSpec, condition, Product.class,
            SearchAndRetrievalParams.builder()
                .pageSize(100)
                .build())) {
    stream.forEach(item -> { /* process */ });
}
```
**Use Case**: Memory-efficient processing of large datasets

## Compliance Notes

### No Reflection
- All entity access is direct field access
- No use of java.lang.reflect API
- Type-safe through generics

### No Changes to common/
- All application code in `com.java_template.application` package
- Common utilities remain untouched in `com.java_template.common`

### Read-Only Processors
- Processors can only update the current entity via return value
- Cross-entity updates use EntityService.update()
- No direct state/transition changes in processors

### Type Safety
- All searches use List<QueryCondition>
- No SimpleCondition used directly in searches
- GroupCondition combines conditions with proper operators

## Testing Recommendations

1. **Unit Tests**: Test processors with mock EntityService
2. **Integration Tests**: Test controllers with embedded Cyoda
3. **Workflow Tests**: Verify state transitions and processor execution
4. **Search Tests**: Verify filter combinations and pagination

## Performance Considerations

1. **Product Search**: Use in-memory for category filters, paginated for free-text
2. **Cart Operations**: Recalculate totals on every change (simple operation)
3. **Order Creation**: Batch product updates in CreateOrderFromPaidProcessor
4. **Shipment Creation**: Create after order to ensure consistency

## Future Enhancements

1. **Inventory Reservations**: Reserve stock during checkout
2. **Payment Providers**: Integrate real payment gateways
3. **Shipping Integration**: Connect to shipping providers
4. **Order Tracking**: Real-time shipment tracking
5. **Product Reviews**: Add review and rating system
6. **Wishlist**: Save products for later
7. **Promotions**: Discount codes and promotional pricing
8. **Multi-Currency**: Support multiple currencies and locales

