package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.application.entity.delivery.version_1.Delivery;
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

import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class BuildDeliveryBatchProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(BuildDeliveryBatchProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public BuildDeliveryBatchProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing BuildDeliveryBatch for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job entity) {
        return entity != null && entity.isValid();
    }

    @SuppressWarnings("unchecked")
    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        try {
            // For prototype, simulate discovering active subscribers and creating Delivery placeholders
            List<Subscriber> subscribers = List.of();
            // In a real implementation we would query Subscriber entities via EntityService for active subscribers.
            // Here we simulate creating deliveries for a hypothetical set of subscribers.
            List<Delivery> created = new ArrayList<>();
            for (int i = 1; i <= 2; i++) {
                Delivery d = new Delivery();
                d.setJobId(job.getId());
                d.setSubscriberId("subscriber-" + i);
                d.setFactId(null);
                d.setScheduledAt(ZonedDateTime.now(ZoneId.of("UTC")).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
                d.setAttempts(0);
                d.setStatus("PENDING");
                d.setRetriesPolicy(job.getRetriesPolicy() != null ? job.getRetriesPolicy() : Map.of("maxRetries", 2));
                created.add(d);
                logger.info("Created Delivery placeholder for subscriber {}", d.getSubscriberId());
            }
            ObjectNode summary = job.getResultSummary();
            if (summary != null) {
                summary.put("queued", created.size() + summary.path("queued").asInt(0));
            }
        } catch (Exception ex) {
            logger.error("Error building delivery batch for Job {}: {}", job.getId(), ex.getMessage(), ex);
        }
        return job;
    }
}
