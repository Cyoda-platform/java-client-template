package com.java_template.application.processor;

import com.java_template.application.entity.extractionjob.version_1.ExtractionJob;
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

import java.time.OffsetDateTime;

@Component
public class ScheduleTriggerProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ScheduleTriggerProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ScheduleTriggerProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ScheduleTrigger for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ExtractionJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ExtractionJob entity) {
        return entity != null;
    }

    private ExtractionJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ExtractionJob> context) {
        ExtractionJob job = context.entity();
        try {
            // If immediateStart or schedule triggers, set status to RUNNING and set lastRunAt
            boolean immediate = Boolean.TRUE.equals(job.getImmediateStart());
            logger.info("Schedule trigger for jobId={} immediateStart={}", job.getJobId(), immediate);
            job.setLastRunAt(OffsetDateTime.now().toString());
            job.setStatus("RUNNING");
        } catch (Exception e) {
            logger.error("Error in ScheduleTriggerProcessor for jobId={}", job != null ? job.getJobId() : "<unknown>", e);
            if (job != null && job.getFailureReason() == null) job.setFailureReason("SCHEDULE_TRIGGER_ERROR");
        }
        return job;
    }
}
