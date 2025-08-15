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

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;

@Component
public class RescheduleProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RescheduleProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public RescheduleProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Rescheduling for request: {}", request.getId());

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
            // Compute next run based on frequency/day/time/timezone
            // Prototype: for weekly frequency compute next occurrence of given day/time
            if ("weekly".equalsIgnoreCase(schedule.getFrequency())) {
                ZoneId zone = ZoneId.of(schedule.getTimezone());
                ZonedDateTime now = ZonedDateTime.now(zone);
                ZonedDateTime next = now.plusWeeks(1).with(java.time.DayOfWeek.valueOf(schedule.getDay().toUpperCase()))
                    .withHour(Integer.parseInt(schedule.getTime().split(":")[0]))
                    .withMinute(Integer.parseInt(schedule.getTime().split(":")[1]))
                    .withSecond(0).withNano(0);
                schedule.setLast_run(schedule.getLast_run()); // keep last_run untouched here
                schedule.setStatus("SCHEDULED");
                logger.info("Schedule {} rescheduled next run to {}", schedule.getSchedule_id(), next.format(DateTimeFormatter.ISO_ZONED_DATE_TIME));
            } else {
                // other frequencies not implemented in prototype
                schedule.setStatus("SCHEDULED");
            }
        } catch (Exception ex) {
            logger.error("Error while rescheduling {}: {}", schedule.getSchedule_id(), ex.getMessage(), ex);
            schedule.setStatus("FAILED");
        }
        return schedule;
    }
}
