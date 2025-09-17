package com.java_template.common.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * ABOUTME: Validation tool that ensures all processors/criteria mentioned in functional requirements
 * are implemented in the corresponding workflow JSON files.
 * 
 * This tool:
 * 1. Scans all functional requirement markdown files in src/main/resources/functional_requirements/*_workflow.md
 * 2. Extracts processor and criteria names from the markdown documentation using regex patterns
 * 3. Checks if they exist in the corresponding workflow JSON files
 * 4. Reports missing workflow definitions with detailed output
 */
public class FunctionalRequirementsValidator {
    private static final Logger logger = LoggerFactory.getLogger(FunctionalRequirementsValidator.class);
    
    private static final Path REQUIREMENTS_DIR = Paths.get(System.getProperty("user.dir"))
            .resolve("src/main/resources/functional_requirements");
    private static final Path WORKFLOW_DIR = Paths.get(System.getProperty("user.dir"))
            .resolve("src/main/resources/workflow");
    
    // Regex patterns for extracting processors and criteria from markdown
    private static final List<Pattern> PROCESSOR_PATTERNS = Arrays.asList(
            Pattern.compile("(\\w+Processor)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("processor[:\\s]+`?(\\w+)`?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("`(\\w+Processor)`", Pattern.CASE_INSENSITIVE),
            Pattern.compile("- (\\w+Processor)", Pattern.CASE_INSENSITIVE)
    );
    
    private static final List<Pattern> CRITERIA_PATTERNS = Arrays.asList(
            Pattern.compile("(\\w+Criterion)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("criteria[:\\s]+`?(\\w+)`?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("criterion[:\\s]+`?(\\w+)`?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("`(\\w+Criterion)`", Pattern.CASE_INSENSITIVE),
            Pattern.compile("- (\\w+Criterion)", Pattern.CASE_INSENSITIVE)
    );
    
    private final ObjectMapper objectMapper;
    
    public FunctionalRequirementsValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    public static void main(String[] args) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
        context.refresh();

        ObjectMapper objectMapper = context.getBean(ObjectMapper.class);
        FunctionalRequirementsValidator validator = new FunctionalRequirementsValidator(objectMapper);

        boolean isValid;
        if (args.length >= 2) {
            // Validate specific files: args[0] = requirements file, args[1] = workflow file
            isValid = validator.validateSpecificFiles(Paths.get(args[0]), Paths.get(args[1]));
        } else {
            // Validate all discovered files
            isValid = validator.validateFunctionalRequirements();
        }

        context.close();
        System.exit(isValid ? 0 : 1);
    }
    
    /**
     * Main validation method that orchestrates the entire validation process
     */
    public boolean validateFunctionalRequirements() {
        logger.info("🔍 Validating functional requirements against workflows...");
        logger.info("======================================================================");
        
        List<Path> requirementFiles = findFunctionalRequirementFiles();
        if (requirementFiles.isEmpty()) {
            logger.error("❌ No functional requirement files found!");
            return false;
        }
        
        boolean allValid = true;
        int totalEntitiesChecked = 0;
        int totalProcessorsChecked = 0;
        int totalCriteriaChecked = 0;
        
        for (Path reqFile : requirementFiles) {
            RequirementResult reqResult = extractProcessorsAndCriteriaFromMarkdown(reqFile);
            if (reqResult == null) {
                allValid = false;
                continue;
            }
            
            logger.info("📋 Checking entity: {}", reqResult.entityName);
            logger.info("   Requirements file: {}", reqFile);
            
            // Find corresponding workflow file
            Path workflowFile = getWorkflowFileForEntity(reqResult.entityName);
            if (workflowFile == null) {
                logger.error("   ❌ No workflow file found for entity: {}", reqResult.entityName);
                allValid = false;
                logger.info("");
                continue;
            }
            
            logger.info("   Workflow file: {}", workflowFile);
            
            // Extract from workflow
            WorkflowResult workflowResult = extractProcessorsAndCriteriaFromWorkflow(workflowFile);
            if (workflowResult == null) {
                allValid = false;
                continue;
            }
            
            // Validate processors
            Set<String> missingProcessors = new HashSet<>(reqResult.processors);
            missingProcessors.removeAll(workflowResult.processors);
            
            if (!missingProcessors.isEmpty()) {
                logger.error("   ❌ Processors in requirements but missing from workflow:");
                missingProcessors.forEach(processor -> logger.error("      - {}", processor));
                allValid = false;
            } else {
                logger.info("   ✅ All {} processors from requirements found in workflow", reqResult.processors.size());
            }
            
            // Validate criteria
            Set<String> missingCriteria = new HashSet<>(reqResult.criteria);
            missingCriteria.removeAll(workflowResult.criteria);
            
            if (!missingCriteria.isEmpty()) {
                logger.error("   ❌ Criteria in requirements but missing from workflow:");
                missingCriteria.forEach(criterion -> logger.error("      - {}", criterion));
                allValid = false;
            } else {
                logger.info("   ✅ All {} criteria from requirements found in workflow", reqResult.criteria.size());
            }
            
            // Show what was found
            if (!reqResult.processors.isEmpty() || !reqResult.criteria.isEmpty()) {
                logger.info("   📊 Required processors: {}", reqResult.processors.isEmpty() ? "None" : String.join(", ", reqResult.processors));
                logger.info("   📊 Required criteria: {}", reqResult.criteria.isEmpty() ? "None" : String.join(", ", reqResult.criteria));
                logger.info("   📊 Workflow processors: {}", workflowResult.processors.isEmpty() ? "None" : String.join(", ", workflowResult.processors));
                logger.info("   📊 Workflow criteria: {}", workflowResult.criteria.isEmpty() ? "None" : String.join(", ", workflowResult.criteria));
            }
            
            totalEntitiesChecked++;
            totalProcessorsChecked += reqResult.processors.size();
            totalCriteriaChecked += reqResult.criteria.size();
            logger.info("");
        }
        
        // Summary
        logger.info("======================================================================");
        logger.info("📊 VALIDATION SUMMARY");
        logger.info("======================================================================");
        logger.info("Entities checked: {}", totalEntitiesChecked);
        logger.info("Total processors in requirements: {}", totalProcessorsChecked);
        logger.info("Total criteria in requirements: {}", totalCriteriaChecked);
        
        if (allValid) {
            logger.info("✅ ALL FUNCTIONAL REQUIREMENTS VALIDATED SUCCESSFULLY!");
            logger.info("   All processors/criteria from requirements are defined in workflows.");
        } else {
            logger.error("❌ VALIDATION FAILED - Missing workflow definitions found!");
            logger.info("");
            logger.info("💡 To fix missing workflow definitions:");
            logger.info("   1. Add missing processors/criteria to the workflow JSON files");
            logger.info("   2. Ensure processor/criteria names match exactly between requirements and workflows");
            logger.info("   3. Verify workflow state transitions include all required components");
        }
        
        return allValid;
    }

    /**
     * Validate specific requirement and workflow files
     */
    public boolean validateSpecificFiles(Path requirementFile, Path workflowFile) {
        logger.info("🔍 Validating specific files...");
        logger.info("======================================================================");
        logger.info("Requirements file: {}", requirementFile);
        logger.info("Workflow file: {}", workflowFile);

        if (!Files.exists(requirementFile)) {
            logger.error("❌ Requirements file not found: {}", requirementFile);
            return false;
        }

        if (!Files.exists(workflowFile)) {
            logger.error("❌ Workflow file not found: {}", workflowFile);
            return false;
        }

        // Extract entity name from workflow file name
        String entityName = workflowFile.getFileName().toString().replace(".json", "");

        RequirementResult reqResult = extractProcessorsAndCriteriaFromMarkdown(requirementFile, entityName);
        if (reqResult == null) {
            return false;
        }

        WorkflowResult workflowResult = extractProcessorsAndCriteriaFromWorkflow(workflowFile);
        if (workflowResult == null) {
            return false;
        }

        logger.info("📋 Validating entity: {}", entityName);

        // Validate processors
        Set<String> missingProcessors = new HashSet<>(reqResult.processors);
        missingProcessors.removeAll(workflowResult.processors);

        boolean allValid = true;
        if (!missingProcessors.isEmpty()) {
            logger.error("❌ Processors in requirements but missing from workflow:");
            missingProcessors.forEach(processor -> logger.error("      - {}", processor));
            allValid = false;
        } else {
            logger.info("✅ All {} processors from requirements found in workflow", reqResult.processors.size());
        }

        // Validate criteria
        Set<String> missingCriteria = new HashSet<>(reqResult.criteria);
        missingCriteria.removeAll(workflowResult.criteria);

        if (!missingCriteria.isEmpty()) {
            logger.error("❌ Criteria in requirements but missing from workflow:");
            missingCriteria.forEach(criterion -> logger.error("      - {}", criterion));
            allValid = false;
        } else {
            logger.info("✅ All {} criteria from requirements found in workflow", reqResult.criteria.size());
        }

        // Show what was found
        logger.info("📊 Required processors: {}", reqResult.processors.isEmpty() ? "None" : String.join(", ", reqResult.processors));
        logger.info("📊 Required criteria: {}", reqResult.criteria.isEmpty() ? "None" : String.join(", ", reqResult.criteria));
        logger.info("📊 Workflow processors: {}", workflowResult.processors.isEmpty() ? "None" : String.join(", ", workflowResult.processors));
        logger.info("📊 Workflow criteria: {}", workflowResult.criteria.isEmpty() ? "None" : String.join(", ", workflowResult.criteria));

        logger.info("======================================================================");
        if (allValid) {
            logger.info("✅ VALIDATION PASSED for {}", entityName);
        } else {
            logger.error("❌ VALIDATION FAILED for {}", entityName);
        }

        return allValid;
    }
    
    /**
     * Find all functional requirement markdown files
     */
    private List<Path> findFunctionalRequirementFiles() {
        List<Path> requirementFiles = new ArrayList<>();
        
        if (!Files.exists(REQUIREMENTS_DIR)) {
            logger.error("❌ Functional requirements directory not found: {}", REQUIREMENTS_DIR);
            return requirementFiles;
        }
        
        try (Stream<Path> entityDirs = Files.list(REQUIREMENTS_DIR)) {
            entityDirs.filter(Files::isDirectory)
                    .forEach(entityDir -> {
                        try (Stream<Path> files = Files.list(entityDir)) {
                            files.filter(file -> file.toString().endsWith("_workflow.md"))
                                    .forEach(requirementFiles::add);
                        } catch (IOException e) {
                            logger.warn("Error reading entity directory {}: {}", entityDir, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            logger.error("Error reading requirements directory: {}", e.getMessage());
        }
        
        return requirementFiles;
    }
    
    /**
     * Extract processor and criteria names from markdown file
     */
    private RequirementResult extractProcessorsAndCriteriaFromMarkdown(Path mdFile) {
        // Extract entity name from directory and convert to proper case
        String entityDirName = mdFile.getParent().getFileName().toString();
        String entityName = convertToEntityName(entityDirName);
        return extractProcessorsAndCriteriaFromMarkdown(mdFile, entityName);
    }

    /**
     * Extract processor and criteria names from markdown file with explicit entity name
     */
    private RequirementResult extractProcessorsAndCriteriaFromMarkdown(Path mdFile, String entityName) {
        try {
            String content = Files.readString(mdFile);
            Set<String> processors = new HashSet<>();
            Set<String> criteria = new HashSet<>();
            
            // Extract processors
            for (Pattern pattern : PROCESSOR_PATTERNS) {
                Matcher matcher = pattern.matcher(content);
                while (matcher.find()) {
                    String match = matcher.group(1);
                    if (match != null && match.endsWith("Processor")) {
                        processors.add(match);
                    }
                }
            }
            
            // Extract criteria
            for (Pattern pattern : CRITERIA_PATTERNS) {
                Matcher matcher = pattern.matcher(content);
                while (matcher.find()) {
                    String match = matcher.group(1);
                    if (match != null && match.endsWith("Criterion")) {
                        criteria.add(match);
                    }
                }
            }

            return new RequirementResult(entityName, processors, criteria);
            
        } catch (IOException e) {
            logger.error("❌ Error reading {}: {}", mdFile, e.getMessage());
            return null;
        }
    }
    
    /**
     * Convert directory name to proper entity name by discovering actual workflow file names
     */
    private String convertToEntityName(String entityDirName) {
        if (entityDirName == null || entityDirName.isEmpty()) {
            return entityDirName;
        }

        // First, try to find the actual workflow file to get the correct entity name
        Path entityWorkflowDir = WORKFLOW_DIR.resolve(entityDirName).resolve("version_1");
        if (Files.exists(entityWorkflowDir)) {
            try (Stream<Path> files = Files.list(entityWorkflowDir)) {
                Optional<String> actualEntityName = files
                    .filter(file -> file.toString().endsWith(".json"))
                    .map(file -> file.getFileName().toString().replace(".json", ""))
                    .findFirst();

                if (actualEntityName.isPresent()) {
                    return actualEntityName.get();
                }
            } catch (IOException e) {
                logger.debug("Could not read workflow directory {}: {}", entityWorkflowDir, e.getMessage());
            }
        }

        // Fallback: Generic PascalCase conversion
        String[] parts = entityDirName.toLowerCase().split("[_\\-\\s]+");
        StringBuilder result = new StringBuilder();

        for (String part : parts) {
            if (!part.isEmpty()) {
                result.append(part.substring(0, 1).toUpperCase())
                      .append(part.substring(1));
            }
        }

        return result.toString();
    }
    
    /**
     * Get the workflow JSON file path for an entity
     */
    private Path getWorkflowFileForEntity(String entityName) {
        // Convert entity name to lowercase for directory matching
        String entityDir = entityName.toLowerCase();
        
        // Try different naming conventions
        List<Path> possiblePaths = Arrays.asList(
                WORKFLOW_DIR.resolve(entityDir).resolve("version_1").resolve(entityName + ".json"),
                WORKFLOW_DIR.resolve(entityName.toLowerCase()).resolve("version_1").resolve(entityName + ".json"),
                WORKFLOW_DIR.resolve(entityName).resolve("version_1").resolve(entityName + ".json")
        );
        
        for (Path path : possiblePaths) {
            if (Files.exists(path)) {
                return path;
            }
        }
        
        return null;
    }
    
    /**
     * Extract processor and criteria names from workflow JSON file
     */
    private WorkflowResult extractProcessorsAndCriteriaFromWorkflow(Path workflowFile) {
        try {
            JsonNode workflow = objectMapper.readTree(workflowFile.toFile());
            Set<String> processors = new HashSet<>();
            Set<String> criteria = new HashSet<>();
            
            JsonNode states = workflow.get("states");
            if (states != null && states.isObject()) {
                states.fields().forEachRemaining(stateEntry -> {
                    JsonNode state = stateEntry.getValue();
                    JsonNode transitions = state.get("transitions");
                    if (transitions != null && transitions.isArray()) {
                        transitions.forEach(transition -> {
                            // Extract processors
                            JsonNode transitionProcessors = transition.get("processors");
                            if (transitionProcessors != null && transitionProcessors.isArray()) {
                                transitionProcessors.forEach(processor -> {
                                    JsonNode name = processor.get("name");
                                    if (name != null) {
                                        processors.add(name.asText());
                                    }
                                });
                            }
                            
                            // Extract criteria
                            JsonNode criterion = transition.get("criterion");
                            if (criterion != null) {
                                extractCriteriaRecursively(criterion, criteria);
                            }
                        });
                    }
                });
            }
            
            return new WorkflowResult(processors, criteria);
            
        } catch (IOException e) {
            logger.error("❌ Error reading {}: {}", workflowFile, e.getMessage());
            return null;
        }
    }
    
    /**
     * Recursively extract criteria names from nested criterion structures
     */
    private void extractCriteriaRecursively(JsonNode criterion, Set<String> criteria) {
        if (criterion == null || !criterion.isObject()) {
            return;
        }
        
        // Check if this is a function criterion
        if ("function".equals(criterion.path("type").asText())) {
            JsonNode function = criterion.get("function");
            if (function != null) {
                JsonNode name = function.get("name");
                if (name != null) {
                    criteria.add(name.asText());
                }
            }
        }
        
        // Handle nested criteria in various structures
        for (String key : Arrays.asList("and", "or", "not")) {
            JsonNode nested = criterion.get(key);
            if (nested != null) {
                if (nested.isArray()) {
                    nested.forEach(item -> extractCriteriaRecursively(item, criteria));
                } else if (nested.isObject()) {
                    extractCriteriaRecursively(nested, criteria);
                }
            }
        }
        
        // Handle 'conditions' array (used in group criteria)
        JsonNode conditions = criterion.get("conditions");
        if (conditions != null && conditions.isArray()) {
            conditions.forEach(condition -> extractCriteriaRecursively(condition, criteria));
        }
    }
    
    /**
     * Result holder for requirement data
     */
    private static class RequirementResult {
        final String entityName;
        final Set<String> processors;
        final Set<String> criteria;
        
        RequirementResult(String entityName, Set<String> processors, Set<String> criteria) {
            this.entityName = entityName;
            this.processors = processors;
            this.criteria = criteria;
        }
    }
    
    /**
     * Result holder for workflow data
     */
    private static class WorkflowResult {
        final Set<String> processors;
        final Set<String> criteria;
        
        WorkflowResult(Set<String> processors, Set<String> criteria) {
            this.processors = processors;
            this.criteria = criteria;
        }
    }
}
