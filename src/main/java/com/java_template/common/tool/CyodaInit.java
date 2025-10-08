package com.java_template.common.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.java_template.common.auth.Authentication;
import com.java_template.common.util.HttpUtils;
import com.java_template.common.workflow.CyodaEntity;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.java_template.common.config.Config.*;


/**
 * ABOUTME: Initialization tool for setting up Cyoda platform configuration
 * including workflow definitions, entity models, and system bootstrapping.
 *
 * This tool dynamically discovers entities and uses their getModelKey() method
 * to get the correct entity name and version instead of parsing file paths.
 */
@Component
public class CyodaInit {
    private static final Logger logger = LoggerFactory.getLogger(CyodaInit.class);
    private static final Path WORKFLOW_DTO_DIR = Paths.get(System.getProperty("user.dir")).resolve("src/main/resources/workflow");
    private static final Path ENTITY_DIR = Paths.get(System.getProperty("user.dir")).resolve("src/main/java/com/java_template/application/entity");

    private final HttpUtils httpUtils;
    private final Authentication authentication;
    private final ObjectMapper objectMapper;

    public CyodaInit(HttpUtils httpUtils, Authentication authentication, ObjectMapper objectMapper) {
        this.httpUtils = httpUtils;
        this.authentication = authentication;
        this.objectMapper = objectMapper;
    }

