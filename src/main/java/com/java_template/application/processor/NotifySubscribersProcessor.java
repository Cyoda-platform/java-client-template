package com.java_template.application.processor;

import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Component
public class NotifySubscribersProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NotifySubscribersProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public NotifySubscribersProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job for notification request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid Job entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job entity) {
        return entity != null && entity.getStatus() != null;
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        logger.info("Starting notification process for job: {}", job.getJobName());

        try {
            // Query active subscribers
            CompletableFuture<ArrayNode> future = entityService.getItemsByCondition(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    "$.active", true
            );

            ArrayNode activeSubscribersNode = future.join();

            List<Subscriber> activeSubscribers = activeSubscribersNode.findValuesAsText("subscriberId").stream()
                    .map(id -> {
                        ObjectNode subscriberNode = (ObjectNode) activeSubscribersNode.findValue(id);
                        Subscriber s = new Subscriber();
                        s.setSubscriberId(subscriberNode.path("subscriberId").asText());
                        s.setContactType(subscriberNode.path("contactType").asText());
                        s.setContactAddress(subscriberNode.path("contactAddress").asText());
                        s.setActive(subscriberNode.path("active").asBoolean());
                        return s;
                    })
                    .collect(Collectors.toList());

            for (Subscriber subscriber : activeSubscribers) {
                // Simulate notification logic
                logger.info("Notifying subscriber: {} at {}", subscriber.getSubscriberId(), subscriber.getContactAddress());
            }

            job.setStatus("NOTIFIED_SUBSCRIBERS");
            logger.info("Job status updated to NOTIFIED_SUBSCRIBERS");
        } catch (Exception e) {
            logger.error("Error during notification process", e);
        }

        return job;
    }
}
