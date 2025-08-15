package com.java_template.application.processor;

import com.java_template.application.entity.extractionschedule.version_1.ExtractionSchedule;
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
public class PersistScheduleProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistScheduleProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public PersistScheduleProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ExtractionSchedule for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ExtractionSchedule.class)
            .validate(this::isValidEntity, "Invalid ExtractionSchedule state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ExtractionSchedule entity) {
        return entity != null && entity.isValid();
    }

    private ExtractionSchedule processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ExtractionSchedule> context) {
        ExtractionSchedule schedule = context.entity();
        // Ensure minimal required state for a newly persisted schedule
        try {
            if (schedule.getStatus() == null || schedule.getStatus().isBlank()) {
                schedule.setStatus("CREATED");
            }
            // created_on/updated_on are not present on the entity class; rely on persistence hooks
            // last_run should be null for a newly persisted schedule
            schedule.setLast_run(null);
            logger.info("Persisted schedule_id={}, status={}", schedule.getSchedule_id(), schedule.getStatus());
        } catch (Exception ex) {
            logger.error("Error while persisting schedule {}: {}", schedule.getSchedule_id(), ex.getMessage(), ex);
            // Do not throw - allow serializer to complete and persist entity state change
        }
        return schedule;
    }
}
