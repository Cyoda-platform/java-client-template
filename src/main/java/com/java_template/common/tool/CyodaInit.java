package com.java_template.common.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.java_template.common.auth.Authentication;
import com.java_template.common.config.Config;
import com.java_template.common.util.HttpUtils;
import com.java_template.common.workflow.CyodaEntity;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;


/**
 * ABOUTME: Initialization tool for setting up Cyoda platform configuration
 * including workflow definitions, entity models, and system bootstrapping.
 *<p>
 * This tool dynamically discovers entities and uses their getModelKey() method
 * to get the correct entity name and version instead of parsing file paths.
 */
@Component
public class CyodaInit {
    private static final Logger logger = LoggerFactory.getLogger(CyodaInit.class);
    private static final Path WORKFLOW_DTO_DIR = Paths.get(System.getProperty("user.dir")).resolve("src/main/resources/workflow");
    private static final Path ENTITY_DIR = Paths.get(System.getProperty("user.dir")).resolve("src/main/java/com/java_template/application/entity");
    public static final int THREAD_POOL_SIZE = 20;

    private final HttpUtils httpUtils;
    private final Authentication authentication;
    private final ObjectMapper objectMapper;
    private final Config config;

    public CyodaInit(HttpUtils httpUtils, Authentication authentication, ObjectMapper objectMapper, Config config) {
        this.httpUtils = httpUtils;
        this.authentication = authentication;
        this.objectMapper = objectMapper;
        this.config = config;
    }

    public void initCyoda(CyodaInitConfig config) {
        logger.info("🔄 Starting workflow import into Cyoda...");
        if (config.recreateModels()) {
            logger.info("⚠️  Recreate models flag is enabled - existing models will be deleted and recreated");
        }

        String token = authentication.getAccessToken().getTokenValue();
        initEntitiesSchemaFromEntities(token, config);
        logger.info("✅ Workflow import process completed.");
    }


    /**
     * Initialize entities schema from discovered entities using their getModelKey() method
     */
    private void initEntitiesSchemaFromEntities(String token, CyodaInitConfig config) {
        logger.info("🔍 Discovering entities dynamically...");

        List<ModelSpec> modelSpecs = discoverEntities();
        logger.info("🔍 Discovered {} entities: {}", modelSpecs.size(),
                modelSpecs.stream().map(spec -> spec.getName() + ":" + spec.getVersion()).toList());

        // Process workflows in parallel for better performance
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        try {
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (ModelSpec modelSpec : modelSpecs) {
                Path workflowFile = findWorkflowFile(WORKFLOW_DTO_DIR, modelSpec.getName(), modelSpec.getVersion());
                if (workflowFile != null) {
                    logger.info("✅ Found workflow file for {}: {}", modelSpec.getName(), workflowFile);

                    CompletableFuture<Void> future = CompletableFuture.runAsync(() ->
                                    importWorkflowForEntity(workflowFile, modelSpec.getName(), modelSpec.getVersion(), token, config),
                            executor
                    );
                    futures.add(future);
                } else {
                    logger.warn("⚠️ No workflow file found for entity: {} (version: {})", modelSpec.getName(), modelSpec.getVersion());
                }
            }

            // Wait for all imports to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } finally {
            executor.shutdown();
        }
    }


