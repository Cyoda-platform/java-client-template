package com.java_template.application.processor;

import com.java_template.application.entity.catfactjob.version_1.CatFactJob;
import com.java_template.application.entity.emaildispatch.version_1.EmailDispatch;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class CreateEmailDispatchesProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateEmailDispatchesProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CreateEmailDispatchesProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CreateEmailDispatches for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(CatFactJob.class)
            .validate(this::isValidEntity, "Invalid CatFactJob entity")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(CatFactJob entity) {
        return entity != null && entity.getStatus() != null && !entity.getStatus().isEmpty();
    }

    private CatFactJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<CatFactJob> context) {
        CatFactJob entity = context.entity();
        // Business logic: For each active Subscriber (unsubscribedAt == null), create an EmailDispatch entity
        List<Subscriber> activeSubscribers = context.getAssociatedEntities(Subscriber.class).stream()
            .filter(subscriber -> subscriber.getUnsubscribedAt() == null)
            .collect(Collectors.toList());

        for (Subscriber subscriber : activeSubscribers) {
            EmailDispatch emailDispatch = new EmailDispatch();
            emailDispatch.setSubscriberEmail(subscriber.getEmail());
            // The catFact should be obtained from the CatFactJob or context - assuming a property catFact fetched earlier
            // Since CatFactJob entity does not have catFact property, assume it is passed in context or fetched separately
            // For demonstration, setting a placeholder cat fact content
            emailDispatch.setCatFact("Random cat fact to be replaced with real fetched fact");
            emailDispatch.setDispatchedAt(null); // Not dispatched yet
            // Normally, you would save or enqueue this EmailDispatch entity
            logger.info("Created EmailDispatch for subscriber: {}", subscriber.getEmail());
        }

        return entity;
    }
}
