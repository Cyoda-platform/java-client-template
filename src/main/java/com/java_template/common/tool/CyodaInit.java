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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.java_template.common.config.Config.*;

public class CyodaInit {
    private static final Logger logger = LoggerFactory.getLogger(CyodaInit.class);
    private static final Path WORKFLOW_DTO_DIR = Paths.get(System.getProperty("user.dir")).resolve("src/main/java/com/java_template/application/cyoda_dto");

    private final HttpUtils httpUtils;
    private final Authentication authentication;

    Set<String> pendingFiles = new HashSet<>();

    public CyodaInit(HttpUtils httpUtils, Authentication authentication) {
        this.httpUtils = httpUtils;
        this.authentication = authentication;
    }

    public CompletableFuture<Void> initCyoda() {
        logger.info("🔄 Starting workflow import into Cyoda...");
        String token = authentication.getAccessToken().getTokenValue();
        return initEntitiesSchema(WORKFLOW_DTO_DIR, token)
                .thenRun(() -> logger.info("✅ Workflow import process completed."))
                .exceptionally(ex -> {
                    logger.error("❌ Cyoda workflow import failed: {}", ex.getMessage(), ex);
                    return null;
                });
    }

    public CompletableFuture<Void> initEntitiesSchema(Path entityDir, String token) {
        if (!Files.exists(entityDir)) {
            logger.warn("📁 Directory '{}' does not exist. Skipping workflow import.", entityDir.toAbsolutePath());
            return CompletableFuture.completedFuture(null);
        }

        ExecutorService executor = Executors.newFixedThreadPool(3);

        CompletableFuture<JsonNode> workflowsFuture = httpUtils
                .sendGetRequest(token, CYODA_API_URL, "platform-api/statemachine/workflows")
                .thenApply(response -> {
                    int status = response.get("status").asInt();
                    if (status != 200) {
                        throw new RuntimeException("Failed to fetch workflows");
                    }
                    JsonNode json = response.get("json");
                    if (!json.isArray()) {
                        throw new IllegalStateException("Expected workflows as array");
                    }
                    return json;
                });

        try (Stream<Path> jsonFilesStream = Files.walk(entityDir)) {
            List<Path> jsonFiles = jsonFilesStream
                    .filter(path -> path.toString().toLowerCase().endsWith("workflow.json"))
                    .filter(path -> path.getParent() != null && path.getParent().getParent() != null)
                    .filter(path -> path.getParent().getParent().getFileName().toString().equals("cyoda_dto"))
                    .collect(Collectors.toList());

            if (jsonFiles.isEmpty()) {
                logger.warn("⚠️ No workflow JSON files found in directory: {}", entityDir);
                executor.shutdown();
                return CompletableFuture.completedFuture(null);
            }

            List<CompletableFuture<Void>> futures = jsonFiles.stream()
                    .map(jsonFile -> {
                        String entityName = jsonFile.getParent().getFileName().toString();
                        String fileName = WORKFLOW_DTO_DIR.relativize(jsonFile).toString();
                        pendingFiles.add(fileName);
                        return workflowsFuture.thenCompose(workflows ->
                                CompletableFuture.supplyAsync(() -> null, executor)
                                        .thenCompose(v -> processWorkflowFile(jsonFile, token, entityName, (ArrayNode) workflows)
                                                .whenComplete((res, ex) -> {
                                                    if (ex == null) {
                                                        pendingFiles.remove(fileName);
                                                    }
                                                }))
                        );
                    })
                    .collect(Collectors.toList());

            return CompletableFuture
                    .allOf(futures.toArray(new CompletableFuture[0]))
                    .whenComplete((res, ex) -> {
                        executor.shutdown();
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
            executor.shutdown();
            return CompletableFuture.failedFuture(e);
        }
    }

    private CompletableFuture<Void> processWorkflowFile(Path file, String token, String entityName, ArrayNode workflows) {
        try {
            String dtoContent = Files.readString(file);
            JsonNode dtoJson = new ObjectMapper().readTree(dtoContent);
            String dtoWorkflowName = dtoJson.path("workflow").get(0).path("operationName").asText();

            List<CompletableFuture<Void>> deactivationFutures = new ArrayList<>();

            for (JsonNode workflow : workflows) {
                String name = workflow.path("operationName").asText();
                boolean active = workflow.path("active").asBoolean();
                if (active && dtoWorkflowName.equals(name)) {
                    ((ObjectNode) workflow).put("active", false);
                    String workflowId = workflow.path("id").asText();
                    String putPath = "platform-api/statemachine/persisted/workflows/" + workflowId;
                    String updatedWorkflowJson = workflow.toString();

                    CompletableFuture<Void> deactivation = httpUtils.sendPutRequest(
                                    token, CYODA_API_URL, putPath, updatedWorkflowJson)
                            .thenAccept(putResponse -> {
                                int putStatus = putResponse.get("status").asInt();
                                if (putStatus != 200) {
                                    throw new RuntimeException("Failed to deactivate workflow with id " + workflowId);
                                }
                            });
                    deactivationFutures.add(deactivation);
                }
            }

            return CompletableFuture.allOf(deactivationFutures.toArray(new CompletableFuture[0]))
                    .thenCompose(ignored -> httpUtils.sendPostRequest(
                            token, CYODA_API_URL, "platform-api/statemachine/import?needRewrite=true",
                            dtoContent))
                    .thenApply(response -> {
                        int statusCode = response.get("status").asInt();
                        if (statusCode >= 200 && statusCode < 300) {
                            logger.info("Successfully imported workflow for entity: {}", entityName);
                            return null;
                        } else {
                            String body = response.path("json").toString();
                            throw new RuntimeException("Failed to import workflow for entity " + entityName +
                                    ". Status code: " + statusCode +
                                    ", body: " + body);
                        }
                    });
        } catch (IOException e) {
            logger.error("Error reading file {}: {}", file, e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }
}
