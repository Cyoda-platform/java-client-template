package com.java_template.application.processor;

import com.java_template.application.entity.extractionschedule.version_1.ExtractionSchedule;
import com.java_template.application.entity.weeklyreport.version_1.WeeklyReport;
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
public class NotifyOnFailureProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NotifyOnFailureProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public NotifyOnFailureProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("NotifyOnFailure invoked for request: {}", request.getId());

        // The serializer will route to appropriate entity type; we support both schedule and report notifications
        return serializer.withRequest(request)
            .toEntity(ExtractionSchedule.class)
            .validate(this::isValidSchedule, "Invalid ExtractionSchedule for notification")
            .map(ctx -> { notifyScheduleFailure(ctx.entity()); return ctx.entity(); })
            .complete();
    }

    private boolean isValidSchedule(ExtractionSchedule s) { return s != null && s.isValid(); }

    private void notifyScheduleFailure(ExtractionSchedule schedule) {
        try {
            // In prototype, simply log notification. Real implementation would call email service and throttle.
            logger.warn("Notify failure for schedule {}: status={} last_run={}", schedule.getSchedule_id(), schedule.getStatus(), schedule.getLast_run());
            if (schedule.getRecipients() != null) {
                schedule.getRecipients().forEach(r -> logger.info("Would notify recipient: {}", r));
            }
        } catch (Exception ex) {
            logger.error("Error while notifying failure for schedule {}: {}", schedule.getSchedule_id(), ex.getMessage(), ex);
        }
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }
}