    public void initCyoda() {
        logger.info("🔄 Starting workflow import into Cyoda...");

        try {
            String token = authentication.getAccessToken().getTokenValue();
            initEntitiesSchemaFromEntities(token);
            logger.info("✅ Workflow import process completed.");
        } catch (Exception ex) {
            logger.error("❌ Failed to initialize Cyoda workflows");
            handleImportError(ex);
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

    /**
     * Initialize entities schema from discovered entities using their getModelKey() method
     */
    private void initEntitiesSchemaFromEntities(String token) {
        logger.info("🔍 Discovering entities dynamically...");

        List<ModelSpec> modelSpecs = discoverEntities();
        logger.info("🔍 Discovered {} entities: {}", modelSpecs.size(),
            modelSpecs.stream().map(spec -> spec.getName() + ":" + spec.getVersion()).toList());

        for (ModelSpec modelSpec : modelSpecs) {
            Path workflowFile = findWorkflowFile(WORKFLOW_DTO_DIR, modelSpec.getName(), modelSpec.getVersion());
            if (workflowFile != null) {
                logger.info("✅ Found workflow file for {}: {}", modelSpec.getName(), workflowFile);
                importWorkflowForEntity(workflowFile, modelSpec.getName(), modelSpec.getVersion(), token);
            } else {
                logger.warn("⚠️ No workflow file found for entity: {} (version: {})", modelSpec.getName(), modelSpec.getVersion());
            }
        }
    }


    /**
     * Discover entities from the entity directory and return their ModelSpec information
     */
    private List<ModelSpec> discoverEntities() {
        List<ModelSpec> modelSpecs = new ArrayList<>();

        if (!Files.exists(ENTITY_DIR)) {
            logger.warn("📁 Entity directory '{}' does not exist", ENTITY_DIR);
            return modelSpecs;
        }

        try (Stream<Path> javaFiles = Files.walk(ENTITY_DIR)) {
            List<Path> entityFiles = javaFiles
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> !path.getFileName().toString().startsWith("Test"))
                .toList();

            for (Path javaFile : entityFiles) {
                ModelSpec modelSpec = extractEntityModelSpec(javaFile);
                if (modelSpec != null) {
                    modelSpecs.add(modelSpec);
                    logger.debug("✅ Discovered entity: {} (version: {})", modelSpec.getName(), modelSpec.getVersion());
                }
            }
        } catch (IOException e) {
            logger.error("❌ Error scanning entity directory: {}", e.getMessage(), e);
        }

        return modelSpecs;
    }

    /**
     * Extract ModelSpec from entity class by loading it and calling getModelKey()
     */
    private ModelSpec extractEntityModelSpec(Path javaFile) {
        try {
            // Convert file path to class name
            String relativePath = ENTITY_DIR.relativize(javaFile).toString();
            String className = relativePath.replace(File.separator, ".")
                .replace(".java", "");
            String fullClassName = "com.java_template.application.entity." + className;

            // Load the class
            Class<?> clazz = Class.forName(fullClassName);

            // Check if it implements CyodaEntity
            if (!CyodaEntity.class.isAssignableFrom(clazz)) {
                return null; // Skip non-entity classes
            }

            // Create instance and get model information
            CyodaEntity entity = (CyodaEntity) clazz.getDeclaredConstructor().newInstance();
            return entity.getModelKey().modelKey();

        } catch (Exception e) {
            logger.debug("Could not load entity class from {}: {}", javaFile, e.getMessage());
            return null;
        }
    }

    /**
     * Find workflow file for the given entity name and version
     */
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
     * Import workflow for a specific entity
     */
    private void importWorkflowForEntity(Path workflowFile, String entityName, Integer version, String token) {
        try {
            logger.info("📄 Processing workflow file for entity: {}, version: {}", entityName, version);

            // First check and create entity model if needed
            checkAndCreateEntityModel(token, entityName, version);

            // Read and process workflow file
            String dtoContent = Files.readString(workflowFile);
            JsonNode dtoJson = objectMapper.readTree(dtoContent);

            // Wrap the workflow content in the required format: {"workflows": [file_content]}
            ObjectNode wrappedContent = objectMapper.createObjectNode();
            ArrayNode workflowsArray = objectMapper.createArrayNode();
            workflowsArray.add(dtoJson);
            wrappedContent.set("workflows", workflowsArray);

            // Other alternatives are "MERGE" and "ACTIVATE"
            // MERGE will just add these workflows which may not be what you want, because you might have several workflows active for the same model
            // ACTIVATE will activate the imported ones and deactivate the others for the same model
            // Since we want to initialize, we'll just REPLACE, meaning for the models imported, only this one workflow will exist.
            wrappedContent.set("importMode", new TextNode("REPLACE") );

            String wrappedContentJson = wrappedContent.toString();

            // Use the endpoint format: model/{entity_name}/{version}/workflow/import
            String importPath = String.format("model/%s/%s/workflow/import", entityName, version);
            logger.debug("🔗 Using import endpoint: {}", importPath);

            JsonNode response = httpUtils.sendPostRequest(token, CYODA_API_URL, importPath, wrappedContentJson).join();

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
        } catch (Exception e) {
            logger.error("❌ Error importing workflow for entity {}: {}", entityName, e.getMessage());
            throw new RuntimeException("Failed to import workflow for entity " + entityName, e);
        }
    }

    /**
     * Checks if entity model exists and creates it if needed
     */
    private void checkAndCreateEntityModel(String token, String entityName, Integer version) {
        String exportPath = String.format("model/export/SIMPLE_VIEW/%s/%s", entityName, version);
        logger.debug("🔍 Checking if entity model exists: {}", exportPath);

        try {
            JsonNode response = httpUtils.sendGetRequest(token, CYODA_API_URL, exportPath).join();
            int statusCode = response.get("status").asInt();

            if (statusCode >= 200 && statusCode < 300) {
                logger.info("✅ Entity model already exists for: {} (version: {})", entityName, version);
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

        JsonNode response = httpUtils.sendPostRequest(token, CYODA_API_URL, importPath, "{}").join();
        int statusCode = response.get("status").asInt();

        if (statusCode >= 200 && statusCode < 300) {
            logger.info("✅ Successfully created entity model for: {} (version: {})", entityName, version);
        } else {
            String body = response.path("json").toString();
            String errorMsg = String.format("Failed to create entity model for %s (version %s). Status code: %d, body: %s",
                    entityName, version, statusCode, body);
            logger.error("❌ {}", errorMsg);
            throw new RuntimeException(errorMsg);
        }

        setChangeLevel(token, entityName, version);
        lockModel(token, entityName, version);
    }

    /**
     * Sets the change level to STRUCTURAL for the entity model
     */
    private void setChangeLevel(String token, String entityName, Integer version) {
        String changeLevel = "STRUCTURAL";
        String changeLevelPath = String.format("model/%s/%s/changeLevel/%s", entityName, version, changeLevel);
        logger.debug("🔗 Setting change level to {} for entity: {} (version: {})", changeLevel, entityName, version);

        JsonNode response = httpUtils.sendPostRequest(token, CYODA_API_URL, changeLevelPath, null).join();
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

        JsonNode response = httpUtils.sendPutRequest(token, CYODA_API_URL, lockPath, null).join();
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
}