    /**
     * Discover entities from workflow JSON files
     */
    private List<ModelSpec> discoverEntities() {
        List<ModelSpec> modelSpecs = new ArrayList<>();
        Set<String> discoveredNames = new HashSet<>();

        if (!Files.exists(WORKFLOW_DTO_DIR)) {
            logger.warn("📁 Workflow directory '{}' does not exist", WORKFLOW_DTO_DIR);
            return modelSpecs;
        }

        try (Stream<Path> jsonFiles = Files.walk(WORKFLOW_DTO_DIR)) {
            List<Path> workflowFiles = jsonFiles
                    .filter(path -> path.toString().endsWith(".json"))
                    .toList();

            logger.debug("📁 Found {} JSON files in workflow directory", workflowFiles.size());

            for (Path jsonFile : workflowFiles) {
                ModelSpec modelSpec = extractModelSpecFromWorkflow(jsonFile);
                if (modelSpec != null) {
                    String key = modelSpec.getName() + ":" + modelSpec.getVersion();
                    if (!discoveredNames.contains(key)) {
                        modelSpecs.add(modelSpec);
                        discoveredNames.add(key);
                        logger.debug("✅ Discovered entity: {} (version: {})", modelSpec.getName(), modelSpec.getVersion());
                    }
                }
            }
        } catch (IOException e) {
            logger.error("❌ Error scanning workflow directory: {}", e.getMessage(), e);
        }

        return modelSpecs;
    }

    /**
     * Extract ModelSpec from workflow JSON file
     */
    private ModelSpec extractModelSpecFromWorkflow(Path jsonFile) {
        try {
            String jsonContent = Files.readString(jsonFile);
            JsonNode workflowNode = objectMapper.readTree(jsonContent);

            JsonNode nameNode = workflowNode.get("name");
            if (nameNode == null || nameNode.isNull()) {
                logger.debug("❌ No 'name' field in {}", jsonFile);
                return null;
            }

            String name = nameNode.asText();
            String version = workflowNode.get("version").asText("1.0");

            // Parse version to integer (e.g., "1.0" -> 1)
            int versionInt = Integer.parseInt(version.split("\\.")[0]);

            ModelSpec modelSpec = new ModelSpec();
            modelSpec.setName(name);
            modelSpec.setVersion(versionInt);
            logger.debug("✅ Extracted ModelSpec from {}: name={}, version={}", jsonFile.getFileName(), name, versionInt);
            return modelSpec;

        } catch (Exception e) {
            logger.debug("❌ Could not extract ModelSpec from {}: {}", jsonFile, e.getMessage());
            return null;
        }
    }

