package com.java_template.application.processor;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class ResumeProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ResumeProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ResumeProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Subscriber for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Subscriber.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }
    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Subscriber entity) {
        return entity != null && entity.isValid();
    }

    private Subscriber processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Subscriber> context) {
        Subscriber entity = context.entity();
        
        // Business logic:
        // Resume a suspended subscriber by setting active = true.
        // Do not perform any external entity add/update/delete for this triggering entity;
        // simply modify its state — Cyoda will persist the change automatically.
        if (entity == null) {
            logger.warn("Received null Subscriber entity in ResumeProcessor context");
            return entity;
        }

        String sid = null;
        try {
            sid = entity.getSubscriberId();
        } catch (Exception e) {
            // safe guard - subscriberId might be absent or throw; proceed without it
            logger.debug("Could not obtain subscriberId: {}", e.getMessage());
        }

        Boolean currentlyActive = entity.getActive();
        if (currentlyActive != null && currentlyActive) {
            logger.info("Subscriber already active (no-op resume): {}", sid);
            return entity;
        }

        // Set subscriber to active
        entity.setActive(Boolean.TRUE);
        logger.info("Subscriber resumed and set to active: {}", sid);

        // Do not modify other fields (lastNotifiedAt, channels, filters, name)
        // Validation will be enforced by the framework after this processor completes.

        return entity;
    }
}