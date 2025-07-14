import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class Application {

    public static void main(String[] args) {
        ObjectMapper objectMapper = new ObjectMapper();
        EntityService entityService = new EntityServiceImpl(objectMapper);

        EntityController controller = new EntityController(entityService, objectMapper);

        // Example entity data creation
        ObjectNode customerData = objectMapper.createObjectNode();
        customerData.put("id", UUID.randomUUID().toString());
        customerData.put("name", "John Doe");
        customerData.put("email", "john.doe@example.com");
        customerData.put("externalId", "ext-12345");

        // Call createCustomer endpoint
        controller.createCustomer(customerData)
                .thenAccept(uuid -> System.out.println("Customer persisted with UUID: " + uuid))
                .exceptionally(ex -> {
                    ex.printStackTrace();
                    return null;
                });
    }
}

class EntityController {

    private static final int ENTITY_VERSION = 1;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public EntityController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    public CompletableFuture<UUID> createCustomer(ObjectNode customerData) {
        // Basic validation example (could be more complex or moved to workflow)
        if (!customerData.hasNonNull("email")) {
            CompletableFuture<UUID> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(new IllegalArgumentException("Email is required"));
            return failedFuture;
        }

        return entityService.addItem(
                "Customer",
                ENTITY_VERSION,
                customerData,
                this::processCustomer
        );
    }

    private CompletableFuture<ObjectNode> processCustomer(ObjectNode entity) {
        // Prevent infinite recursion: do NOT call add/update/delete on "Customer" here

        // Set default status if missing
        if (!entity.has("status")) {
            entity.put("status", "NEW");
        }

        // Validate email format (simple check)
        String email = entity.hasNonNull("email") ? entity.get("email").asText() : "";
        if (!email.contains("@")) {
            CompletableFuture<ObjectNode> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalArgumentException("Invalid email format"));
            return failed;
        }

        // Async fetch external data and enrich entity
        String externalId = entity.hasNonNull("externalId") ? entity.get("externalId").asText() : null;

        CompletableFuture<ObjectNode> externalDataFuture = externalId != null
                ? fetchExternalDataAsync(externalId)
                : CompletableFuture.completedFuture(objectMapper.createObjectNode());

        return externalDataFuture.thenCompose(externalData -> {
            // Add external data to entity
            entity.set("externalDetails", externalData);

            // Fire-and-forget async side effect wrapped in CompletableFuture
            CompletableFuture<Void> emailFuture = sendWelcomeEmailAsync(entity);

            // Add audit record entity (different entity model)
            ObjectNode auditRecord = objectMapper.createObjectNode();
            auditRecord.put("customerId", entity.get("id").asText());
            auditRecord.put("action", "CREATE");
            auditRecord.put("timestamp", System.currentTimeMillis());

            CompletableFuture<UUID> auditAddFuture = entityService.addItem(
                    "AuditRecord",
                    ENTITY_VERSION,
                    auditRecord,
                    Function.identity() // no workflow for audit record
            );

            // Combine all futures and return modified entity when all complete
            return CompletableFuture.allOf(emailFuture, auditAddFuture)
                    .thenApply(v -> entity);
        });
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    private CompletableFuture<ObjectNode> fetchExternalDataAsync(String externalId) {
        // Simulate async external service call with dummy data
        ObjectNode dummyData = objectMapper.createObjectNode();
        dummyData.put("info", "External info for " + externalId);
        return CompletableFuture.completedFuture(dummyData);
    }

    private CompletableFuture<Void> sendWelcomeEmailAsync(ObjectNode entity) {
        // Simulate async email sending with delay
        return CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(100); // simulate delay
                System.out.println("Welcome email sent to " + entity.get("email").asText());
            } catch (InterruptedException ignored) {
            }
        });
    }
}

interface EntityService {
    <T> CompletableFuture<UUID> addItem(String entityModel, int entityVersion, T entity, Function<T, CompletableFuture<T>> workflow);
}

// Simulated implementation of EntityService
class EntityServiceImpl implements EntityService {

    private final ObjectMapper objectMapper;

    public EntityServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public <T> CompletableFuture<UUID> addItem(String entityModel, int entityVersion, T entity, Function<T, CompletableFuture<T>> workflow) {
        // Validate inputs
        if (entityModel == null || entityModel.isEmpty()) {
            CompletableFuture<UUID> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalArgumentException("entityModel is required"));
            return failed;
        }
        if (entity == null) {
            CompletableFuture<UUID> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalArgumentException("entity is required"));
            return failed;
        }
        if (workflow == null) {
            CompletableFuture<UUID> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalArgumentException("workflow function is required"));
            return failed;
        }

        // Apply workflow function asynchronously
        return workflow.apply(entity)
                .thenCompose(modifiedEntity -> {
                    // Persist the modified entity
                    return persistEntity(entityModel, entityVersion, modifiedEntity);
                });
    }

    private <T> CompletableFuture<UUID> persistEntity(String entityModel, int entityVersion, T entity) {
        // Simulated persistence logic; in real code replace with DB call or similar
        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(50); // simulate I/O delay
            } catch (InterruptedException ignored) {
            }
            UUID generatedId = UUID.randomUUID();
            System.out.println("Persisted entity [" + entityModel + "] version " + entityVersion + " with ID: " + generatedId);
            return generatedId;
        });
    }
}