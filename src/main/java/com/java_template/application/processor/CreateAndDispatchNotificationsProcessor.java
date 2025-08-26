package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class CreateAndDispatchNotificationsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateAndDispatchNotificationsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public CreateAndDispatchNotificationsProcessor(SerializerFactory serializerFactory,
                                                   EntityService entityService,
                                                   ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job entity) {
        return entity != null && entity.isValid();
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();

        try {
            // Determine notification policy
            Job.NotificationPolicy np = job.getNotificationPolicy();
            String policyType = np != null ? np.getType() : null;

            // Default: notify all active subscribers
            List<Subscriber> subscribersToNotify = new ArrayList<>();

            // Build condition to fetch active subscribers
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.active", "EQUALS", "true")
            );

            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                Subscriber.ENTITY_NAME,
                String.valueOf(Subscriber.ENTITY_VERSION),
                condition,
                true
            );

            ArrayNode results = filteredItemsFuture.join();
            if (results != null) {
                for (JsonNode node : results) {
                    try {
                        Subscriber subscriber = objectMapper.treeToValue(node, Subscriber.class);
                        // Only include valid/active subscribers
                        if (subscriber != null && Boolean.TRUE.equals(subscriber.getActive()) && subscriber.isValid()) {
                            // If job has filtering in policy in future, apply here.
                            subscribersToNotify.add(subscriber);
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to parse subscriber node for job {}: {}", job.getTechnicalId(), e.getMessage());
                    }
                }
            }

            // Create notification tasks and dispatch (simulation / logging)
            for (Subscriber sub : subscribersToNotify) {
                // In a full implementation we would create a Notification entity via entityService.addItem(...)
                // and let SendNotificationProcessor handle delivery. Here we log the dispatch intent.
                logger.info("Job {} -> creating notification for subscriber {} (technicalId={})",
                    job.getTechnicalId(), sub.getName(), sub.getTechnicalId());

                // Simulate asynchronous dispatch by logging. Real dispatch would enqueue/send and update Notification entity.
            }

            // Finalize job state
            job.setStatus("NOTIFIED_SUBSCRIBERS");
            job.setFinishedAt(Instant.now().toString());

        } catch (Exception e) {
            logger.error("Error while creating/dispatching notifications for job {}: {}", job.getTechnicalId(), e.getMessage(), e);
            // Set finishedAt even in error scenario and mark as NOTIFIED_SUBSCRIBERS to indicate notification attempts completed.
            job.setErrorDetails(e.getMessage());
            job.setFinishedAt(Instant.now().toString());
            job.setStatus("NOTIFIED_SUBSCRIBERS");
        }

        return job;
    }
}