    /**
     * Find workflow file for the given entity name and version
     */
    @SuppressWarnings("SameParameterValue")
    private Path findWorkflowFile(Path workflowDir, String entityName, Integer version) {
        if (!Files.exists(workflowDir)) {
            return null;
        }

        try (Stream<Path> workflowFilesStream = Files.walk(workflowDir)) {
            return workflowFilesStream
                    .filter(path -> path.toString().toLowerCase().endsWith(".json"))
                    .filter(path -> {
                        String pathStr = path.toString().toLowerCase();
                        String fileName = path.getFileName().toString().toLowerCase();
                        String entityNameLower = entityName.toLowerCase();

                        // Remove .json extension from filename
                        String fileNameWithoutExtension = fileName.endsWith(".json")
                                ? fileName.substring(0, fileName.length() - 5)
                                : fileName;

                        // Match by entity name and version directory
                        return fileNameWithoutExtension.equals(entityNameLower) &&
                                (pathStr.contains("version_" + version) || pathStr.contains("v" + version));
                    })
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            logger.error("❌ Error searching for workflow file: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Import workflow for a specific entity.
     * Supports workflow files containing either a single workflow object or an array of workflows.
     */
    private void importWorkflowForEntity(Path workflowFile, String entityName, Integer version, String token, CyodaInitConfig initConfig) {
        logger.info("📄 Processing workflow file for entity: {}, version: {}", entityName, version);


        // Read and process workflow file
        JsonNode dtoJson;
        try {
            String dtoContent = Files.readString(workflowFile);
            dtoJson = objectMapper.readTree(dtoContent);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Wrap the workflow content in the required format: {"workflows": [...]}
        // If the file contains an array, use it directly; otherwise wrap single workflow in array
        ObjectNode wrappedContent = objectMapper.createObjectNode();
        ArrayNode workflowsArray;
        if (dtoJson.isArray()) {
            // File contains an array of workflows - use it directly
            workflowsArray = (ArrayNode) dtoJson;
            logger.debug("📄 Workflow file contains {} workflow(s)", workflowsArray.size());
        } else {
            // File contains a single workflow object - wrap it in an array
            workflowsArray = objectMapper.createArrayNode();
            workflowsArray.add(dtoJson);
        }
        wrappedContent.set("workflows", workflowsArray);

        // Other alternatives are "MERGE" and "ACTIVATE"
        // MERGE will just add these workflows which may not be what you want, because you might have several workflows active for the same model
        // ACTIVATE will activate the imported ones and deactivate the others for the same model
        // Since we want to initialize, we'll just REPLACE, meaning for the models imported, only this one workflow will exist.
        wrappedContent.set("importMode", new TextNode("REPLACE"));

        String wrappedContentJson = wrappedContent.toString();

        // Use the endpoint format: model/{entity_name}/{version}/workflow/import
        String importPath = String.format("model/%s/%s/workflow/import", entityName, version);
        logger.debug("🔗 Using import endpoint: {}", importPath);

        JsonNode response = httpUtils.sendPostRequest(token, config.getCyodaApiUrl(), importPath, wrappedContentJson).join();

        int statusCode = response.get("status").asInt();
        if (statusCode >= 200 && statusCode < 300) {
            logger.info("✅ Successfully imported workflow for entity: {} (version: {})", entityName, version);
        } else {
            String body = response.path("json").toString();
            String errorMsg = String.format("Failed to import workflow for entity %s (version %s). Status code: %d, body: %s",
                    entityName, version, statusCode, body);
            logger.error("❌ {}", errorMsg);
            throw new RuntimeException(errorMsg);
        }

        // Check and create entity model if needed
        checkAndCreateEntityModel(token, entityName, version, initConfig);

    }

    /**
     * Checks if entity model exists and creates it if needed
     */
    private void checkAndCreateEntityModel(String token, String entityName, Integer version, CyodaInitConfig config) {
        String exportPath = String.format("model/export/SIMPLE_VIEW/%s/%s", entityName, version);
        logger.debug("🔍 Checking if entity model exists: {}", exportPath);

        try {
            JsonNode response = httpUtils.sendGetRequest(token, this.config.getCyodaApiUrl(), exportPath).join();
            int statusCode = response.get("status").asInt();

            if (statusCode >= 200 && statusCode < 300) {
                if (config.recreateModels()) {
                    logger.info("🗑️  Entity model exists for: {} (version: {}), deleting due to --recreate-models flag", entityName, version);
                    deleteEntityModel(token, entityName, version);
                    logger.info("📝 Creating entity model for: {} (version: {})", entityName, version);
                    createEntityModel(token, entityName, version);
                } else {
                    logger.info("✅ Entity model already exists for: {} (version: {})", entityName, version);
                }
            } else if (statusCode == 404) {
                logger.info("📝 Entity model not found, creating for: {} (version: {})", entityName, version);
                createEntityModel(token, entityName, version);
            } else {
                String body = response.path("json").toString();
                String errorMsg = String.format("Failed to check entity model for %s (version %s). Status code: %d, body: %s",
                        entityName, version, statusCode, body);
                logger.error("❌ {}", errorMsg);
                throw new RuntimeException(errorMsg);
            }
        } catch (Exception ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("404")) {
                logger.info("📝 Entity model not found (404), creating for: {} (version: {})", entityName, version);
                createEntityModel(token, entityName, version);
            } else {
                throw new RuntimeException("Failed to check entity model for " + entityName, ex);
            }
        }
    }

    /**
     * Creates entity model using sample data, then sets change level to STRUCTURAL and locks the model
     */
    private void createEntityModel(String token, String entityName, Integer version) {
        String importPath = String.format("model/import/JSON/SAMPLE_DATA/%s/%s", entityName, version);
        logger.debug("🔗 Creating entity model at: {}", importPath);

        // Load example JSON files from classpath
        List<String> exampleJsonFiles = loadExampleJsonFiles(entityName);

        if (exampleJsonFiles.isEmpty()) {
            logger.debug("No example JSON files found for entity: {}, using empty object", entityName);
            sendEntityModelRequest(token, importPath, "{}", entityName, version);
        } else {
            logger.info("Found {} example JSON file(s) for entity: {}", exampleJsonFiles.size(), entityName);
            for (int i = 0; i < exampleJsonFiles.size(); i++) {
                String jsonContent = exampleJsonFiles.get(i);
                logger.debug("Sending example {} of {} for entity: {}", i + 1, exampleJsonFiles.size(), entityName);
                sendEntityModelRequest(token, importPath, jsonContent, entityName, version);
            }
        }

        setChangeLevel(token, entityName, version);
        lockModel(token, entityName, version);
    }

    /**
     * Load example JSON files from classpath for the given entity
     */
    private List<String> loadExampleJsonFiles(String entityName) {
        List<String> jsonContents = new ArrayList<>();
        String examplesPath = String.format("/entity-schemas/examples/%s", entityName);

        try {
            // Get the resource as a URL to check if directory exists
            var resource = getClass().getResource(examplesPath);
            if (resource == null) {
                logger.debug("No examples directory found at classpath: {}", examplesPath);
                return jsonContents;
            }

            // Read directory contents from classpath
            var uri = resource.toURI();
            Path examplesDir;

            if (uri.getScheme().equals("jar")) {
                // Running from JAR - need to use FileSystem
                try (var fs = java.nio.file.FileSystems.newFileSystem(uri, java.util.Collections.emptyMap())) {
                    examplesDir = fs.getPath(examplesPath);
                    jsonContents = readJsonFilesFromDirectory(examplesDir);
                }
            } else {
                // Running from IDE/filesystem
                examplesDir = Paths.get(uri);
                jsonContents = readJsonFilesFromDirectory(examplesDir);
            }

        } catch (Exception e) {
            logger.debug("Could not load example files for entity {}: {}", entityName, e.getMessage());
        }

        return jsonContents;
    }

    /**
     * Read all JSON files from a directory
     */
    private List<String> readJsonFilesFromDirectory(Path directory) throws IOException {
        List<String> jsonContents = new ArrayList<>();

        if (!Files.exists(directory) || !Files.isDirectory(directory)) {
            return jsonContents;
        }

        try (Stream<Path> files = Files.list(directory)) {
            List<Path> jsonFiles = files
                    .filter(path -> path.toString().endsWith(".json"))
                    .sorted()
                    .toList();

            for (Path jsonFile : jsonFiles) {
                String content = Files.readString(jsonFile);
                jsonContents.add(content);
                logger.debug("Loaded example file: {}", jsonFile.getFileName());
            }
        }

        return jsonContents;
    }

    /**
     * Send a single entity model creation request
     */
    private void sendEntityModelRequest(String token, String importPath, String requestBody, String entityName, Integer version) {
        JsonNode response = httpUtils.sendPostRequest(token, config.getCyodaApiUrl(), importPath, requestBody).join();
        int statusCode = response.get("status").asInt();

        if (statusCode >= 200 && statusCode < 300) {
            logger.debug("✅ Successfully sent entity model request for: {} (version: {})", entityName, version);
        } else {
            String body = response.path("json").toString();
            String errorMsg = String.format("Failed to create entity model for %s (version %s). Status code: %d, body: %s",
                    entityName, version, statusCode, body);
            logger.error("❌ {}", errorMsg);
            throw new RuntimeException(errorMsg);
        }
    }

    /**
     * Sets the change level to STRUCTURAL for the entity model
     */
    private void setChangeLevel(String token, String entityName, Integer version) {
        String changeLevel = "STRUCTURAL";
        String changeLevelPath = String.format("model/%s/%s/changeLevel/%s", entityName, version, changeLevel);
        logger.debug("🔗 Setting change level to {} for entity: {} (version: {})", changeLevel, entityName, version);

        JsonNode response = httpUtils.sendPostRequest(token, config.getCyodaApiUrl(), changeLevelPath, null).join();
        int statusCode = response.get("status").asInt();

        if (statusCode >= 200 && statusCode < 300) {
            logger.info("✅ Successfully set change level to {} for entity: {} (version: {})", changeLevel, entityName, version);
        } else {
            String body = response.path("json").toString();
            String errorMsg = String.format("Failed to set change level for %s (version %s). Status code: %d, body: %s",
                    entityName, version, statusCode, body);
            logger.error("❌ {}", errorMsg);
            throw new RuntimeException(errorMsg);
        }
    }

    /**
     * Locks the entity model
     */
    private void lockModel(String token, String entityName, Integer version) {
        String lockPath = String.format("model/%s/%s/lock", entityName, version);
        logger.debug("🔗 Locking entity model for: {} (version: {})", entityName, version);

        JsonNode response = httpUtils.sendPutRequest(token, config.getCyodaApiUrl(), lockPath, null).join();
        int statusCode = response.get("status").asInt();

        if (statusCode >= 200 && statusCode < 300) {
            logger.info("✅ Successfully locked entity model for: {} (version: {})", entityName, version);
        } else {
            String body = response.path("json").toString();
            String errorMsg = String.format("Failed to lock entity model for %s (version %s). Status code: %d, body: %s",
                    entityName, version, statusCode, body);
            logger.error("❌ {}", errorMsg);
            throw new RuntimeException(errorMsg);
        }
    }

    /**
     * Deletes the entity model if it exists
     */
    private void deleteEntityModel(String token, String entityName, Integer version) {
        // First check if the model exists
        String exportPath = String.format("model/export/SIMPLE_VIEW/%s/%s", entityName, version);
        logger.debug("🔍 Checking if entity model exists before deletion: {}", exportPath);

        try {
            JsonNode checkResponse = httpUtils.sendGetRequest(token, config.getCyodaApiUrl(), exportPath).join();
            int checkStatusCode = checkResponse.get("status").asInt();

            if (checkStatusCode == 404) {
                logger.info("ℹ️  Entity model does not exist for: {} (version: {}), skipping deletion", entityName, version);
                return;
            } else if (checkStatusCode < 200 || checkStatusCode >= 300) {
                String body = checkResponse.path("json").toString();
                logger.warn("⚠️  Could not verify entity model existence for {} (version {}). Status code: {}, body: {}",
                        entityName, version, checkStatusCode, body);
                // Continue with deletion attempt anyway
            }
        } catch (Exception ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("404")) {
                logger.info("ℹ️  Entity model does not exist for: {} (version: {}), skipping deletion", entityName, version);
                return;
            }
            logger.warn("⚠️  Could not verify entity model existence for {} (version {}): {}",
                    entityName, version, ex.getMessage());
            // Continue with deletion attempt anyway
        }

        // Model exists, proceed with deletion
        String deletePath = String.format("model/%s/%s", entityName, version);
        logger.debug("🔗 Deleting entity model for: {} (version: {})", entityName, version);

        JsonNode response = httpUtils.sendDeleteRequest(token, config.getCyodaApiUrl(), deletePath).join();
        int statusCode = response.get("status").asInt();

        if (statusCode >= 200 && statusCode < 300) {
            logger.info("✅ Successfully deleted entity model for: {} (version: {})", entityName, version);
        } else if (statusCode == 404) {
            logger.info("ℹ️  Entity model was already deleted for: {} (version: {})", entityName, version);
        } else {
            String body = response.path("json").toString();
            String errorMsg = String.format("Failed to delete entity model for %s (version %s). Status code: %d, body: %s",
                    entityName, version, statusCode, body);
            logger.error("❌ {}", errorMsg);
            throw new RuntimeException(errorMsg);
        }
    }
}
