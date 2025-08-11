package com.java_template.application.processor;

import com.java_template.application.entity.Job;
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

@Component
public class JobProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(JobProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public JobProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job for request: {}", request.getId());

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

    private boolean isValidEntity(Job job) {
        return job != null && job.getJobName() != null && !job.getJobName().trim().isEmpty();
    }

    private Job processEntityLogic(Job job) {
        // Implement state transitions and business logic
        String status = job.getStatus();
        if (status == null) {
            job.setStatus("SCHEDULED");
        } else {
            switch (status) {
                case "SCHEDULED":
                    job.setStatus("INGESTING");
                    break;
                case "INGESTING":
                    // Normally would trigger ingestion logic here
                    // For example: fetch data, create laureates, handle errors
                    // Simulate success for now
                    job.setStatus("SUCCEEDED");
                    job.setResultSummary("Ingestion succeeded");
                    break;
                case "SUCCEEDED":
                case "FAILED":
                    // After completion, notify subscribers
                    job.setStatus("NOTIFIED_SUBSCRIBERS");
                    break;
                case "NOTIFIED_SUBSCRIBERS":
                    // Final state, no further changes
                    break;
                default:
                    // Unknown state, reset to SCHEDULED
                    job.setStatus("SCHEDULED");
                    break;
            }
        }
        return job;
    }
}
