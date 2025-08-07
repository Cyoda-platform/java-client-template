package com.java_template.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Standalone test that validates all processors and criteria referenced in workflow JSON files
 * have corresponding Java implementation classes.
 * 
 * This test runs without any system properties and can be executed using:
 * ./gradlew test --tests WorkflowComponentValidationStandaloneTest
 * 
 * This is the version that should be used in GitHub workflows for validation.
 */
public class WorkflowComponentValidationStandaloneTest {

    private static final String WORKFLOW_DIR = "src/main/java/com/java_template/application/workflow";
    private static final String PROCESSOR_DIR = "src/main/java/com/java_template/application/processor";
    private static final String CRITERION_DIR = "src/main/java/com/java_template/application/criterion";
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testAllWorkflowComponentsExist() throws IOException {
        System.out.println("🔍 Starting workflow component validation...");
        System.out.println("Workflow directory: " + WORKFLOW_DIR);
        System.out.println("Processor directory: " + PROCESSOR_DIR);
        System.out.println("Criterion directory: " + CRITERION_DIR);
        System.out.println();

        // Get all workflow JSON files
        List<File> workflowFiles = getWorkflowFiles();
        assertFalse(workflowFiles.isEmpty(), "No workflow files found in " + WORKFLOW_DIR);
        System.out.println("Found " + workflowFiles.size() + " workflow files");

        Set<String> allProcessors = new HashSet<>();
        Set<String> allCriteria = new HashSet<>();
        Map<String, Set<String>> workflowProcessors = new HashMap<>();
        Map<String, Set<String>> workflowCriteria = new HashMap<>();

        // Parse each workflow file and extract component names
        for (File workflowFile : workflowFiles) {
            String workflowName = workflowFile.getName();
            System.out.println("\n📄 Processing workflow: " + workflowName);
            
            JsonNode workflow = objectMapper.readTree(workflowFile);
            
            Set<String> processors = extractProcessors(workflow);
            Set<String> criteria = extractCriteria(workflow);
            
            allProcessors.addAll(processors);
            allCriteria.addAll(criteria);
            workflowProcessors.put(workflowName, processors);
            workflowCriteria.put(workflowName, criteria);
            
            if (!processors.isEmpty()) {
                System.out.println("  📦 Processors: " + processors);
            }
            if (!criteria.isEmpty()) {
                System.out.println("  🎯 Criteria: " + criteria);
            }
            if (processors.isEmpty() && criteria.isEmpty()) {
                System.out.println("  ℹ️  No processors or criteria found");
            }
        }

        System.out.println("\n🔍 Validation Summary:");
        System.out.println("Total unique processors to validate: " + allProcessors.size());
        System.out.println("Total unique criteria to validate: " + allCriteria.size());
        System.out.println();

        // Validate processors exist
        boolean processorsValid = validateProcessors(allProcessors, workflowProcessors);
        
        // Validate criteria exist
        boolean criteriaValid = validateCriteria(allCriteria, workflowCriteria);
        
        if (processorsValid && criteriaValid) {
            System.out.println("\n✅ All workflow components validation passed!");
            System.out.println("✅ All " + allProcessors.size() + " processors found");
            System.out.println("✅ All " + allCriteria.size() + " criteria found");
        } else {
            System.out.println("\n❌ Workflow component validation failed!");
            fail("Some workflow components are missing their corresponding Java implementation files");
        }
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

    private boolean validateProcessors(Set<String> allProcessors, Map<String, Set<String>> workflowProcessors) {
        if (allProcessors.isEmpty()) {
            System.out.println("ℹ️  No processors to validate");
            return true;
        }

        System.out.println("🔍 Validating processors...");
        List<String> missingProcessors = new ArrayList<>();
        
        for (String processorName : allProcessors.stream().sorted().toList()) {
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
        
        return missingProcessors.isEmpty();
    }

    private boolean validateCriteria(Set<String> allCriteria, Map<String, Set<String>> workflowCriteria) {
        if (allCriteria.isEmpty()) {
            System.out.println("ℹ️  No criteria to validate");
            return true;
        }

        System.out.println("\n🔍 Validating criteria...");
        List<String> missingCriteria = new ArrayList<>();
        
        for (String criterionName : allCriteria.stream().sorted().toList()) {
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
        
        return missingCriteria.isEmpty();
    }
}
