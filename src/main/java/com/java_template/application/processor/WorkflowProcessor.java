package com.java_template.application.processor;

import com.java_template.application.entity.Workflow;
import com.java_template.application.entity.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class WorkflowProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;
    private final EntityService entityService;
    private final String className = this.getClass().getSimpleName();

    public WorkflowProcessor(SerializerFactory serializerFactory, ObjectMapper objectMapper, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.objectMapper = objectMapper;
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Workflow for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(Workflow.class)
                .validate(this::isValidEntity, "Invalid workflow state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Workflow entity) {
        return entity != null && entity.isValid();
    }

    private Workflow processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Workflow> context) {
        Workflow workflow = context.entity();

        String technicalId = context.request().getEntityId();

        logger.info("Processing Workflow with id: {}", technicalId);

        try {
            // Validation: Check pet exists and status is "available"
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.technicalId", "EQUALS", workflow.getPetId())
            );

            CompletableFuture<ArrayNode> petsFuture = entityService.getItemsByCondition(
                    Pet.ENTITY_NAME,
                    com.java_template.common.config.Config.ENTITY_VERSION,
                    condition,
                    true
            );

            ArrayNode petNodes = petsFuture.get();
            if (petNodes == null || petNodes.isEmpty()) {
                logger.error("Pet referenced by Workflow {} not found", technicalId);
                workflow.setStatus("FAILED");
                return workflow;
            }

            ObjectNode petNode = (ObjectNode) petNodes.get(0);
            Pet pet = Pet.fromJsonNode(petNode);

            if (!"available".equalsIgnoreCase(pet.getStatus())) {
                logger.error("Pet {} is not available for Workflow {}", workflow.getPetId(), technicalId);
                workflow.setStatus("FAILED");
                return workflow;
            }

            // Simulate triggering adoption request processing or other logic
            workflow.setStatus("COMPLETED");
            logger.info("Workflow {} processing COMPLETED", technicalId);

        } catch (Exception e) {
            logger.error("Exception in processWorkflow", e);
            workflow.setStatus("FAILED");
        }

        return workflow;
    }
}
