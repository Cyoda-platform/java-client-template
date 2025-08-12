package com.java_template.prototype.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.workflow.OperationFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.prototype.workflow.model.WorkflowDefinition;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Test for GenericWorkflowOrchestrator to verify it can load and execute workflows from JSON definitions.
 */
@ExtendWith(MockitoExtension.class)
class GenericWorkflowOrchestratorTest {

    @Mock
    private WorkflowDefinitionLoader workflowLoader;
    
    @Mock
    private OperationFactory operationFactory;
    
    @Mock
    private CyodaEventContext<EntityProcessorCalculationRequest> processorContext;
    
    @Mock
    private CyodaEventContext<EntityCriteriaCalculationRequest> criteriaContext;
    
    private GenericWorkflowOrchestrator orchestrator;
    private ObjectMapper objectMapper;

    /**
     * Checks if the prototype is enabled via system property.
     * This method is used by @EnabledIf to conditionally run the test.
     */
    static boolean isPrototypeEnabled() {
        return "true".equals(System.getProperty("prototype.enabled"));
    }

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        orchestrator = new GenericWorkflowOrchestrator(workflowLoader, operationFactory, "Job");
    }
    
    @Test
    @EnabledIf("isPrototypeEnabled")
    void testGetSupportedEntityModel() {
        assertEquals("Job", orchestrator.getSupportedEntityModel());
    }
    
    @Test
    @EnabledIf("isPrototypeEnabled")
    void testRunWithNoWorkflowDefinition() {
        // Given
        when(workflowLoader.loadWorkflowDefinition("Job")).thenReturn(null);
        
        // When
        String result = orchestrator.run("test-id", processorContext, criteriaContext, "some_transition");
        
        // Then
        assertEquals("error_state", result);
    }
    
    @Test
    @EnabledIf("isPrototypeEnabled")
    void testRunWithInitialState() throws Exception {
        // Given
        String workflowJson = """
            {
              "version": "1.0",
              "name": "Test Workflow",
              "desc": "Test workflow for unit testing",
              "initialState": "SCHEDULED",
              "active": true,
              "states": {
                "SCHEDULED": {
                  "transitions": [
                    {
                      "name": "start_processing",
                      "next": "PROCESSING",
                      "manual": false
                    }
                  ]
                },
                "PROCESSING": {
                  "transitions": []
                }
              }
            }
            """;
        
        WorkflowDefinition workflow = objectMapper.readValue(workflowJson, WorkflowDefinition.class);
        when(workflowLoader.loadWorkflowDefinition("Job")).thenReturn(workflow);
        
        // When
        String result = orchestrator.run("test-id", processorContext, criteriaContext, "state_initial");

        // Then
        // Should execute the entire workflow: SCHEDULED -> start_processing -> PROCESSING
        assertEquals("PROCESSING", result);
    }
    
    @Test
    @EnabledIf("isPrototypeEnabled")
    void testRunWithEntityUpdated() throws Exception {
        // Given
        String workflowJson = """
            {
              "version": "1.0",
              "name": "Test Workflow",
              "desc": "Test workflow for unit testing",
              "initialState": "VALIDATION",
              "active": true,
              "states": {
                "VALIDATION": {
                  "transitions": []
                }
              }
            }
            """;
        
        WorkflowDefinition workflow = objectMapper.readValue(workflowJson, WorkflowDefinition.class);
        when(workflowLoader.loadWorkflowDefinition("Job")).thenReturn(workflow);
        
        // When
        String result = orchestrator.run("test-id", processorContext, criteriaContext, "entity_updated");
        
        // Then
        assertEquals("VALIDATION", result);
    }
    
    @Test
    @EnabledIf("isPrototypeEnabled")
    void testWorkflowDefinitionLoaderCanLoadRealWorkflows() {
        // Given
        WorkflowDefinitionLoader realLoader = new WorkflowDefinitionLoader(new ObjectMapper());

        // When & Then - Test that real workflow files can be loaded
        // Note: These tests depend on the actual workflow files being present in src/main/resources/workflow/
        WorkflowDefinition jobWorkflow = realLoader.loadWorkflowDefinition("Job");
        assertNotNull(jobWorkflow, "Job workflow should be loadable");
        assertTrue(jobWorkflow.isValid(), "Job workflow should be valid");
        assertEquals("SCHEDULED", jobWorkflow.getInitialState());

        WorkflowDefinition laureateWorkflow = realLoader.loadWorkflowDefinition("Laureate");
        assertNotNull(laureateWorkflow, "Laureate workflow should be loadable");
        assertTrue(laureateWorkflow.isValid(), "Laureate workflow should be valid");
        assertEquals("validation", laureateWorkflow.getInitialState());

        WorkflowDefinition subscriberWorkflow = realLoader.loadWorkflowDefinition("Subscriber");
        assertNotNull(subscriberWorkflow, "Subscriber workflow should be loadable");
        assertTrue(subscriberWorkflow.isValid(), "Subscriber workflow should be valid");
        assertEquals("active_check", subscriberWorkflow.getInitialState());
    }
    
    @Test
    @EnabledIf("isPrototypeEnabled")
    void testWorkflowOrchestratorFactoryIntegration() {
        // Given
        WorkflowDefinitionLoader realLoader = new WorkflowDefinitionLoader(new ObjectMapper());
        GenericWorkflowOrchestratorFactory factory = new GenericWorkflowOrchestratorFactory(realLoader, operationFactory);

        // When & Then
        // Test that the factory can determine which entities have workflows
        assertTrue(factory.canCreateOrchestrator("Job"));
        assertTrue(factory.canCreateOrchestrator("Laureate"));
        assertTrue(factory.canCreateOrchestrator("Subscriber"));

        // Non-existent entities should not have workflows
        assertFalse(factory.canCreateOrchestrator("NonExistentEntity"));

        // Test orchestrator creation and caching
        GenericWorkflowOrchestrator jobOrchestrator = factory.getOrchestrator("Job");
        assertNotNull(jobOrchestrator);
        assertEquals("Job", jobOrchestrator.getSupportedEntityModel());

        // Verify caching works
        GenericWorkflowOrchestrator jobOrchestrator2 = factory.getOrchestrator("Job");
        assertSame(jobOrchestrator, jobOrchestrator2);
    }
}
