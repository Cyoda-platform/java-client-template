# Development Guide - Crypto Exchange Platform

## 🎯 Quick Start for Developers

### Environment Setup

```bash
# Clone repository
git clone <repo-url>
cd crypto-exchange

# Install dependencies
./gradlew build

# Start development server
./gradlew bootRun

# Run tests
./gradlew test
```

## 📋 Adding New Features

### 1. Create Entity

Create `src/main/java/com/java_template/application/entity/{name}/version_1/{Name}.java`:

```java
@Data
public class MyEntity implements CyodaEntity {
    public static final String ENTITY_NAME = "MyEntity";
    public static final Integer ENTITY_VERSION = 1;
    
    private String myEntityId;  // Business ID
    private String status;
    
    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }
    
    @Override
    public boolean isValid(EntityMetadata metadata) {
        return myEntityId != null && !myEntityId.isBlank();
    }
}
```

### 2. Create Entity JSON Example

Create `src/main/resources/entity/{name}/version_1/{Name}.json`:

```json
{
  "myEntityId": "entity_001",
  "status": "ACTIVE"
}
```

### 3. Create Workflow

Create `src/main/resources/workflow/{name}/version_1/{Name}.json`:

```json
{
  "entityName": "MyEntity",
  "modelVersion": 1,
  "importMode": "REPLACE",
  "workflows": [
    {
      "name": "MyEntity Workflow",
      "initialState": "initial",
      "active": true,
      "states": {
        "initial": {
          "transitions": [
            {
              "name": "activate",
              "next": "active",
              "manual": true,
              "disabled": false
            }
          ]
        },
        "active": {
          "transitions": []
        }
      }
    }
  ]
}
```

### 4. Create Processor (if needed)

Create `src/main/java/com/java_template/application/processor/{Name}Processor.java`:

```java
@Component
public class MyEntityProcessor implements CyodaProcessor {
    private static final Logger logger = LoggerFactory.getLogger(MyEntityProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public MyEntityProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing MyEntity for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(MyEntity.class)
                .validate(this::isValidEntityWithMetadata, "Invalid entity wrapper")
                .map(this::processEntityWithMetadataLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<MyEntity> entityWithMetadata) {
        MyEntity entity = entityWithMetadata.entity();
        return entity != null && entity.isValid(entityWithMetadata.metadata());
    }

    private EntityWithMetadata<MyEntity> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<MyEntity> context) {
        EntityWithMetadata<MyEntity> entityWithMetadata = context.entityResponse();
        MyEntity entity = entityWithMetadata.entity();
        
        // Your business logic here
        logger.info("Processing entity: {}", entity.getMyEntityId());
        
        return entityWithMetadata;
    }
}
```

### 5. Create Controller

Create `src/main/java/com/java_template/application/controller/{Name}Controller.java`:

```java
@RestController
@RequestMapping("/ui/myentity")
@CrossOrigin(origins = "*")
public class MyEntityController {
    private static final Logger logger = LoggerFactory.getLogger(MyEntityController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public MyEntityController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<EntityWithMetadata<MyEntity>> create(@Valid @RequestBody MyEntity entity) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                .withName(MyEntity.ENTITY_NAME)
                .withVersion(MyEntity.ENTITY_VERSION);
            
            EntityWithMetadata<MyEntity> response = entityService.create(entity);
            URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.metadata().getId()).toUri();
            return ResponseEntity.created(location).body(response);
        } catch (Exception e) {
            logger.error("Failed to create entity", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<MyEntity>> getById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                .withName(MyEntity.ENTITY_NAME)
                .withVersion(MyEntity.ENTITY_VERSION);
            EntityWithMetadata<MyEntity> response = entityService.getById(id, modelSpec, MyEntity.class);
            return response != null ? ResponseEntity.ok(response) : ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Failed to retrieve entity", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<MyEntity>> update(
            @PathVariable UUID id,
            @Valid @RequestBody MyEntity entity,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<MyEntity> response = entityService.update(id, entity, transition);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to update entity", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Failed to delete entity", e);
            return ResponseEntity.badRequest().build();
        }
    }
}
```

