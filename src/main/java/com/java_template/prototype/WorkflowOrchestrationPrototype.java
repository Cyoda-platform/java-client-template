package com.java_template.prototype;

import com.java_template.application.entity.DigestData;
import com.java_template.application.entity.DigestRequest;
import com.java_template.application.entity.EmailDispatch;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.stereotype.RestController;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/prototype")
public class WorkflowOrchestrationPrototype {

    private final Map<String, CyodaProcessor> processors;
    private final Map<String, CyodaCriterion> criteria;

    public WorkflowOrchestrationPrototype(List<CyodaProcessor> processors, List<CyodaCriterion> criteria) {
        this.processors = processors.stream()
                .collect(Collectors.toMap(
                        p -> p.getClass().getSimpleName(),
                        p -> p
                ));
        this.criteria = criteria.stream()
                .collect(Collectors.toMap(
                        c -> c.getClass().getSimpleName(),
                        c -> c
                ));
    }

    @PostMapping("/entities/{entityType}")
    public ResponseEntity<Map<String, Object>> processEntity(
            @PathVariable String entityType,
            @RequestBody Map<String, Object> entityData) {

        // 1. Imitate saving entity
        String entityId = imitateSaveEntity(entityType, entityData);

        // 2. Execute entity workflow (key EDA pattern)
        List<String> results = executeEntityWorkflow(entityType, entityData);

        return ResponseEntity.ok(Map.of(
                "entityId", entityId,
                "workflowResults", results
        ));
    }

    private List<String> executeEntityWorkflow(String entityType, Map<String, Object> entityData) {
        List<String> results = new ArrayList<>();

        // Example workflows based on your generated processors and criteria
        if ("digestRequest".equals(entityType)) {
            CyodaProcessor processor1 = processors.get("DigestRequestProcessor");
            if (processor1 != null) {
                processor1.process(createProcessingContext(entityData));
                results.add("DigestRequestProcessor executed");
            }
            // No criteria defined for DigestRequest in workflow
        } else if ("digestData".equals(entityType)) {
            CyodaProcessor processor1 = processors.get("DigestDataProcessor");
            if (processor1 != null) {
                processor1.process(createProcessingContext(entityData));
                results.add("DigestDataProcessor executed");
            }
            // No criteria defined for DigestData in workflow
        } else if ("emailDispatch".equals(entityType)) {
            CyodaProcessor processor1 = processors.get("EmailDispatchProcessor");
            if (processor1 != null) {
                processor1.process(createProcessingContext(entityData));
                results.add("EmailDispatchProcessor executed");
            }
            // No criteria defined for EmailDispatch in workflow
        } else {
            results.add("No workflow defined for entity type: " + entityType);
        }

        return results;
    }

    private String imitateSaveEntity(String entityType, Map<String, Object> entityData) {
        // Simulate saving by generating a random UUID
        return UUID.randomUUID().toString();
    }

    private CyodaEventContext<EntityProcessorCalculationRequest> createProcessingContext(Map<String, Object> entityData) {
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        // Here you would map entityData to request fields as needed
        return new CyodaEventContext<>(request);
    }

    private CyodaEventContext<EntityCriteriaCalculationRequest> createCriteriaContext(Map<String, Object> entityData) {
        EntityCriteriaCalculationRequest request = new EntityCriteriaCalculationRequest();
        // Map entityData to criteria request fields as needed
        return new CyodaEventContext<>(request);
    }

}
