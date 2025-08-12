package com.java_template.prototype.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.prototype.workflow.model.WorkflowDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads and caches workflow definitions from JSON files.
 * Scans the src/main/resources/workflow directory structure.
 */
@Component
@Profile("prototype")
public class WorkflowDefinitionLoader {
    
    private static final Logger logger = LoggerFactory.getLogger(WorkflowDefinitionLoader.class);
    
    private final ObjectMapper objectMapper;
    private final Map<String, WorkflowDefinition> workflowCache = new ConcurrentHashMap<>();
    
    public WorkflowDefinitionLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    /**
     * Loads a workflow definition for the specified entity.
     * 
     * @param entityName The entity name (e.g., "Job", "Laureate", "Subscriber")
     * @return The workflow definition, or null if not found or invalid
     */
    public WorkflowDefinition loadWorkflowDefinition(String entityName) {
        return workflowCache.computeIfAbsent(entityName, this::loadWorkflowFromFile);
    }
    
    /**
     * Loads workflow definition from file system.
     * 
     * @param entityName The entity name
     * @return The workflow definition, or null if not found
     */
    private WorkflowDefinition loadWorkflowFromFile(String entityName) {
        String workflowPath = buildWorkflowPath(entityName);
        
        try {
            ClassPathResource resource = new ClassPathResource(workflowPath);
            if (!resource.exists()) {
                logger.warn("Workflow file not found: {}", workflowPath);
                return null;
            }
            
            try (InputStream inputStream = resource.getInputStream()) {
                WorkflowDefinition definition = objectMapper.readValue(inputStream, WorkflowDefinition.class);
                
                if (definition.isValid()) {
                    logger.info("Successfully loaded workflow definition for entity: {} from {}", entityName, workflowPath);
                    return definition;
                } else {
                    logger.error("Invalid workflow definition for entity: {} in file: {}", entityName, workflowPath);
                    return null;
                }
            }
            
        } catch (IOException e) {
            logger.error("Failed to load workflow definition for entity: {} from file: {}", entityName, workflowPath, e);
            return null;
        }
    }
    
    /**
     * Builds the path to the workflow JSON file for an entity.
     * Uses reflection to get the entity's version from its class.
     *
     * @param entityName The entity name
     * @return The path to the workflow file
     */
    private String buildWorkflowPath(String entityName) {
        String entityNameLower = entityName.toLowerCase();
        Integer entityVersion = getEntityVersion(entityName);
        return String.format("workflow/%s/version_%s/%s.json",
                entityNameLower,
                entityVersion,
                entityName);
    }

    /**
     * Gets the entity version for a given entity name using reflection.
     *
     * @param entityName The entity name
     * @return The entity version, or 1000 as default
     */
    private Integer getEntityVersion(String entityName) {
        try {
            // Try to load the entity class and get its ENTITY_VERSION constant
            String className = String.format("com.java_template.application.entity.%s.version_1000.%s",
                    entityName.toLowerCase(), entityName);
            Class<?> entityClass = Class.forName(className);
            return (Integer) entityClass.getField("ENTITY_VERSION").get(null);
        } catch (Exception e) {
            logger.debug("Could not get entity version for {}, using default 1000", entityName, e);
            return 1000; // Default fallback
        }
    }
    
    /**
     * Checks if a workflow definition exists for the given entity.
     * 
     * @param entityName The entity name
     * @return true if workflow exists and is valid, false otherwise
     */
    public boolean hasWorkflowDefinition(String entityName) {
        WorkflowDefinition definition = loadWorkflowDefinition(entityName);
        return definition != null && definition.isValid();
    }
    
    /**
     * Clears the workflow cache. Useful for testing or reloading configurations.
     */
    public void clearCache() {
        workflowCache.clear();
        logger.debug("Workflow definition cache cleared");
    }
    
    /**
     * Gets the number of cached workflow definitions.
     * 
     * @return The cache size
     */
    public int getCacheSize() {
        return workflowCache.size();
    }
}
