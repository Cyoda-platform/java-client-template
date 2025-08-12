package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class PersistSubscriberProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistSubscriberProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    @Autowired
    public PersistSubscriberProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Persisting subscriber for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Subscriber.class)
            .validate(this::isValidEntity, "Invalid Subscriber entity")
            .map(this::persistSubscriber)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Subscriber subscriber) {
        if (subscriber == null) {
            logger.error("Subscriber entity is null");
            return false;
        }
        if (subscriber.getContactType() == null || subscriber.getContactType().isEmpty()) {
            logger.error("ContactType is required");
            return false;
        }
        if (subscriber.getContactDetails() == null || subscriber.getContactDetails().isEmpty()) {
            logger.error("ContactDetails is required");
            return false;
        }
        // Additional validation for email or webhook format could be added here
        return true;
    }

    private Subscriber persistSubscriber(ProcessorSerializer.ProcessorEntityExecutionContext<Subscriber> context) {
        Subscriber subscriber = context.entity();
        try {
            CompletableFuture<UUID> idFuture = entityService.addItem(
                Subscriber.ENTITY_NAME,
                String.valueOf(Subscriber.ENTITY_VERSION),
                subscriber
            );
            UUID id = idFuture.get();
            logger.info("Persisted Subscriber with generated id: {}", id);
        } catch (Exception e) {
            logger.error("Error persisting Subscriber", e);
        }
        return subscriber;
    }
}
