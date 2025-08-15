package com.java_template.application.processor;

import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ReceiveAdoptionRequestProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReceiveAdoptionRequestProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ReceiveAdoptionRequestProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ReceiveAdoptionRequest for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(AdoptionRequest.class)
            .validate(this::isValidEntity, "Invalid adoption request entity")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(AdoptionRequest entity) {
        return entity != null;
    }

    private AdoptionRequest processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<AdoptionRequest> context) {
        AdoptionRequest ar = context.entity();
        try {
            // Ensure initial state is PENDING and createdAt set
            if (ar.getStatus() == null || ar.getStatus().trim().isEmpty()) {
                ar.setStatus("PENDING");
            }
            if (ar.getTechnicalId() == null || ar.getTechnicalId().trim().isEmpty()) {
                // technical id should be assigned upstream; leave as-is if missing
            }
            if (ar.getCreatedAt() == null || ar.getCreatedAt().trim().isEmpty()) {
                ar.setCreatedAt(java.time.Instant.now().toString());
            }
            logger.info("AdoptionRequest {} persisted with status {}", ar.getTechnicalId(), ar.getStatus());
            // Enqueue screening is handled by workflow engine; processor just ensures entity persisted
        } catch (Exception e) {
            logger.error("Error during ReceiveAdoptionRequestProcessor for request {}: {}", ar == null ? "<null>" : ar.getTechnicalId(), e.getMessage(), e);
        }
        return ar;
    }
}
