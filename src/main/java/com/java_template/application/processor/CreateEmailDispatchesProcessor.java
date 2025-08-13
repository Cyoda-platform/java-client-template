package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.catfactjob.version_1.CatFactJob;
import com.java_template.application.entity.emaildispatch.version_1.EmailDispatch;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Component
public class CreateEmailDispatchesProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateEmailDispatchesProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public CreateEmailDispatchesProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
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
        // Business logic: For each active Subscriber (unsubscribedAt == null), create an EmailDispatch entity with catFact

        // Fetch active subscribers
        CompletableFuture<ArrayNode> activeSubscribersFuture = entityService.getItemsByCondition(
            Subscriber.ENTITY_NAME,
            String.valueOf(Subscriber.ENTITY_VERSION),
            SearchConditionRequest.group("AND",
                Condition.of("$.unsubscribedAt", "EQUALS", null)
            ),
            true
        );

        try {
            ArrayNode activeSubscribers = activeSubscribersFuture.get();
            String catFact = entity.getStatus().equals("fact_retrieved") && entity.getCatFact() != null ? entity.getCatFact() : "No cat fact available";

            for (int i = 0; i < activeSubscribers.size(); i++) {
                ObjectNode subscriberNode = (ObjectNode) activeSubscribers.get(i);
                String email = subscriberNode.path("email").asText(null);
                if (email == null) {
                    logger.warn("Subscriber entity missing email field, skipping");
                    continue;
                }

                EmailDispatch emailDispatch = new EmailDispatch();
                emailDispatch.setSubscriberEmail(email);
                emailDispatch.setCatFact(catFact);
                emailDispatch.setDispatchedAt(null); // Not dispatched yet

                CompletableFuture<UUID> addFuture = entityService.addItem(
                    EmailDispatch.ENTITY_NAME,
                    String.valueOf(EmailDispatch.ENTITY_VERSION),
                    emailDispatch
                );
                UUID dispatchId = addFuture.get();
                logger.info("Created EmailDispatch {} for subscriber: {}", dispatchId, email);
            }

            entity.setStatus("COMPLETED");
        } catch (Exception e) {
            logger.error("Error creating email dispatches", e);
            entity.setStatus("FAILED");
        }

        return entity;
    }
}
