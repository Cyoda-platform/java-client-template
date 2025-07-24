package com.java_template.application.processor;

import com.java_template.application.entity.Workflow;
import com.java_template.application.entity.Order;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class WorkflowProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final com.java_template.common.service.EntityService entityService;

    public WorkflowProcessor(SerializerFactory serializerFactory, com.java_template.common.service.EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("WorkflowProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Workflow for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(Workflow.class)
                .validate(workflow -> workflow.getName() != null && !workflow.getName().isBlank(), "Workflow name is required")
                .validate(Workflow::isValid, "Workflow entity validation failed")
                .map(this::processWorkflow)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "WorkflowProcessor".equals(modelSpec.operationName()) &&
                "workflow".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private Workflow processWorkflow(Workflow workflow) {
        try {
            logger.info("Processing Workflow with name: {}", workflow.getName());
            if (workflow.getParameters() == null || workflow.getParameters().isEmpty()) {
                logger.error("Workflow parameters are missing");
                workflow.setStatus("FAILED");
                return workflow;
            }
            workflow.setStatus("RUNNING");
            Object orderIdObj = workflow.getParameters().get("orderId");
            if (orderIdObj instanceof String) {
                String orderId = (String) orderIdObj;
                Order order = new Order();
                order.setOrderId(orderId);
                order.setCustomerId("unknown");
                order.setItems(new ArrayList<>());
                order.setShippingAddress("");
                order.setPaymentMethod("");
                order.setCreatedAt(new Date().toInstant().toString());
                order.setStatus("CREATED");
                try {
                    CompletableFuture<UUID> orderIdFuture = entityService.addItem(
                            "order",
                            Config.ENTITY_VERSION,
                            order);
                    UUID technicalOrderId = orderIdFuture.get();
                    logger.info("Workflow created Order with technicalId: {}", technicalOrderId);
                    // Simulate processOrder call (business logic should be in OrderProcessor)
                    // Here just logging
                    logger.info("Processed Order in WorkflowProcessor: {}", order.getOrderId());
                } catch (Exception e) {
                    logger.error("Failed to create order in processWorkflow", e);
                    workflow.setStatus("FAILED");
                    return workflow;
                }
            } else {
                logger.error("orderId parameter is missing or invalid");
                workflow.setStatus("FAILED");
                return workflow;
            }
            workflow.setStatus("COMPLETED");
            logger.info("Workflow processing completed for: {}", workflow.getName());
        } catch (Exception e) {
            logger.error("Error processing workflow", e);
            workflow.setStatus("FAILED");
        }
        return workflow;
    }
}
