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
public class StartScheduleProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StartScheduleProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public StartScheduleProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Starting schedule for request: {}", request.getId());

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
        try {
            schedule.setStatus("RUNNING");
            // update timestamp handled by persistence layer
            logger.info("Schedule {} set to RUNNING", schedule.getSchedule_id());
            // In real implementation this would trigger FetchFromPetStoreProcessor asynchronously
        } catch (Exception ex) {
            logger.error("Failed to start schedule {}: {}", schedule.getSchedule_id(), ex.getMessage(), ex);
            schedule.setStatus("FAILED");
        }
        return schedule;
    }
}
