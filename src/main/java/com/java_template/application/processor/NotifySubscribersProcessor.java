package com.java_template.application.processor;

import com.java_template.application.entity.job.version_1.Job;
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

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

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
        logger.info("Notifying subscribers for Job request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid Job state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job job) {
        return job != null && job.getStatus() != null;
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        try {
            // Fetch all active subscribers
            CompletableFuture<List<Subscriber>> futureSubscribers = entityService.getItemsByCondition(
                Subscriber.ENTITY_NAME,
                String.valueOf(Subscriber.ENTITY_VERSION),
                SearchConditionRequest.group("AND",
                    Condition.of("$.active", "EQUALS", true)
                ),
                true
            ).thenApply(arrayNode -> {
                return arrayNode.findValuesAsText("") // We need to convert ArrayNode to List<Subscriber>
                    .stream()
                    .map(json -> {
                        try {
                            return serializer.getObjectMapper().treeToValue(json, Subscriber.class);
                        } catch (Exception e) {
                            logger.error("Error parsing subscriber JSON", e);
                            return null;
                        }
                    })
                    .filter(subscriber -> subscriber != null)
                    .collect(Collectors.toList());
            });

            List<Subscriber> activeSubscribers = futureSubscribers.join();

            // Simulate notification sending
            for (Subscriber subscriber : activeSubscribers) {
                logger.info("Notifying subscriber {} at {}", subscriber.getContactType(), subscriber.getContactAddress());
                // Real implementation could send email or HTTP webhook here
            }

            // Update job status to NOTIFIED_SUBSCRIBERS
            job.setStatus("NOTIFIED_SUBSCRIBERS");

        } catch (Exception ex) {
            logger.error("Error notifying subscribers", ex);
            // Optionally handle failure scenario
        }
        return job;
    }
}
