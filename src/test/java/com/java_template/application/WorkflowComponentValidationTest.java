package com.java_template.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test that validates all processors and criteria referenced in workflow JSON files
 * have corresponding Java implementation classes.
 * 
 * This test can be run independently using:
 * ./gradlew test --tests WorkflowComponentValidationTest
 * 
 * Or with system property for CI:
 * ./gradlew test --tests WorkflowComponentValidationTest -Dworkflow.validation.enabled=true
 */
@EnabledIfSystemProperty(named = "workflow.validation.enabled", matches = "true", disabledReason = "Workflow validation test disabled by default")
public class WorkflowComponentValidationTest {

    private static final String WORKFLOW_DIR = "src/main/java/com/java_template/application/workflow";
    private static final String PROCESSOR_DIR = "src/main/java/com/java_template/application/processor";
    private static final String CRITERION_DIR = "src/main/java/com/java_template/application/criterion";
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testAllWorkflowComponentsExist() throws IOException {
        // Get all workflow JSON files
        List<File> workflowFiles = getWorkflowFiles();
        assertFalse(workflowFiles.isEmpty(), "No workflow files found in " + WORKFLOW_DIR);

        Set<String> allProcessors = new HashSet<>();
        Set<String> allCriteria = new HashSet<>();
        Map<String, Set<String>> workflowProcessors = new HashMap<>();
        Map<String, Set<String>> workflowCriteria = new HashMap<>();

        // Parse each workflow file and extract component names
        for (File workflowFile : workflowFiles) {
            String workflowName = workflowFile.getName();
            System.out.println("Processing workflow: " + workflowName);
            
            JsonNode workflow = objectMapper.readTree(workflowFile);
            
            Set<String> processors = extractProcessors(workflow);
            Set<String> criteria = extractCriteria(workflow);
            
            allProcessors.addAll(processors);
            allCriteria.addAll(criteria);
            workflowProcessors.put(workflowName, processors);
            workflowCriteria.put(workflowName, criteria);
            
            System.out.println("  Processors: " + processors);
            System.out.println("  Criteria: " + criteria);
        }

        // Validate processors exist
        validateProcessors(allProcessors, workflowProcessors);
        
        // Validate criteria exist
        validateCriteria(allCriteria, workflowCriteria);
        
        System.out.println("\n✅ All workflow components validation passed!");
        System.out.println("Total processors validated: " + allProcessors.size());
        System.out.println("Total criteria validated: " + allCriteria.size());
    }

    private List<File> getWorkflowFiles() throws IOException {
        Path workflowPath = Paths.get(WORKFLOW_DIR);
        if (!Files.exists(workflowPath)) {
            throw new IllegalStateException("Workflow directory does not exist: " + WORKFLOW_DIR);
        }

        try (Stream<Path> paths = Files.walk(workflowPath)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .map(Path::toFile)
                    .sorted()
                    .toList();
        }
    }

    private Set<String> extractProcessors(JsonNode workflow) {
        Set<String> processors = new HashSet<>();
        JsonNode states = workflow.get("states");
        
        if (states != null) {
            states.fields().forEachRemaining(stateEntry -> {
                JsonNode state = stateEntry.getValue();
                JsonNode transitions = state.get("transitions");
                
                if (transitions != null && transitions.isArray()) {
                    for (JsonNode transition : transitions) {
                        JsonNode processorsList = transition.get("processors");
                        if (processorsList != null && processorsList.isArray()) {
                            for (JsonNode processor : processorsList) {
                                JsonNode name = processor.get("name");
                                if (name != null && name.isTextual()) {
                                    processors.add(name.asText());
                                }
                            }
                        }
                    }
                }
            });
        }
        
        return processors;
    }

    private Set<String> extractCriteria(JsonNode workflow) {
        Set<String> criteria = new HashSet<>();
        JsonNode states = workflow.get("states");
        
        if (states != null) {
            states.fields().forEachRemaining(stateEntry -> {
                JsonNode state = stateEntry.getValue();
                JsonNode transitions = state.get("transitions");
                
                if (transitions != null && transitions.isArray()) {
                    for (JsonNode transition : transitions) {
                        JsonNode criterion = transition.get("criterion");
                        if (criterion != null) {
                            JsonNode function = criterion.get("function");
                            if (function != null) {
                                JsonNode name = function.get("name");
                                if (name != null && name.isTextual()) {
                                    criteria.add(name.asText());
                                }
                            }
                        }
                    }
                }
            });
        }
        
        return criteria;
    }

    private void validateProcessors(Set<String> allProcessors, Map<String, Set<String>> workflowProcessors) {
        List<String> missingProcessors = new ArrayList<>();
        
        for (String processorName : allProcessors) {
            String expectedFile = PROCESSOR_DIR + "/" + processorName + ".java";
            if (!Files.exists(Paths.get(expectedFile))) {
                missingProcessors.add(processorName);
                
                // Find which workflows reference this missing processor
                List<String> referencingWorkflows = workflowProcessors.entrySet().stream()
                        .filter(entry -> entry.getValue().contains(processorName))
                        .map(Map.Entry::getKey)
                        .sorted()
                        .toList();
                
                System.err.println("❌ Missing processor: " + processorName);
                System.err.println("   Expected file: " + expectedFile);
                System.err.println("   Referenced in workflows: " + referencingWorkflows);
            } else {
                System.out.println("✅ Processor found: " + processorName);
            }
        }
        
        if (!missingProcessors.isEmpty()) {
            fail("Missing processor implementations: " + missingProcessors + 
                 ". Expected files in " + PROCESSOR_DIR);
        }
    }

    private void validateCriteria(Set<String> allCriteria, Map<String, Set<String>> workflowCriteria) {
        List<String> missingCriteria = new ArrayList<>();
        
        for (String criterionName : allCriteria) {
            String expectedFile = CRITERION_DIR + "/" + criterionName + ".java";
            if (!Files.exists(Paths.get(expectedFile))) {
                missingCriteria.add(criterionName);
                
                // Find which workflows reference this missing criterion
                List<String> referencingWorkflows = workflowCriteria.entrySet().stream()
                        .filter(entry -> entry.getValue().contains(criterionName))
                        .map(Map.Entry::getKey)
                        .sorted()
                        .toList();
                
                System.err.println("❌ Missing criterion: " + criterionName);
                System.err.println("   Expected file: " + expectedFile);
                System.err.println("   Referenced in workflows: " + referencingWorkflows);
            } else {
                System.out.println("✅ Criterion found: " + criterionName);
            }
        }
        
        if (!missingCriteria.isEmpty()) {
            fail("Missing criterion implementations: " + missingCriteria + 
                 ". Expected files in " + CRITERION_DIR);
        }
    }
}
