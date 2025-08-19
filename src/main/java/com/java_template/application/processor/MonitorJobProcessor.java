package com.java_template.application.processor;

import com.java_template.application.entity.job.version_1.Job;
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
public class MonitorJobProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MonitorJobProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public MonitorJobProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing MonitorJob for request: {}", request.getId());

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

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        try {
            // For prototype, simple monitor: if running and resultSummary indicates processed > 0, mark as COMPLETED
            if ("RUNNING".equalsIgnoreCase(job.getStatus()) && job.getResultSummary() != null) {
                Object processed = job.getResultSummary().get("processed");
                if (processed instanceof Number && ((Number) processed).intValue() > 0) {
                    job.setStatus("COMPLETED");
                    logger.info("Job {} marked COMPLETED by MonitorJobProcessor", job.getId());
                }
            }
        } catch (Exception ex) {
            logger.error("Error monitoring Job {}: {}", job.getId(), ex.getMessage(), ex);
        }
        return job;
    }
}
