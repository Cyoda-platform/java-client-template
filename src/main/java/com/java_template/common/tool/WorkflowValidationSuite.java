package com.java_template.common.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * ABOUTME: Comprehensive validation suite that runs both workflow validation tools:
 * 1. WorkflowImplementationValidator - validates workflow implementations exist as Java classes
 * 2. FunctionalRequirementsValidator - validates functional requirements are implemented in workflows
 * 
 * This tool provides a single entry point for complete workflow consistency validation
 * and reports overall success/failure status for CI/CD integration.
 */
public class WorkflowValidationSuite {
    private static final Logger logger = LoggerFactory.getLogger(WorkflowValidationSuite.class);
    
    private final ObjectMapper objectMapper;
    
    public WorkflowValidationSuite(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    public static void main(String[] args) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
        context.refresh();
        
        ObjectMapper objectMapper = context.getBean(ObjectMapper.class);
        WorkflowValidationSuite suite = new WorkflowValidationSuite(objectMapper);
        
        boolean isValid = suite.runAllValidations();
        
        context.close();
        System.exit(isValid ? 0 : 1);
    }
    
    /**
     * Run all validation tools and return overall success status
     */
    public boolean runAllValidations() {
        logger.info("🚀 Running comprehensive workflow validation...");
        logger.info("================================================================================");
        
        boolean allPassed = true;
        
        // Run Workflow Implementation Validation
        logger.info("");
        logger.info("🔍 Workflow Implementation Validation");
        logger.info("--------------------------------------------------------------------------------");
        
        WorkflowImplementationValidator implementationValidator = new WorkflowImplementationValidator(objectMapper);
        boolean implementationValid = implementationValidator.validateWorkflowImplementations();
        
        if (implementationValid) {
            logger.info("✅ Workflow Implementation Validation PASSED");
        } else {
            logger.error("❌ Workflow Implementation Validation FAILED");
            allPassed = false;
        }
        
        // Run Functional Requirements Validation
        logger.info("");
        logger.info("🔍 Functional Requirements Validation");
        logger.info("--------------------------------------------------------------------------------");
        
        FunctionalRequirementsValidator requirementsValidator = new FunctionalRequirementsValidator(objectMapper);
        boolean requirementsValid = requirementsValidator.validateFunctionalRequirements();
        
        if (requirementsValid) {
            logger.info("✅ Functional Requirements Validation PASSED");
        } else {
            logger.error("❌ Functional Requirements Validation FAILED");
            allPassed = false;
        }
        
        // Final Results
        logger.info("");
        logger.info("================================================================================");
        logger.info("🏁 FINAL VALIDATION RESULTS");
        logger.info("================================================================================");
        
        if (allPassed) {
            logger.info("🎉 ALL VALIDATIONS PASSED!");
            logger.info("✅ Workflow implementations are complete");
            logger.info("✅ Functional requirements are properly implemented");
            logger.info("");
            logger.info("💡 Your Cyoda application is ready for deployment!");
        } else {
            logger.error("❌ SOME VALIDATIONS FAILED!");
            logger.error("🔧 Please review the errors above and fix the issues");
            logger.info("");
            logger.info("💡 Common fixes:");
            logger.info("   - Create missing processor/criterion Java classes");
            logger.info("   - Add missing workflow definitions to JSON files");
            logger.info("   - Ensure naming consistency between requirements, workflows, and implementations");
        }
        
        return allPassed;
    }
    
    /**
     * Run only the workflow implementation validation
     */
    public boolean runImplementationValidation() {
        logger.info("🔍 Running Workflow Implementation Validation...");
        WorkflowImplementationValidator validator = new WorkflowImplementationValidator(objectMapper);
        return validator.validateWorkflowImplementations();
    }
    
    /**
     * Run only the functional requirements validation
     */
    public boolean runRequirementsValidation() {
        logger.info("🔍 Running Functional Requirements Validation...");
        FunctionalRequirementsValidator validator = new FunctionalRequirementsValidator(objectMapper);
        return validator.validateFunctionalRequirements();
    }
}
