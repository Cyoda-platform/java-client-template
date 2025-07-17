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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/prototype")
@Validated
public class WorkflowOrchestrationPrototype {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowOrchestrationPrototype.class);

    private final Map<String, CyodaProcessor> processors;
    private final Map<String, CyodaCriterion> criteria;

    public WorkflowOrchestrationPrototype(List<CyodaProcessor> processors, List<CyodaCriterion> criteria) {
        this.processors = processors.stream()
                .collect(Collectors.toMap(p -> p.getClass().getSimpleName(), p -> p));
        this.criteria = criteria.stream()
                .collect(Collectors.toMap(c -> c.getClass().getSimpleName(), c -> c));
        logger.info("WorkflowOrchestrationPrototype initialized with processors: {} and criteria: {}",
                this.processors.keySet(), this.criteria.keySet());
    }

    // DTO for POST entity data - flat structure with validation, all fields optional except id for demonstration
    public static class EntityDataDTO {
        @Size(max = 100)
        private String id;

        @Size(max = 100)
        private String status;

        @Size(max = 100)
        private String type;

        @Size(max = 255)
        private String content;

        @Size(max = 50)
        private String format;

        // Other fields can be added as Strings with validation if needed

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }

        public String getFormat() { return format; }
        public void setFormat(String format) { this.format = format; }
    }

    @PostMapping("/entities/{entityType}")
    public ResponseEntity<Map<String, Object>> processEntity(
            @PathVariable @NotBlank String entityType,
            @RequestBody @Valid EntityDataDTO entityData) {

        logger.info("Received request to process entityType={} with entityData={}", entityType, entityData);

        String entityId = imitateSaveEntity(entityType, entityData);

        List<String> workflowResults = executeEntityWorkflow(entityType, entityData);

        Map<String, Object> response = new HashMap<>();
        response.put("entityId", entityId);
        response.put("workflowResults", workflowResults);

        return ResponseEntity.ok(response);
    }

    private String imitateSaveEntity(String entityType, EntityDataDTO entityData) {
        // Simulate generating a unique ID if missing
        String id = entityData.getId();
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        logger.info("Entity of type '{}' saved with ID '{}'", entityType, id);
        return id;
    }

    private List<String> executeEntityWorkflow(String entityType, EntityDataDTO entityData) {
        List<String> results = new ArrayList<>();
        logger.info("Executing workflow for entityType={}", entityType);

        // Convert DTO to matching entity for processing (dummy minimal mapping)
        switch (entityType.toLowerCase()) {
            case "digestrequest": {
                DigestRequest entity = new DigestRequest();
                entity.setStatus(entityData.getStatus());
                entity.setType(entityData.getType());
                entity.setContent(entityData.getContent());

                // DigestRequestProcessor
                CyodaProcessor processor = processors.get("DigestRequestProcessor");
                if (processor != null) {
                    processor.process(createProcessingContext(entity));
                    results.add("DigestRequestProcessor executed");
                }

                // No criteria in workflow for DigestRequest in prototype

                break;
            }
            case "digestdata": {
                DigestData entity = new DigestData();
                entity.setContent(entityData.getContent());
                entity.setFormat(entityData.getFormat());

                CyodaProcessor processor = processors.get("DigestDataProcessor");
                if (processor != null) {
                    processor.process(createProcessingContext(entity));
                    results.add("DigestDataProcessor executed");
                }

                // No criteria in workflow for DigestData in prototype

                break;
            }
            case "emaildispatch": {
                EmailDispatch entity = new EmailDispatch();
                entity.setStatus(entityData.getStatus());
                entity.setContent(entityData.getContent());

                CyodaProcessor processor = processors.get("EmailDispatchProcessor");
                if (processor != null) {
                    processor.process(createProcessingContext(entity));
                    results.add("EmailDispatchProcessor executed");
                }

                // No criteria in workflow for EmailDispatch in prototype

                break;
            }
            default:
                logger.warn("Unknown entityType '{}'; no processors executed", entityType);
                results.add("No processors executed for unknown entityType");
                break;
        }

        return results;
    }

    private <T> CyodaEventContext<EntityProcessorCalculationRequest> createProcessingContext(T entity) {
        // Dummy stub context - in real scenario would build from actual event/request
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        // request.setId(...) set if needed

        return new CyodaEventContext<>() {
            @Override
            public EntityProcessorCalculationRequest getEvent() {
                return request;
            }

            @Override
            public <R> R getAttribute(String key, Class<R> clazz) {
                return null;
            }

            @Override
            public void setAttribute(String key, Object value) {
            }
        };
    }

    private <T> CyodaEventContext<EntityCriteriaCalculationRequest> createCriteriaContext(T entity) {
        // Dummy stub context - criteria not used in current prototype
        EntityCriteriaCalculationRequest request = new EntityCriteriaCalculationRequest();

        return new CyodaEventContext<>() {
            @Override
            public EntityCriteriaCalculationRequest getEvent() {
                return request;
            }

            @Override
            public <R> R getAttribute(String key, Class<R> clazz) {
                return null;
            }

            @Override
            public void setAttribute(String key, Object value) {
            }
        };
    }
}