package com.java_template.application.processor;
import com.java_template.application.entity.weeklyjob.version_1.WeeklyJob;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class HandleFailureProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(HandleFailureProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public HandleFailureProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing WeeklyJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(WeeklyJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(WeeklyJob entity) {
        return entity != null && entity.isValid();
    }

    private WeeklyJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<WeeklyJob> context) {
        WeeklyJob entity = context.entity();

        // mark when failure was observed
        String now = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
        entity.setLastRunAt(now);

        String policy = entity.getFailurePolicy();
        if (policy == null || policy.isBlank()) {
            // No policy: escalate immediately
            logger.warn("WeeklyJob [{}] failed but has no failurePolicy defined. Marking as ALERTING.", entity.getId());
            entity.setStatus("ALERTING");
            return entity;
        }

        // Try to detect retry count from policy string (e.g., "retry 3 times then alert")
        Pattern p = Pattern.compile("(\\d+)");
        Matcher m = p.matcher(policy);
        if (m.find()) {
            try {
                int retries = Integer.parseInt(m.group(1));
                if (retries > 0) {
                    int remaining = retries - 1;
                    // update policy to reflect remaining retries
                    String updatedPolicy = policy.replaceFirst("\\d+", String.valueOf(remaining));
                    entity.setFailurePolicy(updatedPolicy);

                    if (remaining > 0) {
                        // Schedule a retry in the near future (simple backoff: +1 hour)
                        String nextRunAt = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS).toString();
                        entity.setNextRunAt(nextRunAt);
                        entity.setStatus("RETRYING");
                        logger.info("WeeklyJob [{}] will retry. Remaining retries: {}. Next run at {}.", entity.getId(), remaining, nextRunAt);
                    } else {
                        // No retries left -> escalate/alert
                        entity.setStatus("ALERTING");
                        logger.info("WeeklyJob [{}] exhausted retries. Moving to ALERTING.", entity.getId());
                    }
                    return entity;
                } else {
                    // already zero retries -> escalate
                    entity.setStatus("ALERTING");
                    logger.info("WeeklyJob [{}] configured with 0 retries. Marking ALERTING.", entity.getId());
                    return entity;
                }
            } catch (NumberFormatException ex) {
                logger.warn("Failed to parse retry count from failurePolicy for WeeklyJob [{}]: {}. Escalating.", entity.getId(), policy);
                entity.setStatus("ALERTING");
                return entity;
            }
        } else {
            // No explicit numeric retry count found. Determine by keyword presence.
            String lower = policy.toLowerCase();
            if (lower.contains("retry")) {
                // ambiguous retry policy: schedule a single retry
                String nextRunAt = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS).toString();
                entity.setNextRunAt(nextRunAt);
                entity.setStatus("RETRYING");
                logger.info("WeeklyJob [{}] has retry instruction without explicit count. Scheduling one retry at {}.", entity.getId(), nextRunAt);
            } else {
                // No retry instruction: escalate
                entity.setStatus("ALERTING");
                logger.info("WeeklyJob [{}] failurePolicy does not request retry. Marking ALERTING.", entity.getId());
            }
            return entity;
        }
    }
}