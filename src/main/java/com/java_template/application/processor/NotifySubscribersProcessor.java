package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.job.version_1.Job;
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
import org.springframework.beans.factory.annotation.Autowired;
import java.util.ArrayList;
import java.util.List;

@Component
public class NotifySubscribersProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NotifySubscribersProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    @Autowired
    public NotifySubscribersProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Notifying subscribers for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid Job entity")
            .map(this::notifySubscribers)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job job) {
        return job != null && ("SUCCEEDED".equals(job.getStatus()) || "FAILED".equals(job.getStatus()));
    }

    private Job notifySubscribers(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        logger.info("Fetching active subscribers for notification");

        try {
            // Build search condition: active == "true"
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.active", "EQUALS", "true")
            );

            // Query active subscribers
            ArrayNode activeSubscribersJson = entityService.getItemsByCondition(
                Subscriber.ENTITY_NAME,
                String.valueOf(Subscriber.ENTITY_VERSION),
                condition,
                true
            ).get();

            List<Subscriber> activeSubscribers = new ArrayList<>();
            for (int i = 0; i < activeSubscribersJson.size(); i++) {
                ObjectNode node = (ObjectNode) activeSubscribersJson.get(i);
                Subscriber subscriber = serializer.deserialize(node.toString(), Subscriber.class);
                activeSubscribers.add(subscriber);
            }

            if (activeSubscribers.isEmpty()) {
                logger.info("No active subscribers to notify");
            } else {
                for (Subscriber subscriber : activeSubscribers) {
                    // Implement notification logic here
                    logger.info("Notifying subscriber: {} via {}", subscriber.getSubscriberId(), subscriber.getContactType());
                    // For email, send email; for webhook, POST to URL. Here, simulate only.
                }
            }

            job.setStatus("NOTIFIED_SUBSCRIBERS");
        } catch (Exception e) {
            logger.error("Error notifying subscribers", e);
            job.setErrorDetails("Notification error: " + e.getMessage());
            // We do not change status here to allow retry or manual fix
        }
        return job;
    }
}
