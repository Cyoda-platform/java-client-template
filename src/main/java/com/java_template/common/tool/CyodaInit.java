package com.java_template.common.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.auth.Authentication;
import com.java_template.common.util.HttpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.java_template.common.config.Config.*;

/**
 * Utility class for initializing Cyoda with entity models and workflow configurations.
 */
public class CyodaInit {
    private static final Logger logger = LoggerFactory.getLogger(CyodaInit.class);
    private static final Path WORKFLOW_DTO_DIR = Paths.get(System.getProperty("user.dir")).resolve("src/main/java/com/java_template/application/workflow");

    private final HttpUtils httpUtils;
    private final Authentication authentication;

    Set<String> pendingFiles = new HashSet<>();

    public CyodaInit(HttpUtils httpUtils, Authentication authentication) {
        this.httpUtils = httpUtils;
        this.authentication = authentication;
    }

    public CompletableFuture<Void> initCyoda() {
        logger.info("🔄 Starting workflow import into Cyoda...");

        try {
            String token = authentication.getAccessToken().getTokenValue();
            return initEntitiesSchema(WORKFLOW_DTO_DIR, token)
                    .thenRun(() -> logger.info("✅ Workflow import process completed."))
                    .exceptionally(ex -> {
                        handleImportError(ex);
                        return null;
                    });
        } catch (Exception ex) {
            logger.error("❌ Failed to obtain access token for workflow import");
            handleImportError(ex);
            return CompletableFuture.completedFuture(null);
        }
    }

    private void handleImportError(Throwable ex) {
        if (ex.getMessage() != null && ex.getMessage().contains("errorCode cannot be empty")) {
            logger.error("❌ OAuth2 authentication failed: The server returned an invalid error response format");
            logger.info("💡 This usually means the client credentials are invalid or the client is not registered");
            logger.info("💡 Please check your CYODA_CLIENT_ID and CYODA_CLIENT_SECRET in the .env file");
        } else if (ex.getMessage() != null && ex.getMessage().contains("M2M client not found")) {
            logger.error("❌ OAuth2 client not found: {}", ex.getMessage());
            logger.info("💡 Please verify your CYODA_CLIENT_ID is correct and registered on the server");
        } else {
            logger.error("❌ Cyoda workflow import failed: {}", ex.getMessage(), ex);
        }
    }

