package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.catfactjob.version_1.CatFactJob;
import com.java_template.application.entity.emaildispatch.version_1.EmailDispatch;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.config.Config;
import com.java_template.common.service.EntityService;
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

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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
        logger.info("Processing CreateEmailDispatchesProcessor for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(CatFactJob.class)
                .validate(this::isValidEntity, "Invalid entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(CatFactJob entity) {
        return entity != null && entity.isValid();
    }

    private CatFactJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<CatFactJob> context) {
        CatFactJob entity = context.entity();

        try {
            // Fetch all active subscribers
            CompletableFuture<ArrayNode> activeSubscribersFuture = entityService.getItemsByCondition(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    Config.SearchConditionRequest.group("AND",
                            Config.Condition.of("$.status", "EQUALS", "active")
                    ),
                    true
            );
            ArrayNode activeSubscribers = activeSubscribersFuture.get();

            // Determine cat fact string
            String catFact = entity.getStatus() != null && entity.getStatus().equalsIgnoreCase("fact_retrieved")
                    && entity.getFact() != null ? entity.getFact() : "No cat fact available";

            // Create EmailDispatch entities for each active subscriber
            for (int i = 0; i < activeSubscribers.size(); i++) {
                ObjectNode subscriberNode = (ObjectNode) activeSubscribers.get(i);
                Subscriber subscriber = context.serializer().toEntity(subscriberNode, Subscriber.class);

                EmailDispatch emailDispatch = new EmailDispatch();
                emailDispatch.setStatus("pending");
                emailDispatch.setSubscriberId(subscriber.getId());
                emailDispatch.setCatFact(catFact);

                // Persist EmailDispatch
                entityService.addItem(
                        EmailDispatch.ENTITY_NAME,
                        String.valueOf(EmailDispatch.ENTITY_VERSION),
                        emailDispatch
                );
            }

            entity.setStatus("completed");
        } catch (Exception e) {
            logger.error("Error creating email dispatches", e);
            entity.setStatus("failed");
        }

        return entity;
    }
}
