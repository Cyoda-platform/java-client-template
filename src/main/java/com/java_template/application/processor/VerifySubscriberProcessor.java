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
public class VerifySubscriberProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(VerifySubscriberProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public VerifySubscriberProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Subscriber verification for request: {}", request.getId());

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
            logger.info("VerifySubscriberProcessor starting for subscriberTechnicalId={}", sub.getTechnicalId());
            sub.setSubscription_status("VERIFYING");

            // Simple auto-verify rule: emails containing "@" and not from disposable domains pass
            String email = sub.getEmail();
            if (email == null || !email.contains("@") || email.endsWith("@disposable.test")) {
                sub.setSubscription_status("UNVERIFIED");
                logger.info("Subscriber {} verification failed", sub.getTechnicalId());
            } else {
                sub.setSubscription_status("ACTIVE");
                sub.setLast_verified_at(Instant.now().toString());
                logger.info("Subscriber {} verified and activated", sub.getTechnicalId());
            }

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
                logger.error("Failed to update Subscriber {} after verification: {}", sub.getTechnicalId(), e.getMessage(), e);
                sub.setSubscription_status("UNVERIFIED");
            }

            return sub;
        } catch (Exception ex) {
            logger.error("Unexpected error while verifying subscriber {}: {}", sub.getTechnicalId(), ex.getMessage(), ex);
            sub.setSubscription_status("UNVERIFIED");
            return sub;
        }
    }
}
