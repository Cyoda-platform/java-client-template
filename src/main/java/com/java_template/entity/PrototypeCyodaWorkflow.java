import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class Application {

    private static final int ENTITY_VERSION = 1;

    private final EntityService entityService;

    public Application(EntityService entityService) {
        this.entityService = entityService;
    }

    // Public API method to add a User entity
    public CompletableFuture<UUID> addUser(ObjectNode userData) {
        // Validate userData here if needed (omitted for brevity)
        // Pass workflow function to entityService.addItem
        return entityService.addItem("User", ENTITY_VERSION, userData, this::processUser);
    }

    // Workflow function for User entity; modifies entity, performs async enrichment, logging, supplementary entities
    private CompletableFuture<ObjectNode> processUser(ObjectNode entity) {
        // Defensive null checks
        if (entity == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Entity data cannot be null"));
        }

        // Mark entity as processed with timestamp
        entity.put("processedAt", System.currentTimeMillis());

        // Example: async enrichment - fetch user profile data and attach
        CompletableFuture<ObjectNode> enrichmentFuture = fetchUserProfile(getStringSafe(entity, "userId"))
            .thenApply(profileData -> {
                if (profileData != null) {
                    entity.set("profile", profileData);
                }
                return entity;
            });

        // Fire-and-forget audit logging (non-blocking)
        enrichmentFuture.thenAcceptAsync(e -> logAuditEvent(e));

        // Create supplementary UserAuditLog entity asynchronously with its own workflow
        CompletableFuture<Void> supplementaryEntityFuture =
            entityService.addItem(
                "UserAuditLog",
                ENTITY_VERSION,
                createAuditLog(entity),
                this::processUserAuditLog
            ).thenAccept(id -> {
                // Optional: log supplementary entity creation
                System.out.println("UserAuditLog created with id: " + id);
            }).exceptionally(ex -> {
                // Prevent exceptions from breaking main flow, log error
                System.err.println("Failed to create UserAuditLog: " + ex.getMessage());
                return null;
            });

        // Return combined future completing when enrichment and supplementary entity creation done
        return CompletableFuture.allOf(enrichmentFuture, supplementaryEntityFuture)
                .thenApply(v -> entity);
    }

    // Workflow function for UserAuditLog entity; modifies audit log entity before persistence
    private CompletableFuture<ObjectNode> processUserAuditLog(ObjectNode auditLogEntity) {
        if (auditLogEntity == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Audit log entity cannot be null"));
        }
        auditLogEntity.put("loggedAt", System.currentTimeMillis());
        return CompletableFuture.completedFuture(auditLogEntity);
    }

    // Helper async method simulating fetching user profile data
    private CompletableFuture<ObjectNode> fetchUserProfile(String userId) {
        if (userId == null || userId.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }
        ObjectNode profile = JsonNodeFactory.instance.objectNode();
        profile.put("nickname", "user_" + userId);
        profile.put("preferences", "dark_mode");
        return CompletableFuture.completedFuture(profile);
    }

    // Fire-and-forget asynchronous audit event logger
    private void logAuditEvent(ObjectNode entity) {
        // In real scenario, dispatch to async logging system, here just print
        System.out.println("Audit event logged for entity: " + entity);
    }

    // Create UserAuditLog entity data from User entity data
    private ObjectNode createAuditLog(ObjectNode userEntity) {
        ObjectNode auditLog = JsonNodeFactory.instance.objectNode();
        auditLog.put("userId", getStringSafe(userEntity, "userId"));
        auditLog.put("action", "CREATE_USER");
        auditLog.put("timestamp", System.currentTimeMillis());
        return auditLog;
    }

    // Safe getter for String fields from ObjectNode
    private String getStringSafe(ObjectNode node, String fieldName) {
        if (node != null && node.has(fieldName) && node.get(fieldName).isTextual()) {
            return node.get(fieldName).asText();
        }
        return null;
    }

    // Interface definition for EntityService
    public interface EntityService {
        CompletableFuture<UUID> addItem(
                String entityModel,
                int entityVersion,
                ObjectNode entity,
                Function<ObjectNode, CompletableFuture<ObjectNode>> workflow);
    }

    // Example main method to demonstrate usage
    public static void main(String[] args) {
        // Dummy EntityService implementation for demonstration
        EntityService service = new EntityService() {
            @Override
            public CompletableFuture<UUID> addItem(String entityModel, int entityVersion, ObjectNode entity, Function<ObjectNode, CompletableFuture<ObjectNode>> workflow) {
                if (entity == null) {
                    return CompletableFuture.failedFuture(new IllegalArgumentException("Entity cannot be null"));
                }
                // Apply workflow function before persistence
                return workflow.apply(entity).thenCompose(processedEntity -> {
                    // Simulate persistence logic here
                    System.out.println("Persisting entityModel: " + entityModel + " with data: " + processedEntity);
                    // Return dummy UUID
                    return CompletableFuture.completedFuture(UUID.randomUUID());
                });
            }
        };

        Application app = new Application(service);

        // Create sample user data
        ObjectNode userData = JsonNodeFactory.instance.objectNode();
        userData.put("userId", "12345");
        userData.put("name", "John Doe");

        // Call addUser with workflow processing
        app.addUser(userData).thenAccept(uuid -> {
            System.out.println("User persisted with UUID: " + uuid);
        }).exceptionally(ex -> {
            System.err.println("Failed to add user: " + ex.getMessage());
            return null;
        });

        // Sleep main thread briefly to allow async tasks to complete in this demo
        try {
            Thread.sleep(2000);
        } catch (InterruptedException ignored) {
        }
    }
}