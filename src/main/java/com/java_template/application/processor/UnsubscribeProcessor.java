package com.java_template.application.processor;

import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@Component
public class UnsubscribeProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(UnsubscribeProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public UnsubscribeProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Subscriber unsubscription for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Subscriber.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
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
        Subscriber sub = context.entity();
        try {
            logger.info("UnsubscribeProcessor starting for subscriberTechnicalId={}", sub.getTechnicalId());
            sub.setSubscription_status("UNSUBSCRIBED");
            sub.setLast_verified_at(Instant.now().toString());

            // persist update via entityService
            try {
                CompletableFuture<java.util.UUID> updated = entityService.updateItem(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    java.util.UUID.fromString(sub.getTechnicalId()),
                    sub
                );
                updated.get();
            } catch (Exception e) {
                logger.error("Failed to update Subscriber {} during unsubscribe: {}", sub.getTechnicalId(), e.getMessage(), e);
            }

            return sub;
        } catch (Exception ex) {
            logger.error("Unexpected error while unsubscribing subscriber {}: {}", sub.getTechnicalId(), ex.getMessage(), ex);
            return sub;
        }
    }
}