    public CompletableFuture<Void> initEntitiesSchema(Path entityDir, String token) {
        if (!Files.exists(entityDir)) {
            logger.warn("📁 Directory '{}' does not exist. Skipping workflow import.", entityDir.toAbsolutePath());
            return CompletableFuture.completedFuture(null);
        }

        try (ExecutorService executor = Executors.newFixedThreadPool(3);
             Stream<Path> jsonFilesStream = Files.walk(entityDir)) {

            List<Path> jsonFiles = jsonFilesStream
                    .filter(path -> path.toString().toLowerCase().endsWith(".json"))
                    .filter(path -> path.getParent() != null)
                    .filter(path -> path.getParent().getFileName().toString().equals("workflow"))
                    .toList();

            if (jsonFiles.isEmpty()) {
                logger.warn("⚠️ No workflow JSON files found in directory: {}", entityDir);
                return CompletableFuture.completedFuture(null);
            }

            List<CompletableFuture<Void>> futures = jsonFiles.stream()
                    .map(jsonFile -> {
                        // Extract entity name from filename (remove .json extension)
                        String fileName = jsonFile.getFileName().toString();
                        String entityName = fileName.substring(0, fileName.lastIndexOf('.'));
                        String relativePath = WORKFLOW_DTO_DIR.relativize(jsonFile).toString();
                        pendingFiles.add(relativePath);
                        return CompletableFuture.supplyAsync(() -> null, executor)
                                .thenCompose(v -> processWorkflowFile(jsonFile, token, entityName)
                                        .whenComplete((res, ex) -> {
                                            if (ex == null) {
                                                pendingFiles.remove(relativePath);
                                            }
                                        }));
                    })
                    .toList();

            return CompletableFuture
                    .allOf(futures.toArray(new CompletableFuture[0]))
                    .whenComplete((res, ex) -> {
                        if (ex != null) {
                            logger.error("❌ Errors occurred during workflow import: {}", ex.getMessage(), ex);
                        }
                        if (!pendingFiles.isEmpty()) {
                            logger.warn("⚠️ Not all workflows were imported. Remaining files:");
                            pendingFiles.forEach(name -> logger.warn(" - {}", name));
                        } else {
                            logger.info("🎉 All workflow files processed successfully.");
                        }
                    });
        } catch (IOException e) {
            logger.error("Error reading files at {}: {}", entityDir, e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    private CompletableFuture<Void> processWorkflowFile(Path file, String token, String entityName) {
        try {
            logger.info("📄 Processing workflow file for entity: {}", entityName);

            // First check and create entity model if needed
            return checkAndCreateEntityModel(token, entityName)
                    .thenCompose(v -> {
                        try {
                            String dtoContent = Files.readString(file);
                            ObjectMapper objectMapper = new ObjectMapper();
                            JsonNode dtoJson = objectMapper.readTree(dtoContent);

                            // Wrap the workflow content in the required format: {"workflows": [file_content]}
                            ObjectNode wrappedContent = objectMapper.createObjectNode();
                            ArrayNode workflowsArray = objectMapper.createArrayNode();
                            workflowsArray.add(dtoJson);
                            wrappedContent.set("workflows", workflowsArray);

                            String wrappedContentJson = wrappedContent.toString();

                            // Use the new endpoint format: model/{entity_name}/{ENTITY_VERSION}/workflow/import
                            String importPath = String.format("model/%s/%s/workflow/import", entityName, ENTITY_VERSION);
                            logger.debug("🔗 Using import endpoint: {}", importPath);

                            return httpUtils.sendPostRequest(token, CYODA_API_URL, importPath, wrappedContentJson,
                                    Map.of("importMode", "MERGE"))
                                    .thenApply(response -> {
                                        int statusCode = response.get("status").asInt();
                                        if (statusCode >= 200 && statusCode < 300) {
                                            logger.info("✅ Successfully imported workflow for entity: {}", entityName);
                                            return null;
                                        } else {
                                            String body = response.path("json").toString();
                                            String errorMsg = String.format("Failed to import workflow for entity %s. Status code: %d, body: %s",
                                                    entityName, statusCode, body);
                                            logger.error("❌ {}", errorMsg);
                                            throw new RuntimeException(errorMsg);
                                        }
                                    });
                        } catch (IOException e) {
                            logger.error("❌ Error reading workflow file {}: {}", file, e.getMessage());
                            throw new RuntimeException("Failed to read workflow file for entity " + entityName, e);
                        }
                    });
        } catch (Exception e) {
            logger.error("❌ Unexpected error processing workflow file {}: {}", file, e.getMessage());
            return CompletableFuture.failedFuture(new RuntimeException("Failed to process workflow file for entity " + entityName, e));
        }
    }

    /**
     * Checks if entity model exists and creates it if needed
     */
    private CompletableFuture<Void> checkAndCreateEntityModel(String token, String entityName) {
        String exportPath = String.format("model/export/SIMPLE_VIEW/%s/%s", entityName, ENTITY_VERSION);
        logger.debug("🔍 Checking if entity model exists: {}", exportPath);

        return httpUtils.sendGetRequest(token, CYODA_API_URL, exportPath)
                .thenCompose(response -> {
                    int statusCode = response.get("status").asInt();
                    if (statusCode >= 200 && statusCode < 300) {
                        logger.info("✅ Entity model already exists for: {}", entityName);
                        return CompletableFuture.completedFuture(null);
                    } else if (statusCode == 404) {
                        logger.info("📝 Entity model not found, creating for: {}", entityName);
                        return createEntityModel(token, entityName);
                    } else {
                        String body = response.path("json").toString();
                        String errorMsg = String.format("Failed to check entity model for %s. Status code: %d, body: %s",
                                entityName, statusCode, body);
                        logger.error("❌ {}", errorMsg);
                        throw new RuntimeException(errorMsg);
                    }
                })
                .exceptionally(ex -> {
                    if (ex.getCause() instanceof RuntimeException && ex.getMessage().contains("404")) {
                        logger.info("📝 Entity model not found (404), creating for: {}", entityName);
                        return createEntityModel(token, entityName).join();
                    }
                    throw new RuntimeException("Failed to check entity model for " + entityName, ex);
                });
    }

    /**
     * Creates entity model using sample data, then sets change level to STRUCTURAL and locks the model
     */
    private CompletableFuture<Void> createEntityModel(String token, String entityName) {
        String importPath = String.format("model/import/JSON/SAMPLE_DATA/%s/%s", entityName, ENTITY_VERSION);
        logger.debug("🔗 Creating entity model at: {}", importPath);

        return httpUtils.sendPostRequest(token, CYODA_API_URL, importPath, "{}")
                .thenApply(response -> {
                    int statusCode = response.get("status").asInt();
                    if (statusCode >= 200 && statusCode < 300) {
                        logger.info("✅ Successfully created entity model for: {}", entityName);
                        return null;
                    } else {
                        String body = response.path("json").toString();
                        String errorMsg = String.format("Failed to create entity model for %s. Status code: %d, body: %s",
                                entityName, statusCode, body);
                        logger.error("❌ {}", errorMsg);
                        throw new RuntimeException(errorMsg);
                    }
                })
                .thenCompose(v -> setChangeLevel(token, entityName))
                .thenCompose(v -> lockModel(token, entityName));
    }

    /**
     * Sets the change level to STRUCTURAL for the entity model
     */
    private CompletableFuture<Void> setChangeLevel(String token, String entityName) {
        String changeLevel = "STRUCTURAL";
        String changeLevelPath = String.format("model/%s/%s/changeLevel/%s", entityName, ENTITY_VERSION, changeLevel);
        logger.debug("🔗 Setting change level to {} for entity: {}", changeLevel, entityName);

        return httpUtils.sendPostRequest(token, CYODA_API_URL, changeLevelPath, null)
                .thenApply(response -> {
                    int statusCode = response.get("status").asInt();
                    if (statusCode >= 200 && statusCode < 300) {
                        logger.info("✅ Successfully set change level to {} for entity: {}", changeLevel, entityName);
                        return null;
                    } else {
                        String body = response.path("json").toString();
                        String errorMsg = String.format("Failed to set change level for %s. Status code: %d, body: %s",
                                entityName, statusCode, body);
                        logger.error("❌ {}", errorMsg);
                        throw new RuntimeException(errorMsg);
                    }
                });
    }

    /**
     * Locks the entity model
     */
    private CompletableFuture<Void> lockModel(String token, String entityName) {
        String lockPath = String.format("model/%s/%s/lock", entityName, ENTITY_VERSION);
        logger.debug("🔗 Locking entity model for: {}", entityName);

        return httpUtils.sendPutRequest(token, CYODA_API_URL, lockPath, null)
                .thenApply(response -> {
                    int statusCode = response.get("status").asInt();
                    if (statusCode >= 200 && statusCode < 300) {
                        logger.info("✅ Successfully locked entity model for: {}", entityName);
                        return null;
                    } else {
                        String body = response.path("json").toString();
                        String errorMsg = String.format("Failed to lock entity model for %s. Status code: %d, body: %s",
                                entityName, statusCode, body);
                        logger.error("❌ {}", errorMsg);
                        throw new RuntimeException(errorMsg);
                    }
                });
    }
}