## 🧪 Testing

### Unit Test Example

```java
@SpringBootTest
class MyEntityProcessorTest {
    @Autowired
    private MyEntityProcessor processor;
    
    @Test
    void testProcessing() {
        MyEntity entity = new MyEntity();
        entity.setMyEntityId("test_001");
        entity.setStatus("ACTIVE");
        
        // Test logic
        assertTrue(entity.isValid(null));
    }
}
```

### Integration Test Example

```java
@SpringBootTest
class MyEntityControllerTest {
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    void testCreateEntity() throws Exception {
        MyEntity entity = new MyEntity();
        entity.setMyEntityId("test_001");
        
        mockMvc.perform(post("/ui/myentity")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(entity)))
            .andExpect(status().isCreated());
    }
}
```

## ✅ Validation Checklist

Before committing:

- [ ] Entity implements `CyodaEntity`
- [ ] Entity has `ENTITY_NAME` and `ENTITY_VERSION` constants
- [ ] Entity has `isValid()` method
- [ ] JSON example file exists and is valid
- [ ] Workflow JSON has `"initialState": "initial"`
- [ ] Processor class name matches workflow processor name (case-sensitive)
- [ ] Controller uses `/ui/{entity_name}/**` pattern
- [ ] All tests pass: `./gradlew test`
- [ ] Build succeeds: `./gradlew build`
- [ ] Workflows validate: `./gradlew validateWorkflowImplementations`

## 🔍 Debugging

### Enable Debug Logging

Edit `src/main/resources/application.yml`:

```yaml
logging:
  level:
    com.java_template: DEBUG
    org.springframework: INFO
```

### Debug Processor

Add breakpoints in processor `processEntityWithMetadataLogic()` method and run:

```bash
./gradlew bootRun --debug
```

### View Workflow State

Check entity metadata:

```java
EntityWithMetadata<MyEntity> entity = entityService.getById(id, modelSpec, MyEntity.class);
String currentState = entity.metadata().getState();
logger.info("Current state: {}", currentState);
```

## 📊 Performance Tips

1. **Use streaming for large datasets**:
   ```java
   try (Stream<EntityWithMetadata<MyEntity>> stream = 
        entityService.streamAll(modelSpec, MyEntity.class, params)) {
       stream.forEach(e -> processEntity(e));
   }
   ```

2. **Use pagination for searches**:
   ```java
   SearchAndRetrievalParams params = SearchAndRetrievalParams.builder()
       .pageSize(100)
       .pageNumber(0)
       .build();
   ```

3. **Cache frequently accessed entities**:
   ```java
   @Cacheable("myentities")
   public EntityWithMetadata<MyEntity> getById(UUID id) { ... }
   ```

## 🚀 Deployment

### Local Development
```bash
./gradlew bootRun
```

### Docker
```bash
./gradlew bootJar
docker build -t crypto-exchange:dev .
docker run -p 8080:8080 crypto-exchange:dev
```

### Production
```bash
./gradlew build -Pprod
# Deploy JAR to production environment
```

## 📚 Resources

- **Framework**: Cyoda Workflow Engine
- **Spring Boot**: 3.5.3
- **Java**: 21
- **Build Tool**: Gradle 8.7
- **Testing**: JUnit 5, Mockito

## 🤝 Code Style

- Use Lombok `@Data` for entities
- Follow Spring naming conventions
- Add comprehensive logging
- Write JavaDoc for public methods
- Use meaningful variable names
- Keep methods focused and small

## 📞 Getting Help

1. Check existing entity implementations in `src/main/java/com/java_template/application/entity/`
2. Review test examples in `src/test/java/com/example/application/`
3. Consult functional requirements: `src/main/resources/functional_requirements/crypto_exchange.md`
4. Review workflow examples in `src/main/resources/workflow/`

---

Happy coding! 🚀

