package com.java_template.application.processor;

import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
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

import java.util.List;
import java.util.stream.Collectors;

@Component
public class JobNotificationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(JobNotificationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public JobNotificationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job notification for request: {}", request.getId());

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
        return entity != null && entity.getJobId() != null && entity.getStatus() != null;
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        // Business logic for notifying subscribers of successful ingestion
        logger.info("Notifying subscribers for job {}", job.getJobId());

        // Simulate fetching active subscribers and notifying
        List<Subscriber> activeSubscribers = fetchActiveSubscribers();
        activeSubscribers.forEach(subscriber -> {
            logger.info("Notifying subscriber {} via {}", subscriber.getSubscriberId(), subscriber.getContactType());
            // Simulate notification logic here
        });
        // Update job status to NOTIFIED_SUBSCRIBERS
        job.setStatus("NOTIFIED_SUBSCRIBERS");
        return job;
    }

    private List<Subscriber> fetchActiveSubscribers() {
        // Simulated method to fetch active subscribers
        // In real implementation, query persistence layer
        return List.of();
    }
}
